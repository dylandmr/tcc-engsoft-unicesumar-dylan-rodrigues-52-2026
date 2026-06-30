package com.promptarena.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import org.junit.jupiter.api.Test;

class ProviderResultMapperTest {

  @Test
  void nonBlankTextIsSuccess() {
    ProviderResponse response = ProviderResultMapper.success(Provider.CLAUDE, "answer", 12L);

    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("answer");
    assertThat(response.errorMessage()).isNull();
    assertThat(response.responseTimeMs()).isEqualTo(12L);
  }

  @Test
  void nullTextIsEmpty() {
    ProviderResponse response = ProviderResultMapper.success(Provider.CLAUDE, null, 5L);

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void blankTextIsEmpty() {
    ProviderResponse response = ProviderResultMapper.success(Provider.GEMINI, "   ", 5L);

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void errorKeepsMessage() {
    ProviderResponse response = ProviderResultMapper.error(Provider.GROK, "rate_limited", 9L);

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.text()).isNull();
    assertThat(response.errorMessage()).isEqualTo("rate_limited");
    assertThat(response.responseTimeMs()).isEqualTo(9L);
  }

  @Test
  void errorWithoutMessageFallsBack() {
    ProviderResponse response = ProviderResultMapper.error(Provider.GROK, null, null);

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("error");
    assertThat(response.responseTimeMs()).isNull();
  }

  @Test
  void timeoutHasNoLatency() {
    ProviderResponse response = ProviderResultMapper.timeout(Provider.DEEPSEEK);

    assertThat(response.outcome()).isEqualTo(Outcome.TIMEOUT);
    assertThat(response.text()).isNull();
    assertThat(response.errorMessage()).isEqualTo("timeout");
    assertThat(response.responseTimeMs()).isNull();
  }
}
