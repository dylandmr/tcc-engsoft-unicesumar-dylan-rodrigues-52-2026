package com.promptarena.config;

import com.promptarena.model.User;
import com.promptarena.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo user at startup. Accounts are provisioned out of band for the MVP (spec
 * Assumptions); self-service registration is out of scope. The password is stored only as a BCrypt
 * hash (Constitution IV). Pure startup wiring — excluded from the coverage gate.
 *
 * <p>This exists so US1/US2 are reachable behind an authenticated session before the US3 login
 * journey lands.
 */
@Component
public class DataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final String demoUsername;
  private final String demoPassword;

  public DataSeeder(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      @Value("${prompt-arena.demo.username:demo}") String demoUsername,
      @Value("${prompt-arena.demo.password:demo1234}") String demoPassword) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.demoUsername = demoUsername;
    this.demoPassword = demoPassword;
  }

  @Override
  public void run(String... args) {
    if (users.findByUsernameIgnoreCase(demoUsername).isEmpty()) {
      users.save(new User(demoUsername, passwordEncoder.encode(demoPassword)));
      log.info("Seeded demo user '{}' (password from prompt-arena.demo.password)", demoUsername);
    }
  }
}
