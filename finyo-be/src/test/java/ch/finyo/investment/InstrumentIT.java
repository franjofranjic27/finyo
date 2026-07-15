package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    void a_foreign_currency_and_its_provenance_survive_a_round_trip_through_the_database() {
        // V33 added instrument.currency and instrument.source. Both go through a
        // converter (CurrencyCode) or an enum mapping, and both are what stop a USD
        // position from being summed into the portfolio total as though it were CHF.
        // Only a real Postgres proves the schema and the mapping agree.
        Instrument saved = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("iShares Core MSCI World")
                .isin("IE00B4L5Y983")
                .instrumentType(InstrumentType.ETF)
                .assetClass(AssetClass.ETF)
                .currency(new CurrencyCode("USD"))
                .source(DataSource.OPENFIGI)
                .sortOrder(0)
                .build());

        Instrument read = instrumentRepository.findById(saved.getId()).orElseThrow();

        assertThat(read.getCurrency()).isEqualTo(new CurrencyCode("USD"));
        assertThat(read.getSource()).isEqualTo(DataSource.OPENFIGI);
    }

    @Test
    void an_instrument_created_without_a_currency_keeps_it_unknown_rather_than_defaulting_to_CHF() {
        // V33 makes instrument.currency NULLABLE and gives it no default, and that is the
        // load-bearing decision of the whole migration. NULL means "we do not know" —
        // OpenFIGI publishes no currency at all, so instruments resolved through it
        // genuinely have none. A NOT NULL DEFAULT 'CHF' would make an unknown currency
        // indistinguishable from a verified Swiss one and hand PR 4's FX converter a guess
        // dressed as a fact: a USD ETF summed into the portfolio total as francs, which is
        // the original bug wearing a new hat.
        //
        // (Existing rows were backfilled to CHF, because that is what the code already
        // assumed for them — stating the old assumption, not inventing a new one.)
        Instrument saved = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("Manual Fund")
                .instrumentType(InstrumentType.FUND)
                .sortOrder(0)
                .build());

        Instrument read = instrumentRepository.findById(saved.getId()).orElseThrow();

        assertThat(read.getCurrency()).isNull();
        assertThat(read.getSource()).isEqualTo(DataSource.MANUAL);
    }

    @Test
    void the_two_unverified_provenances_survive_a_round_trip_and_stay_distinct() {
        // UNRESOLVED and HEURISTIC look alike and mean the opposite: HEURISTIC is "we
        // asked and nobody knew" (final — an unlisted 3a fund), UNRESOLVED is "we never
        // got to ask" (a to-do that PositionService retries). If the enum lost one of them
        // in the mapping, a provider outage would silently become a permanent guess.
        Instrument unresolved = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("iShares Core MSCI World")
                .isin("IE00B4L5Y983")
                .instrumentType(InstrumentType.OTHER)
                .assetClass(AssetClass.ETF)
                .source(DataSource.UNRESOLVED)
                .sortOrder(0)
                .build());
        Instrument heuristic = instrumentRepository.save(Instrument.builder()
                .userId(TEST_USER_ID)
                .name("CSIF Switzerland Equity Fund")
                .isin("CH0214967314")
                .instrumentType(InstrumentType.OTHER)
                .assetClass(AssetClass.FUND)
                .source(DataSource.HEURISTIC)
                .sortOrder(0)
                .build());

        assertThat(instrumentRepository.findById(unresolved.getId()).orElseThrow().getSource())
                .isEqualTo(DataSource.UNRESOLVED);
        assertThat(instrumentRepository.findById(heuristic.getId()).orElseThrow().getSource())
                .isEqualTo(DataSource.HEURISTIC);
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
