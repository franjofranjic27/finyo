package ch.finyo.profile;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.SwissTime;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/profile: default view (never 404, never
 * persisted on GET), PUT upsert semantics with derived age and retirement
 * figures, PATCH /preferences partial semantics, validation, tenant isolation
 * and authentication.
 */
class ProfileIT extends BaseIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 4, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @BeforeEach
    void cleanUp() {
        userProfileRepository.deleteAll();
    }

    private String profileBody(String birthDate, String civilStatus, String churchAffiliation,
                               String preferredLanguage, String theme, Boolean onboardingCompleted) {
        Map<String, Object> body = new HashMap<>();
        body.put("birthDate", birthDate);
        body.put("civilStatus", civilStatus);
        body.put("churchAffiliation", churchAffiliation);
        body.put("preferredLanguage", preferredLanguage);
        body.put("theme", theme);
        body.put("onboardingCompleted", onboardingCompleted);
        return objectMapper.writeValueAsString(body);
    }

    private String referenceBody() {
        return profileBody(BIRTH_DATE.toString(), "MARRIED", "NONE", "de", "DARK", true);
    }

    private JsonNode getProfile(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor user) throws Exception {
        String body = mockMvc.perform(get("/api/v1/profile").with(user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void get_returns_the_defaults_without_persisting_when_no_profile_exists() throws Exception {
        JsonNode json = getProfile(asUser());

        assertThat(json.get("birthDate").isNull()).isTrue();
        assertThat(json.get("civilStatus").isNull()).isTrue();
        assertThat(json.get("churchAffiliation").isNull()).isTrue();
        assertThat(json.get("preferredLanguage").isNull()).isTrue();
        assertThat(json.get("theme").asText()).isEqualTo("SYSTEM");
        assertThat(json.get("onboardingCompleted").asBoolean()).isFalse();
        assertThat(json.get("age").isNull()).isTrue();
        assertThat(json.get("yearsToRetirement").isNull()).isTrue();
        assertThat(json.get("retirementYear").isNull()).isTrue();

        assertThat(userProfileRepository.count()).isZero();
    }

    @Test
    void put_upserts_the_profile_and_get_reflects_stored_values_and_derived_figures() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        JsonNode json = getProfile(asUser());
        assertThat(json.get("birthDate").asText()).isEqualTo("1990-04-15");
        assertThat(json.get("civilStatus").asText()).isEqualTo("MARRIED");
        assertThat(json.get("churchAffiliation").asText()).isEqualTo("NONE");
        assertThat(json.get("preferredLanguage").asText()).isEqualTo("de");
        assertThat(json.get("theme").asText()).isEqualTo("DARK");
        assertThat(json.get("onboardingCompleted").asBoolean()).isTrue();

        int expectedAge = Period.between(BIRTH_DATE, LocalDate.now(SwissTime.ZONE)).getYears();
        assertThat(json.get("age").asInt()).isEqualTo(expectedAge);
        assertThat(json.get("yearsToRetirement").asInt()).isEqualTo(65 - expectedAge);
        assertThat(json.get("retirementYear").asInt()).isEqualTo(2055);

        // second PUT updates the same row; null onboardingCompleted preserves the flag
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(BIRTH_DATE.toString(), "SINGLE", "PROTESTANT", "en", "LIGHT", null)))
                .andExpect(status().isOk());
        assertThat(userProfileRepository.count()).isEqualTo(1);

        JsonNode updated = getProfile(asUser());
        assertThat(updated.get("civilStatus").asText()).isEqualTo("SINGLE");
        assertThat(updated.get("preferredLanguage").asText()).isEqualTo("en");
        assertThat(updated.get("theme").asText()).isEqualTo("LIGHT");
        assertThat(updated.get("onboardingCompleted").asBoolean()).isTrue();
    }

    @Test
    void put_with_a_birth_date_before_1900_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("1899-12-31", "MARRIED", "NONE", "de", "DARK", true)))
                .andExpect(status().isBadRequest());

        assertThat(userProfileRepository.count()).isZero();
    }

    @Test
    void put_with_an_unsupported_preferred_language_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(BIRTH_DATE.toString(), "MARRIED", "NONE", "fr", "DARK", true)))
                .andExpect(status().isBadRequest());

        assertThat(userProfileRepository.count()).isZero();
    }

    @Test
    void other_user_still_sees_the_default_view_not_the_owners_profile() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        JsonNode json = getProfile(asOtherUser());
        assertThat(json.get("birthDate").isNull()).isTrue();
        assertThat(json.get("theme").asText()).isEqualTo("SYSTEM");
        assertThat(json.get("onboardingCompleted").asBoolean()).isFalse();
        assertThat(userProfileRepository.count()).isEqualTo(1);
    }

    /**
     * Regression: a preference toggle used to go through the full-replace PUT
     * and wiped every master-data field the caller did not resend.
     */
    @Test
    void patching_the_theme_preserves_the_master_data() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/profile/preferences").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isOk());

        JsonNode json = getProfile(asUser());
        assertThat(json.get("birthDate").asText()).isEqualTo("1990-04-15");
        assertThat(json.get("civilStatus").asText()).isEqualTo("MARRIED");
        assertThat(json.get("churchAffiliation").asText()).isEqualTo("NONE");
        assertThat(json.get("preferredLanguage").asText()).isEqualTo("de");
        assertThat(json.get("theme").asText()).isEqualTo("DARK");
        assertThat(json.get("onboardingCompleted").asBoolean()).isTrue();
        assertThat(userProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void patching_the_language_preserves_the_master_data_and_the_theme() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/profile/preferences").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"en\"}"))
                .andExpect(status().isOk());

        JsonNode json = getProfile(asUser());
        assertThat(json.get("preferredLanguage").asText()).isEqualTo("en");
        assertThat(json.get("theme").asText()).isEqualTo("DARK");
        assertThat(json.get("birthDate").asText()).isEqualTo("1990-04-15");
        assertThat(json.get("civilStatus").asText()).isEqualTo("MARRIED");
    }

    @Test
    void patching_preferences_without_a_stored_profile_creates_one_with_the_defaults() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/preferences").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"LIGHT\"}"))
                .andExpect(status().isOk());

        JsonNode json = getProfile(asUser());
        assertThat(json.get("theme").asText()).isEqualTo("LIGHT");
        assertThat(json.get("birthDate").isNull()).isTrue();
        assertThat(json.get("onboardingCompleted").asBoolean()).isFalse();
        assertThat(userProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void patching_preferences_with_an_empty_patch_returns_400() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/preferences").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(userProfileRepository.count()).isZero();
    }

    @Test
    void patching_preferences_with_an_unsupported_language_returns_400() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/preferences").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"fr\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userProfileRepository.count()).isZero();
    }

    @Test
    void patching_preferences_does_not_touch_another_users_profile() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/profile/preferences").with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"LIGHT\"}"))
                .andExpect(status().isOk());

        JsonNode owner = getProfile(asUser());
        assertThat(owner.get("theme").asText()).isEqualTo("DARK");
        assertThat(owner.get("birthDate").asText()).isEqualTo("1990-04-15");

        JsonNode other = getProfile(asOtherUser());
        assertThat(other.get("theme").asText()).isEqualTo("LIGHT");
        assertThat(other.get("birthDate").isNull()).isTrue();
        assertThat(userProfileRepository.count()).isEqualTo(2);
    }

    @Test
    void unauthenticated_requests_return_401() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/profile/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isUnauthorized());
    }
}
