package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.promptarena.model.Provider;
import com.promptarena.web.ValidationException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ComparisonValidator#resolveModels} (FR-020) — the pure model-choice
 * validation/resolution logic. Endpoint-level behavior (HTTP codes, catalog wiring) is covered by
 * {@code ComparisonEndpointTest}; prompt/provider validation by its existing endpoint tests.
 */
class ComparisonValidatorTest {

  private static final List<Provider> SELECTED = List.of(Provider.CLAUDE, Provider.GEMINI);
  private static final Function<Provider, Set<String>> ALLOWED =
      provider -> Set.of("allowed-model", "other-allowed-model");
  private static final Function<Provider, String> DEFAULTS =
      provider -> "default-" + provider.name();

  @Test
  void nullModelsMapResolvesEveryProviderToItsDefault() {
    Map<Provider, String> resolved =
        ComparisonValidator.resolveModels(null, SELECTED, ALLOWED, DEFAULTS);

    assertThat(resolved)
        .isEqualTo(
            Map.of(
                Provider.CLAUDE, "default-CLAUDE",
                Provider.GEMINI, "default-GEMINI"));
  }

  @Test
  void explicitChoiceWinsAndDefaultsFillTheRest() {
    Map<Provider, String> resolved =
        ComparisonValidator.resolveModels(
            Map.of("CLAUDE", "allowed-model"), SELECTED, ALLOWED, DEFAULTS);

    assertThat(resolved)
        .isEqualTo(
            Map.of(
                Provider.CLAUDE, "allowed-model",
                Provider.GEMINI, "default-GEMINI"));
  }

  @Test
  void unknownProviderNameKeyIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.resolveModels(
                    Map.of("FOO", "allowed-model"), SELECTED, ALLOWED, DEFAULTS))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("model_for_unselected_provider");
  }

  @Test
  void nullProviderKeyIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.resolveModels(
                    Collections.singletonMap(null, "allowed-model"), SELECTED, ALLOWED, DEFAULTS))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("model_for_unselected_provider");
  }

  @Test
  void keyOutsideTheSelectedProvidersIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.resolveModels(
                    Map.of("GROK", "allowed-model"), SELECTED, ALLOWED, DEFAULTS))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("model_for_unselected_provider");
  }

  @Test
  void nullModelValueIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.resolveModels(
                    Collections.singletonMap("CLAUDE", null), SELECTED, ALLOWED, DEFAULTS))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("unknown_model");
  }

  @Test
  void blankModelValueIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.resolveModels(
                    Map.of("CLAUDE", "   "), SELECTED, ALLOWED, DEFAULTS))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("unknown_model");
  }

  @Test
  void modelOutsideTheAllowedSetIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.resolveModels(
                    Map.of("CLAUDE", "clod-9000"), SELECTED, ALLOWED, DEFAULTS))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("unknown_model");
  }

  @Test
  void nullDefaultLeavesTheProviderUnresolved() {
    Map<Provider, String> resolved =
        ComparisonValidator.resolveModels(null, SELECTED, ALLOWED, provider -> null);

    assertThat(resolved).isEmpty();
  }
}
