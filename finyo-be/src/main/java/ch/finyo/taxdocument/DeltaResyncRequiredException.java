package ch.finyo.taxdocument;

/**
 * The stored delta cursor is no longer accepted (Graph answers 410 Gone with
 * {@code resyncRequired}). Recovery is to forget the cursor and enumerate the
 * drive from scratch, which is safe because documents are keyed by
 * (source, item id) and re-applying a value is a no-op.
 */
public class DeltaResyncRequiredException extends RuntimeException {

    public DeltaResyncRequiredException(String message) {
        super(message);
    }
}
