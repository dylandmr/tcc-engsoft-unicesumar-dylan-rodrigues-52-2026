package com.promptarena.provider.google;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import com.google.genai.types.ThinkingConfig;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.StreamTelemetry;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Adapter over the official Google GenAI Java SDK (Gemini). No SDK type leaks past this class
 * (Constitution II). An unconfigured key yields an {@code ERROR} result rather than building a
 * client, so Gemini never breaks the other providers (FR-010).
 */
public final class GeminiProvider implements LlmProvider {

  // gemini-2.5-flash is the current default because the free tier for gemini-2.0-flash now grants
  // zero requests on new keys (429 "limit: 0"); 2.5-flash works on the free tier.
  public static final String DEFAULT_MODEL = "gemini-2.5-flash";
  public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

  // Gemini 2.5 models "think" (reason silently) before emitting output, which on longer prompts
  // delays the first streamed token by many seconds — the response then appears in a late burst
  // rather than progressively. Disabling thinking (budget 0) makes tokens stream immediately,
  // giving
  // the live-chatbot experience; it trades some answer depth for responsiveness.
  private static final GenerateContentConfig STREAM_CONFIG =
      GenerateContentConfig.builder()
          .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0))
          .build();

  private final String model;
  private final Client client;

  public GeminiProvider(String apiKey, String model, String baseUrl) {
    this.model = model;
    this.client =
        (apiKey == null || apiKey.isBlank())
            ? null
            : Client.builder()
                .apiKey(apiKey)
                .httpOptions(
                    HttpOptions.builder()
                        .baseUrl(baseUrl)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
  }

  @Override
  public Provider id() {
    return Provider.GEMINI;
  }

  @Override
  public String defaultModel() {
    return model;
  }

  /**
   * The chat-capable models Gemini's own API reports (FR-020). The SDK reads the wire field {@code
   * supportedGenerationMethods} into {@code supportedActions} (javap-verified against google-genai
   * 1.60.0's {@code listModelsResponseFromMldev}); only models that can {@code generateContent}
   * qualify. May throw on an API failure — the model catalog isolates the fetch.
   */
  @Override
  public List<String> listModels() {
    if (client == null) {
      return List.of();
    }
    List<String> ids = new ArrayList<>();
    for (Model entry : client.models.list(ListModelsConfig.builder().build())) {
      if (entry.supportedActions().orElse(List.of()).contains("generateContent")) {
        entry.name().map(GeminiProvider::stripModelsPrefix).ifPresent(ids::add);
      }
    }
    return ids;
  }

  /** The wire name is {@code models/<id>}; the catalog and the SPA use the bare id. */
  private static String stripModelsPrefix(String name) {
    return name.startsWith("models/") ? name.substring("models/".length()) : name;
  }

  @Override
  public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
    if (client == null) {
      return ProviderResultMapper.error(Provider.GEMINI, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    // Telemetry (FR-019). TTFT is stamped here, against this adapter's own start, so it shares the
    // clock epoch of responseTimeMs. Usage counts grow across chunks and are final on the terminal
    // chunk — the last non-empty value wins.
    AtomicReference<Long> firstTokenMs = new AtomicReference<>();
    AtomicReference<Long> inputTokens = new AtomicReference<>();
    AtomicReference<Long> outputTokens = new AtomicReference<>();
    AtomicReference<String> modelVersion = new AtomicReference<>();
    try {
      StringBuilder full = new StringBuilder();
      boolean completed = false;
      try (ResponseStream<GenerateContentResponse> chunks =
          client.models.generateContentStream(
              requestedModel(request), request.prompt(), STREAM_CONFIG)) {
        for (GenerateContentResponse chunk : chunks) {
          Optional.ofNullable(chunk.text())
              .ifPresent(
                  delta -> {
                    firstTokenMs.compareAndSet(null, elapsedMs(start));
                    full.append(delta);
                    onToken.accept(delta);
                  });
          chunk
              .usageMetadata()
              .ifPresent(
                  usage -> {
                    usage.promptTokenCount().ifPresent(count -> inputTokens.set(count.longValue()));
                    usage
                        .candidatesTokenCount()
                        .ifPresent(count -> outputTokens.set(count.longValue()));
                  });
          chunk.modelVersion().ifPresent(modelVersion::set);
          // The model stamps a finish reason on its terminal chunk; if the loop ends without one,
          // the stream dropped mid-answer (no SDK retry — attempts(1)) and the text is partial.
          if (hasFinishReason(chunk)) {
            completed = true;
          }
        }
      }
      return completed
          ? ProviderResultMapper.success(
              Provider.GEMINI,
              full.toString(),
              elapsedMs(start),
              new StreamTelemetry(
                  firstTokenMs.get(), inputTokens.get(), outputTokens.get(), modelVersion.get()))
          : ProviderResultMapper.truncated(Provider.GEMINI, elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(Provider.GEMINI, ex.getMessage(), elapsedMs(start));
    }
  }

  /** The per-comparison model choice (FR-020), or this adapter's configured default. */
  private String requestedModel(PromptRequest request) {
    return request.model() != null ? request.model() : model;
  }

  /** Whether any candidate on this chunk carries a finish reason (a definitive end-of-stream). */
  private static boolean hasFinishReason(GenerateContentResponse chunk) {
    return chunk.candidates().stream()
        .flatMap(List::stream)
        .map(Candidate::finishReason)
        .anyMatch(Optional::isPresent);
  }

  private static long elapsedMs(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
  }
}
