package ch.finyoapi.common;

import ch.finyoapi.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for GlobalExceptionHandler, exercised via a minimal fake controller.
 *
 * Strategy: @WebMvcTest loads only the web layer. A static inner TestController
 * is included so Spring MVC can invoke it and the exception handler can intercept
 * the thrown exceptions. Each test targets one @ExceptionHandler mapping.
 *
 * All endpoints on the inner TestController are placed under /test/**, which is
 * NOT permit-all in SecurityConfig. Tests that hit these endpoints therefore
 * supply a jwt() post-processor to avoid a 401 masking the real assertion.
 */
@WebMvcTest({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // UserProvisioningFilter is part of the filter chain registered in SecurityConfig.
    @MockitoBean
    private ch.finyoapi.auth.UserProvisioningFilter userProvisioningFilter;

    // -------------------------------------------------------------------------
    // Minimal fake controller — exercises every exception handler branch
    // -------------------------------------------------------------------------

    @RestController
    static class TestController {

        @GetMapping("/test/notfound")
        public void throwNotFound() {
            throw new ResourceNotFoundException("Thing not found");
        }

        @GetMapping("/test/notfound-of-factory")
        public void throwNotFoundViaFactory() {
            throw ResourceNotFoundException.of("Account", "some-uuid");
        }

        @GetMapping("/test/illegal")
        public void throwIllegal() {
            throw new IllegalArgumentException("bad argument provided");
        }

        @GetMapping("/test/unexpected")
        public void throwUnexpected() {
            throw new RuntimeException("something blew up unexpectedly");
        }

        @PostMapping("/test/validation")
        public void validate(@Valid @RequestBody SomeRequest req) {
            // If validation passes the handler is never reached in error tests
        }

        record SomeRequest(@NotBlank String name) {}
    }

    // -------------------------------------------------------------------------
    // ResourceNotFoundException → 404 + RFC 9457 Problem JSON
    // -------------------------------------------------------------------------

    @Test
    void resource_not_found_exception_returns_404() throws Exception {
        mockMvc.perform(get("/test/notfound").with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void resource_not_found_exception_returns_problem_json_content_type() throws Exception {
        mockMvc.perform(get("/test/notfound").with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void resource_not_found_exception_body_contains_correct_title() throws Exception {
        mockMvc.perform(get("/test/notfound").with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void resource_not_found_exception_body_contains_detail_from_exception_message() throws Exception {
        mockMvc.perform(get("/test/notfound").with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Thing not found"));
    }

    @Test
    void resource_not_found_exception_body_has_correct_problem_type_uri() throws Exception {
        mockMvc.perform(get("/test/notfound").with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://finyo.ch/errors/not-found"));
    }

    @Test
    void resource_not_found_via_factory_method_includes_resource_name_and_id_in_detail() throws Exception {
        // ResourceNotFoundException.of("Account", "some-uuid") should produce
        // a detail of "Account not found: some-uuid"
        mockMvc.perform(get("/test/notfound-of-factory").with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail", containsString("Account")))
            .andExpect(jsonPath("$.detail", containsString("some-uuid")));
    }

    // -------------------------------------------------------------------------
    // IllegalArgumentException → 400 Bad Request
    // -------------------------------------------------------------------------

    @Test
    void illegal_argument_exception_returns_400() throws Exception {
        mockMvc.perform(get("/test/illegal").with(jwt()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void illegal_argument_exception_body_contains_correct_title() throws Exception {
        mockMvc.perform(get("/test/illegal").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void illegal_argument_exception_body_contains_exception_message_in_detail() throws Exception {
        mockMvc.perform(get("/test/illegal").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("bad argument provided"));
    }

    @Test
    void illegal_argument_exception_body_has_correct_problem_type_uri() throws Exception {
        mockMvc.perform(get("/test/illegal").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("https://finyo.ch/errors/bad-request"));
    }

    // -------------------------------------------------------------------------
    // MethodArgumentNotValidException → 400 + field name in detail
    // -------------------------------------------------------------------------

    @Test
    void validation_failure_returns_400() throws Exception {
        mockMvc.perform(post("/test/validation").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))   // @NotBlank violated
            .andExpect(status().isBadRequest());
    }

    @Test
    void validation_failure_body_contains_field_name_in_detail() throws Exception {
        mockMvc.perform(post("/test/validation").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("name")));
    }

    @Test
    void validation_failure_body_has_correct_title() throws Exception {
        mockMvc.perform(post("/test/validation").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void validation_failure_body_has_correct_problem_type_uri() throws Exception {
        mockMvc.perform(post("/test/validation").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("https://finyo.ch/errors/validation"));
    }

    @Test
    void validation_failure_when_name_is_whitespace_only_returns_400() throws Exception {
        // @NotBlank rejects blank strings (spaces only), not just empty strings
        mockMvc.perform(post("/test/validation").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("name")));
    }

    @Test
    void validation_failure_when_body_is_missing_required_field_returns_400() throws Exception {
        // Completely omitting the "name" field — deserialized as null → @NotBlank fails
        mockMvc.perform(post("/test/validation").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Unexpected RuntimeException → 500 Internal Server Error
    // -------------------------------------------------------------------------

    @Test
    void unexpected_runtime_exception_returns_500() throws Exception {
        mockMvc.perform(get("/test/unexpected").with(jwt()))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void unexpected_runtime_exception_body_has_generic_detail_not_exception_message() throws Exception {
        // The internal exception message ("something blew up unexpectedly") must NOT
        // be leaked to the caller — only the sanitised generic message is safe to return.
        mockMvc.perform(get("/test/unexpected").with(jwt()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }

    @Test
    void unexpected_runtime_exception_does_not_leak_stack_trace_in_response() throws Exception {
        String responseBody = mockMvc.perform(get("/test/unexpected").with(jwt()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Stack trace lines contain "at " followed by a class name
        org.assertj.core.api.Assertions.assertThat(responseBody)
            .as("Response body must not contain stack trace information")
            .doesNotContain("at ch.")
            .doesNotContain("java.lang.RuntimeException");
    }
}
