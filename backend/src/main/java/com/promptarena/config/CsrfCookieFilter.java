package com.promptarena.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Materializes the deferred CSRF token on every request so the {@code XSRF-TOKEN} cookie is always
 * written for the SPA to read (Spring Security's documented single-page-application pattern). The
 * SPA echoes that value back in the {@code X-XSRF-TOKEN} header on state-changing requests.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    // Touch the token to render its value into the response cookie.
    csrfToken.getToken();
    filterChain.doFilter(request, response);
  }
}
