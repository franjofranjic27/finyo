package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

/**
 * A file as the remote drive sees it.
 *
 * @param itemId      unique within its drive, not globally
 * @param filename    file name including extension
 * @param path        folder path within the drive, used to derive year and type hints
 * @param ctag        changes only when the content changes — the re-processing trigger
 * @param size        bytes, checked before downloading
 * @param downloadUrl short-lived, pre-authenticated URL; must be fetched WITHOUT an
 *                    Authorization header, or the storage backend rejects it
 * @param deleted     the item disappeared from the drive
 */
public record RemoteDocument(
        String itemId,
        String filename,
        String path,
        @Nullable String ctag,
        long size,
        @Nullable String downloadUrl,
        boolean deleted) {
}
