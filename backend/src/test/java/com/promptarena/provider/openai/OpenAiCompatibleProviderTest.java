package com.promptarena.provider.openai;

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

/** WireMock-backed adapter tests for the OpenAI-compatible provider (no live keys/network). */
class OpenAiCompatibleProviderTest {

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

  private OpenAiCompatibleProvider provider(String apiKey) {
    return new OpenAiCompatibleProvider(Provider.CHATGPT, apiKey, "gpt-test", baseUrl);
  }

  /** A JSON error response (non-streaming) — the SDK raises, mapping to ERROR. */
  private void stubError(int status) {
    server.stubFor(
        post(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":{\"message\":\"boom\"}}")));
  }

  /**
   * A complete streaming (SSE) chat-completion response, mirroring the live API with {@code
   * stream_options.include_usage}: the given content deltas in order, the chunk carrying {@code
   * finish_reason: "stop"}, then a terminal usage chunk whose {@code choices} array is EMPTY, and
   * finally {@code [DONE]}. Every chunk reports the exact {@code model}.
   */
  private void stubChatStream(String... deltas) {
    StringBuilder body = deltaChunks(deltas);
    body.append(
            "data: {\"model\":\"gpt-test-2024\",\"choices\":[{\"index\":0,\"delta\":{},"
                + "\"finish_reason\":\"stop\"}]}\n\n")
        .append(
            "data: {\"model\":\"gpt-test-2024\",\"choices\":[],\"usage\":{\"prompt_tokens\":9,"
                + "\"completion_tokens\":21,\"total_tokens\":30}}\n\n")
        .append("data: [DONE]\n\n");
    stubStream(body);
  }

  /**
   * A truncated stream: the deltas arrive but the HTTP stream ends without any {@code
   * finish_reason} chunk, as happens on a mid-answer network drop.
   */
  private void stubIncompleteChatStream(String... deltas) {
    stubStream(deltaChunks(deltas));
  }

  private static StringBuilder deltaChunks(String... deltas) {
    StringBuilder body = new StringBuilder();
    for (String delta : deltas) {
      body.append(
              "data: {\"model\":\"gpt-test-2024\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"")
          .append(delta)
          .append("\"}}]}\n\n");
    }
    return body;
  }

  private void stubStream(StringBuilder body) {
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
    stubChatStream("Hello there");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.provider()).isEqualTo(Provider.CHATGPT);
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("Hello there");
    assertThat(response.errorMessage()).isNull();
    assertThat(response.responseTimeMs()).isNotNull();
  }

  @Test
  void streamsEachDeltaThenAggregatesTheFullText() {
    stubChatStream("Hello", " there", "!");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), tokens::add);

    assertThat(tokens).containsExactly("Hello", " there", "!");
    assertThat(response.text()).isEqualTo("Hello there!");
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void streamedSuccessHarvestsUsageModelAndTimeToFirstToken() {
    stubChatStream("Hello", " there");

    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), token -> {});

    // TTFT was stamped on the first delta, on the same clock as the total latency.
    assertThat(response.firstTokenMs()).isNotNull().isBetween(0L, response.responseTimeMs());
    // Usage rides the terminal chunk whose choices array is empty — chunks are read whole.
    assertThat(response.inputTokens()).isEqualTo(9L);
    assertThat(response.outputTokens()).isEqualTo(21L);
    assertThat(response.model()).isEqualTo("gpt-test-2024");
  }

  @Test
  void streamThatEndsWithoutAFinishReasonIsMappedToError() {
    // A mid-answer drop: deltas stream in, but the finish_reason chunk never arrives.
    stubIncompleteChatStream("The answer starts", " and is then cut");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), tokens::add);

    // The partial deltas still stream to the caller, but the result is a truncation error — never a
    // partial answer dressed up as a complete SUCCESS. A truncated stream carries no telemetry.
    assertThat(tokens).containsExactly("The answer starts", " and is then cut");
    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).contains("interrompida");
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.model()).isNull();
  }

  @Test
  void blankContentIsMappedToEmpty() {
    stubChatStream("");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void serverErrorIsMappedToError() {
    stubError(500);

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isNotNull();
    assertThat(response.responseTimeMs()).isNotNull();
  }

  @Test
  void nullKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider(null).complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  @Test
  void blankKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider("   ").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }
}
