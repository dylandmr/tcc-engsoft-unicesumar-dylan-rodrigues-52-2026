package com.promptarena.auth;

import com.promptarena.model.User;
import com.promptarena.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges our {@link User} records to Spring Security. The seeded demo user authenticates through
 * this service via HTTP Basic for US1/US2; session login + CSRF replace Basic in US3.
 */
@Service
public class PromptArenaUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public PromptArenaUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    User user =
        users
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("unknown user"));
    return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
        .password(user.getPasswordHash())
        .authorities("USER")
        .build();
  }
}
