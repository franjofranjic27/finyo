package ch.finyo.transaction;

/**
 * Resource limits shared by every statement-import stage (camt parser, tabular
 * reader, preview and commit). Uploads are untrusted, and the JSON commit body
 * is not covered by the multipart size limit — so the same caps are enforced on
 * both entry paths.
 */
final class ImportLimits {

    /** Hard cap on rows accepted from one statement: camt entries, CSV/Excel rows and commit rows alike. */
    static final int MAX_ROWS = 10_000;

    /** Descriptions are truncated here so a preview row always survives the commit validation. */
    static final int MAX_DESCRIPTION_LENGTH = 500;

    /** Mirrors the {@code transaction.external_ref} column width. */
    static final int MAX_EXTERNAL_REF_LENGTH = 255;

    static final String TOO_MANY_ROWS_MESSAGE =
            "The statement exceeds the maximum of " + MAX_ROWS + " rows";

    private ImportLimits() {
    }
}
