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
import com.promptarena.dto.ResultEvent;
import com.promptarena.model.Comparison;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.web.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private CurrentUserService currentUser;
  @Mock private ComparisonRepository comparisons;

  /** Runs the stream task inline so behavior is deterministic. */
  private final Executor inline = Runnable::run;

  private ComparisonController controller() {
    return new ComparisonController(comparisonService, currentUser, comparisons, inline, 8000);
  }

  private static ResultEvent sampleEvent() {
    return new ResultEvent(Provider.CLAUDE, Outcome.SUCCESS, "answer", null, 10L);
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
}
