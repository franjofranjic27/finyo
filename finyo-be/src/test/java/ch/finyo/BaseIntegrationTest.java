package ch.finyo;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ch.finyo.config.TestSecurityConfig;

/**
 * Base class for integration tests.
 *
 * Provides helpers for authenticated MockMvc requests using jwt() post-processors
 * so that each test can control exactly which user identity is used. This avoids
 * relying on the mock JwtDecoder in TestSecurityConfig for the caller identity.
 *
 * Convention:
 *   - TEST_USER_ID  : the "primary" user who owns resources created in a test
 *   - OTHER_USER_ID : a second, distinct user used to probe cross-tenant isolation
 *   - ADMIN_USER_ID : a user with both "user" and "admin" realm roles
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public abstract class BaseIntegrationTest {

    protected static final String TEST_USER_ID  = "user-abc-123";
    protected static final String OTHER_USER_ID = "user-xyz-789";
    protected static final String ADMIN_USER_ID = "admin-abc-123";

    /**
     * Returns a JWT post-processor that authenticates as the primary test user.
     * The JWT carries a "user" realm role and a preferred_username claim.
     */
    protected org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor asUser() {
        // authorities must be set explicitly: the jwt() post-processor bypasses
        // the application's realm_access converter and would otherwise only
        // grant SCOPE_* authorities derived from the scope claim.
        return jwt()
            .jwt(builder -> builder
                .subject(TEST_USER_ID)
                .claim("preferred_username", "testuser")
                .claim("realm_access", Map.of("roles", List.of("user")))
            )
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"));
    }

    /**
     * Returns a JWT post-processor that authenticates as a second, unrelated user.
     * Used to verify that user A cannot access user B's resources.
     */
    protected org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor asOtherUser() {
        return jwt()
            .jwt(builder -> builder
                .subject(OTHER_USER_ID)
                .claim("preferred_username", "otheruser")
                .claim("realm_access", Map.of("roles", List.of("user")))
            )
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"));
    }

    /**
     * Returns a JWT post-processor for a user with both "user" and "admin" realm roles.
     */
    protected org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor asAdmin() {
        return jwt()
            .jwt(builder -> builder
                .subject(ADMIN_USER_ID)
                .claim("preferred_username", "adminuser")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
            )
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_user"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"));
    }

    /**
     * Returns a JWT post-processor for a token that has no realm_access claim at all.
     * This simulates a token issued by an IdP that does not include Keycloak role claims.
     */
    protected org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor asUserWithNoRoles() {
        return jwt()
            .jwt(builder -> builder
                .subject("no-roles-user-id")
                .claim("preferred_username", "noroles")
                // Intentionally omit realm_access claim
            );
    }
}
