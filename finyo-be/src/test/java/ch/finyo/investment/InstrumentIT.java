package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/instruments.
 *
 * The market-data cases only use instruments WITHOUT valor/ISIN/ticker so the
 * SIX client is never invoked (no external HTTP in the IT): the cached-price
 * fallback and the no-data conflict are fully deterministic.
 */
class InstrumentIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @BeforeEach
    void cleanTable() {
        instrumentRepository.deleteAll();
    }

    private String instrumentBody(String name) {
        return objectMapper.writeValueAsString(Map.of(
                "valor", "3886335",
                "isin", "CH0038863350",
                "ticker", "NESN",
                "name", name,
                "instrumentType", "STOCK",
                "sortOrder", 1));
    }

    @Test
    void full_lifecycle_create_list_get_update_delete() throws Exception {
        String created = mockMvc.perform(post("/api/v1/instruments").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instrumentBody("Nestlé")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Nestlé")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/instruments").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        mockMvc.perform(get("/api/v1/instruments/{id}", id).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin", is("CH0038863350")));

        mockMvc.perform(put("/api/v1/instruments/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instrumentBody("Nestlé SA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Nestlé SA")));

        mockMvc.perform(delete("/api/v1/instruments/{id}", id).with(asUser()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/instruments/{id}", id).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void market_data_falls_back_to_the_cached_price_when_no_identifier_exists() throws Exception {
        Instrument cached = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("Manual Fund")
                .instrumentType(InstrumentType.FUND)
                .sortOrder(0)
                .lastPrice(new BigDecimal("123.4500"))
                .lastPriceUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        mockMvc.perform(get("/api/v1/instruments/{id}/market-data", cached.getId()).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache", is(true)))
                .andExpect(jsonPath("$.lastPrice", is(123.45)))
                .andExpect(jsonPath("$.currency", is("CHF")));
    }

    @Test
    void market_data_returns_conflict_when_neither_identifier_nor_cached_price_exists() throws Exception {
        Instrument empty = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("Empty Instrument")
                .instrumentType(InstrumentType.OTHER)
                .sortOrder(0)
                .build());

        mockMvc.perform(get("/api/v1/instruments/{id}/market-data", empty.getId()).with(asUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));
    }

    @Test
    void other_user_cannot_access_a_foreign_instrument() throws Exception {
        Instrument owned = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("Owner Instrument")
                .instrumentType(InstrumentType.ETF)
                .sortOrder(0)
                .build());

        mockMvc.perform(get("/api/v1/instruments/{id}", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/instruments/{id}/market-data", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/instruments/{id}", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());
    }
}
