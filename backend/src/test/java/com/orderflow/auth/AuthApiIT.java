package com.orderflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.support.IntegrationTestBase;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class AuthApiIT extends IntegrationTestBase {

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void registerReturnsTokenAndStoresBcryptHash() throws Exception {
        postAs(null, "/api/auth/register", Map.of("name", "Asha", "email", "Asha@Example.com", "password", "password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("asha@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'asha@example.com'", String.class);
        assertThat(hash).startsWith("$2a$").doesNotContain("password123");
    }

    @Test
    void duplicateEmailIsConflictRegardlessOfCase() throws Exception {
        Map<String, String> body = Map.of("name", "Asha", "email", "asha@example.com", "password", "password123");
        postAs(null, "/api/auth/register", body).andExpect(status().isCreated());
        postAs(null, "/api/auth/register", Map.of("name", "A", "email", "ASHA@example.com", "password", "password123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        postAs(null, "/api/auth/register", Map.of("name", "", "email", "nope", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItem("password")))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItem("name")));
    }

    @Test
    void loginAndMe() throws Exception {
        TestUser user = registerCustomer();
        postAs(null, "/api/auth/login", Map.of("email", user.email(), "password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        getAs(user, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.id()));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        TestUser user = registerCustomer();
        postAs(null, "/api/auth/login", Map.of("email", user.email(), "password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        TestUser user = registerCustomer();
        String tampered = user.token().substring(0, user.token().length() - 4) + "AAAA";
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        TestUser user = registerCustomer();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("orderflow").subject(String.valueOf(user.id()))
                .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))
                .claim("role", "CUSTOMER").build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUseAdminEndpoints() throws Exception {
        TestUser customer = registerCustomer();
        postAs(customer, "/api/products", Map.of("name", "X")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        getAs(customer, "/api/admin/orders").andExpect(status().isForbidden());
        getAs(customer, "/api/inventory/1").andExpect(status().isForbidden());
    }

    @Test
    void adminCanUseAdminEndpoints() throws Exception {
        TestUser admin = createAdmin();
        getAs(admin, "/api/admin/orders").andExpect(status().isOk());
    }

    @Test
    void requestIdIsEchoedAndGeneratedWhenMissingOrUnsafe() throws Exception {
        mvc.perform(get("/api/categories").header("X-Request-Id", "client-req-123"))
                .andExpect(header().string("X-Request-Id", "client-req-123"))
                .andExpect(jsonPath("$.requestId").value("client-req-123"));
        String generated = mvc.perform(get("/api/categories").header("X-Request-Id", "bad\nvalue"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(generated).hasSize(36).doesNotContain("bad");
    }
}
