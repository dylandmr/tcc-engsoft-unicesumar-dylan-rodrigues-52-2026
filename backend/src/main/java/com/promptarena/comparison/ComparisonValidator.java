package com.promptarena.comparison;

import com.promptarena.model.Provider;
import com.promptarena.web.ValidationException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates a comparison request and parses provider names, mapping each failure to the
 * machine-readable error code of the REST contract (FR-005, FR-006, FR-020). Pure logic, no Spring
 * — the per-provider allowed-model sets are passed in by the caller.
 */
final class ComparisonValidator {

  private ComparisonValidator() {}

  /** The maximum number of providers a single comparison may target (FR-005). */
  private static final int MAX_PROVIDERS = 4;

  /**
   * Validate the prompt and provider list. Returns the parsed providers in request order, or throws
   * a {@link ValidationException} carrying the first failing rule's code.
   */
  static List<Provider> validate(String prompt, List<String> providerNames, int maxPromptLen) {
    if (prompt == null || prompt.isBlank()) {
      throw new ValidationException("empty_prompt");
    }
    if (prompt.length() > maxPromptLen) {
      throw new ValidationException("prompt_too_long");
    }
    if (providerNames == null || providerNames.isEmpty()) {
      throw new ValidationException("no_providers");
    }
    if (providerNames.size() > MAX_PROVIDERS) {
      throw new ValidationException("too_many_providers");
    }
    List<Provider> parsed = new ArrayList<>();
    Set<Provider> seen = EnumSet.noneOf(Provider.class);
    for (String name : providerNames) {
      Provider provider = parse(name);
      if (!seen.add(provider)) {
        throw new ValidationException("duplicate_provider");
      }
      parsed.add(provider);
    }
    return parsed;
  }

  private static Provider parse(String name) {
    try {
      return Provider.valueOf(name);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ValidationException("unknown_provider");
    }
  }

  /**
   * Validate the per-provider model choices (FR-020): the map MUST name a model for every selected
   * provider — there are no defaults anywhere. A choice for a provider that is not selected (or not
   * a provider at all) fails with {@code model_for_unselected_provider}; a selected provider with
   * no entry or a blank one fails with {@code missing_model}; a choice outside the provider's
   * current catalog fails with {@code unknown_model}. Returns the validated choices, keyed by
   * provider.
   */
  static Map<Provider, String> requireModels(
      Map<String, String> requestedModels,
      List<Provider> selectedProviders,
      Function<Provider, Set<String>> allowedModels) {
    Map<Provider, String> chosen = new EnumMap<>(Provider.class);
    if (requestedModels != null) {
      for (Map.Entry<String, String> choice : requestedModels.entrySet()) {
        Provider provider = parseSelected(choice.getKey(), selectedProviders);
        String model = choice.getValue();
        if (model == null || model.isBlank()) {
          throw new ValidationException("missing_model");
        }
        if (!allowedModels.apply(provider).contains(model)) {
          throw new ValidationException("unknown_model");
        }
        chosen.put(provider, model);
      }
    }
    for (Provider provider : selectedProviders) {
      if (!chosen.containsKey(provider)) {
        throw new ValidationException("missing_model");
      }
    }
    return chosen;
  }

  private static Provider parseSelected(String name, List<Provider> selectedProviders) {
    Provider provider;
    try {
      provider = Provider.valueOf(name);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ValidationException("model_for_unselected_provider");
    }
    if (!selectedProviders.contains(provider)) {
      throw new ValidationException("model_for_unselected_provider");
    }
    return provider;
  }
}
