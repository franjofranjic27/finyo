package ch.finyo.investment;

import java.util.Arrays;
import java.util.Objects;

/** A stored factsheet PDF ready for download. */
public record FactsheetDownload(
        String filename,
        byte[] content
) {

    @Override
    public boolean equals(Object o) {
        return o instanceof FactsheetDownload other
                && Objects.equals(filename, other.filename)
                && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(filename) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "FactsheetDownload[filename=%s, content=%s bytes]"
                .formatted(filename, content == null ? 0 : content.length);
    }
}
