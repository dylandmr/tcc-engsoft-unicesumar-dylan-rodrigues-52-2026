package com.promptarena.config;

import com.promptarena.dto.ProviderDescriptor;
import com.promptarena.model.Provider;
import com.promptarena.provider.LlmProvider;
import com.promptarena.provider.anthropic.AnthropicProvider;
import com.promptarena.provider.google.GeminiProvider;
import com.promptarena.provider.openai.OpenAiCompatibleProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires the five {@link LlmProvider} adapters from server-side configuration. API keys come from
 * the environment (FR-018); base URLs are fixed per provider. No model is configured anywhere
 * (FR-020) — the user chooses one per comparison from the provider's live catalog. A provider with
 * a blank key builds an "unavailable" adapter whose call yields an {@code ERROR} result, so a
 * missing key never breaks the other providers (FR-010).
 *
 * <p>Pure wiring (no business logic) — excluded from the coverage gate.
 */
@Configuration
public class ProviderConfig {

  @Bean
  LlmProvider chatgptProvider(@Value("${OPENAI_API_KEY:}") String apiKey) {
    return new OpenAiCompatibleProvider(
        Provider.CHATGPT, apiKey, OpenAiCompatibleProvider.CHATGPT_BASE_URL);
  }

  @Bean
  LlmProvider grokProvider(@Value("${XAI_API_KEY:}") String apiKey) {
    return new OpenAiCompatibleProvider(
        Provider.GROK, apiKey, OpenAiCompatibleProvider.GROK_BASE_URL);
  }

  @Bean
  LlmProvider deepseekProvider(@Value("${DEEPSEEK_API_KEY:}") String apiKey) {
    return new OpenAiCompatibleProvider(
        Provider.DEEPSEEK, apiKey, OpenAiCompatibleProvider.DEEPSEEK_BASE_URL);
  }

  @Bean
  LlmProvider claudeProvider(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
    return new AnthropicProvider(apiKey, AnthropicProvider.DEFAULT_BASE_URL);
  }

  @Bean
  LlmProvider geminiProvider(@Value("${GOOGLE_API_KEY:}") String apiKey) {
    return new GeminiProvider(apiKey, GeminiProvider.DEFAULT_BASE_URL);
  }

  /**
   * One descriptor per provider for the model catalog (FR-020): whether a server-side key is
   * present, resolved once at wiring time next to the adapters above.
   */
  @Bean
  List<ProviderDescriptor> providerDescriptors(
      @Value("${GOOGLE_API_KEY:}") String googleKey,
      @Value("${OPENAI_API_KEY:}") String openaiKey,
      @Value("${ANTHROPIC_API_KEY:}") String anthropicKey,
      @Value("${XAI_API_KEY:}") String xaiKey,
      @Value("${DEEPSEEK_API_KEY:}") String deepseekKey) {
    return List.of(
        new ProviderDescriptor(Provider.GEMINI, StringUtils.hasText(googleKey)),
        new ProviderDescriptor(Provider.CHATGPT, StringUtils.hasText(openaiKey)),
        new ProviderDescriptor(Provider.CLAUDE, StringUtils.hasText(anthropicKey)),
        new ProviderDescriptor(Provider.GROK, StringUtils.hasText(xaiKey)),
        new ProviderDescriptor(Provider.DEEPSEEK, StringUtils.hasText(deepseekKey)));
  }
}
