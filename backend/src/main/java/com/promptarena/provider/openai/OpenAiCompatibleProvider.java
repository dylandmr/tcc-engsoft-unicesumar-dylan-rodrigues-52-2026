package com.promptarena.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.models.Model;
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
 * Adapter over the OpenAI Java SDK's chat-completions API. Because Grok and DeepSeek are
 * OpenAI-compatible, a single adapter serves ChatGPT, Grok and DeepSeek by varying the base URL
 * (research Decision 1). No SDK type leaks past this class (Constitution II).
 *
 * <p>A provider with no API key is treated as unavailable: its call yields an {@code ERROR} result
 * instead of building a client, so it never breaks the others (FR-010).
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

  /** Base URL constants — fixed per provider, wired in {@code ProviderConfig}. */
  public static final String CHATGPT_BASE_URL = "https://api.openai.com/v1";

  public static final String GROK_BASE_URL = "https://api.x.ai/v1";
  public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

  /**
   * OpenAI's {@code /models} mixes chat models with the other endpoints' models (audio, images,
   * embeddings, …); any id containing one of these fragments is a non-chat modality variant.
   */
  private static final List<String> CHATGPT_EXCLUDED_FRAGMENTS =
      List.of(
          "audio",
          "realtime",
          "embedding",
          "tts",
          "whisper",
          "moderation",
          "image",
          "dall-e",
          "transcribe",
          "search");

  private final Provider id;
  private final OpenAIClient client;

  public OpenAiCompatibleProvider(Provider id, String apiKey, String baseUrl) {
    this.id = id;
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

  /**
   * The chat-capable models this provider's own {@code GET /models} reports (FR-020), filtered per
   * provider — the endpoint is unpaginated in the OpenAI SDK (javap-verified: {@code
   * ModelListPage.hasNextPage()} is constant {@code false} in openai-java 4.41.0). May throw on an
   * API failure — the model catalog isolates the fetch.
   */
  @Override
  public List<String> listModels() {
    if (client == null) {
      return List.of();
    }
    return client.models().list().data().stream().map(Model::id).filter(this::servesChat).toList();
  }

  /** Whether {@code modelId} is a chat model of the specific provider this adapter serves. */
  private boolean servesChat(String modelId) {
    return switch (id) {
      case CHATGPT -> isChatgptChatModel(modelId);
      case GROK -> modelId.startsWith("grok");
      default -> modelId.startsWith("deepseek");
    };
  }

  /** Keep the chat families (gpt-*, chatgpt-*, o&lt;N&gt;*) and drop modality variants by name. */
  private static boolean isChatgptChatModel(String modelId) {
    boolean chatFamily =
        modelId.startsWith("gpt-") || modelId.startsWith("chatgpt-") || modelId.matches("^o\\d.*");
    return chatFamily && CHATGPT_EXCLUDED_FRAGMENTS.stream().noneMatch(modelId::contains);
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
              .model(request.model())
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
