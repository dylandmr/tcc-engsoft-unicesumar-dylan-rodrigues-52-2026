package com.promptarena.provider.google;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** WireMock-backed adapter tests for the Google Gemini provider (no live keys/network). */
class GeminiProviderTest {

  private WireMockServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    baseUrl = "http://localhost:" + server.port();
  }

  @AfterEach
  void stopServer() {
    server.stop();
  }

  private GeminiProvider provider(String apiKey) {
    return new GeminiProvider(apiKey, "gemini-test", baseUrl);
  }

  /** A JSON error response — the SDK raises, mapping to ERROR. */
  private void stubError(int status) {
    server.stubFor(
        post(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":{\"code\":500,\"message\":\"boom\",\"status\":\"INTERNAL\"}}")));
  }

  /**
   * A complete streaming (SSE) generateContent response: the given text deltas in order, with the
   * model's terminal {@code finishReason: STOP} stamped on the last chunk (as the live API does).
   * Mirroring the live API, every chunk reports {@code modelVersion} and a {@code usageMetadata}
   * with the prompt count, while the final output count ({@code candidatesTokenCount}) only arrives
   * on the terminal chunk.
   */
  private void stubGenerateStream(String... deltas) {
    stubStream(true, deltas);
  }

  /**
   * A truncated stream: the given deltas arrive but the HTTP stream ends without any {@code
   * finishReason}, as happens on a mid-answer network drop.
   */
  private void stubIncompleteStream(String... deltas) {
    stubStream(false, deltas);
  }

  private void stubStream(boolean complete, String... deltas) {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < deltas.length; i++) {
      boolean terminal = complete && i == deltas.length - 1;
      body.append("data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"")
          .append(deltas[i])
          .append("\"}]}");
      if (terminal) {
        body.append(",\"finishReason\":\"STOP\"");
      }
      body.append("}],\"usageMetadata\":{\"promptTokenCount\":7");
      if (terminal) {
        body.append(",\"candidatesTokenCount\":12,\"totalTokenCount\":19");
      } else {
        body.append(",\"totalTokenCount\":7");
      }
      body.append("},\"modelVersion\":\"gemini-test-001\"}\n\n");
    }
    server.stubFor(
        post(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(body.toString())));
  }

  @Test
  void successfulResponseIsMappedToSuccess() {
    stubGenerateStream("Hello from Gemini");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.provider()).isEqualTo(Provider.GEMINI);
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("Hello from Gemini");
  }

  @Test
  void streamsEachDeltaThenAggregatesTheFullText() {
    stubGenerateStream("Hello", " from", " Gemini");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), tokens::add);

    assertThat(tokens).containsExactly("Hello", " from", " Gemini");
    assertThat(response.text()).isEqualTo("Hello from Gemini");
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void streamedSuccessHarvestsUsageModelAndTimeToFirstToken() {
    stubGenerateStream("Hello", " from", " Gemini");

    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), token -> {});

    // TTFT was stamped on the first delta, on the same clock as the total latency.
    assertThat(response.firstTokenMs()).isNotNull().isBetween(0L, response.responseTimeMs());
    // The last non-empty usage values win — the terminal chunk carries the final counts.
    assertThat(response.inputTokens()).isEqualTo(7L);
    assertThat(response.outputTokens()).isEqualTo(12L);
    assertThat(response.model()).isEqualTo("gemini-test-001");
  }

  @Test
  void streamThatEndsWithoutAFinishReasonIsMappedToError() {
    // A mid-answer drop: deltas stream in, but the terminal finishReason chunk never arrives.
    stubIncompleteStream("A resposta começa", " e então é cortada");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), tokens::add);

    // The partial deltas still stream to the caller, but the result is a truncation error — never a
    // partial answer dressed up as a complete SUCCESS. A truncated stream carries no telemetry.
    assertThat(tokens).containsExactly("A resposta começa", " e então é cortada");
    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).contains("interrompida");
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.model()).isNull();
  }

  @Test
  void serverErrorIsMappedToError() {
    stubError(500);

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isNotNull();
  }

  @Test
  void nullKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider(null).complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  @Test
  void blankKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider("").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }
}
