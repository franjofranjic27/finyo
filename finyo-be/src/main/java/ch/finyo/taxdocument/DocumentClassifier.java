package ch.finyo.taxdocument;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weighted keyword scoring per document type. The score of each type is
 * normalized against its maximum achievable weight (0..1); below the
 * confidence threshold the document is classified as {@link TaxDocumentType#UNKNOWN}.
 */
@Component
public class DocumentClassifier {

    private static final double CONFIDENCE_THRESHOLD = 0.25;

    private record WeightedKeyword(String keyword, int weight) {}

    private static final Map<TaxDocumentType, List<WeightedKeyword>> KEYWORDS = buildKeywords();

    private static Map<TaxDocumentType, List<WeightedKeyword>> buildKeywords() {
        Map<TaxDocumentType, List<WeightedKeyword>> keywords = new EnumMap<>(TaxDocumentType.class);
        keywords.put(TaxDocumentType.SALARY_CERTIFICATE, List.of(
                new WeightedKeyword("lohnausweis", 3),
                new WeightedKeyword("certificat de salaire", 2),
                new WeightedKeyword("bruttolohn total", 3),
                new WeightedKeyword("nettolohn", 2)));
        keywords.put(TaxDocumentType.HEALTH_INSURANCE, List.of(
                new WeightedKeyword("kvg", 3),
                new WeightedKeyword("vvg", 3),
                new WeightedKeyword("prämien", 2),
                new WeightedKeyword("steuerjahr", 1),
                new WeightedKeyword("krankenversicherung", 2),
                new WeightedKeyword("krankenkasse", 2)));
        keywords.put(TaxDocumentType.SECURITIES_STATEMENT, List.of(
                new WeightedKeyword("steuerverzeichnis", 3),
                new WeightedKeyword("steuerauszug", 3),
                new WeightedKeyword("verrechnungssteuer", 2),
                new WeightedKeyword("steuerwert", 2)));
        keywords.put(TaxDocumentType.PILLAR_3A, List.of(
                new WeightedKeyword("säule 3a", 3),
                new WeightedKeyword("bvv 3", 3),
                new WeightedKeyword("vorsorgebeiträge", 2),
                new WeightedKeyword("art. 81 abs. 3 bvg", 2)));
        keywords.put(TaxDocumentType.ASSESSMENT, List.of(
                new WeightedKeyword("veranlagung", 3),
                new WeightedKeyword("veranlagungsverfügung", 2),
                new WeightedKeyword("steuerrechnung", 3),
                new WeightedKeyword("kantons- und gemeindesteuer", 3),
                new WeightedKeyword("direkte bundessteuer", 3)));
        return keywords;
    }

    public ClassificationResponse classify(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        TaxDocumentType bestType = TaxDocumentType.UNKNOWN;
        double bestScore = 0.0;
        for (Map.Entry<TaxDocumentType, List<WeightedKeyword>> entry : KEYWORDS.entrySet()) {
            double score = normalizedScore(normalized, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }
        if (bestScore < CONFIDENCE_THRESHOLD) {
            return new ClassificationResponse(TaxDocumentType.UNKNOWN, bestScore);
        }
        return new ClassificationResponse(bestType, bestScore);
    }

    private double normalizedScore(String normalizedText, List<WeightedKeyword> keywords) {
        int totalWeight = 0;
        int matchedWeight = 0;
        for (WeightedKeyword keyword : keywords) {
            totalWeight += keyword.weight();
            if (normalizedText.contains(keyword.keyword())) {
                matchedWeight += keyword.weight();
            }
        }
        return totalWeight == 0 ? 0.0 : (double) matchedWeight / totalWeight;
    }
}
