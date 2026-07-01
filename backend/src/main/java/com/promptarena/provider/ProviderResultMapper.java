package com.promptarena.provider;

import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Outcome;
import com.promptarena.model.Provider;

/**
 * Maps a raw provider call into the uniform {@link ProviderResponse}, classifying the outcome
 * (FR-011, FR-012, FR-013). This is the single place that decides {@code SUCCESS} vs {@code EMPTY}
 * vs {@code ERROR} vs {@code TIMEOUT}, so every adapter and the orchestrator agree.
 */
public final class ProviderResultMapper {

  private ProviderResultMapper() {}

  /**
   * A completed call. A blank/null body is a successful-but-empty response ({@code EMPTY}),
   * distinguished from an error (FR-013); otherwise {@code SUCCESS}.
   */
  public static ProviderResponse success(Provider provider, String text, Long responseTimeMs) {
    boolean empty = text == null || text.isBlank();
    return new ProviderResponse(
        provider, empty ? Outcome.EMPTY : Outcome.SUCCESS, empty ? "" : text, null, responseTimeMs);
  }

  /**
   * A failed call (HTTP error, SDK exception, unconfigured provider). The provider's own message is
   * kept verbatim (may be in the provider's language); only the fallback is localized. The {@code
   * provider_not_configured} sentinel is passed through by the adapters and must not be translated.
   */
  public static ProviderResponse error(Provider provider, String message, Long responseTimeMs) {
    return new ProviderResponse(
        provider,
        Outcome.ERROR,
        null,
        message == null ? "Erro no provedor." : message,
        responseTimeMs);
  }

  /** A call that exceeded the per-provider response-time limit (FR-012). */
  public static ProviderResponse timeout(Provider provider) {
    return new ProviderResponse(
        provider, Outcome.TIMEOUT, null, "Sem resposta dentro do tempo limite.", null);
  }
}
