package ch.finyo.category;

import ch.finyo.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

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
 * Integration tests for /api/v1/category-rules: CRUD lifecycle, the
 * case-insensitive per-user keyword uniqueness (409), tenant isolation and
 * the DB-level cascade when the referenced category is deleted.
 */
class CategoryRuleIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRuleRepository categoryRuleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category groceries;

    @BeforeEach
    void seedCategory() {
        categoryRuleRepository.deleteAll();
        categoryRepository.deleteAll();
        groceries = categoryRepository.save(Category.builder()
                .userId(TEST_USER_ID)
                .name("Groceries")
                .color("#8b5cf6")
                .type(CategoryType.EXPENSE)
                .build());
    }

    private String ruleBody(String keyword, UUID categoryId) {
        return objectMapper.writeValueAsString(Map.of("keyword", keyword, "categoryId", categoryId.toString()));
    }

    @Test
    void full_crud_lifecycle() throws Exception {
        String created = mockMvc.perform(post("/api/v1/category-rules").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("migros", groceries.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyword", is("migros")))
                .andExpect(jsonPath("$.categoryName", is("Groceries")))
                .andExpect(jsonPath("$.categoryColor", is("#8b5cf6")))
                .andReturn().getResponse().getContentAsString();
        UUID ruleId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/category-rules").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].keyword", is("migros")));

        mockMvc.perform(put("/api/v1/category-rules/{id}", ruleId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("migros filiale", groceries.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyword", is("migros filiale")));

        mockMvc.perform(delete("/api/v1/category-rules/{id}", ruleId).with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/category-rules").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void creating_a_duplicate_keyword_ignoring_case_returns_409() throws Exception {
        mockMvc.perform(post("/api/v1/category-rules").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("migros", groceries.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/category-rules").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("MIGROS", groceries.getId())))
                .andExpect(status().isConflict());

        assertThat(categoryRuleRepository.count()).isEqualTo(1);
    }

    @Test
    void creating_a_rule_for_a_foreign_category_returns_404() throws Exception {
        Category foreignCategory = categoryRepository.save(Category.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign")
                .type(CategoryType.EXPENSE)
                .build());

        mockMvc.perform(post("/api/v1/category-rules").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("sneaky", foreignCategory.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void rules_are_isolated_between_tenants() throws Exception {
        String created = mockMvc.perform(post("/api/v1/category-rules").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("migros", groceries.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID ruleId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/category-rules").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(delete("/api/v1/category-rules/{id}", ruleId).with(asOtherUser()))
                .andExpect(status().isNotFound());

        assertThat(categoryRuleRepository.count()).isEqualTo(1);
    }

    @Test
    void deleting_the_category_cascades_to_its_rules() throws Exception {
        mockMvc.perform(post("/api/v1/category-rules").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("migros", groceries.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/categories/{id}", groceries.getId()).with(asUser()))
                .andExpect(status().isNoContent());

        assertThat(categoryRuleRepository.count()).isZero();
    }
}
