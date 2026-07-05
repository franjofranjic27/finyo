package ch.finyoapi.helloworld;

import ch.finyoapi.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, slice-level unit tests for HelloWorldController.
 *
 * Uses @WebMvcTest so that only the web layer (filters, DispatcherServlet,
 * controllers) is loaded — no database, no full application context.
 *
 * SecurityConfig is imported explicitly so the real permit-all rule for
 * /hello-world is exercised. A mock JwtDecoder is provided to prevent Spring
 * Boot from trying to contact the Keycloak JWKS endpoint at startup.
 *
 * HelloWorldService is mocked so the test has no database dependency.
 */
@WebMvcTest(HelloWorldController.class)
@Import({SecurityConfig.class, ch.finyoapi.auth.UserProvisioningFilter.class})
class HelloWorldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Prevents Spring Boot oauth2-resource-server auto-config from trying to
    // reach the Keycloak issuer URI while only the web slice is loaded.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // HelloWorldController depends on this service; mock it to avoid wiring
    // EndpointLogRepository and a real DataSource in a web-only slice test.
    @MockitoBean
    private HelloWorldService helloWorldService;

    // The real UserProvisioningFilter runs in the chain (imported above); its
    // service dependency is mocked — a mocked *filter* would never call
    // chain.doFilter and every request would short-circuit to an empty 200.
    @MockitoBean
    private ch.finyoapi.auth.UserProvisioningService userProvisioningService;

    // -------------------------------------------------------------------------
    // Happy-path: verify response body and status
    // -------------------------------------------------------------------------

    @Test
    void get_hello_world_returns_200_with_expected_greeting() throws Exception {
        when(helloWorldService.greet()).thenReturn("Hello, I'm finyo!");

        mockMvc.perform(get("/hello-world"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hello, I'm finyo!"));
    }

    @Test
    void get_hello_world_returns_text_plain_content_type() throws Exception {
        when(helloWorldService.greet()).thenReturn("Hello, I'm finyo!");

        mockMvc.perform(get("/hello-world")
                .accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    // -------------------------------------------------------------------------
    // Security: /hello-world must be accessible WITHOUT any authentication token
    // -------------------------------------------------------------------------

    @Test
    void get_hello_world_is_accessible_without_any_authentication_token() throws Exception {
        // No .with(jwt()) — completely unauthenticated request
        when(helloWorldService.greet()).thenReturn("Hello, I'm finyo!");

        mockMvc.perform(get("/hello-world"))
            .andExpect(status().isOk());
    }

    @Test
    void get_hello_world_is_also_accessible_with_a_valid_jwt() throws Exception {
        // Providing a token must not break access (the endpoint is permit-all, not
        // "deny authenticated users")
        when(helloWorldService.greet()).thenReturn("Hello, I'm finyo!");

        mockMvc.perform(get("/hello-world").with(jwt()))
            .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Delegation: verify the controller calls the service exactly once
    // -------------------------------------------------------------------------

    @Test
    void get_hello_world_delegates_to_service_exactly_once() throws Exception {
        when(helloWorldService.greet()).thenReturn("Hello, I'm finyo!");

        mockMvc.perform(get("/hello-world"))
            .andExpect(status().isOk());

        verify(helloWorldService, times(1)).greet();
    }

    // -------------------------------------------------------------------------
    // Edge case: service returns an empty string — controller must still return 200
    // -------------------------------------------------------------------------

    @Test
    void get_hello_world_propagates_empty_string_returned_by_service() throws Exception {
        // The controller wraps whatever the service returns in ResponseEntity.ok().
        // An empty greeting is odd but should still produce a 200, not a 500.
        when(helloWorldService.greet()).thenReturn("");

        mockMvc.perform(get("/hello-world"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    // -------------------------------------------------------------------------
    // Adversarial: HTTP methods other than GET must not be routed to this handler
    // -------------------------------------------------------------------------

    @Test
    void post_to_hello_world_returns_405_method_not_allowed() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/hello-world")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_to_hello_world_returns_405_method_not_allowed() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/hello-world")
            )
            .andExpect(status().isMethodNotAllowed());
    }
}
