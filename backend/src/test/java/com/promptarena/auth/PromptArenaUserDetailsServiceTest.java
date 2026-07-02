package com.promptarena.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.promptarena.model.User;
import com.promptarena.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class PromptArenaUserDetailsServiceTest {

  @Mock private UserRepository users;

  @Test
  void loadsKnownUser() {
    when(users.findByUsernameIgnoreCase("demo"))
        .thenReturn(Optional.of(new User("demo", "bcrypt-hash")));

    UserDetails details = new PromptArenaUserDetailsService(users).loadUserByUsername("demo");

    assertThat(details.getUsername()).isEqualTo("demo");
    assertThat(details.getPassword()).isEqualTo("bcrypt-hash");
    assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("USER");
  }

  @Test
  void throwsForUnknownUser() {
    when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new PromptArenaUserDetailsService(users).loadUserByUsername("ghost"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
