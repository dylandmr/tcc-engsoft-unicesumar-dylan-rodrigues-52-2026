package com.promptarena.provider.openai;

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
    return provider(Provider.CHATGPT, apiKey);
  }

  private OpenAiCompatibleProvider provider(Provider id, String apiKey) {
    return new OpenAiCompatibleProvider(id, apiKey, baseUrl);
  }

  /** FR-020: the model always rides the request — adapters have no configured model. */
  private static PromptRequest request() {
    return new PromptRequest("hi", "gpt-test");
  }

  /** Mirrors the live OpenAI-style {@code GET /v1/models} list shape for the given model ids. */
  private void stubModelList(String... ids) {
    StringBuilder data = new StringBuilder();
    for (String id : ids) {
      if (data.length() > 0) {
        data.append(',');
      }
      data.append("{\"id\":\"")
          .append(id)
          .append("\",\"object\":\"model\",\"created\":1700000000,\"owned_by\":\"system\"}");
    }
    server.stubFor(
        get(urlPathEqualTo("/models"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"object\":\"list\",\"data\":[" + data + "]}")));
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

    ProviderResponse response = provider("test-key").complete(request());

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
    ProviderResponse response = provider("test-key").stream(request(), tokens::add);

    assertThat(tokens).containsExactly("Hello", " there", "!");
    assertThat(response.text()).isEqualTo("Hello there!");
    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void streamedSuccessHarvestsUsageModelAndTimeToFirstToken() {
    stubChatStream("Hello", " there");

    ProviderResponse response = provider("test-key").stream(request(), token -> {});

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
    ProviderResponse response = provider("test-key").stream(request(), tokens::add);

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

    ProviderResponse response = provider("test-key").complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(response.text()).isEmpty();
  }

  @Test
  void serverErrorIsMappedToError() {
    stubError(500);

    ProviderResponse response = provider("test-key").complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isNotNull();
    assertThat(response.responseTimeMs()).isNotNull();
  }

  @Test
  void nullKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider(null).complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  @Test
  void blankKeyIsUnavailableAndYieldsError() {
    ProviderResponse response = provider("   ").complete(request());

    assertThat(response.outcome()).isEqualTo(Outcome.ERROR);
    assertThat(response.errorMessage()).isEqualTo("provider_not_configured");
  }

  /** FR-020: the model on the wire is exactly {@code request.model()} — there is no fallback. */
  @Test
  void streamSendsTheRequestedModelOnTheWire() {
    stubChatStream("Hello");

    ProviderResponse response =
        provider("test-key").stream(new PromptRequest("hi", "gpt-chosen"), token -> {});

    assertThat(response.outcome()).isEqualTo(Outcome.SUCCESS);
    server.verify(
        postRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-chosen"))));
  }

  @Test
  void listModelsForChatgptKeepsChatFamiliesAndDropsModalityVariants() {
    // gpt-*, chatgpt-* and o<N>* qualify; audio/image/embedding/etc. variants do not.
    stubModelList(
        "gpt-4o",
        "chatgpt-4o-latest",
        "o3-mini",
        "gpt-4o-audio-preview",
        "dall-e-3",
        "whisper-1",
        "text-embedding-3-small");

    assertThat(provider("test-key").listModels())
        .containsExactly("gpt-4o", "chatgpt-4o-latest", "o3-mini");
  }

  @Test
  void listModelsForGrokKeepsOnlyGrokIds() {
    stubModelList("grok-4", "grok-3-mini", "gpt-4o");

    assertThat(provider(Provider.GROK, "test-key").listModels())
        .containsExactly("grok-4", "grok-3-mini");
  }

  @Test
  void listModelsForDeepseekKeepsOnlyDeepseekIds() {
    stubModelList("deepseek-chat", "deepseek-reasoner", "grok-4");

    assertThat(provider(Provider.DEEPSEEK, "test-key").listModels())
        .containsExactly("deepseek-chat", "deepseek-reasoner");
  }

  @Test
  void listModelsIsEmptyWhenUnconfigured() {
    assertThat(provider(null).listModels()).isEmpty();
  }
}
