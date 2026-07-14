package ch.finyo.taxdocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The source always belongs to the admin who registers it. There is deliberately no
 * {@code userId} field: it would let anyone holding the admin role point a folder at
 * another user's tax year, or pull another user's documents into their own inbox —
 * a much lower bar than database access for the same effect.
 *
 * @param driveId        Graph drive id of the document library
 * @param rootFolderPath folder to ingest from, e.g. {@code /Steuern} — matched on
 *                       folder boundaries, since the delta feed covers the whole drive
 */
public record DocumentSourceRequest(
        @NotBlank String driveId,
        @NotBlank @Pattern(regexp = "^/[^\\\\]*$", message = "must be an absolute drive path") String rootFolderPath,
        boolean enabled) {
}
