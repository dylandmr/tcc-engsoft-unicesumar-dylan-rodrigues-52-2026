package com.promptarena.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Security wiring for US3 — session-cookie authentication with CSRF protection (replacing the
 * US1/US2 HTTP-Basic stopgap). Health and the bundled SPA's static assets are public, and {@code
 * POST /api/auth/login} is reachable so a signed-out user can authenticate; every other route
 * requires a session and is scoped to the current user (FR-001, FR-016).
 *
 * <p><strong>CSRF:</strong> tokens are stored in a readable {@code XSRF-TOKEN} cookie via {@link
 * CookieCsrfTokenRepository}; the SPA echoes the value in the {@code X-XSRF-TOKEN} header on
 * state-changing requests. {@link CsrfCookieFilter} forces the (otherwise deferred) token to be
 * written on every response. The SSE {@code GET} stream is naturally exempt because CSRF only
 * guards unsafe methods and {@code EventSource} cannot set headers. Unauthenticated API calls get a
 * JSON {@code 401} from {@link RestAuthenticationEntryPoint} rather than a login redirect.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    // SPA cookie pattern: render with XOR/BREACH protection, but resolve the raw token the SPA
    // echoes back in the X-XSRF-TOKEN header. A pure XOR handler would reject that header.
    SpaCsrfTokenRequestHandler csrfRequestHandler = new SpaCsrfTokenRequestHandler();

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health", "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    // Serve the bundled SPA (static assets) same-origin, plus the SPA's
                    // client-side routes (forwarded to index.html by SpaForwardingController so
                    // deep links / hard refreshes work; data stays behind the /api routes).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/assets/**",
                        "/*.svg",
                        "/*.png",
                        "/icons.svg",
                        "/login",
                        "/history",
                        "/results/*")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .exceptionHandling(ex -> ex.authenticationEntryPoint(new RestAuthenticationEntryPoint()));
    return http.build();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
