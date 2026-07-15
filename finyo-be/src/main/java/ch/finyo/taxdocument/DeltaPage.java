package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One page of a delta enumeration.
 *
 * <p>Exactly one of {@code nextLink} and {@code deltaLink} is set: further pages
 * follow, or this was the last one and {@code deltaLink} is the cursor for the
 * next sync. The cursor must only be persisted once the whole run succeeded — a
 * {@code nextLink} is never a valid cursor.
 */
public record DeltaPage(
        List<RemoteDocument> items,
        @Nullable String nextLink,
        @Nullable String deltaLink) {

    public boolean hasMore() {
        return nextLink != null;
    }
}
