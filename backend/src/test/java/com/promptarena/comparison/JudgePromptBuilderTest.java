package com.promptarena.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pt-BR judge prompt (FR-021): anonymous ordered labels, every answer included
 * verbatim in the given (already shuffled) order, and the bias mitigations — impartial-evaluator
 * framing and the explicit no-winner/no-scores instruction.
 */
class JudgePromptBuilderTest {

  @Test
  void labelsMapIndicesToLetters() {
    assertThat(JudgePromptBuilder.label(0)).isEqualTo("A");
    assertThat(JudgePromptBuilder.label(1)).isEqualTo("B");
    assertThat(JudgePromptBuilder.label(2)).isEqualTo("C");
    assertThat(JudgePromptBuilder.label(3)).isEqualTo("D");
  }

  @Test
  void thereIsNoFifthLabelBecauseAComparisonHasAtMostFourLanes() {
    assertThatThrownBy(() -> JudgePromptBuilder.label(4))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void includesEveryAnswerVerbatimUnderItsLabelInOrder() {
    String prompt =
        JudgePromptBuilder.build(
            "qual a capital do Brasil?", List.of("resposta-um", "resposta-dois", "resposta-tres"));

    assertThat(prompt).contains("qual a capital do Brasil?");
    assertThat(prompt).contains("Modelo A:\n\"\"\"\nresposta-um\n\"\"\"");
    assertThat(prompt).contains("Modelo B:\n\"\"\"\nresposta-dois\n\"\"\"");
    assertThat(prompt).contains("Modelo C:\n\"\"\"\nresposta-tres\n\"\"\"");
    // Label order follows the given (already shuffled) answer order: A before B before C.
    assertThat(prompt.indexOf("Modelo A:"))
        .isLessThan(prompt.indexOf("Modelo B:"))
        .isLessThan(prompt.indexOf("Modelo C:"));
  }

  @Test
  void announcesTheAnswersAsAnonymousAndRandomlyOrdered() {
    String prompt = JudgePromptBuilder.build("p", List.of("a1", "a2"));

    assertThat(prompt).contains("avaliador imparcial");
    assertThat(prompt).contains("2 respostas");
    assertThat(prompt).contains("modelos anônimos");
    assertThat(prompt).contains("ordem aleatória");
  }

  @Test
  void asksForTheKeyDifferenceCriteriaInShortMarkdownLists() {
    String prompt = JudgePromptBuilder.build("p", List.of("a1", "a2"));

    assertThat(prompt)
        .contains("cobertura")
        .contains("precisão factual")
        .contains("estrutura")
        .contains("estilo")
        .contains("concisão");
    assertThat(prompt).contains("listas curtas em markdown");
    assertThat(prompt).contains("português do Brasil");
    assertThat(prompt).contains("200 palavras");
  }

  @Test
  void forbidsDeclaringAWinnerOrAssigningScores() {
    String prompt = JudgePromptBuilder.build("p", List.of("a1", "a2"));

    assertThat(prompt).contains("NÃO declare um vencedor nem atribua notas");
  }
}
