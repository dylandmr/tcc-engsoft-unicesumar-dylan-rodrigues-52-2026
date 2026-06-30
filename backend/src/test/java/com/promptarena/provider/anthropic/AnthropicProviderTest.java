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

  private void stubMessage(int status, String contentArray) {
    String body =
        contentArray == null
            ? "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}"
            : "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":"
                + "\"claude-test\",\"content\":"
                + contentArray
                + ",\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":1,"
                + "\"output_tokens\":1}}";
    server.stubFor(
        post(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  @Test
  void successfulResponseConcatenatesTextBlocks() {
    stubMessage(200, "[{\"type\":\"text\",\"text\":\"Hello \"},{\"type\":\"text\",\"text\":\"world\"}]");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.provider()).isEqualTo(Provider.CLAUDE);
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("Hello world");
  }

  @Test
  void emptyContentIsMappedToEmpty() {
    stubMessage(200, "[]");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void serverErrorIsMappedToError() {
    stubMessage(500, null);

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
