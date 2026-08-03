package ch.finyo.common.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link CurrencyCode} as the plain VARCHAR(3) the schema declares (bpchar breaks Hibernate's schema validation, see V34/V37). */
@Converter(autoApply = true)
public class CurrencyCodeConverter implements AttributeConverter<CurrencyCode, String> {

    @Override
    public String convertToDatabaseColumn(CurrencyCode attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CurrencyCode convertToEntityAttribute(String dbData) {
        return CurrencyCode.ofNullable(dbData);
    }
}
