package com.promptarena.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;

/**
 * Adapter over the OpenAI Java SDK's chat-completions API. Because Grok and DeepSeek are
 * OpenAI-compatible, a single adapter serves ChatGPT, Grok and DeepSeek by varying the base URL and
 * model (research Decision 1). No SDK type leaks past this class (Constitution II).
 *
 * <p>A provider with no API key is treated as unavailable: its call yields an {@code ERROR} result
 * instead of building a client, so it never breaks the others (FR-010).
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

  /** Default model + base URL constants (overridable via env in {@code ProviderConfig}). */
  public static final String DEFAULT_CHATGPT_MODEL = "gpt-4o-mini";

  public static final String CHATGPT_BASE_URL = "https://api.openai.com/v1";
  public static final String DEFAULT_GROK_MODEL = "grok-2-latest";
  public static final String GROK_BASE_URL = "https://api.x.ai/v1";
  public static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
  public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

  private final Provider id;
  private final String model;
  private final OpenAIClient client;

  public OpenAiCompatibleProvider(Provider id, String apiKey, String model, String baseUrl) {
    this.id = id;
    this.model = model;
    this.client =
        (apiKey == null || apiKey.isBlank())
            ? null
            : OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .maxRetries(0)
                .checkJacksonVersionCompatibility(false)
                .build();
  }

  @Override
  public Provider id() {
    return id;
  }

  @Override
  public ProviderResponse complete(PromptRequest request) {
    if (client == null) {
      return ProviderResultMapper.error(id, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    try {
      ChatCompletionCreateParams params =
          ChatCompletionCreateParams.builder()
              .model(model)
              .addUserMessage(request.prompt())
              .build();
      ChatCompletion completion = client.chat().completions().create(params);
      String text = completion.choices().get(0).message().content().orElse("");
      return ProviderResultMapper.success(id, text, elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(id, ex.getMessage(), elapsedMs(start));
    }
  }

  private static long elapsedMs(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
  }
}
