package com.promptarena.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF token handler for the single-page-application cookie pattern (Spring Security's documented
 * SPA recipe). {@link CookieCsrfTokenRepository} writes the <em>raw</em> token into the readable
 * {@code XSRF-TOKEN} cookie, and the SPA echoes that value back in the {@code X-XSRF-TOKEN} header.
 *
 * <p>A pure {@link XorCsrfTokenRequestAttributeHandler} would reject that header because its {@code
 * resolveCsrfTokenValue} tries to un-mask the value — but the cookie/header carry the raw token,
 * not a masked one, so resolution fails and the request is denied (surfacing as {@code 401} for an
 * anonymous caller). This handler keeps XOR/BREACH protection when <em>rendering</em> the token,
 * but resolves header-borne tokens with the plain handler so the SPA echo works. Form/body
 * parameters (server-rendered case) still resolve through XOR.
 */
final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  private final XorCsrfTokenRequestAttributeHandler delegate =
      new XorCsrfTokenRequestAttributeHandler();

  SpaCsrfTokenRequestHandler() {
    // Opt out of deferred token loading so the token is rendered eagerly and CsrfCookieFilter can
    // always write the XSRF-TOKEN cookie, independent of when (or whether) the response body reads
    // the token. Without this the cookie is only written on demand, which is timing-dependent.
    this.delegate.setCsrfRequestAttributeName(null);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    // Render with BREACH protection (XOR-masked when embedded in a response body).
    this.delegate.handle(request, response, csrfToken);
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    // SPA sends the raw cookie value in a header -> resolve plainly. Parameters still use XOR.
    if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
      return super.resolveCsrfTokenValue(request, csrfToken);
    }
    return this.delegate.resolveCsrfTokenValue(request, csrfToken);
  }
}
