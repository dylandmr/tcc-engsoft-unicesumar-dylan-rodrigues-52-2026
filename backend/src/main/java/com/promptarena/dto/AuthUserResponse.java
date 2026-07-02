package com.promptarena.dto;

/**
 * The signed-in user as exposed to the SPA by {@code /api/auth/login} and {@code /api/auth/me}.
 * Deliberately minimal — only the username; no id, hash, or roles leak to the client (FR-018).
 */
public record AuthUserResponse(String username) {}
