package com.promptarena.dto;

/**
 * Credentials posted to {@code POST /api/auth/login}. Pure data carrier; validation and
 * authentication happen in the security layer (no plaintext password ever persists).
 */
public record LoginRequest(String username, String password) {}
