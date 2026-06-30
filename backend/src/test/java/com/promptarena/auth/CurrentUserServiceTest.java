package com.promptarena.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.promptarena.model.User;
import com.promptarena.repository.UserRepository;
import com.promptarena.web.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

  @Mock private UserRepository users;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAs(String username) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(username, null));
  }

  @Test
  void resolvesAuthenticatedUser() {
    User alice = new User("alice", "hash");
    when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
    authenticateAs("alice");

    assertThat(new CurrentUserService(users).require()).isSameAs(alice);
  }

  @Test
  void throwsWhenPrincipalHasNoRecord() {
    when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
    authenticateAs("ghost");

    assertThatThrownBy(() -> new CurrentUserService(users).require())
        .isInstanceOf(NotFoundException.class);
  }
}
