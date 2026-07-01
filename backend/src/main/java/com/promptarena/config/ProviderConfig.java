package com.promptarena.config;

import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.anthropic.AnthropicProvider;
import com.promptarena.provider.google.GeminiProvider;
import com.promptarena.provider.openai.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires the five {@link LlmProvider} adapters from server-side configuration. API keys and per-
 * provider model ids come from the environment (FR-018); base URLs are fixed per provider. A
 * provider with a blank key builds an "unavailable" adapter whose call yields an {@code ERROR}
 * result, so a missing key never breaks the other providers (FR-010).
 *
 * <p>Model ids fall back to the per-provider default when the env var is <em>unset or blank</em>.
 * The blank case matters because Docker Compose forwards {@code *_MODEL} as an empty string when
 * the key is present-but-empty in {@code .env}, which would otherwise override the {@code @Value}
 * default and send an empty model to the SDK.
 *
 * <p>Pure wiring (no business logic) — excluded from the coverage gate.
 */
@Configuration
public class ProviderConfig {

  private static String modelOrDefault(String configured, String fallback) {
    return StringUtils.hasText(configured) ? configured : fallback;
  }

  @Bean
  LlmProvider chatgptProvider(
      @Value("${OPENAI_API_KEY:}") String apiKey, @Value("${OPENAI_MODEL:}") String model) {
    return new OpenAiCompatibleProvider(
        Provider.CHATGPT,
        apiKey,
        modelOrDefault(model, OpenAiCompatibleProvider.DEFAULT_CHATGPT_MODEL),
        OpenAiCompatibleProvider.CHATGPT_BASE_URL);
  }

  @Bean
  LlmProvider grokProvider(
      @Value("${XAI_API_KEY:}") String apiKey, @Value("${XAI_MODEL:}") String model) {
    return new OpenAiCompatibleProvider(
        Provider.GROK,
        apiKey,
        modelOrDefault(model, OpenAiCompatibleProvider.DEFAULT_GROK_MODEL),
        OpenAiCompatibleProvider.GROK_BASE_URL);
  }

  @Bean
  LlmProvider deepseekProvider(
      @Value("${DEEPSEEK_API_KEY:}") String apiKey, @Value("${DEEPSEEK_MODEL:}") String model) {
    return new OpenAiCompatibleProvider(
        Provider.DEEPSEEK,
        apiKey,
        modelOrDefault(model, OpenAiCompatibleProvider.DEFAULT_DEEPSEEK_MODEL),
        OpenAiCompatibleProvider.DEEPSEEK_BASE_URL);
  }

  @Bean
  LlmProvider claudeProvider(
      @Value("${ANTHROPIC_API_KEY:}") String apiKey, @Value("${ANTHROPIC_MODEL:}") String model) {
    return new AnthropicProvider(
        apiKey,
        modelOrDefault(model, AnthropicProvider.DEFAULT_MODEL),
        AnthropicProvider.DEFAULT_BASE_URL);
  }

  @Bean
  LlmProvider geminiProvider(
      @Value("${GOOGLE_API_KEY:}") String apiKey, @Value("${GOOGLE_MODEL:}") String model) {
    return new GeminiProvider(
        apiKey,
        modelOrDefault(model, GeminiProvider.DEFAULT_MODEL),
        GeminiProvider.DEFAULT_BASE_URL);
  }
}
