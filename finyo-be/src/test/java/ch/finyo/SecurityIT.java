package ch.finyo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the security filter chain defined in SecurityConfig.
 *
 * Scope: verify that authentication and authorisation rules are enforced at the
 * HTTP layer for every route category. These tests do NOT depend on any database
 * state — they exercise only the security configuration.
 *
 * Key scenarios:
 *   1. Unauthenticated requests to protected endpoints → 401
 *   2. Public endpoints (swagger, actuator/health) → accessible without a token
 *   3. Admin-only endpoints → 403 for authenticated non-admin users
 *   4. Admin-only endpoints → accessible (not 403) for admin users
 *   5. Token with no realm_access claim → treated as having no roles → 403 for admin endpoints
 */
class SecurityIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // 1. Unauthenticated access to protected endpoints must return 401
    // -------------------------------------------------------------------------

    @Test
    void unauthenticated_request_to_accounts_list_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_request_to_single_account_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_post_to_accounts_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType("application/json")
                .content("{\"name\":\"My Bank\",\"type\":\"CHECKING\",\"currency\":\"CHF\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_request_to_transactions_list_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_post_to_transactions_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_request_to_categories_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_delete_on_account_returns_401() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 2. Public endpoints must remain accessible without any token
    // -------------------------------------------------------------------------

    @Test
    void actuator_health_is_publicly_accessible_without_token() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void swagger_ui_html_is_publicly_accessible_without_token() throws Exception {
        // SpringDoc redirects /swagger-ui.html → /swagger-ui/index.html (302).
        // Either 200 or a redirect (3xx) is acceptable; the critical assertion is
        // NOT 401 and NOT 403, confirming the route is permit-all.
        int status = mockMvc.perform(get("/swagger-ui.html"))
            .andReturn()
            .getResponse()
            .getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
            .as("swagger-ui.html must not require authentication (expected 2xx or 3xx)")
            .isLessThan(400);
    }

    @Test
    void openapi_docs_endpoint_is_publicly_accessible_without_token() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // 3. Admin endpoint — non-admin authenticated user must get 403
    // -------------------------------------------------------------------------

    @Test
    void authenticated_user_without_admin_role_gets_403_on_admin_endpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(asUser()))
            .andExpect(status().isForbidden());
    }

    @Test
    void authenticated_user_with_only_user_role_gets_403_on_admin_endpoint() throws Exception {
        // "user" role only — must not reach admin resources
        mockMvc.perform(get("/api/v1/admin/users").with(asUser()))
            .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 4. Admin endpoint — admin user must NOT get 403
    //    (404 is acceptable because the route may not exist yet; 403 is the bug)
    // -------------------------------------------------------------------------

    @Test
    void admin_user_does_not_get_403_on_admin_endpoint() throws Exception {
        // We only assert that the response is NOT 401 and NOT 403.
        // 404 means the route doesn't exist yet — that's fine for now.
        int status = mockMvc.perform(get("/api/v1/admin/users").with(asAdmin()))
            .andReturn()
            .getResponse()
            .getStatus();

        // Must have passed the authorisation layer
        org.assertj.core.api.Assertions.assertThat(status)
            .as("Admin user must not receive 401 or 403 on the admin endpoint")
            .isNotIn(401, 403);
    }

    // -------------------------------------------------------------------------
    // 5. JWT with no realm_access (no roles) must be forbidden on admin endpoint
    // -------------------------------------------------------------------------

    @Test
    void jwt_with_no_roles_claim_gets_403_on_admin_endpoint() throws Exception {
        // The token is valid (authenticated) but carries no realm_access → no ROLE_admin
        mockMvc.perform(get("/api/v1/admin/users").with(asUserWithNoRoles()))
            .andExpect(status().isForbidden());
    }

    @Test
    void jwt_with_no_roles_claim_gets_403_on_regular_protected_endpoint() throws Exception {
        // /api/** requires the "user" (or "admin") realm role — a valid token
        // without any roles is authenticated but not authorised.
        mockMvc.perform(get("/api/v1/accounts").with(asUserWithNoRoles()))
            .andExpect(status().isForbidden());
    }

    @Test
    void jwt_with_user_role_can_access_regular_protected_endpoint() throws Exception {
        int status = mockMvc.perform(get("/api/v1/accounts").with(asUser()))
            .andReturn()
            .getResponse()
            .getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
            .as("Token with the user role must pass the authorisation layer")
            .isNotIn(401, 403);
    }

    // -------------------------------------------------------------------------
    // 6. Session must be stateless — no JSESSIONID must be set
    // -------------------------------------------------------------------------

    @Test
    void successful_authenticated_request_does_not_create_a_session_cookie() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
            .andExpect(result -> {
                var cookies = result.getResponse().getCookies();
                for (var cookie : cookies) {
                    org.assertj.core.api.Assertions.assertThat(cookie.getName())
                        .as("A stateless API must not issue JSESSIONID cookies")
                        .isNotEqualToIgnoringCase("JSESSIONID");
                }
            });
    }

    // -------------------------------------------------------------------------
    // 7. CORS — preflight for an allowed origin must succeed
    // -------------------------------------------------------------------------

    @Test
    void cors_preflight_from_allowed_origin_returns_200() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .options("/api/v1/accounts")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "GET")
            )
            .andExpect(status().isOk());
    }
}
