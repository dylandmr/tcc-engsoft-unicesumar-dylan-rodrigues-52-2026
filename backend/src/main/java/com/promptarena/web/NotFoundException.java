package com.promptarena.web;

/**
 * Thrown when a resource does not exist or is not owned by the current user. Mapped to {@code 404}
 * so the existence of another user's data is never revealed (FR-016).
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
