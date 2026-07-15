package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

/**
 * Read access to a remote drive. Exists so the ingestion pipeline can be
 * integration-tested against a stub instead of Microsoft Graph.
 */
public interface RemoteDrive {

    /**
     * @param cursor the delta link from the previous run, or {@code null} for a full enumeration
     * @throws DeltaResyncRequiredException when the cursor expired (Graph 410) — the
     *                                      caller must drop it and enumerate from scratch
     */
    DeltaPage listChanges(String driveId, @Nullable String cursor);

    /** Fetches the file's content, resolving its download URL if needed. */
    byte[] download(String driveId, RemoteDocument document);
}
