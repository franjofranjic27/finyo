package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSourceResponse(
        UUID id,
        String userId,
        String driveId,
        String rootFolderPath,
        boolean enabled,
        boolean initialized,
        @Nullable OffsetDateTime lastSyncAt) {

    public static DocumentSourceResponse from(DocumentSource source) {
        return new DocumentSourceResponse(
                source.getId(),
                source.getUserId(),
                source.getDriveId(),
                source.getRootFolderPath(),
                source.isEnabled(),
                // The delta cursor is never exposed: it is an opaque, credential-bearing URL.
                source.getDeltaLink() != null,
                source.getLastSyncAt());
    }
}
