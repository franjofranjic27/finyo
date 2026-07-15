package ch.finyo.taxdocument;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Microsoft Graph access for document ingestion (app-only, client credentials).
 *
 * @param enabled          master switch; off by default so no test context and no
 *                         default deployment ever talks to Graph
 * @param tenantId         Entra ID tenant
 * @param clientId         registered application
 * @param clientSecret     client secret of that application
 * @param loginBaseUrl     token endpoint host — overridable so tests can point at a local stub
 * @param graphBaseUrl     Graph API host — same reason
 * @param maxFileSizeBytes files larger than this are skipped without downloading; the
 *                         multipart limit does not apply to Graph downloads
 */
@ConfigurationProperties(prefix = "finyo.graph")
public record GraphProperties(
        boolean enabled,
        String tenantId,
        String clientId,
        String clientSecret,
        String loginBaseUrl,
        String graphBaseUrl,
        long maxFileSizeBytes) {

    /** A record's generated toString() would print the client secret. One stray log line is enough. */
    @Override
    public String toString() {
        return "GraphProperties[enabled=%s, tenantId=%s, clientId=%s, clientSecret=***, loginBaseUrl=%s, "
                .formatted(enabled, tenantId, clientId, loginBaseUrl)
                + "graphBaseUrl=%s, maxFileSizeBytes=%s]".formatted(graphBaseUrl, maxFileSizeBytes);
    }
}
