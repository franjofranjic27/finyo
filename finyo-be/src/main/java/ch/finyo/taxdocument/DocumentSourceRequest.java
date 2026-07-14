package ch.finyo.taxdocument;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * @param userId         whose documents these are; defaults to the caller when omitted
 * @param driveId        Graph drive id of the document library
 * @param rootFolderPath folder to ingest from, e.g. {@code /Steuern} — applied as a
 *                       client-side prefix filter, since the delta feed covers the whole drive
 */
public record DocumentSourceRequest(
        @Nullable String userId,
        @NotBlank String driveId,
        @NotBlank String rootFolderPath,
        boolean enabled) {
}
