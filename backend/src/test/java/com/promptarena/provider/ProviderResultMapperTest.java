package com.promptarena.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.promptarena.dto.ProviderResponse;
import com.promptarena.dto.StreamTelemetry;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import org.junit.jupiter.api.Test;

class ProviderResultMapperTest {

  @Test
  void nonBlankTextIsSuccess() {
    ProviderResponse response =
        ProviderResultMapper.success(Provider.CLAUDE, "answer", 12L, StreamTelemetry.none());

    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("answer");
    assertThat(response.errorMessage()).isNull();
    assertThat(response.responseTimeMs()).isEqualTo(12L);
  }

  @Test
  void successCarriesTheHarvestedTelemetry() {
    StreamTelemetry telemetry = new StreamTelemetry(320L, 12L, 256L, "claude-test-20250101");

    ProviderResponse response =
        ProviderResultMapper.success(Provider.CLAUDE, "answer", 1840L, telemetry);

    assertThat(response.firstTokenMs()).isEqualTo(320L);
    assertThat(response.inputTokens()).isEqualTo(12L);
    assertThat(response.outputTokens()).isEqualTo(256L);
    assertThat(response.model()).isEqualTo("claude-test-20250101");
  }

  @Test
  void successWithoutTelemetryRecordsItAsAbsent() {
    ProviderResponse response =
        ProviderResultMapper.success(Provider.CLAUDE, "answer", 12L, StreamTelemetry.none());

    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.inputTokens()).isNull();
    assertThat(response.outputTokens()).isNull();
    assertThat(response.model()).isNull();
  }

  @Test
  void nullTextIsEmpty() {
    ProviderResponse response =
        ProviderResultMapper.success(Provider.CLAUDE, null, 5L, StreamTelemetry.none());

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void blankTextIsEmpty() {
    ProviderResponse response =
        ProviderResultMapper.success(Provider.GEMINI, "   ", 5L, StreamTelemetry.none());

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
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.inputTokens()).isNull();
    assertThat(response.outputTokens()).isNull();
    assertThat(response.model()).isNull();
  }

  @Test
  void errorWithoutMessageFallsBack() {
    ProviderResponse response = ProviderResultMapper.error(Provider.GROK, null, null);

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("Erro no provedor.");
    assertThat(response.responseTimeMs()).isNull();
  }

  @Test
  void truncatedStreamIsAnErrorWithTheLocalizedMessage() {
    ProviderResponse response = ProviderResultMapper.truncated(Provider.CHATGPT, 321L);

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.text()).isNull();
    assertThat(response.errorMessage())
        .isEqualTo("A resposta foi interrompida antes de ser concluída. Tente novamente.");
    assertThat(response.responseTimeMs()).isEqualTo(321L);
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.inputTokens()).isNull();
    assertThat(response.outputTokens()).isNull();
    assertThat(response.model()).isNull();
  }

  @Test
  void timeoutHasNoLatencyAndNoTelemetry() {
    ProviderResponse response = ProviderResultMapper.timeout(Provider.DEEPSEEK);

    assertThat(response.outcome()).isEqualTo(Outcome.TIMEOUT);
    assertThat(response.text()).isNull();
    assertThat(response.errorMessage()).isEqualTo("Sem resposta dentro do tempo limite.");
    assertThat(response.responseTimeMs()).isNull();
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.inputTokens()).isNull();
    assertThat(response.outputTokens()).isNull();
    assertThat(response.model()).isNull();
  }
}
