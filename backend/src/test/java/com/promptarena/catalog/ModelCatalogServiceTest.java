package com.promptarena.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderCatalogEntry;
import com.promptarena.dto.ProviderDescriptor;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderRegistry;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the model catalog (FR-020): curated∪live∪default union, per-provider isolation of
 * live-fetch failures, and the TTL cache — expiry is driven by a mutable {@link Clock}, never by
 * sleeping.
 */
@ExtendWith(MockitoExtension.class)
class ModelCatalogServiceTest {

  private static final long TTL_MS = 300_000;
  private static final long FETCH_TIMEOUT_MS = 2_000;

  @Mock private ProviderRegistry registry;

  private ExecutorService executor;
  private final MutableClock clock = new MutableClock();

  @BeforeEach
  void setUp() {
    executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private ModelCatalogService service(ProviderDescriptor... descriptors) {
    return service(FETCH_TIMEOUT_MS, descriptors);
  }

  private ModelCatalogService service(long fetchTimeoutMs, ProviderDescriptor... descriptors) {
    return new ModelCatalogService(
        List.of(descriptors), registry, executor, clock, TTL_MS, fetchTimeoutMs);
  }

  private static ProviderDescriptor[] allUnconfigured() {
    return new ProviderDescriptor[] {
      new ProviderDescriptor(Provider.GEMINI, false, "gemini-2.5-flash"),
      new ProviderDescriptor(Provider.CHATGPT, false, "gpt-4o-mini"),
      new ProviderDescriptor(Provider.CLAUDE, false, "claude-3-5-sonnet-latest"),
      new ProviderDescriptor(Provider.GROK, false, "grok-2-latest"),
      new ProviderDescriptor(Provider.DEEPSEEK, false, "deepseek-chat")
    };
  }

  /** A stub adapter whose {@code listModels} is supplied per call and counted. */
  private void registerListing(Provider id, Supplier<List<String>> models, AtomicInteger calls) {
    lenient()
        .when(registry.get(id))
        .thenReturn(
            new LlmProvider() {
              @Override
              public Provider id() {
                return id;
              }

              @Override
              public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
                return ProviderResultMapper.error(id, "not_under_test", null);
              }

              @Override
              public List<String> listModels() {
                calls.incrementAndGet();
                return models.get();
              }
            });
  }

  @Test
  void fullCatalogListsAllProvidersInCanonicalOrder() {
    List<ProviderCatalogEntry> catalog = service(allUnconfigured()).fullCatalog();

    assertThat(catalog)
        .extracting(ProviderCatalogEntry::provider)
        .containsExactly(
            Provider.GEMINI, Provider.CHATGPT, Provider.CLAUDE, Provider.GROK, Provider.DEEPSEEK);
    assertThat(catalog).extracting(ProviderCatalogEntry::source).containsOnly("curated");
    assertThat(catalog).extracting(ProviderCatalogEntry::configured).containsOnly(false);
  }

  @Test
  void unconfiguredProviderGetsTheSortedCuratedListAndNeverFetches() {
    Map<Provider, ProviderCatalogEntry> catalog =
        service(allUnconfigured()).catalogFor(List.of(Provider.GEMINI));

    ProviderCatalogEntry gemini = catalog.get(Provider.GEMINI);
    assertThat(gemini.models())
        .containsExactly(
            "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro");
    assertThat(gemini.defaultModel()).isEqualTo("gemini-2.5-flash");
    assertThat(gemini.source()).isEqualTo("curated");
    verifyNoInteractions(registry);
  }

  @Test
  void modelsAreTheSortedDedupedUnionOfCuratedLiveAndDefault() {
    AtomicInteger calls = new AtomicInteger();
    // The live list overlaps curated (dupe), repeats itself (dupe) and adds a new id.
    registerListing(
        Provider.GEMINI,
        () -> List.of("zzz-live-model", "gemini-2.5-flash", "zzz-live-model"),
        calls);
    ModelCatalogService service =
        service(new ProviderDescriptor(Provider.GEMINI, true, "custom-default-model"));

    ProviderCatalogEntry gemini = service.catalogFor(List.of(Provider.GEMINI)).get(Provider.GEMINI);

    assertThat(gemini.models())
        .containsExactly(
            "custom-default-model",
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
            "zzz-live-model");
    assertThat(gemini.defaultModel()).isEqualTo("custom-default-model");
    assertThat(gemini.source()).isEqualTo("live");
    assertThat(gemini.configured()).isTrue();
  }

  @Test
  void defaultModelIsAlwaysOfferedEvenWhenUnconfigured() {
    ProviderCatalogEntry grok =
        service(new ProviderDescriptor(Provider.GROK, false, "grok-env-override"))
            .catalogFor(List.of(Provider.GROK))
            .get(Provider.GROK);

    assertThat(grok.models()).contains("grok-env-override");
    assertThat(grok.source()).isEqualTo("curated");
  }

  @Test
  void nullDefaultModelYieldsTheCuratedListAlone() {
    ProviderCatalogEntry deepseek =
        service(new ProviderDescriptor(Provider.DEEPSEEK, false, null))
            .catalogFor(List.of(Provider.DEEPSEEK))
            .get(Provider.DEEPSEEK);

    assertThat(deepseek.defaultModel()).isNull();
    assertThat(deepseek.models()).containsExactly("deepseek-chat", "deepseek-reasoner");
  }

