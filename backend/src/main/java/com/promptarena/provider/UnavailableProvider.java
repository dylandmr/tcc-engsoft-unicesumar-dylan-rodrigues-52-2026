package com.promptarena.provider;

import com.promptarena.dto.PromptRequest;
import com.promptarena.dto.ProviderResponse;
import com.promptarena.model.Provider;

/**
 * Stand-in for a provider that has no adapter registered. Its call always yields an {@code ERROR}
 * result so a missing provider surfaces in its own panel without breaking the others (FR-010).
 */
public final class UnavailableProvider implements LlmProvider {

  private final Provider id;

  public UnavailableProvider(Provider id) {
    this.id = id;
  }

  @Override
  public Provider id() {
    return id;
  }

  @Override
  public ProviderResponse complete(PromptRequest request) {
    return ProviderResultMapper.error(id, "provider_not_configured", null);
  }
}
