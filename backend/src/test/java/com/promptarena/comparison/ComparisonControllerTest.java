package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.promptarena.auth.CurrentUserService;
import com.promptarena.catalog.ModelCatalogService;
import com.promptarena.dto.AnalysisEvent;
import com.promptarena.dto.ResultEvent;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.web.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for the SSE-driving paths of the controller, where MockMvc's async machinery is
 * awkward. HTTP wiring (validation, persistence, listing) is covered by {@code
 * ComparisonEndpointTest}.
 */
@ExtendWith(MockitoExtension.class)
class ComparisonControllerTest {

  @Mock private ComparisonService comparisonService;
  @Mock private AnalysisService analysisService;
  @Mock private CurrentUserService currentUser;
  @Mock private ComparisonRepository comparisons;
  @Mock private ModelCatalogService modelCatalog;

  /** Runs the stream task inline so behavior is deterministic. */
  private final Executor inline = Runnable::run;

  private ComparisonController controller() {
    return new ComparisonController(
        comparisonService, analysisService, currentUser, comparisons, modelCatalog, inline, 8000);
  }

  /** The SSE event names sent through {@code emitter}, in order. */
  private static List<String> sentEventNames(SseEmitter emitter) throws IOException {
    ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
        ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
    verify(emitter, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
    return captor.getAllValues().stream().map(ComparisonControllerTest::eventName).toList();
  }

  /** Extracts the {@code event:} name from a built SSE event ("event:<name>\ndata:"). */
  private static String eventName(SseEmitter.SseEventBuilder builder) {
    String header = builder.build().iterator().next().getData().toString();
    return header.substring("event:".length(), header.indexOf('\n'));
  }

  private static ResultEvent sampleEvent() {
    return new ResultEvent(
        Provider.CLAUDE, Outcome.SUCCESS, "answer", null, 10L, 2L, 5L, 9L, "model-x");
  }

  @Test
  void runStreamPendingEmitsChunksResultsThenDone() throws Exception {
    Comparison comparison = new Comparison(new User("demo", "h"), "p", List.of(Provider.CLAUDE));
    SseEmitter emitter = mock(SseEmitter.class);
    when(comparisonService.execute(anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              BiConsumer<Provider, String> onToken = invocation.getArgument(1);
              Consumer<ResultEvent> onResult = invocation.getArgument(2);
              onToken.accept(Provider.CLAUDE, "hel");
              onResult.accept(sampleEvent());
              return 1;
            });

    controller().runStream(comparison, emitter);

    // chunk + result + done
    verify(emitter, org.mockito.Mockito.times(3)).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
  }

  @Test
  void runStreamCompleteReplaysPersistedResults() throws Exception {
    Comparison comparison = new Comparison(new User("demo", "h"), "p", List.of(Provider.CLAUDE));
    comparison.markComplete();
    SseEmitter emitter = mock(SseEmitter.class);
    when(comparisonService.replay(anyString(), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ResultEvent> callback = invocation.getArgument(1);
              callback.accept(sampleEvent());
              return 1;
            });

    controller().runStream(comparison, emitter);

    verify(comparisonService).replay(eq(comparison.getId()), any());
    verify(emitter).complete();
  }

  @Test
  void runStreamCompletesWithErrorWhenResultSendFails() throws Exception {
    Comparison comparison = new Comparison(new User("demo", "h"), "p", List.of(Provider.CLAUDE));
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    when(comparisonService.execute(anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ResultEvent> onResult = invocation.getArgument(2);
              onResult.accept(sampleEvent());
              return 1;
            });

    controller().runStream(comparison, emitter);

    verify(emitter).completeWithError(any());
  }

  @Test
  void runStreamCompletesWithErrorWhenChunkSendFails() throws Exception {
    Comparison comparison = new Comparison(new User("demo", "h"), "p", List.of(Provider.CLAUDE));
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    when(comparisonService.execute(anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              BiConsumer<Provider, String> onToken = invocation.getArgument(1);
              onToken.accept(Provider.CLAUDE, "x");
              return 1;
            });

    controller().runStream(comparison, emitter);

    verify(emitter).completeWithError(any());
  }

  @Test
  void streamLoadsOwnedComparisonAndDispatches() {
    User user = new User("demo", "h");
    Comparison comparison = new Comparison(user, "p", List.of(Provider.CLAUDE));
    when(currentUser.require()).thenReturn(user);
    when(comparisons.findByIdAndUser(comparison.getId(), user)).thenReturn(Optional.of(comparison));
    when(comparisonService.execute(anyString(), any(), any())).thenReturn(0);

    SseEmitter emitter = controller().stream(comparison.getId());

    assertThat(emitter).isNotNull();
    verify(comparisonService).execute(eq(comparison.getId()), any(), any());
  }

  @Test
  void streamRejectsUnownedComparisonWith404() {
    User user = new User("demo", "h");
    when(currentUser.require()).thenReturn(user);
    when(comparisons.findByIdAndUser(anyString(), eq(user))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller().stream("missing")).isInstanceOf(NotFoundException.class);
  }

  private static AnalysisEvent sampleAnalysisEvent() {
    return new AnalysisEvent(
        "as diferenças…",
        null,
        Provider.CHATGPT,
        "gpt-x",
        Map.of("A", Provider.CLAUDE, "B", Provider.GEMINI));
  }

  /** FR-021: replaying a COMPLETE comparison with a recorded analysis also emits it before done. */
  @Test
  void runStreamReplayWithRecordedAnalysisEmitsTheAnalysisEventBeforeDone() throws Exception {
    Comparison comparison =
        new Comparison(new User("demo", "h"), "p", List.of(Provider.CLAUDE, Provider.GEMINI));
    comparison.markComplete();
    comparison.recordAnalysis(
        "as diferenças…", Provider.CHATGPT, "gpt-x", List.of(Provider.CLAUDE, Provider.GEMINI));
    SseEmitter emitter = mock(SseEmitter.class);
    when(comparisonService.replay(anyString(), any()))
        .thenAnswer(
            invocation -> {
              Consumer<ResultEvent> callback = invocation.getArgument(1);
              callback.accept(sampleEvent());
              return 1;
            });

    controller().runStream(comparison, emitter);

    assertThat(sentEventNames(emitter)).containsExactly("result", "analysis", "done");
    verify(emitter).complete();
  }

  @Test
  void analysisStreamLoadsOwnedComparisonPreparesTheJudgeAndDispatches() {
    User user = new User("demo", "h");
    Comparison comparison = new Comparison(user, "p", List.of(Provider.CLAUDE, Provider.GEMINI));
    when(currentUser.require()).thenReturn(user);
    when(comparisons.findByIdAndUser(comparison.getId(), user)).thenReturn(Optional.of(comparison));
    JudgeSelection judge = new JudgeSelection(Provider.CHATGPT, "gpt-x");
    when(analysisService.prepare(comparison.getId(), "CHATGPT", "gpt-x")).thenReturn(judge);
    when(analysisService.run(eq(comparison.getId()), eq(judge), any()))
        .thenReturn(sampleAnalysisEvent());

    SseEmitter emitter = controller().analysisStream(comparison.getId(), "CHATGPT", "gpt-x");

    assertThat(emitter).isNotNull();
    verify(analysisService).run(eq(comparison.getId()), eq(judge), any());
  }

  @Test
  void analysisStreamRejectsUnownedComparisonWith404BeforeValidatingTheJudge() {
    User user = new User("demo", "h");
    when(currentUser.require()).thenReturn(user);
    when(comparisons.findByIdAndUser(anyString(), eq(user))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller().analysisStream("missing", "CHATGPT", "gpt-x"))
        .isInstanceOf(NotFoundException.class);
    verify(analysisService, org.mockito.Mockito.never()).prepare(anyString(), any(), any());
  }

  /** Persist-then-emit: the terminal analysis event is sent only after the service returned. */
  @Test
  void runAnalysisStreamEmitsChunksTerminalAnalysisThenDone() throws Exception {
    SseEmitter emitter = mock(SseEmitter.class);
    JudgeSelection judge = new JudgeSelection(Provider.CHATGPT, "gpt-x");
    when(analysisService.run(eq("c1"), eq(judge), any()))
        .thenAnswer(
            invocation -> {
              Consumer<String> onToken = invocation.getArgument(2);
              onToken.accept("as dife");
              onToken.accept("renças…");
              return sampleAnalysisEvent();
            });

    controller().runAnalysisStream("c1", judge, emitter);

    assertThat(sentEventNames(emitter))
        .containsExactly("analysis-chunk", "analysis-chunk", "analysis", "done");
    verify(emitter).complete();
  }

  @Test
  void runAnalysisStreamCompletesWithErrorWhenAChunkSendFails() throws Exception {
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    JudgeSelection judge = new JudgeSelection(Provider.CHATGPT, "gpt-x");
    when(analysisService.run(eq("c1"), eq(judge), any()))
        .thenAnswer(
            invocation -> {
              Consumer<String> onToken = invocation.getArgument(2);
              onToken.accept("x");
              return sampleAnalysisEvent();
            });

    controller().runAnalysisStream("c1", judge, emitter);

    verify(emitter).completeWithError(any());
  }
}
