package ch.finyo.taxdocument;

/**
 * Lifecycle of a document discovered in a cloud folder.
 *
 * <p>{@link #AUTO_APPLIED}, {@link #APPLIED}, {@link #DISMISSED} and
 * {@link #FAILED} are terminal for the ingestion pipeline: a later sync only
 * refreshes metadata (a moved file updates its path) and never re-applies values.
 * The one exception is a changed cTag, which means the file's content itself was
 * replaced — then the document is processed again from scratch.
 */
public enum DocumentStatus {

    /** Seen in the delta feed, content not fetched yet. */
    DISCOVERED,

    /** Downloaded, parsed and classified; no value has been written anywhere. */
    ANALYZED,

    /** Extracted values were written into the tax year without asking. */
    AUTO_APPLIED,

    /** Waiting for the user: type/year hints disagree, or a field already holds a different value. */
    NEEDS_REVIEW,

    /** The user confirmed the values in the inbox. */
    APPLIED,

    /** The user does not want this document imported. */
    DISMISSED,

    /** Permanently unprocessable (scan without OCR, encrypted, corrupt, too many retries). */
    FAILED
}
