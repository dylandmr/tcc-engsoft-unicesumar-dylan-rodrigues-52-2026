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
 * Unit tests for {@link ComparisonValidator#requireModels} (FR-020) — the pure model-choice
 * validation logic. There are no defaults: every selected provider MUST carry an explicit,
 * catalog-backed model. Endpoint-level behavior (HTTP codes, catalog wiring) is covered by {@code
 * ComparisonEndpointTest}; prompt/provider validation by its existing endpoint tests.
 */
class ComparisonValidatorTest {

  private static final List<Provider> SELECTED = List.of(Provider.CLAUDE, Provider.GEMINI);
  private static final Function<Provider, Set<String>> ALLOWED =
      provider -> Set.of("allowed-model", "other-allowed-model");

  @Test
  void fullValidMapIsReturnedKeyedByProvider() {
    Map<Provider, String> models =
        ComparisonValidator.requireModels(
            Map.of("CLAUDE", "allowed-model", "GEMINI", "other-allowed-model"), SELECTED, ALLOWED);

    assertThat(models)
        .isEqualTo(
            Map.of(
                Provider.CLAUDE, "allowed-model",
                Provider.GEMINI, "other-allowed-model"));
  }

  @Test
  void absentModelsMapIsRejected() {
    assertThatThrownBy(() -> ComparisonValidator.requireModels(null, SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("missing_model");
  }

  @Test
  void partialModelsMapIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Map.of("CLAUDE", "allowed-model"), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("missing_model");
  }

  @Test
  void nullModelValueIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Collections.singletonMap("CLAUDE", null), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("missing_model");
  }

  @Test
  void blankModelValueIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Map.of("CLAUDE", "   ", "GEMINI", "allowed-model"), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("missing_model");
  }

  @Test
  void unknownProviderNameKeyIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Map.of("FOO", "allowed-model"), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("model_for_unselected_provider");
  }

  @Test
  void nullProviderKeyIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Collections.singletonMap(null, "allowed-model"), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("model_for_unselected_provider");
  }

  @Test
  void keyOutsideTheSelectedProvidersIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Map.of("GROK", "allowed-model"), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("model_for_unselected_provider");
  }

  @Test
  void modelOutsideTheAllowedSetIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Map.of("CLAUDE", "clod-9000", "GEMINI", "allowed-model"), SELECTED, ALLOWED))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("unknown_model");
  }

  /** With a live-only catalog, an unconfigured provider's allowed set is empty — never valid. */
  @Test
  void anyChoiceForAProviderWithAnEmptyCatalogIsRejected() {
    assertThatThrownBy(
            () ->
                ComparisonValidator.requireModels(
                    Map.of("CLAUDE", "allowed-model", "GEMINI", "allowed-model"),
                    SELECTED,
                    provider -> provider == Provider.GEMINI ? Set.of() : Set.of("allowed-model")))
        .isInstanceOf(ValidationException.class)
        .extracting("code")
        .isEqualTo("unknown_model");
  }
}
