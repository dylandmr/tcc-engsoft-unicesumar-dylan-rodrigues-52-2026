package com.promptarena.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into the machine-readable error responses of the REST contract:
 * validation failures to {@code 400 {"error": code}} and missing/unowned resources to {@code 404}.
 * Unauthenticated access is handled upstream by Spring Security (HTTP 401).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", ex.getCode()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
  }
}
