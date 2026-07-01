package com.promptarena.provider.anthropic;

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
    return new AnthropicProvider(apiKey, "claude-test", baseUrl);
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
   * message_start → content_block_delta* → message_stop event sequence.
   */
  private void stubMessagesStream(String... deltas) {
    StringBuilder body = new StringBuilder();
    body.append("event: message_start\n")
        .append(
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\","
                + "\"role\":\"assistant\",\"content\":[],\"model\":\"claude-test\",\"stop_reason\":null,"
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}}\n\n")
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
    body.append("event: content_block_stop\n")
        .append("data: {\"type\":\"content_block_stop\",\"index\":0}\n\n")
        .append("event: message_delta\n")
        .append(
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null},\"usage\":{\"output_tokens\":1}}\n\n")
        .append("event: message_stop\n")
        .append("data: {\"type\":\"message_stop\"}\n\n");
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

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.provider()).isEqualTo(Provider.CLAUDE);
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("Hello world");
  }

  @Test
  void streamsEachDeltaThenAggregatesTheFullText() {
    stubMessagesStream("Hello", " ", "world");

    List<String> tokens = new ArrayList<>();
    ProviderResponse response = provider("test-key").stream(new PromptRequest("hi"), tokens::add);

    assertThat(tokens).containsExactly("Hello", " ", "world");
    assertThat(response.text()).isEqualTo("Hello world");
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void emptyContentIsMappedToEmpty() {
    stubMessagesStream();

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
