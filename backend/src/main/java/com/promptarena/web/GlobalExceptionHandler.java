package com.promptarena.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into the machine-readable error responses of the REST contract:
 * validation failures to {@code 400 {"error": code}}, missing/unowned resources to {@code 404}, and
 * failed credentials at login to a non-revealing {@code 401 invalid_credentials}. Unauthenticated
 * access to an already-protected route is handled upstream by the security entry point (HTTP 401).
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

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, String>> handleAuthentication(AuthenticationException ex) {
    // Same code for bad password and unknown user — never reveal which was wrong (FR-003).
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "invalid_credentials"));
  }
}
