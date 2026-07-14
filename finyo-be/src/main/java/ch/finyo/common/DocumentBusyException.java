package ch.finyo.common;

/**
 * Raised when a document cannot be parsed right now because all parse slots are
 * taken. Unlike its supertype this is a <em>transient</em> condition: the very
 * same bytes will succeed on a later attempt. Batch callers must retry instead
 * of marking the document as permanently failed.
 *
 * <p>Still mapped to HTTP 422 by the {@link GlobalExceptionHandler}, which keeps
 * the interactive upload behaviour unchanged.
 */
public class DocumentBusyException extends DocumentProcessingException {

    public DocumentBusyException(String message) {
        super(message);
    }
}
