package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.CachedSecurityReference;
import ch.finyo.marketdata.SecurityReferenceRepository;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.SecurityType;
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

    @Autowired
    private SecurityReferenceRepository securityReferenceRepository;

    @BeforeEach
    void cleanTable() {
        instrumentRepository.deleteAll();
        securityReferenceRepository.deleteAll();
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
        // V34 added instrument.currency and instrument.source. Both go through a
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
        // V34 makes instrument.currency NULLABLE and gives it no default, and that is the
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

    // =========================================================================
    // GET /api/v1/instruments/lookup — the add-position preview
    // =========================================================================
    //
    // The test profile runs with reference-providers: [] — no adapter bean exists
    // and no HTTP call can happen. So the SecurityLookup answers from the Postgres
    // cache alone: an unknown identifier is a deterministic NOT_FOUND, and a FOUND
    // result requires a security_reference row to be seeded first. That makes every
    // case below reproducible without a network.

    @Test
    void lookup_reports_NOT_FOUND_for_an_identifier_no_provider_and_no_cache_row_knows() throws Exception {
        mockMvc.perform(get("/api/v1/instruments/lookup").param("isin", "CH9999999999").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist())
                .andExpect(jsonPath("$.assetClass").doesNotExist());
    }

    @Test
    void lookup_reports_FOUND_from_the_cache_when_a_reference_row_exists() throws Exception {
        // A cached reference lets the lookup answer FOUND with no provider in the chain.
        // The mapped response must carry the master data and the derived asset class.
        securityReferenceRepository.save(CachedSecurityReference.builder()
                .isin("IE00B4L5Y983")
                .valor("24476758")
                .ticker("SWDA")
                .name("iShares Core MSCI World")
                .type(SecurityType.ETF)
                .currency(new CurrencyCode("USD"))
                .issuer("BlackRock")
                .source(DataSource.OPENFIGI)
                .retrievedAt(OffsetDateTime.of(2026, 7, 14, 10, 30, 0, 0, ZoneOffset.UTC))
                .build());

        mockMvc.perform(get("/api/v1/instruments/lookup").param("isin", "IE00B4L5Y983").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FOUND")))
                .andExpect(jsonPath("$.name", is("iShares Core MSCI World")))
                .andExpect(jsonPath("$.ticker", is("SWDA")))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.assetClass", is("ETF")));
    }

    @Test
    void lookup_reports_NOT_FOUND_when_neither_isin_nor_valor_is_supplied() throws Exception {
        // No identifier at all — nothing to resolve, so the form gets a clean NOT_FOUND
        // rather than an error.
        mockMvc.perform(get("/api/v1/instruments/lookup").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")));
    }

    @Test
    void lookup_reports_NOT_FOUND_for_a_malformed_isin_rather_than_a_client_error() throws Exception {
        // A half-typed ISIN is the normal state of a live-lookup field. InstrumentFactory
        // swallows the malformed identifier and yields NOT_FOUND, so the form is never
        // handed a 400/500 mid-keystroke.
        mockMvc.perform(get("/api/v1/instruments/lookup").param("isin", "abc").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")));
    }

    @Test
    void lookup_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/instruments/lookup").param("isin", "IE00B4L5Y983"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookup_is_not_swallowed_by_the_id_path_variable_mapping() throws Exception {
        // /lookup must be matched by its own handler and not routed to GET /{id} as
        // getById("lookup") — that would try to parse "lookup" as a UUID and 400. A 200
        // with a lookup-shaped body proves the static segment wins over the variable one.
        mockMvc.perform(get("/api/v1/instruments/lookup").param("isin", "CH9999999999").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
