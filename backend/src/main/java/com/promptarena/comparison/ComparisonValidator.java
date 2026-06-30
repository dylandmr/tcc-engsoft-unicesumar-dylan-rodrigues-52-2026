package com.promptarena.comparison;

import com.promptarena.model.Provider;
import com.promptarena.web.ValidationException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a comparison request and parses provider names, mapping each failure to the
 * machine-readable error code of the REST contract (FR-005, FR-006). Pure logic, no Spring.
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
}
