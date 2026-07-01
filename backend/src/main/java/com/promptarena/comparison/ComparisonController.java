package com.promptarena.comparison;

import com.promptarena.auth.CurrentUserService;
import com.promptarena.dto.ChunkEvent;
import com.promptarena.dto.ComparisonDetailResponse;
import com.promptarena.dto.ComparisonListResponse;
import com.promptarena.dto.ComparisonSummary;
import com.promptarena.dto.CreateComparisonRequest;
import com.promptarena.dto.CreateComparisonResponse;
import com.promptarena.dto.DoneEvent;
import com.promptarena.dto.ResultEvent;
import com.promptarena.model.Comparison;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.Status;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.web.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST + SSE endpoints for comparisons (US1/US2). Following the contract, {@code POST} only
 * validates and persists a {@code PENDING} comparison; the provider fan-out is triggered lazily
 * when the SSE stream is opened, so the client is always subscribed before any {@code result} event
 * is emitted. All data is scoped to the authenticated user (FR-016).
 */
@RestController
@RequestMapping("/api/comparisons")
public class ComparisonController {

  /** Generous stream timeout — the orchestrator's per-provider timeouts bound real work. */
  private static final long STREAM_TIMEOUT_MS = 120_000L;

  private final ComparisonService comparisonService;
  private final CurrentUserService currentUser;
  private final ComparisonRepository comparisons;
  private final Executor streamExecutor;
  private final int maxPromptLen;

  public ComparisonController(
      ComparisonService comparisonService,
      CurrentUserService currentUser,
      ComparisonRepository comparisons,
      @Qualifier("providerExecutor") Executor streamExecutor,
      @Value("${prompt-arena.max-prompt-len:8000}") int maxPromptLen) {
    this.comparisonService = comparisonService;
    this.currentUser = currentUser;
    this.comparisons = comparisons;
    this.streamExecutor = streamExecutor;
    this.maxPromptLen = maxPromptLen;
  }

  /**
   * Validate, persist a {@code PENDING} comparison owned by the caller, return its id (FR-004/5/6).
   */
  @PostMapping
  public ResponseEntity<CreateComparisonResponse> create(
      @RequestBody CreateComparisonRequest body) {
    List<Provider> providers =
        ComparisonValidator.validate(body.prompt(), body.providers(), maxPromptLen);
    User user = currentUser.require();
    Comparison comparison = comparisons.save(new Comparison(user, body.prompt(), providers));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreateComparisonResponse(comparison.getId(), providers));
  }

  /** List the caller's comparisons, newest first (FR-015, FR-017). */
  @GetMapping
  public ComparisonListResponse list() {
    User user = currentUser.require();
    List<ComparisonSummary> items =
        comparisons.findByUserOrderByCreatedAtDesc(user).stream()
            .map(
                comparison ->
                    new ComparisonSummary(
                        comparison.getId(),
                        comparison.getPrompt(),
                        comparison.getProviders(),
                        comparison.getCreatedAt()))
            .toList();
    return new ComparisonListResponse(items);
  }

  /**
   * Full detail of one owned comparison, including each recorded result (FR-014, FR-016).
   * Transactional so the lazy {@code results} collection loads within the session (open-in-view is
   * disabled, so view-time lazy access would otherwise fail).
   */
  @Transactional(readOnly = true)
  @GetMapping("/{id}")
  public ComparisonDetailResponse detail(@PathVariable String id) {
    Comparison comparison = loadOwned(id);
    List<ResultEvent> results =
        comparison.getResults().stream().map(ComparisonController::toEvent).toList();
    return new ComparisonDetailResponse(
        comparison.getId(), comparison.getPrompt(), comparison.getCreatedAt(), results);
  }

  /**
   * Open the live results stream. Opening triggers the fan-out for a {@code PENDING} comparison or
   * replays a {@code COMPLETE} one (FR-008, FR-009, FR-010). The work runs on a virtual thread so
   * it does not tie up the request thread; the emitter is always completed.
   */
  @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String id) {
    Comparison comparison = loadOwned(id);
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    streamExecutor.execute(() -> runStream(comparison, emitter));
    return emitter;
  }

  /** Drive one stream to completion, isolating any failure into {@code completeWithError}. */
  void runStream(Comparison comparison, SseEmitter emitter) {
    try {
      int completed =
          comparison.getStatus() == Status.PENDING
              ? comparisonService.execute(
                  comparison.getId(),
                  (provider, delta) -> sendChunk(emitter, provider, delta),
                  event -> send(emitter, event))
              : comparisonService.replay(comparison.getId(), event -> send(emitter, event));
      emitter.send(
          SseEmitter.event().name("done").data(new DoneEvent(comparison.getId(), completed)));
      emitter.complete();
    } catch (Exception ex) {
      emitter.completeWithError(ex);
    }
  }

  private static void send(SseEmitter emitter, ResultEvent event) {
    try {
      emitter.send(SseEmitter.event().name("result").data(event));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** Emit one provider's incremental text delta as a {@code chunk} SSE event. */
  private static void sendChunk(SseEmitter emitter, Provider provider, String delta) {
    try {
      emitter.send(SseEmitter.event().name("chunk").data(new ChunkEvent(provider, delta)));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private Comparison loadOwned(String id) {
    User user = currentUser.require();
    return comparisons
        .findByIdAndUser(id, user)
        .orElseThrow(() -> new NotFoundException("comparison not found: " + id));
  }

  private static ResultEvent toEvent(ProviderResult result) {
    return new ResultEvent(
        result.getProvider(),
        result.getOutcome(),
        result.getResponseText(),
        result.getErrorMessage(),
        result.getResponseTimeMs(),
        result.getFirstTokenMs(),
        result.getInputTokens(),
        result.getOutputTokens(),
        result.getModel());
  }
}
