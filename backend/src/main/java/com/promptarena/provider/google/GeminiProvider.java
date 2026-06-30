package com.promptarena.provider.google;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;

/**
 * Adapter over the official Google GenAI Java SDK (Gemini). No SDK type leaks past this class
 * (Constitution II). An unconfigured key yields an {@code ERROR} result rather than building a
 * client, so Gemini never breaks the other providers (FR-010).
 */
public final class GeminiProvider implements LlmProvider {

  public static final String DEFAULT_MODEL = "gemini-2.0-flash";
  public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

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
  public ProviderResponse complete(PromptRequest request) {
    if (client == null) {
      return ProviderResultMapper.error(Provider.GEMINI, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    try {
      GenerateContentResponse response =
          client.models.generateContent(model, request.prompt(), null);
      return ProviderResultMapper.success(Provider.GEMINI, response.text(), elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(Provider.GEMINI, ex.getMessage(), elapsedMs(start));
    }
  }

  private static long elapsedMs(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
  }
}
