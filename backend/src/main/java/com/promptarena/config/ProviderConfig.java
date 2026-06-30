package com.promptarena.config;

import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.anthropic.AnthropicProvider;
import com.promptarena.provider.google.GeminiProvider;
import com.promptarena.provider.openai.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the five {@link LlmProvider} adapters from server-side configuration. API keys and per-
 * provider model ids come from the environment (FR-018); base URLs are fixed per provider. A
 * provider with a blank key builds an "unavailable" adapter whose call yields an {@code ERROR}
 * result, so a missing key never breaks the other providers (FR-010).
 *
 * <p>Pure wiring (no business logic) — excluded from the coverage gate.
 */
@Configuration
public class ProviderConfig {

  @Bean
  LlmProvider chatgptProvider(
      @Value("${OPENAI_API_KEY:}") String apiKey,
      @Value("${OPENAI_MODEL:" + OpenAiCompatibleProvider.DEFAULT_CHATGPT_MODEL + "}")
          String model) {
    return new OpenAiCompatibleProvider(
        Provider.CHATGPT, apiKey, model, OpenAiCompatibleProvider.CHATGPT_BASE_URL);
  }

  @Bean
  LlmProvider grokProvider(
      @Value("${XAI_API_KEY:}") String apiKey,
      @Value("${XAI_MODEL:" + OpenAiCompatibleProvider.DEFAULT_GROK_MODEL + "}") String model) {
    return new OpenAiCompatibleProvider(
        Provider.GROK, apiKey, model, OpenAiCompatibleProvider.GROK_BASE_URL);
  }

  @Bean
  LlmProvider deepseekProvider(
      @Value("${DEEPSEEK_API_KEY:}") String apiKey,
      @Value("${DEEPSEEK_MODEL:" + OpenAiCompatibleProvider.DEFAULT_DEEPSEEK_MODEL + "}")
          String model) {
    return new OpenAiCompatibleProvider(
        Provider.DEEPSEEK, apiKey, model, OpenAiCompatibleProvider.DEEPSEEK_BASE_URL);
  }

  @Bean
  LlmProvider claudeProvider(
      @Value("${ANTHROPIC_API_KEY:}") String apiKey,
      @Value("${ANTHROPIC_MODEL:" + AnthropicProvider.DEFAULT_MODEL + "}") String model) {
    return new AnthropicProvider(apiKey, model, AnthropicProvider.DEFAULT_BASE_URL);
  }

  @Bean
  LlmProvider geminiProvider(
      @Value("${GOOGLE_API_KEY:}") String apiKey,
      @Value("${GOOGLE_MODEL:" + GeminiProvider.DEFAULT_MODEL + "}") String model) {
    return new GeminiProvider(apiKey, model, GeminiProvider.DEFAULT_BASE_URL);
  }
}
