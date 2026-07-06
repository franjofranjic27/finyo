package ch.finyo.category;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.budget.BudgetRepository;
import ch.finyo.transaction.TransactionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/categories: full lifecycle including the
 * parent/child hierarchy exposed via /top-level, plus tenant isolation.
 */
class CategoryIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private String categoryBody(String name, UUID parentId) {
        if (parentId == null) {
            return objectMapper.writeValueAsString(Map.of("name", name, "type", "EXPENSE"));
        }
        return objectMapper.writeValueAsString(Map.of(
                "name", name, "type", "EXPENSE", "parentId", parentId.toString()));
    }

    @Test
    void full_lifecycle_with_parent_child_hierarchy() throws Exception {
        String parentJson = mockMvc.perform(post("/api/v1/categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Housing", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId", nullValue()))
                .andReturn().getResponse().getContentAsString();
        UUID parentId = UUID.fromString(objectMapper.readTree(parentJson).get("id").asText());

        String childJson = mockMvc.perform(post("/api/v1/categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Rent", parentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId", is(parentId.toString())))
                .andExpect(jsonPath("$.parentName", is("Housing")))
                .andReturn().getResponse().getContentAsString();
        UUID childId = UUID.fromString(objectMapper.readTree(childJson).get("id").asText());

        mockMvc.perform(get("/api/v1/categories").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));

        // Only the parent is top-level
        mockMvc.perform(get("/api/v1/categories/top-level").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Housing")));

        mockMvc.perform(get("/api/v1/categories/{id}", childId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Rent")));

        mockMvc.perform(put("/api/v1/categories/{id}", childId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Rent & Utilities", parentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Rent & Utilities")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        mockMvc.perform(delete("/api/v1/categories/{id}", childId).with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/categories/{id}", childId).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_with_a_foreign_parent_returns_404() throws Exception {
        Category foreignParent = categoryRepository.save(Category.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign Parent")
                .type(CategoryType.EXPENSE)
                .build());

        mockMvc.perform(post("/api/v1/categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Sneaky Child", foreignParent.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_without_type_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "No Type"));

        mockMvc.perform(post("/api/v1/categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
