package com.promptarena.provider.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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

/** WireMock-backed adapter tests for the Anthropic (Claude) provider (no live keys/network). */
class AnthropicProviderTest {

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

  private AnthropicProvider provider(String apiKey) {
    return new AnthropicProvider(apiKey, baseUrl);
  }

  /** FR-020: the model always rides the request — adapters have no configured model. */
  private static PromptRequest request() {
    return new PromptRequest("hi", "claude-test");
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
                        "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));
  }

  /**
   * A streaming (SSE) Messages response delivering the given text deltas within the canonical
   * message_start → content_block_delta* → message_stop event sequence. Mirroring the live API,
   * message_start reports the exact model + input usage, and the closing message_delta reports the
   * CUMULATIVE output token count.
   */
  private void stubMessagesStream(String... deltas) {
    StringBuilder body = openingEvents(deltas);
    body.append("event: content_block_stop\n")
        .append("data: {\"type\":\"content_block_stop\",\"index\":0}\n\n")
        .append("event: message_delta\n")
        .append(
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null},\"usage\":{\"output_tokens\":27}}\n\n")
        .append("event: message_stop\n")
        .append("data: {\"type\":\"message_stop\"}\n\n");
    stubStream(body);
  }

  /**
   * A truncated stream: the deltas arrive but the HTTP stream ends before the terminal {@code
   * message_stop} event, as happens on a mid-answer network drop.
   */
  private void stubIncompleteMessagesStream(String... deltas) {
    stubStream(openingEvents(deltas));
  }

  private static StringBuilder openingEvents(String... deltas) {
    StringBuilder body = new StringBuilder();
    body.append("event: message_start\n")
        .append(
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\","
                + "\"role\":\"assistant\",\"content\":[],\"model\":\"claude-test-20250101\","
                + "\"stop_reason\":null,\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":9,\"output_tokens\":1}}}\n\n")
        .append("event: content_block_start\n")
        .append(
            "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\","
                + "\"text\":\"\"}}\n\n");
    for (String delta : deltas) {
      body.append("event: content_block_delta\n")
          .append(
              "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\","
                  + "\"text\":\"")
          .append(delta)
          .append("\"}}\n\n");
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
  void successfulResponseConcatenatesTextDeltas() {
    stubMessagesStream("Hello ", "world");

    ProviderResponse response = provider("test-key").complete(request());

    assertThat(response.provider()).isEqualTo(Provider.CLAUDE);
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("Hello world");
  }

  @Test
  void streamsEachDeltaThenAggregatesTheFullText() {
    stubMessagesStream("Hello", " ", "world");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(request(), tokens::add);

    assertThat(tokens).containsExactly("Hello", " ", "world");
    assertThat(response.text()).isEqualTo("Hello world");
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void streamedSuccessHarvestsUsageModelAndTimeToFirstToken() {
    stubMessagesStream("Hello ", "world");

    ProviderResponse response = provider("test-key").stream(request(), token -> {});

    // TTFT was stamped on the first delta, on the same clock as the total latency.
    assertThat(response.firstTokenMs()).isNotNull().isBetween(0L, response.responseTimeMs());
    // input usage + exact model ride message_start; the last (cumulative) message_delta wins.
    assertThat(response.inputTokens()).isEqualTo(9L);
    assertThat(response.outputTokens()).isEqualTo(27L);
    assertThat(response.model()).isEqualTo("claude-test-20250101");
  }

  @Test
  void streamThatEndsWithoutMessageStopIsMappedToError() {
    // A mid-answer drop: deltas stream in, but the terminal message_stop event never arrives.
    stubIncompleteMessagesStream("The answer starts", " and is then cut");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(request(), tokens::add);

    // The partial deltas still stream to the caller, but the result is a truncation error — never a
    // partial answer dressed up as a complete SUCCESS. A truncated stream carries no telemetry,
    // even though message_start had already reported usage and model.
    assertThat(tokens).containsExactly("The answer starts", " and is then cut");
    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).contains("interrompida");
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.inputTokens()).isNull();
    assertThat(response.model()).isNull();
  }

  @Test
  void emptyContentIsMappedToEmpty() {
    stubMessagesStream();

    ProviderResponse response = provider("test-key").complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
    // No token ever streamed, so there is no TTFT — but the reported usage/model still count.
    assertThat(response.firstTokenMs()).isNull();
    assertThat(response.inputTokens()).isEqualTo(9L);
    assertThat(response.model()).isEqualTo("claude-test-20250101");
  }

  @Test
  void serverErrorIsMappedToError() {
    stubError(500);

    ProviderResponse response = provider("test-key").complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isNotNull();
  }

  @Test
  void nullKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider(null).complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  @Test
  void blankKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider("").complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  /** FR-020: the model on the wire is exactly {@code request.model()} — there is no fallback. */
  @Test
  void streamSendsTheRequestedModelOnTheWire() {
    stubMessagesStream("Hello");

    ProviderResponse response =
        provider("test-key").stream(new PromptRequest("hi", "claude-chosen"), token -> {});

    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    server.verify(
        postRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-chosen"))));
  }

  /** Mirrors the live {@code GET /v1/models} shape — every returned id is a chat model. */
  @Test
  void listModelsReturnsEveryReportedId() {
    server.stubFor(
        get(urlPathEqualTo("/v1/models"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"data":[
                          {"type":"model","id":"claude-sonnet-4-5",
                           "display_name":"Claude Sonnet 4.5","created_at":"2025-09-29T00:00:00Z"},
                          {"type":"model","id":"claude-3-5-haiku-20241022",
                           "display_name":"Claude Haiku 3.5","created_at":"2024-10-22T00:00:00Z"}
                        ],"has_more":false,
                        "first_id":"claude-sonnet-4-5","last_id":"claude-3-5-haiku-20241022"}
                        """)));

    assertThat(provider("test-key").listModels())
        .containsExactly("claude-sonnet-4-5", "claude-3-5-haiku-20241022");
  }

  @Test
  void listModelsIsEmptyWhenUnconfigured() {
    assertThat(provider(null).listModels()).isEmpty();
  }
}
