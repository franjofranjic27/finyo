package ch.finyo.taxdocument;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads year and type hints out of a folder path.
 *
 * <p>These hints carry more weight than they might seem to: the classifier's
 * confidence is normalized per type against unequal keyword maxima, so it is not
 * comparable across document types and cannot serve as an auto-apply threshold.
 * The folder — which the user maintains deliberately — is the far better signal.
 */
@Slf4j
@Component
public class FolderConventionRules {

    private final Pattern taxYearPattern;
    private final Map<String, TaxDocumentType> folderTypes;

    public FolderConventionRules(FolderConventionProperties properties) {
        this.taxYearPattern = Pattern.compile(properties.taxYearPattern());
        this.folderTypes = properties.folderTypes() == null ? Map.of() : properties.folderTypes().entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> normalize(entry.getKey()), Map.Entry::getValue));
    }

    public FolderHint hintFor(String path) {
        return new FolderHint(taxYearIn(path), typeIn(path));
    }

    private @Nullable Integer taxYearIn(String path) {
        Matcher matcher = taxYearPattern.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            log.warn("Tax year pattern matched but yielded no usable year for path segment");
            return null;
        }
    }

    private @Nullable TaxDocumentType typeIn(String path) {
        for (String segment : path.split("/")) {
            TaxDocumentType type = folderTypes.get(normalize(segment));
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    /** Folder names are user-typed: fold case and umlauts so "Säule3a" and "saeule3a" agree. */
    private static String normalize(String segment) {
        return segment.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    /** What the folder path claims about a document, before anyone looked inside it. */
    public record FolderHint(@Nullable Integer taxYear, @Nullable TaxDocumentType type) {
    }
}
