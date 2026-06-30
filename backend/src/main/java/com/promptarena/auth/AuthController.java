package com.promptarena.auth;

import com.promptarena.dto.AuthUserResponse;
import com.promptarena.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session login journey for US3 (replaces the US1/US2 HTTP-Basic stopgap). Credentials are checked
 * by the {@link AuthenticationManager}; on success the authentication is persisted into the HTTP
 * session via the {@link SecurityContextRepository} so subsequent requests carry it by cookie. Bad
 * credentials raise an {@code AuthenticationException}, which {@code GlobalExceptionHandler}
 * renders as a non-revealing {@code 401 invalid_credentials}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;

  public AuthController(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  @PostMapping("/login")
  public AuthUserResponse login(
      @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(body.username(), body.password()));

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    return new AuthUserResponse(authentication.getName());
  }

  @GetMapping("/me")
  public AuthUserResponse me(Authentication authentication) {
    return new AuthUserResponse(authentication.getName());
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    request.getSession().invalidate();
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }
}
