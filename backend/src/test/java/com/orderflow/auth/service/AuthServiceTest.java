package com.orderflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.auth.domain.Role;
import com.orderflow.auth.domain.User;
import com.orderflow.auth.dto.AuthResponse;
import com.orderflow.auth.dto.LoginRequest;
import com.orderflow.auth.dto.RegisterRequest;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.JwtService;
import com.orderflow.common.exception.ApiException;
import com.orderflow.common.exception.ConflictException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository users;
    @Mock
    JwtService jwtService;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, jwtService);
    }

    @Test
    void registerHashesPasswordNormalisesEmailAndAssignsCustomerRole() {
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issue(any())).thenReturn(new JwtService.IssuedToken("token", Instant.now()));

        AuthResponse response = service.register(new RegisterRequest(" Asha ", "  Asha@Example.COM ", "password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Asha");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(encoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Asha", "asha@example.com", "password123")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        User user = new User("Asha", "asha@example.com", encoder.encode("right-password"), Role.CUSTOMER);
        when(users.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "wrong-password")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
        verify(jwtService, never()).issue(any());
    }

    @Test
    void loginWithUnknownEmailGivesSameErrorAsWrongPassword() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User user = new User("Asha", "asha@example.com", encoder.encode("right-password"), Role.CUSTOMER);
        when(users.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new JwtService.IssuedToken("token", Instant.now()));

        assertThat(service.login(new LoginRequest("ASHA@example.com", "right-password")).accessToken()).isEqualTo("token");
    }
}
