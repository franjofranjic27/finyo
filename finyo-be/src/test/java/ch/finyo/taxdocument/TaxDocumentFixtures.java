package ch.finyo.taxdocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads the anonymized text fixtures from {@code src/test/resources/taxdocument}. */
final class TaxDocumentFixtures {

    private TaxDocumentFixtures() {
    }

    static String load(String name) {
        try (InputStream in = TaxDocumentFixtures.class.getResourceAsStream("/taxdocument/" + name)) {
            return new String(
                    Objects.requireNonNull(in, "Fixture not found: " + name).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
