package ch.finyo.common;

import org.jspecify.annotations.Nullable;

/**
 * Raised when an uploaded document cannot be processed (scan without text
 * layer, encrypted, corrupt, or classified as a different document type).
 * Mapped to HTTP 422 by the {@link GlobalExceptionHandler}.
 */
public class DocumentProcessingException extends RuntimeException {

    /** Enum name of the detected document type — a String to keep this shared kernel feature-agnostic. */
    private final @Nullable String detectedType;

    public DocumentProcessingException(String message) {
        this(message, null);
    }

    public DocumentProcessingException(String message, @Nullable String detectedType) {
        super(message);
        this.detectedType = detectedType;
    }

    public @Nullable String getDetectedType() {
        return detectedType;
    }
}
