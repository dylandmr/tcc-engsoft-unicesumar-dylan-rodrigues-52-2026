package com.promptarena.provider.google;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.ThinkingConfig;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
  public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
    if (client == null) {
      return ProviderResultMapper.error(Provider.GEMINI, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    try {
      StringBuilder full = new StringBuilder();
      boolean completed = false;
      try (ResponseStream<GenerateContentResponse> chunks =
          client.models.generateContentStream(model, request.prompt(), STREAM_CONFIG)) {
        for (GenerateContentResponse chunk : chunks) {
          Optional.ofNullable(chunk.text())
              .ifPresent(
                  delta -> {
                    full.append(delta);
                    onToken.accept(delta);
                  });
          // The model stamps a finish reason on its terminal chunk; if the loop ends without one,
          // the stream dropped mid-answer (no SDK retry — attempts(1)) and the text is partial.
          if (hasFinishReason(chunk)) {
            completed = true;
          }
        }
      }
      return completed
          ? ProviderResultMapper.success(Provider.GEMINI, full.toString(), elapsedMs(start))
          : ProviderResultMapper.truncated(Provider.GEMINI, elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(Provider.GEMINI, ex.getMessage(), elapsedMs(start));
    }
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
