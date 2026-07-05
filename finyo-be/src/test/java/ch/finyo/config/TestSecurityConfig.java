package ch.finyo.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Provides a mock JwtDecoder for the test profile.
 *
 * Without this, Spring Boot auto-configuration for oauth2-resource-server
 * attempts to contact the issuer-uri JWKS endpoint during context startup,
 * which fails in CI where no Keycloak instance is running.
 *
 * This bean is @Primary so it wins over any auto-configured JwtDecoder.
 * Tests that need a specific principal should use
 * SecurityMockMvcRequestPostProcessors.jwt() or @WithMockUser rather than
 * relying on the token value decoded here.
 */
@TestConfiguration
public class TestSecurityConfig {

    public static final String TEST_USER_ID = "test-user-id-123";
    public static final String TEST_USERNAME = "testuser";

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return token -> Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject(TEST_USER_ID)
            .claim("preferred_username", TEST_USERNAME)
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }
}
