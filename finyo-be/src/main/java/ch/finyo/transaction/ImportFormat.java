package ch.finyo.transaction;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Supported statement import file formats. */
public enum ImportFormat {
    CSV,
    EXCEL,
    CAMT053;

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /**
     * Best-effort format detection from the filename extension and the first
     * bytes of the file. Falls back to {@link #CSV} — the legacy default —
     * when nothing else matches.
     */
    public static ImportFormat detect(@Nullable String filename, byte @Nullable [] head) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".xml")) {
            return CAMT053;
        }
        if (lowerName.endsWith(".xlsx")) {
            return EXCEL;
        }
        if (head != null) {
            if (startsWith(head, ZIP_MAGIC)) {
                return EXCEL;
            }
            String prefix = textPrefix(head);
            if (prefix.startsWith("<?xml") || prefix.startsWith("<Document")) {
                return CAMT053;
            }
        }
        return CSV;
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** Decodes the head as UTF-8 and strips BOM and leading whitespace for the XML sniff. */
    private static String textPrefix(byte[] head) {
        String text = new String(head, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.stripLeading();
    }
}
