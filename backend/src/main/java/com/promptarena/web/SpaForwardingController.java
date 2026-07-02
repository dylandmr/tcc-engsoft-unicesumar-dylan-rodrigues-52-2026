package com.promptarena.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the SPA's client-side routes to the bundled {@code index.html} so deep links and hard
 * refreshes work instead of falling through to a JSON 401. Serving the shell is public by design:
 * the React router's {@code ProtectedRoute} still redirects signed-out visitors to {@code /login},
 * and every piece of data stays behind the authenticated {@code /api} routes (FR-001, FR-016).
 */
@Controller
public class SpaForwardingController {

  @GetMapping({"/login", "/history", "/results/{id}"})
  public String forwardToSpa() {
    return "forward:/index.html";
  }
}
