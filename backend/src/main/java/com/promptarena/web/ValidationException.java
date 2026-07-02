package com.promptarena.web;

/**
 * Thrown when a request fails a business validation rule. Carries the machine-readable {@code code}
 * defined by the REST contract (e.g. {@code empty_prompt}, {@code too_many_providers}) which the
 * {@link GlobalExceptionHandler} surfaces as a {@code 400} body {@code {"error": code}}.
 */
public class ValidationException extends RuntimeException {

  private final String code;

  public ValidationException(String code) {
    super(code);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
