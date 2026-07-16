package ch.finyo.common.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link CurrencyCode} as the plain CHAR(3) the schema declares. */
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
