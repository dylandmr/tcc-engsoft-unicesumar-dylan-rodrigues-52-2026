package com.promptarena.provider;

import com.promptarena.model.Provider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps each {@link Provider} to its {@link LlmProvider} adapter. A provider with no adapter (none
 * registered) resolves to an {@link UnavailableProvider}, so the fan-out can always call something
 * and a misconfiguration becomes that provider's own error rather than a crash (FR-010).
 */
@Component
public class ProviderRegistry {

  private final Map<Provider, LlmProvider> adapters;

  public ProviderRegistry(List<LlmProvider> providers) {
    Map<Provider, LlmProvider> byId = new EnumMap<>(Provider.class);
    for (LlmProvider provider : providers) {
      byId.put(provider.id(), provider);
    }
    this.adapters = byId;
  }

  /** The adapter for {@code provider}, or an {@link UnavailableProvider} if none is registered. */
  public LlmProvider get(Provider provider) {
    LlmProvider adapter = adapters.get(provider);
    return adapter != null ? adapter : new UnavailableProvider(provider);
  }
}
