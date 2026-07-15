package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the position detail endpoints under
 * /api/v1/positions/{positionId} (detail, instrument patch, factsheet).
 *
 * All positions are created with a name only (no valor/ISIN/ticker) plus a
 * currentPrice override so the SIX client is never invoked (established
 * PortfolioIT pattern — the Caffeine cache would otherwise leak identifiers
 * across tests and users).
 */
class PositionDetailIT extends BaseIntegrationTest {

    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @BeforeEach
    void cleanTables() {
        positionRepository.deleteAll();
        snapshotRepository.deleteAll();
        instrumentRepository.deleteAll();
    }

    private UUID createPosition(String name, String quantity, String purchasePrice, String currentPrice)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("quantity", quantity);
        body.put("purchasePrice", purchasePrice);
        if (currentPrice != null) {
            body.put("currentPrice", currentPrice);
        }
        String created = mockMvc.perform(post("/api/v1/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(created).get("id").asText());
    }

    // =========================================================================
    // GET /api/v1/positions/{positionId}
    // =========================================================================

    @Test
    void detail_returns_the_priced_position_with_its_portfolio_share() throws Exception {
        UUID etfPosition = createPosition("Global ETF", "10", "100", "150"); // value 1500
        createPosition("Nestle Shares", "10", "50", "50");                   // value  500

        mockMvc.perform(get("/api/v1/positions/{id}", etfPosition).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionId", is(etfPosition.toString())))
                .andExpect(jsonPath("$.instrumentId", notNullValue()))
                .andExpect(jsonPath("$.name", is("Global ETF")))
                .andExpect(jsonPath("$.isin", nullValue()))
                .andExpect(jsonPath("$.valor", nullValue()))
                .andExpect(jsonPath("$.assetClass", is("ETF"))) // classified on auto-creation
                .andExpect(jsonPath("$.ter", nullValue()))
                // Name-only position: no ISIN or valor to resolve, so nothing was verified and
                // the currency stays unknown rather than being defaulted to CHF (ADR-007).
                .andExpect(jsonPath("$.currency", nullValue()))
                .andExpect(jsonPath("$.quantity", is(10.0)))
                .andExpect(jsonPath("$.avgPurchasePrice", is(100.0)))
                .andExpect(jsonPath("$.purchaseDate", nullValue())) // POST does not accept one
                .andExpect(jsonPath("$.currentPrice", is(150.0)))
                // The manual price entered on creation — no provider prices a name-only holding.
                .andExpect(jsonPath("$.priceSource", is("MANUAL")))
                .andExpect(jsonPath("$.priceAsOf", notNullValue()))
                .andExpect(jsonPath("$.stale", is(false)))
                .andExpect(jsonPath("$.value", is(1500.0)))
                .andExpect(jsonPath("$.gainLoss", is(500.0)))
                .andExpect(jsonPath("$.returnPercent", is(50.0)))
                .andExpect(jsonPath("$.portfolioShare", is(75.0))) // 1500 of 2000
                .andExpect(jsonPath("$.factsheetUrl", nullValue()))
                .andExpect(jsonPath("$.factsheet", nullValue()));
    }

    @Test
    void detail_returns_404_for_unknown_and_foreign_positions() throws Exception {
        UUID positionId = createPosition("Owner Fund", "1", "10", "10");

        mockMvc.perform(get("/api/v1/positions/{id}", UUID.randomUUID()).with(asUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/positions/{id}", positionId).with(asOtherUser()))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PATCH /api/v1/positions/{positionId} — the holding itself
    // =========================================================================

    @Test
    void patch_position_updates_the_holding_and_the_manual_price() throws Exception {
        UUID positionId = createPosition("Global ETF", "10", "100", "150");

        mockMvc.perform(patch("/api/v1/positions/{id}", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20,\"purchasePrice\":95,"
                                + "\"purchaseDate\":\"2026-03-01\",\"currentPrice\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity", is(20.0)))
                .andExpect(jsonPath("$.avgPurchasePrice", is(95.0)))
                .andExpect(jsonPath("$.purchaseDate", is("2026-03-01")))
                // the explicit edit overwrites the manual price set on creation
                .andExpect(jsonPath("$.currentPrice", is(200.0)))
                .andExpect(jsonPath("$.priceSource", is("MANUAL")))
                .andExpect(jsonPath("$.priceAsOf", notNullValue()))
                .andExpect(jsonPath("$.value", is(4000.0)));

        // purchaseDate is always applied: omitting it while sending another
        // field is an explicit clear (the dialog sends the full field set)
        mockMvc.perform(patch("/api/v1/positions/{id}", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity", is(15.0)))
                .andExpect(jsonPath("$.avgPurchasePrice", is(95.0)))
                .andExpect(jsonPath("$.purchaseDate", nullValue()));
    }

    @Test
    void patch_position_rejects_invalid_values_and_an_empty_body() throws Exception {
        UUID positionId = createPosition("Global ETF", "10", "100", "150");

        // bean validation (@Positive)
        mockMvc.perform(patch("/api/v1/positions/{id}", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));

        // service rule: an all-null patch is an empty body
        mockMvc.perform(patch("/api/v1/positions/{id}", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    void patch_position_returns_404_for_foreign_and_unknown_positions() throws Exception {
        UUID positionId = createPosition("Owner Fund", "1", "10", "10");

        mockMvc.perform(patch("/api/v1/positions/{id}", positionId).with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/positions/{id}", UUID.randomUUID()).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_position_requires_authentication() throws Exception {
        mockMvc.perform(patch("/api/v1/positions/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // PATCH /api/v1/positions/{positionId}/instrument
    // =========================================================================

    @Test
    void patch_keeps_identity_fields_and_clears_omitted_optional_fields() throws Exception {
        UUID positionId = createPosition("My Holding", "1", "10", "10");

        mockMvc.perform(patch("/api/v1/positions/{id}/instrument", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetClass\":\"BOND\",\"ter\":0.20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetClass", is("BOND")))
                .andExpect(jsonPath("$.ter", is(0.2)))
                .andExpect(jsonPath("$.name", is("My Holding")));

        // the edit dialog sends the full field set: omitting ter clears it,
        // while name/assetClass are only overwritten when present
        mockMvc.perform(patch("/api/v1/positions/{id}/instrument", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Holding\",\"factsheetUrl\":\"https://example.com/f.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed Holding")))
                .andExpect(jsonPath("$.factsheetUrl", is("https://example.com/f.pdf")))
                .andExpect(jsonPath("$.assetClass", is("BOND")))
                .andExpect(jsonPath("$.ter", nullValue()));
    }

    @Test
    void patch_rejects_invalid_values() throws Exception {
        UUID positionId = createPosition("My Holding", "1", "10", "10");

        mockMvc.perform(patch("/api/v1/positions/{id}/instrument", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ter\":10.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));

        mockMvc.perform(patch("/api/v1/positions/{id}/instrument", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factsheetUrl\":\"ftp://example.com/f.pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));

        mockMvc.perform(patch("/api/v1/positions/{id}/instrument", positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns_404_for_a_foreign_position() throws Exception {
        UUID positionId = createPosition("Owner Fund", "1", "10", "10");

        mockMvc.perform(patch("/api/v1/positions/{id}/instrument", positionId).with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetClass\":\"BOND\"}"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Factsheet lifecycle
    // =========================================================================

    @Test
    void factsheet_upload_download_and_delete_lifecycle() throws Exception {
        UUID positionId = createPosition("My Holding", "1", "10", "10");
        MockMultipartFile file = new MockMultipartFile(
                "file", "../uploads/Fact Sheet 2025.pdf", "application/pdf", PDF_BYTES);

        // upload stores the PDF and returns the fresh detail with metadata
        mockMvc.perform(multipart("/api/v1/positions/{id}/factsheet", positionId)
                        .file(file).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factsheet.filename", is("Fact Sheet 2025.pdf")))
                .andExpect(jsonPath("$.factsheet.sizeBytes", is(PDF_BYTES.length)))
                .andExpect(jsonPath("$.factsheet.uploadedAt", notNullValue()));

        // download returns the identical bytes as inline PDF, hardened against
        // MIME sniffing and script execution in the PDF viewer context
        MvcResult download = mockMvc.perform(get("/api/v1/positions/{id}/factsheet", positionId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", is(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("filename=\"Fact Sheet 2025.pdf\"")))
                .andExpect(header().string("X-Content-Type-Options", is("nosniff")))
                .andExpect(header().string("Content-Security-Policy", is("sandbox")))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PDF_BYTES);

        // another user can neither download nor delete it
        mockMvc.perform(get("/api/v1/positions/{id}/factsheet", positionId).with(asOtherUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/positions/{id}/factsheet", positionId).with(asOtherUser()))
                .andExpect(status().isNotFound());

        // delete clears the factsheet; further reads and deletes are 404
        mockMvc.perform(delete("/api/v1/positions/{id}/factsheet", positionId).with(asUser()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/positions/{id}/factsheet", positionId).with(asUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/positions/{id}/factsheet", positionId).with(asUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/positions/{id}", positionId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factsheet", nullValue()));
    }

    @Test
    void factsheet_upload_rejects_non_pdf_and_empty_files() throws Exception {
        UUID positionId = createPosition("My Holding", "1", "10", "10");

        mockMvc.perform(multipart("/api/v1/positions/{id}/factsheet", positionId)
                        .file(new MockMultipartFile("file", "evil.pdf", "application/pdf",
                                "not a pdf".getBytes(StandardCharsets.US_ASCII)))
                        .with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));

        mockMvc.perform(multipart("/api/v1/positions/{id}/factsheet", positionId)
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                        .with(asUser()))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/portfolio — extended position items
    // =========================================================================

    @Test
    void portfolio_positions_carry_positionId_instrumentId_and_assetClass() throws Exception {
        UUID positionId = createPosition("Global ETF", "10", "100", "150");

        mockMvc.perform(get("/api/v1/portfolio").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].positionId", is(positionId.toString())))
                .andExpect(jsonPath("$.positions[0].id", is(positionId.toString())))
                .andExpect(jsonPath("$.positions[0].instrumentId", notNullValue()))
                .andExpect(jsonPath("$.positions[0].assetClass", is("ETF")));
    }
}
