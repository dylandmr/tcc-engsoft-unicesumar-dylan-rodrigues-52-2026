package com.promptarena.auth;

import com.promptarena.model.User;
import com.promptarena.repository.UserRepository;
import com.promptarena.web.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resolves the {@link User} entity behind the current Spring Security session. Comparison and
 * history data are strictly scoped to this user (FR-016).
 *
 * <p>Authentication itself is enforced by the security filter chain, so this is only reached for an
 * authenticated principal; the lookup still guards against a principal with no backing record.
 */
@Service
public class CurrentUserService {

  private final UserRepository users;

  public CurrentUserService(UserRepository users) {
    this.users = users;
  }

  /** The authenticated user, or a {@link NotFoundException} if no matching record exists. */
  public User require() {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    return users
        .findByUsernameIgnoreCase(username)
        .orElseThrow(() -> new NotFoundException("user_not_found"));
  }
}
