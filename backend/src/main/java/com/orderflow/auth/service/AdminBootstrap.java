package com.orderflow.auth.service;

import com.orderflow.auth.domain.Role;
import com.orderflow.auth.domain.User;
import com.orderflow.auth.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Creates the initial administrator from ADMIN_EMAIL / ADMIN_PASSWORD if it does not exist yet. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${orderflow.bootstrap.admin-name}") String name,
                          @Value("${orderflow.bootstrap.admin-email}") String email,
                          @Value("${orderflow.bootstrap.admin-password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        users.save(new User(name, normalized, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Bootstrapped admin account {}", normalized);
    }
}
