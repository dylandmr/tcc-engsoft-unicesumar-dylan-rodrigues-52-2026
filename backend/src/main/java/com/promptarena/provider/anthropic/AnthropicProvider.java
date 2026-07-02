package com.promptarena.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextDelta;
import com.anthropic.models.models.ModelInfo;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.StreamTelemetry;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Adapter over the official Anthropic Java SDK (Claude). No SDK type leaks past this class
 * (Constitution II). An unconfigured key yields an {@code ERROR} result rather than building a
 * client, so Claude never breaks the other providers (FR-010).
 */
public final class AnthropicProvider implements LlmProvider {

  public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

  // The Messages API requires an explicit output cap (unlike the other providers, which default to
  // the model maximum). 4096 comfortably fits the arena's longest answers — the 1024 it replaced
  // deterministically truncated detailed responses mid-sentence.
  private static final long MAX_TOKENS = 4096L;

  private final AnthropicClient client;

  public AnthropicProvider(String apiKey, String baseUrl) {
    this.client =
        (apiKey == null || apiKey.isBlank())
            ? null
            : AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .maxRetries(0)
                .checkJacksonVersionCompatibility(false)
                .build();
  }

  @Override
  public Provider id() {
    return Provider.CLAUDE;
  }

  /**
   * The models Anthropic's own {@code GET /v1/models} reports (FR-020) — every returned id is a
   * chat-capable Messages model, so no filtering is needed (accessors javap-verified against
   * anthropic-java 2.45.0: {@code ModelListPage.data()} / {@code ModelInfo.id()}). May throw on an
   * API failure — the model catalog isolates the fetch.
   */
  @Override
  public List<String> listModels() {
    if (client == null) {
      return List.of();
    }
    return client.models().list().data().stream().map(ModelInfo::id).toList();
  }

  @Override
  public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
    if (client == null) {
      return ProviderResultMapper.error(Provider.CLAUDE, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    // Telemetry (FR-019). TTFT is stamped here, against this adapter's own start, so it shares the
    // clock epoch of responseTimeMs. message_start carries input usage + the exact model;
    // message_delta carries the CUMULATIVE output token count, so the last one seen is the total.
    AtomicReference<Long> firstTokenMs = new AtomicReference<>();
    AtomicReference<Long> inputTokens = new AtomicReference<>();
    AtomicReference<Long> outputTokens = new AtomicReference<>();
    AtomicReference<String> reportedModel = new AtomicReference<>();
    try {
      MessageCreateParams params =
          MessageCreateParams.builder()
              .model(request.model())
              .maxTokens(MAX_TOKENS)
              .addUserMessage(request.prompt())
              .build();
      StringBuilder full = new StringBuilder();
      AtomicBoolean completed = new AtomicBoolean(false);
      try (StreamResponse<RawMessageStreamEvent> events =
          client.messages().createStreaming(params)) {
        events.stream()
            .forEach(
                event -> {
                  // message_stop is the API's terminal event; if the stream ends without it (the
                  // SDK ends silently — no retry, maxRetries(0)), the text is partial.
                  if (event.isMessageStop()) {
                    completed.set(true);
                  }
                  event
                      .messageStart()
                      .ifPresent(
                          messageStart -> {
                            inputTokens.set(messageStart.message().usage().inputTokens());
                            reportedModel.set(messageStart.message().model().asString());
                          });
                  event
                      .messageDelta()
                      .ifPresent(
                          messageDelta -> outputTokens.set(messageDelta.usage().outputTokens()));
                  event
                      .contentBlockDelta()
                      .map(RawContentBlockDeltaEvent::delta)
                      .flatMap(RawContentBlockDelta::text)
                      .map(TextDelta::text)
                      .ifPresent(
                          delta -> {
                            firstTokenMs.compareAndSet(null, elapsedMs(start));
                            full.append(delta);
                            onToken.accept(delta);
                          });
                });
      }
      return completed.get()
          ? ProviderResultMapper.success(
              Provider.CLAUDE,
              full.toString(),
              elapsedMs(start),
              new StreamTelemetry(
                  firstTokenMs.get(), inputTokens.get(), outputTokens.get(), reportedModel.get()))
          : ProviderResultMapper.truncated(Provider.CLAUDE, elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(Provider.CLAUDE, ex.getMessage(), elapsedMs(start));
    }
  }

  private static long elapsedMs(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
  }
}
