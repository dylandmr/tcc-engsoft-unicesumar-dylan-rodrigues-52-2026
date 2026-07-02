package com.promptarena.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/**
 * Regression guard for the SPA CSRF cookie pattern. {@code CookieCsrfTokenRepository} stores the
 * <em>raw</em> token in the readable {@code XSRF-TOKEN} cookie, and the SPA echoes that exact value
 * back in the {@code X-XSRF-TOKEN} header. The previous configuration used a pure {@code
 * XorCsrfTokenRequestAttributeHandler}, whose {@code resolveCsrfTokenValue} tries to
 * <em>un-mask</em> the header value — turning the raw token into garbage, so CSRF validation failed
 * and the anonymous login call was rejected with a {@code 401} that looked like bad credentials.
 *
 * <p>{@link SpaCsrfTokenRequestHandler} must resolve a header-borne token <em>plainly</em>
 * (returning it verbatim) while still delegating parameter-borne tokens to the XOR handler. These
 * tests pin that contract; they would fail against a pure XOR handler.
 */
class SpaCsrfTokenRequestHandlerTest {

  private static final String HEADER = "X-XSRF-TOKEN";
  private static final String PARAM = "_csrf";

  private final SpaCsrfTokenRequestHandler handler = new SpaCsrfTokenRequestHandler();

  @Test
  void resolvesRawTokenFromHeaderVerbatim() {
    CsrfToken token = new DefaultCsrfToken(HEADER, PARAM, "raw-token-value");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, "raw-token-value");

    // Plain resolution: the raw echoed value is returned as-is (not un-masked), so it matches the
    // raw token the repository holds. A pure XOR handler would attempt to decode it and return
    // null.
    assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("raw-token-value");
  }

  @Test
  void fallsBackToXorForParameterBorneTokens() {
    // Server-rendered form path (no header, token in the _csrf request parameter). The XOR delegate
    // owns this case; a value that isn't a valid XOR-masked token resolves to null rather than
    // throwing, which is the delegate's documented behaviour.
    CsrfToken token = new DefaultCsrfToken(HEADER, PARAM, "raw-token-value");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(PARAM, "not-a-masked-token");

    assertThat(handler.resolveCsrfTokenValue(request, token)).isNull();
  }
}
