package com.orderflow.auth.service;

import com.orderflow.auth.domain.Role;
import com.orderflow.auth.domain.User;
import com.orderflow.auth.dto.AuthResponse;
import com.orderflow.auth.dto.LoginRequest;
import com.orderflow.auth.dto.RegisterRequest;
import com.orderflow.auth.dto.UserResponse;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.JwtService;
import com.orderflow.common.exception.ApiException;
import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.NotFoundException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-placeholder");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.CUSTOMER));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmailIgnoreCase(normalize(request.email()));
        boolean matches = passwordEncoder.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        return tokenFor(user.get());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return users.findById(userId).map(UserResponse::from).orElseThrow(() -> new NotFoundException("User", userId));
    }

    private AuthResponse tokenFor(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
