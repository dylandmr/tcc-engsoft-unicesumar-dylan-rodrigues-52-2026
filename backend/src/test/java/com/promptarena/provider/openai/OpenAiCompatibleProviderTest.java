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

  private void stubChat(int status, String content) {
    String body =
        content == null
            ? "{\"error\":{\"message\":\"boom\"}}"
            : "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,"
                + "\"model\":\"gpt-test\",\"choices\":[{\"index\":0,\"message\":{\"role\":"
                + "\"assistant\",\"content\":\""
                + content
                + "\"},\"finish_reason\":\"stop\"}]}";
    server.stubFor(
        post(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  @Test
  void successfulResponseIsMappedToSuccess() {
    stubChat(200, "Hello there");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.provider()).isEqualTo(Provider.CHATGPT);
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(response.text()).isEqualTo("Hello there");
    assertThat(response.errorMessage()).isNull();
    assertThat(response.responseTimeMs()).isNotNull();
  }

  @Test
  void blankContentIsMappedToEmpty() {
    stubChat(200, "");

    ProviderResponse response = provider("test-key").complete(new PromptRequest("hi"));

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void serverErrorIsMappedToError() {
    stubChat(500, null);

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
