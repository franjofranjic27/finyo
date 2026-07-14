package ch.finyo.taxdocument;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * How a folder path is read as year and type hints, e.g.
 * {@code /Steuern/STE-2025/Lohnausweise/x.pdf}.
 *
 * <p>Configurable rather than hard-coded: the convention is the user's, not ours.
 *
 * @param taxYearPattern regex with one capturing group holding the year
 * @param folderTypes    folder name (case-insensitive) to document type
 */
@ConfigurationProperties(prefix = "finyo.documents")
public record FolderConventionProperties(
        String taxYearPattern,
        Map<String, TaxDocumentType> folderTypes) {
}
