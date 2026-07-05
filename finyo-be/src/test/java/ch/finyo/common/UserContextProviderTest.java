package ch.finyo.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for UserContextProvider.
 *
 * No Spring context is started — the SecurityContextHolder is manipulated
 * directly and cleaned up after each test via @AfterEach.
 *
 * Each test documents one specific assumption the rest of the codebase makes
 * about how user identity is extracted from the JWT. Getting these wrong would
 * break multi-tenant isolation throughout the application.
 */
class UserContextProviderTest {

    private final UserContextProvider provider = new UserContextProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Helpers to place a Jwt into the SecurityContext
    // -------------------------------------------------------------------------

    private void setJwtInContext(Jwt jwt) {
        // JwtAuthenticationToken would normally be used by the filter chain,
        // but any Authentication whose principal is a Jwt instance is sufficient
        // for UserContextProvider, which pattern-matches on the principal type.
        var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Jwt buildJwt(String subject, String preferredUsername) {
        Jwt.Builder builder = Jwt.withTokenValue("fake-token")
            .header("alg", "none")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));

        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // getUserId() — happy path
    // -------------------------------------------------------------------------

    @Test
    void getUserId_returns_subject_claim_from_jwt() {
        Jwt jwt = buildJwt("user-abc-123", "testuser");
        setJwtInContext(jwt);

        assertThat(provider.getUserId()).isEqualTo("user-abc-123");
    }

    @Test
    void getUserId_returns_subject_not_preferred_username() {
        // These two values differ — getUserId() must return the subject, not the username.
        Jwt jwt = buildJwt("sub-uuid-value", "alice@example.com");
        setJwtInContext(jwt);

        assertThat(provider.getUserId())
            .as("getUserId() must return the JWT subject, never the preferred_username")
            .isEqualTo("sub-uuid-value")
            .isNotEqualTo("alice@example.com");
    }

    // -------------------------------------------------------------------------
    // getUsername() — happy path and fallback
    // -------------------------------------------------------------------------

    @Test
    void getUsername_returns_preferred_username_when_present() {
        Jwt jwt = buildJwt("user-abc-123", "alice");
        setJwtInContext(jwt);

        assertThat(provider.getUsername()).isEqualTo("alice");
    }

    @Test
    void getUsername_falls_back_to_subject_when_preferred_username_is_absent() {
        // Token has no preferred_username claim — subject is the fallback
        Jwt jwt = buildJwt("user-abc-123", null);
        setJwtInContext(jwt);

        assertThat(provider.getUsername())
            .as("getUsername() must fall back to JWT subject when preferred_username is absent")
            .isEqualTo("user-abc-123");
    }

    // -------------------------------------------------------------------------
    // Failure cases: SecurityContext has no authentication
    // -------------------------------------------------------------------------

    @Test
    void getUserId_throws_when_security_context_is_empty() {
        // SecurityContext is cleared in @AfterEach and never populated here
        assertThatThrownBy(() -> provider.getUserId())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated JWT found in security context");
    }

    @Test
    void getUsername_throws_when_security_context_is_empty() {
        assertThatThrownBy(() -> provider.getUsername())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated JWT found in security context");
    }

    @Test
    void getUserId_throws_when_authentication_is_null() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThatThrownBy(() -> provider.getUserId())
            .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // Failure case: principal is NOT a Jwt (e.g. username+password auth)
    // -------------------------------------------------------------------------

    @Test
    void getUserId_throws_when_principal_is_not_a_jwt() {
        // This simulates a scenario where a developer mistakenly configures
        // basic auth alongside the resource server, or a test uses @WithMockUser.
        Authentication nonJwtAuth = new UsernamePasswordAuthenticationToken(
            "someuser", "password", List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(nonJwtAuth);

        assertThatThrownBy(() -> provider.getUserId())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated JWT found in security context");
    }

    @Test
    void getUsername_throws_when_principal_is_not_a_jwt() {
        Authentication nonJwtAuth = new UsernamePasswordAuthenticationToken(
            "someuser", "password", List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(nonJwtAuth);

        assertThatThrownBy(() -> provider.getUsername())
            .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // Subtle edge case: subject is an empty string
    // -------------------------------------------------------------------------

    @Test
    void getUserId_returns_empty_string_if_subject_claim_is_empty() {
        // This should never happen with a properly configured Keycloak, but the
        // code does no additional validation of the subject value. Documenting
        // this behaviour here makes the implicit contract explicit.
        Jwt jwt = buildJwt("", "alice");
        setJwtInContext(jwt);

        // UserContextProvider does NOT throw — it returns whatever the JWT contains.
        // If the platform requires non-empty subjects, validation belongs upstream.
        assertThat(provider.getUserId()).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // Isolation: different threads see different SecurityContexts
    // (using MODE_INHERITABLETHREADLOCAL is intentionally NOT tested here;
    //  the default MODE_THREADLOCAL is the expected production setting)
    // -------------------------------------------------------------------------

    @Test
    void getUserId_is_isolated_to_current_thread() throws InterruptedException {
        // Thread 1 (this thread) sets a user in the security context.
        Jwt jwt = buildJwt("user-thread-1", "user1");
        setJwtInContext(jwt);

        // Thread 2 runs concurrently and must NOT see Thread 1's context.
        var contextLeaked = new boolean[]{false};
        Thread thread2 = new Thread(() -> {
            try {
                provider.getUserId(); // should throw because context is empty for this thread
                contextLeaked[0] = true; // if we reach here, context leaked
            } catch (IllegalStateException expected) {
                // correct: the other thread's context is not visible
            }
        });
        thread2.start();
        thread2.join();

        assertThat(contextLeaked[0])
            .as("SecurityContext must not leak between threads (MODE_THREADLOCAL)")
            .isFalse();
    }
}
