package com.promptarena.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.StreamTelemetry;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
  public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
    if (client == null) {
      return ProviderResultMapper.error(id, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    // Telemetry (FR-019). TTFT is stamped here, against this adapter's own start, so it shares the
    // clock epoch of responseTimeMs. With include_usage, the API appends a terminal chunk carrying
    // usage and an EMPTY choices array — so chunks are iterated whole, never just their choices.
    AtomicReference<Long> firstTokenMs = new AtomicReference<>();
    AtomicReference<Long> inputTokens = new AtomicReference<>();
    AtomicReference<Long> outputTokens = new AtomicReference<>();
    AtomicReference<String> reportedModel = new AtomicReference<>();
    try {
      ChatCompletionCreateParams params =
          ChatCompletionCreateParams.builder()
              .model(model)
              .addUserMessage(request.prompt())
              .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build())
              .build();
      StringBuilder full = new StringBuilder();
      AtomicBoolean completed = new AtomicBoolean(false);
      try (StreamResponse<ChatCompletionChunk> chunks =
          client.chat().completions().createStreaming(params)) {
        chunks.stream()
            .forEach(
                chunk -> {
                  // Read via the raw JSON field: some OpenAI-compatible backends omit `model` on a
                  // chunk, and telemetry must never turn a good answer into an error.
                  chunk._model().asString().ifPresent(reportedModel::set);
                  chunk
                      .usage()
                      .ifPresent(
                          usage -> {
                            inputTokens.set(usage.promptTokens());
                            outputTokens.set(usage.completionTokens());
                          });
                  chunk
                      .choices()
                      .forEach(
                          choice -> {
                            choice
                                .delta()
                                .content()
                                .ifPresent(
                                    delta -> {
                                      firstTokenMs.compareAndSet(null, elapsedMs(start));
                                      full.append(delta);
                                      onToken.accept(delta);
                                    });
                            // The API stamps finish_reason on its terminal content chunk; if the
                            // stream ends without one (the SDK ends silently — no retry,
                            // maxRetries(0)), the text is partial.
                            if (choice.finishReason().isPresent()) {
                              completed.set(true);
                            }
                          });
                });
      }
      return completed.get()
          ? ProviderResultMapper.success(
              id,
              full.toString(),
              elapsedMs(start),
              new StreamTelemetry(
                  firstTokenMs.get(), inputTokens.get(), outputTokens.get(), reportedModel.get()))
          : ProviderResultMapper.truncated(id, elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(id, ex.getMessage(), elapsedMs(start));
    }
  }

  private static long elapsedMs(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
  }
}
