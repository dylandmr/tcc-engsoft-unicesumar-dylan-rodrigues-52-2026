package com.promptarena.comparison;

import com.promptarena.auth.CurrentUserService;
import com.promptarena.catalog.ModelCatalogService;
import com.promptarena.dto.AnalysisChunkEvent;
import com.promptarena.dto.AnalysisEvent;
import com.promptarena.dto.AnalysisSummary;
import com.promptarena.dto.ChunkEvent;
import com.promptarena.dto.ComparisonDetailResponse;
import com.promptarena.dto.ComparisonListResponse;
import com.promptarena.dto.ComparisonSummary;
import com.promptarena.dto.CreateComparisonRequest;
import com.promptarena.dto.CreateComparisonResponse;
import com.promptarena.dto.DoneEvent;
import com.promptarena.dto.ProviderCatalogEntry;
import com.promptarena.dto.ResultEvent;
import com.promptarena.dto.StatsResponse;
import com.promptarena.model.Comparison;
import com.promptarena.model.Provider;
import com.promptarena.model.ProviderResult;
import com.promptarena.model.Status;
import com.promptarena.model.User;
import com.promptarena.repository.ComparisonRepository;
import com.promptarena.web.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final AnalysisService analysisService;
  private final CurrentUserService currentUser;
  private final ComparisonRepository comparisons;
  private final ModelCatalogService modelCatalog;
  private final Executor streamExecutor;
  private final int maxPromptLen;

  public ComparisonController(
      ComparisonService comparisonService,
      AnalysisService analysisService,
      CurrentUserService currentUser,
      ComparisonRepository comparisons,
      ModelCatalogService modelCatalog,
      @Qualifier("providerExecutor") Executor streamExecutor,
      @Value("${prompt-arena.max-prompt-len:8000}") int maxPromptLen) {
    this.comparisonService = comparisonService;
    this.analysisService = analysisService;
    this.currentUser = currentUser;
    this.comparisons = comparisons;
    this.modelCatalog = modelCatalog;
    this.streamExecutor = streamExecutor;
    this.maxPromptLen = maxPromptLen;
  }

  /**
   * Validate, persist a {@code PENDING} comparison owned by the caller, return its id (FR-004/5/6).
   * The request MUST name a model for every selected provider, chosen from that provider's current
   * live catalog — there are no defaults. The validated map is persisted as-is with the comparison
   * (FR-020).
   */
  @PostMapping
  public ResponseEntity<CreateComparisonResponse> create(
      @RequestBody CreateComparisonRequest body) {
    List<Provider> providers =
        ComparisonValidator.validate(body.prompt(), body.providers(), maxPromptLen);
    Map<Provider, ProviderCatalogEntry> catalog = modelCatalog.catalogFor(providers);
    Map<Provider, String> models =
        ComparisonValidator.requireModels(
            body.models(), providers, provider -> Set.copyOf(catalog.get(provider).models()));
    User user = currentUser.require();
    Comparison comparison =
        comparisons.save(new Comparison(user, body.prompt(), providers, models));
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
   * Per-provider aggregate statistics over the caller's own recorded results, derived at read time
   * from the raw rows — nothing persisted (FR-023, FR-016). A caller with no history gets an empty
   * list. The literal path is mapped before Spring considers the {@code /{id}} detail route.
   */
  @GetMapping("/stats")
  public StatsResponse stats() {
    return new StatsResponse(
        ProviderStatsAggregator.aggregate(comparisons.findStatsRowsByUser(currentUser.require())));
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
    // Copied out of the entity so serialization never touches a JPA collection; empty for
    // comparisons persisted before model selection existed (FR-020).
    Map<Provider, String> models = new EnumMap<>(Provider.class);
    models.putAll(comparison.getModels());
    return new ComparisonDetailResponse(
        comparison.getId(),
        comparison.getPrompt(),
        comparison.getCreatedAt(),
        models,
        results,
        toAnalysisSummary(comparison));
  }

  /**
   * Permanently delete one owned comparison and everything recorded for it — provider results,
   * telemetry, chosen models, analysis (FR-022). An unknown id and another user's id are the same
   * non-revealing 404 (FR-016). CSRF is enforced by the security chain (state-changing method).
   * Transactional so the ownership load and the cascading entity delete share one persistence
   * context.
   */
  @Transactional
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    comparisons.delete(loadOwned(id));
    return ResponseEntity.noContent().build();
  }

  /**
   * Permanently clear the caller's entire history (FR-022). Only comparisons owned by the current
   * user are touched (FR-016), and an already-empty history is a no-op — the endpoint is idempotent
   * and always answers 204. CSRF is enforced by the security chain (state-changing method).
   */
  @Transactional
  @DeleteMapping
  public ResponseEntity<Void> clear() {
    comparisons.deleteByUser(currentUser.require());
    return ResponseEntity.noContent().build();
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
      int completed;
      if (comparison.getStatus() == Status.PENDING) {
        completed =
            comparisonService.execute(
                comparison.getId(),
                (provider, delta) -> sendChunk(emitter, provider, delta),
                event -> send(emitter, event));
      } else {
        completed = comparisonService.replay(comparison.getId(), event -> send(emitter, event));
        // A replayed comparison that has a recorded analysis also emits it (FR-021) — additive,
        // terminal shape only (no chunks), before "done".
        if (comparison.hasAnalysis()) {
          emitter.send(
              SseEmitter.event().name("analysis").data(AnalysisService.recordedEvent(comparison)));
        }
      }
      emitter.send(
          SseEmitter.event().name("done").data(new DoneEvent(comparison.getId(), completed)));
      emitter.complete();
    } catch (Exception ex) {
      emitter.completeWithError(ex);
    }
  }

  /**
   * Open the comparative-analysis stream (FR-021): generate the key-differences analysis with the
   * caller-picked judge, or replay the recorded one (judge parameters then ignored). Ownership and
   * validation (judge parameters, comparison completeness, at least two successful answers) are
   * checked here on the request thread, so failures surface as HTTP 404/400 before any SSE.
   */
  @GetMapping(value = "/{id}/analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter analysisStream(
      @PathVariable String id,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model) {
    Comparison comparison = loadOwned(id);
    JudgeSelection judge = analysisService.prepare(comparison.getId(), provider, model);
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    streamExecutor.execute(() -> runAnalysisStream(comparison.getId(), judge, emitter));
    return emitter;
  }

  /**
   * Drive one analysis stream to completion: judge deltas as {@code analysis-chunk} events
   * (generation only), then the terminal {@code analysis} event — persisted first when the judge
   * succeeded — then {@code done}. Any failure is isolated into {@code completeWithError}.
   */
  void runAnalysisStream(String comparisonId, JudgeSelection judge, SseEmitter emitter) {
    try {
      AnalysisEvent analysis =
          analysisService.run(comparisonId, judge, delta -> sendAnalysisChunk(emitter, delta));
      emitter.send(SseEmitter.event().name("analysis").data(analysis));
      emitter.send(SseEmitter.event().name("done").data(new DoneEvent(comparisonId, 1)));
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

  /** Emit one judge text delta as an {@code analysis-chunk} SSE event (FR-021). */
  private static void sendAnalysisChunk(SseEmitter emitter, String delta) {
    try {
      emitter.send(SseEmitter.event().name("analysis-chunk").data(new AnalysisChunkEvent(delta)));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** The recorded analysis for the detail response, or {@code null} when none exists (FR-021). */
  private static AnalysisSummary toAnalysisSummary(Comparison comparison) {
    if (!comparison.hasAnalysis()) {
      return null;
    }
    return new AnalysisSummary(
        comparison.getAnalysisText(),
        comparison.getAnalysisProvider(),
        comparison.getAnalysisModel(),
        AnalysisService.labels(comparison.getAnalysisOrder()));
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
