package com.promptarena.comparison;

import java.util.List;

/**
 * Composes the pt-BR judge prompt for the comparative analysis (FR-021). Pure logic, no Spring. To
 * mitigate known LLM-as-judge biases, the answers arrive already shuffled and are presented
 * anonymously as "Modelo A", "Modelo B", … (index 0 = "A"), and the judge is explicitly instructed
 * to declare no winner and assign no scores.
 */
final class JudgePromptBuilder {

  private JudgePromptBuilder() {}

  /** The anonymous labels, one per lane — a comparison targets at most four providers (FR-005). */
  private static final String LABELS = "ABCD";

  /** The label hiding the answer at {@code index} in the shuffled order: 0 → "A", 1 → "B", … */
  static String label(int index) {
    return String.valueOf(LABELS.charAt(index));
  }

  /**
   * Build the judge prompt from the original comparison prompt and its successful answers, given in
   * an already randomized order. The judge is asked for the key differences — cobertura, precisão
   * factual, estrutura, estilo, concisão — in short pt-BR markdown lists, with no winner and no
   * scores.
   */
  static String build(String prompt, List<String> answers) {
    StringBuilder judgePrompt = new StringBuilder();
    judgePrompt
        .append("Você é um avaliador imparcial de respostas de modelos de linguagem. ")
        .append("Abaixo estão um prompt e as ")
        .append(answers.size())
        .append(" respostas que modelos anônimos deram a ele, apresentadas em ordem aleatória ")
        .append("e identificadas apenas por letras.\n\n");
    judgePrompt.append("Prompt:\n\"\"\"\n").append(prompt).append("\n\"\"\"\n\n");
    for (int i = 0; i < answers.size(); i++) {
      judgePrompt
          .append("Modelo ")
          .append(label(i))
          .append(":\n\"\"\"\n")
          .append(answers.get(i))
          .append("\n\"\"\"\n\n");
    }
    judgePrompt
        .append("Tarefa: aponte objetivamente as principais diferenças entre as respostas — ")
        .append("cobertura, precisão factual, estrutura, estilo e concisão. ")
        .append("Use listas curtas em markdown. ")
        .append("NÃO declare um vencedor nem atribua notas. ")
        .append("Responda em português do Brasil, em markdown, ")
        .append("com no máximo cerca de 200 palavras.");
    return judgePrompt.toString();
  }
}
