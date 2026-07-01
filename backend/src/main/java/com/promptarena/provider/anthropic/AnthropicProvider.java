package com.promptarena.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextDelta;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.ProviderResultMapper;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Adapter over the official Anthropic Java SDK (Claude). No SDK type leaks past this class
 * (Constitution II). An unconfigured key yields an {@code ERROR} result rather than building a
 * client, so Claude never breaks the other providers (FR-010).
 */
public final class AnthropicProvider implements LlmProvider {

  public static final String DEFAULT_MODEL = "claude-3-5-sonnet-latest";
  public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

  private static final long MAX_TOKENS = 1024L;

  private final String model;
  private final AnthropicClient client;

  public AnthropicProvider(String apiKey, String model, String baseUrl) {
    this.model = model;
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

  @Override
  public ProviderResponse stream(PromptRequest request, Consumer<String> onToken) {
    if (client == null) {
      return ProviderResultMapper.error(Provider.CLAUDE, "provider_not_configured", null);
    }
    long start = System.nanoTime();
    try {
      MessageCreateParams params =
          MessageCreateParams.builder()
              .model(model)
              .maxTokens(MAX_TOKENS)
              .addUserMessage(request.prompt())
              .build();
      StringBuilder full = new StringBuilder();
      try (StreamResponse<RawMessageStreamEvent> events =
          client.messages().createStreaming(params)) {
        events.stream()
            .map(RawMessageStreamEvent::contentBlockDelta)
            .flatMap(java.util.Optional::stream)
            .map(RawContentBlockDeltaEvent::delta)
            .map(RawContentBlockDelta::text)
            .flatMap(java.util.Optional::stream)
            .map(TextDelta::text)
            .forEach(
                delta -> {
                  full.append(delta);
                  onToken.accept(delta);
                });
      }
      return ProviderResultMapper.success(Provider.CLAUDE, full.toString(), elapsedMs(start));
    } catch (RuntimeException ex) {
      return ProviderResultMapper.error(Provider.CLAUDE, ex.getMessage(), elapsedMs(start));
    }
  }

  private static long elapsedMs(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
  }
}
