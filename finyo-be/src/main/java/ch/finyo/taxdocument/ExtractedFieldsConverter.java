package ch.finyo.taxdocument;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Stores the extracted field list as JSON in a TEXT column. Nothing queries by
 * field name, so a child table would only add a join and a repository.
 */
@Slf4j
@Converter
public class ExtractedFieldsConverter implements AttributeConverter<List<ExtractedField>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<ExtractedField>> TYPE = new TypeReference<>() {
    };

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable List<ExtractedField> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(fields);
    }

    @Override
    public @Nullable List<ExtractedField> convertToEntityAttribute(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (RuntimeException e) {
            // A single unreadable row must not break the whole inbox listing —
            // the document just shows up without its fields.
            log.warn("Failed to deserialize extracted fields, ignoring them: {}", e.getMessage());
            return null;
        }
    }
}
