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

  /** A streaming (SSE) chat-completion response delivering the given content deltas in order. */
  private void stubChatStream(String... deltas) {
    StringBuilder body = new StringBuilder();
    for (String delta : deltas) {
      body.append("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"")
          .append(delta)
          .append("\"}}]}\n\n");
    }
    body.append("data: [DONE]\n\n");
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
