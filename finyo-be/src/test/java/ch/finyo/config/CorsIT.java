package ch.finyo.config;

import ch.finyo.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Browsers attach an Origin header to every POST — even same-origin ones —
 * so a deployment's public origin must be accepted by the CORS filter or
 * every mutation fails with 403 "Invalid CORS request" (the prod bug this
 * pins down). The allowlist is configurable via finyo.cors.allowed-origins.
 */
class CorsIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST with an allowed origin passes the CORS filter")
    void postWithAllowedOriginIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/positions/bulk")
                        .with(asUser())
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positions\":[{\"name\":\"CORS Probe\",\"quantity\":1,\"purchasePrice\":1}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST with an unknown origin is rejected by the CORS filter")
    void postWithUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/positions/bulk")
                        .with(asUser())
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positions\":[{\"name\":\"CORS Probe\",\"quantity\":1,\"purchasePrice\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET without an Origin header is unaffected by CORS")
    void getWithoutOriginIsUnaffected() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio").with(asUser()))
                .andExpect(status().isOk());
    }
}
