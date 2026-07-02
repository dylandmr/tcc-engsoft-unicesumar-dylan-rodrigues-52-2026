package com.promptarena.catalog;

import com.promptarena.dto.ProviderCatalogEntry;
import com.promptarena.dto.ProviderDescriptor;
import com.promptarena.model.Provider;
import com.promptarena.provider.ProviderRegistry;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds each provider's selectable-model catalog (FR-020): exactly what that provider's own {@code
 * listModels()} API reports, sorted ascending and deduplicated — the system defines no default,
 * curated, or hardcoded model choices. Live lists are fetched concurrently (only for configured
 * providers), bounded by a short per-provider timeout, and cached per provider for a TTL. An
 * unconfigured provider, a failed fetch, or a timed-out fetch yields an EMPTY catalog for that one
 * provider only — mirroring FR-010's isolation, composing is never blocked.
 */
@Service
public class ModelCatalogService {

  private record CachedCatalog(ProviderCatalogEntry entry, long builtAtMs) {}

  private final Map<Provider, ProviderDescriptor> descriptors = new EnumMap<>(Provider.class);
  private final ProviderRegistry registry;
  private final Executor executor;
  private final Clock clock;
  private final long ttlMs;
  private final long fetchTimeoutMs;
  private final Map<Provider, CachedCatalog> cache = new ConcurrentHashMap<>();

  public ModelCatalogService(
      List<ProviderDescriptor> providerDescriptors,
      ProviderRegistry registry,
      @Qualifier("providerExecutor") Executor executor,
      Clock clock,
      @Value("${prompt-arena.model-catalog-ttl-ms:300000}") long ttlMs,
      @Value("${prompt-arena.model-catalog-fetch-timeout-ms:5000}") long fetchTimeoutMs) {
    providerDescriptors.forEach(
        descriptor -> this.descriptors.put(descriptor.provider(), descriptor));
    this.registry = registry;
    this.executor = executor;
    this.clock = clock;
    this.ttlMs = ttlMs;
    this.fetchTimeoutMs = fetchTimeoutMs;
  }

  /** Every supported provider's catalog, in canonical order (the {@link Provider} enum order). */
  public List<ProviderCatalogEntry> fullCatalog() {
    return List.copyOf(entries(List.of(Provider.values())).values());
  }

  /** The catalog entries for exactly {@code providers}, keyed by provider. */
  public Map<Provider, ProviderCatalogEntry> catalogFor(Collection<Provider> providers) {
    return entries(providers);
  }

  private Map<Provider, ProviderCatalogEntry> entries(Collection<Provider> providers) {
    long now = clock.millis();
    Map<Provider, ProviderCatalogEntry> result = new EnumMap<>(Provider.class);
    // Kick off every needed live fetch before assembling, so stale providers refresh in parallel.
    Map<Provider, CompletableFuture<List<String>>> fetches = new EnumMap<>(Provider.class);
    for (Provider provider : providers) {
      ProviderCatalogEntry fresh = freshEntry(provider, now);
      if (fresh != null) {
        result.put(provider, fresh);
      } else if (descriptors.get(provider).configured()) {
        fetches.put(provider, fetchLive(provider));
      }
    }
    for (Provider provider : providers) {
      if (!result.containsKey(provider)) {
        ProviderCatalogEntry entry = build(provider, liveModels(fetches.get(provider)));
        cache.put(provider, new CachedCatalog(entry, now));
        result.put(provider, entry);
      }
    }
    return result;
  }

  /** The cached entry when still within the TTL, otherwise {@code null}. */
  private ProviderCatalogEntry freshEntry(Provider provider, long now) {
    CachedCatalog cached = cache.get(provider);
    return cached != null && now - cached.builtAtMs() < ttlMs ? cached.entry() : null;
  }

  /** A bounded, isolated live fetch: failure or timeout resolves to {@code null}, never throws. */
  private CompletableFuture<List<String>> fetchLive(Provider provider) {
    return CompletableFuture.supplyAsync(() -> registry.get(provider).listModels(), executor)
        .orTimeout(fetchTimeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> null);
  }

  /** {@code null} when the fetch failed, timed out, or never started (unconfigured provider). */
  private static List<String> liveModels(CompletableFuture<List<String>> fetch) {
    return fetch == null ? null : fetch.join();
  }

  private ProviderCatalogEntry build(Provider provider, List<String> live) {
    List<String> models = live == null ? List.of() : List.copyOf(new TreeSet<>(live));
    return new ProviderCatalogEntry(provider, descriptors.get(provider).configured(), models);
  }
}
