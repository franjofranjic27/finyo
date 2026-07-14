package ch.finyo.taxdocument;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Microsoft Graph implementation of {@link RemoteDrive}.
 *
 * <p>Two Graph specifics shape this class:
 * <ul>
 *   <li>The delta feed cannot be filtered by path — it returns the whole drive,
 *       folders and non-PDFs included. Narrowing to a folder happens client-side.
 *   <li>{@code /items/{id}/content} answers 302 with a pre-authenticated storage
 *       URL. Following that redirect while still sending the bearer token makes
 *       the storage backend reject the request, so the download URL is taken from
 *       the delta payload and fetched with a separate, header-less client.
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "finyo.graph.enabled", havingValue = "true")
public class GraphRemoteDrive implements RemoteDrive {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String DELTA_FIELDS =
            "id,name,size,cTag,file,folder,deleted,parentReference,@microsoft.graph.downloadUrl";

    private final RestClient graphClient;

    /** Deliberately carries no Authorization header — see the class comment. */
    private final RestClient downloadClient;

    private final GraphTokenProvider tokenProvider;
    private final GraphProperties properties;

    public GraphRemoteDrive(GraphTokenProvider tokenProvider, GraphProperties properties) {
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.graphClient = RestClient.builder()
                .baseUrl(properties.graphBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        this.downloadClient = RestClient.builder().build();
    }

    @Override
    public DeltaPage listChanges(String driveId, @Nullable String cursor) {
        String body = cursor == null
                ? getWithToken(uri -> uri
                        .path("/drives/{driveId}/root/delta")
                        .queryParam("$select", DELTA_FIELDS)
                        .build(driveId))
                : getWithToken(cursor);

        JsonNode json = MAPPER.readTree(body == null ? "{}" : body);
        List<RemoteDocument> items = new ArrayList<>();
        for (JsonNode item : json.path("value")) {
            items.add(toRemoteDocument(item));
        }
        String nextLink = text(json, "@odata.nextLink");
        String deltaLink = text(json, "@odata.deltaLink");
        log.debug("Graph delta page: items={} hasNext={}", items.size(), nextLink != null);
        return new DeltaPage(items, nextLink, deltaLink);
    }

    @Override
    public byte[] download(RemoteDocument document) {
        String url = document.downloadUrl();
        if (url == null) {
            throw new IllegalArgumentException("Document has no download URL: " + document.itemId());
        }
        if (document.size() > properties.maxFileSizeBytes()) {
            throw new IllegalArgumentException(
                    "File exceeds the maximum ingestion size: " + document.size() + " bytes");
        }
        byte[] content = downloadClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(byte[].class);
        return content == null ? new byte[0] : content;
    }

    /**
     * Graph hands out a 410 when the stored cursor is too old. That is not an error
     * we can retry — the caller has to enumerate from scratch.
     */
    private String getWithToken(String absoluteUrl) {
        return executeWithRetry(() -> graphClient.get()
                .uri(URI.create(absoluteUrl))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
                .retrieve()
                .body(String.class));
    }

    private String getWithToken(java.util.function.Function<org.springframework.web.util.UriBuilder, URI> uriFn) {
        return executeWithRetry(() -> graphClient.get()
                .uri(uriFn::apply)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
                .retrieve()
                .body(String.class));
    }

    /**
     * A 401 can mean the cached token was revoked rather than that we are
     * unauthorized — drop it and try once more before giving up.
     */
    private String executeWithRetry(java.util.function.Supplier<String> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.GONE) {
                throw new DeltaResyncRequiredException("Delta cursor expired, a full resync is required");
            }
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw e;
            }
            log.info("Graph returned 401, refreshing the access token and retrying once");
            tokenProvider.invalidate();
            return call.get();
        }
    }

    private RemoteDocument toRemoteDocument(JsonNode item) {
        String path = text(item, "parentReference", "path");
        return new RemoteDocument(
                item.path("id").asString(),
                item.path("name").asString(),
                path == null ? "" : decodePath(path),
                text(item, "cTag"),
                item.path("size").asLong(0L),
                text(item, "@microsoft.graph.downloadUrl"),
                item.has("deleted"));
    }

    /**
     * Graph reports paths as {@code /drive/root:/Steuern/STE-2025} and percent-encodes
     * them. Strip the prefix so the stored path is what the user sees in the folder tree.
     */
    private static String decodePath(String rawPath) {
        String decoded = java.net.URLDecoder.decode(rawPath, java.nio.charset.StandardCharsets.UTF_8);
        int rootMarker = decoded.indexOf("root:");
        return rootMarker < 0 ? decoded : decoded.substring(rootMarker + "root:".length());
    }

    private static @Nullable String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            current = current.path(segment);
        }
        return current.isMissingNode() || current.isNull() ? null : current.asString();
    }
}
