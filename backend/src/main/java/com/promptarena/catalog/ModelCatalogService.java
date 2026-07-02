package com.promptarena.catalog;

import com.promptarena.dto.ProviderCatalogEntry;
import com.promptarena.dto.ProviderDescriptor;
import com.promptarena.model.Provider;
import com.promptarena.provider.ProviderRegistry;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds each provider's selectable-model catalog (FR-020): the sorted, deduplicated union of a
 * curated list, the provider's live {@code listModels()} report, and the configured default model.
 * Live lists are fetched concurrently (only for configured providers), bounded by a short
 * per-provider timeout, and cached per provider for a TTL; any fetch failure silently degrades that
 * one provider to its curated list — mirroring FR-010's isolation, composing is never blocked.
 */
@Service
public class ModelCatalogService {

  /** Curated per-provider model lists — always offered, even with no key configured (FR-020). */
  private static final Map<Provider, List<String>> CURATED =
      Map.of(
          Provider.GEMINI,
          List.of(
              "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash"),
          Provider.CHATGPT,
          List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano"),
          Provider.CLAUDE,
          List.of(
              "claude-sonnet-4-5",
              "claude-haiku-4-5",
              "claude-opus-4-1",
              "claude-3-5-sonnet-latest",
              "claude-3-5-haiku-latest"),
          Provider.GROK,
          List.of("grok-4", "grok-3", "grok-3-mini", "grok-2-latest"),
          Provider.DEEPSEEK,
          List.of("deepseek-chat", "deepseek-reasoner"));

  private record CachedCatalog(ProviderCatalogEntry entry, long builtAtMs) {}

  private final Map<Provider, ProviderDescriptor> descriptors = new EnumMap<>(Provider.class);
  private final ProviderRegistry registry;
  private final Executor executor;
  private final Clock clock;
  private final long ttlMs;
  private final long fetchTimeoutMs;
  private final Map<Provider, CachedCatalog> cache = new ConcurrentHashMap<>();

  public ModelCatalogService(
      List<ProviderDescriptor> providerDescriptors,
      ProviderRegistry registry,
      @Qualifier("providerExecutor") Executor executor,
      Clock clock,
      @Value("${prompt-arena.model-catalog-ttl-ms:300000}") long ttlMs,
      @Value("${prompt-arena.model-catalog-fetch-timeout-ms:5000}") long fetchTimeoutMs) {
    providerDescriptors.forEach(
        descriptor -> this.descriptors.put(descriptor.provider(), descriptor));
    this.registry = registry;
    this.executor = executor;
    this.clock = clock;
    this.ttlMs = ttlMs;
    this.fetchTimeoutMs = fetchTimeoutMs;
  }

  /** Every supported provider's catalog, in canonical order (the {@link Provider} enum order). */
  public List<ProviderCatalogEntry> fullCatalog() {
    return List.copyOf(entries(List.of(Provider.values())).values());
  }

  /** The catalog entries for exactly {@code providers}, keyed by provider. */
  public Map<Provider, ProviderCatalogEntry> catalogFor(Collection<Provider> providers) {
    return entries(providers);
  }

  private Map<Provider, ProviderCatalogEntry> entries(Collection<Provider> providers) {
    long now = clock.millis();
    Map<Provider, ProviderCatalogEntry> result = new EnumMap<>(Provider.class);
    // Kick off every needed live fetch before assembling, so stale providers refresh in parallel.
    Map<Provider, CompletableFuture<List<String>>> fetches = new EnumMap<>(Provider.class);
    for (Provider provider : providers) {
      ProviderCatalogEntry fresh = freshEntry(provider, now);
      if (fresh != null) {
        result.put(provider, fresh);
      } else if (descriptors.get(provider).configured()) {
        fetches.put(provider, fetchLive(provider));
      }
    }
    for (Provider provider : providers) {
      if (!result.containsKey(provider)) {
        ProviderCatalogEntry entry = build(provider, liveModels(fetches.get(provider)));
        cache.put(provider, new CachedCatalog(entry, now));
        result.put(provider, entry);
      }
    }
    return result;
  }

  /** The cached entry when still within the TTL, otherwise {@code null}. */
  private ProviderCatalogEntry freshEntry(Provider provider, long now) {
    CachedCatalog cached = cache.get(provider);
    return cached != null && now - cached.builtAtMs() < ttlMs ? cached.entry() : null;
  }

  /** A bounded, isolated live fetch: failure or timeout resolves to {@code null}, never throws. */
  private CompletableFuture<List<String>> fetchLive(Provider provider) {
    return CompletableFuture.supplyAsync(() -> registry.get(provider).listModels(), executor)
        .orTimeout(fetchTimeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> null);
  }

  /** {@code null} when the fetch failed, timed out, or never started (unconfigured provider). */
  private static List<String> liveModels(CompletableFuture<List<String>> fetch) {
    return fetch == null ? null : fetch.join();
  }

  private ProviderCatalogEntry build(Provider provider, List<String> live) {
    ProviderDescriptor descriptor = descriptors.get(provider);
    TreeSet<String> models = new TreeSet<>(CURATED.get(provider));
    if (descriptor.defaultModel() != null) {
      models.add(descriptor.defaultModel());
    }
    boolean liveContributed = live != null && !live.isEmpty();
    if (liveContributed) {
      models.addAll(live);
    }
    return new ProviderCatalogEntry(
        provider,
        descriptor.configured(),
        descriptor.defaultModel(),
        List.copyOf(models),
        liveContributed ? "live" : "curated");
  }
}
