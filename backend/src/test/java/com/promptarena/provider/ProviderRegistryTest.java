package com.promptarena.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.StreamTelemetry;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {

  /** Minimal stub adapter for one provider. */
  private static LlmProvider stub(Provider id) {
    return new LlmProvider() {
      @Override
      public Provider id() {
        return id;
      }

      @Override
      public ProviderResponse stream(PromptRequest request, java.util.function.Consumer<String> t) {
        return ProviderResultMapper.success(id, "ok", 1L, StreamTelemetry.none());
      }
    };
  }

  @Test
  void returnsRegisteredAdapter() {
    LlmProvider claude = stub(Provider.CLAUDE);
    ProviderRegistry registry = new ProviderRegistry(List.of(claude));

    assertThat(registry.get(Provider.CLAUDE)).isSameAs(claude);
  }

  @Test
  void unregisteredProviderResolvesToUnavailable() {
    ProviderRegistry registry = new ProviderRegistry(List.of(stub(Provider.CLAUDE)));

    LlmProvider resolved = registry.get(Provider.GEMINI);
    ProviderResponse response = resolved.complete(new PromptRequest("hi"));

    assertThat(resolved).isInstanceOf(UnavailableProvider.class);
    assertThat(resolved.id()).isEqualTo(Provider.GEMINI);
    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  /** The interface defaults (FR-020): no model list and no default model unless overridden. */
  @Test
  void unavailableProviderReportsNoModelsAndNoDefault() {
    LlmProvider unavailable = new UnavailableProvider(Provider.GEMINI);

    assertThat(unavailable.listModels()).isEmpty();
    assertThat(unavailable.defaultModel()).isNull();
  }
}