  @Test
  void liveFetchFailureDegradesToCurated() {
    AtomicInteger calls = new AtomicInteger();
    registerListing(
        Provider.CLAUDE,
        () -> {
          throw new IllegalStateException("api down");
        },
        calls);

    ProviderCatalogEntry claude =
        service(new ProviderDescriptor(Provider.CLAUDE, true, "claude-3-5-sonnet-latest"))
            .catalogFor(List.of(Provider.CLAUDE))
            .get(Provider.CLAUDE);

    assertThat(claude.source()).isEqualTo("curated");
    assertThat(claude.models())
        .containsExactly(
            "claude-3-5-haiku-latest",
            "claude-3-5-sonnet-latest",
            "claude-haiku-4-5",
            "claude-opus-4-1",
            "claude-sonnet-4-5");
  }

  @Test
  void emptyLiveListKeepsSourceCurated() {
    AtomicInteger calls = new AtomicInteger();
    registerListing(Provider.CHATGPT, List::of, calls);

    ProviderCatalogEntry chatgpt =
        service(new ProviderDescriptor(Provider.CHATGPT, true, "gpt-4o-mini"))
            .catalogFor(List.of(Provider.CHATGPT))
            .get(Provider.CHATGPT);

    assertThat(chatgpt.source()).isEqualTo("curated");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void slowFetchTimesOutAndDegradesToCurated() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    registerListing(
        Provider.GEMINI,
        () -> {
          try {
            release.await();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          return List.of("too-late-model");
        },
        calls);
    try {
      ProviderCatalogEntry gemini =
          service(50, new ProviderDescriptor(Provider.GEMINI, true, "gemini-2.5-flash"))
              .catalogFor(List.of(Provider.GEMINI))
              .get(Provider.GEMINI);

      assertThat(gemini.source()).isEqualTo("curated");
      assertThat(gemini.models()).doesNotContain("too-late-model");
    } finally {
      release.countDown();
    }
  }

  @Test
  void catalogIsCachedWithinTheTtl() {
    AtomicInteger calls = new AtomicInteger();
    registerListing(Provider.GEMINI, () -> List.of("live-model"), calls);
    ModelCatalogService service =
        service(new ProviderDescriptor(Provider.GEMINI, true, "gemini-2.5-flash"));

    service.catalogFor(List.of(Provider.GEMINI));
    clock.advanceMillis(TTL_MS - 1);
    ProviderCatalogEntry cached = service.catalogFor(List.of(Provider.GEMINI)).get(Provider.GEMINI);

    assertThat(calls.get()).isEqualTo(1);
    assertThat(cached.models()).contains("live-model");
  }

  @Test
  void staleCatalogIsRefetchedAfterTheTtl() {
    AtomicInteger calls = new AtomicInteger();
    registerListing(Provider.GEMINI, () -> List.of("live-model-" + calls.get()), calls);
    ModelCatalogService service =
        service(new ProviderDescriptor(Provider.GEMINI, true, "gemini-2.5-flash"));

    service.catalogFor(List.of(Provider.GEMINI));
    clock.advanceMillis(TTL_MS);
    ProviderCatalogEntry refreshed =
        service.catalogFor(List.of(Provider.GEMINI)).get(Provider.GEMINI);

    assertThat(calls.get()).isEqualTo(2);
    assertThat(refreshed.models()).contains("live-model-2");
  }

  @Test
  void oneProviderFailureIsIsolatedFromTheOthers() {
    AtomicInteger geminiCalls = new AtomicInteger();
    AtomicInteger chatgptCalls = new AtomicInteger();
    registerListing(
        Provider.GEMINI,
        () -> {
          throw new IllegalStateException("rate_limited");
        },
        geminiCalls);
    registerListing(Provider.CHATGPT, () -> List.of("gpt-live-model"), chatgptCalls);

    Map<Provider, ProviderCatalogEntry> catalog =
        service(
                new ProviderDescriptor(Provider.GEMINI, true, "gemini-2.5-flash"),
                new ProviderDescriptor(Provider.CHATGPT, true, "gpt-4o-mini"))
            .catalogFor(List.of(Provider.GEMINI, Provider.CHATGPT));

    assertThat(catalog.get(Provider.GEMINI).source()).isEqualTo("curated");
    assertThat(catalog.get(Provider.CHATGPT).source()).isEqualTo("live");
    assertThat(catalog.get(Provider.CHATGPT).models()).contains("gpt-live-model");
  }

  @Test
  void catalogForReturnsOnlyTheRequestedProviders() {
    Map<Provider, ProviderCatalogEntry> catalog =
        service(allUnconfigured()).catalogFor(List.of(Provider.CLAUDE));

    assertThat(catalog.keySet()).containsExactly(Provider.CLAUDE);
  }

  /** A test clock advanced explicitly — TTL expiry without ever sleeping. */
  private static final class MutableClock extends Clock {

    private Instant now = Instant.EPOCH;

    void advanceMillis(long millis) {
      now = now.plusMillis(millis);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
