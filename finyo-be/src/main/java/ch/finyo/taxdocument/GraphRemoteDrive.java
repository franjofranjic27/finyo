package ch.finyo.taxdocument;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** Content is served from SharePoint/OneDrive storage hosts, not from the Graph host. */
    private static final List<String> DOWNLOAD_HOST_SUFFIXES =
            List.of(".sharepoint.com", ".sharepointonline.com", ".onedrive.com", ".live.com");

    /** A hanging call must not pin a request thread forever. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** Backstop against a pathological or cyclic delta feed. */
    private static final int MAX_PAGES_PER_RUN = 200;

    private final RestClient graphClient;

    /** Deliberately carries no Authorization header — see the class comment. */
    private final RestClient downloadClient;

    private final GraphTokenProvider tokenProvider;
    private final GraphProperties properties;
    private final String graphHost;

    public GraphRemoteDrive(GraphTokenProvider tokenProvider, GraphProperties properties) {
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.graphHost = URI.create(properties.graphBaseUrl()).getHost();
        this.graphClient = RestClient.builder()
                .requestFactory(timeoutingRequestFactory())
                .baseUrl(properties.graphBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        this.downloadClient = RestClient.builder()
                .requestFactory(timeoutingRequestFactory())
                .build();
    }

    /** A hanging Graph call must not pin a thread indefinitely. */
    private static JdkClientHttpRequestFactory timeoutingRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * Both the delta cursor and the download URL are absolute URLs that we replay
     * from data rather than construct. Pinning the host keeps a tampered cursor
     * from redirecting a bearer token — or an unauthenticated fetch — at an
     * arbitrary target.
     */
    private void requireTrustedHost(String url, List<String> allowedSuffixes) {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        // The configured Graph host is trusted as configured (in production that
        // config is https). Anything else must be https on a known storage host.
        boolean trusted = host.equalsIgnoreCase(graphHost)
                || ("https".equalsIgnoreCase(uri.getScheme())
                    && allowedSuffixes.stream().anyMatch(host::endsWith));
        if (!trusted) {
            throw new RemoteDriveException("The drive returned a URL on an untrusted host", null);
        }
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
            throw new IllegalArgumentException("The drive returned no download URL for this file");
        }
        // A missing size must not slip past the cap as "0 bytes".
        if (document.size() <= 0 || document.size() > properties.maxFileSizeBytes()) {
            throw new IllegalArgumentException(
                    "File exceeds the maximum ingestion size or reports no size at all");
        }
        requireTrustedHost(url, DOWNLOAD_HOST_SUFFIXES);
        try {
            byte[] content = downloadClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(byte[].class);
            return content == null ? new byte[0] : content;
        } catch (RestClientException e) {
            // Never surface the message: it embeds the full pre-authenticated
            // download URL, which grants unauthenticated access to the document
            // for about an hour. It would end up in logs and in failure_reason.
            throw new RemoteDriveException("Downloading the file from the drive failed", e);
        }
    }

    /**
     * Graph hands out a 410 when the stored cursor is too old. That is not an error
     * we can retry — the caller has to enumerate from scratch.
     */
    private String getWithToken(String absoluteUrl) {
        requireTrustedHost(absoluteUrl, List.of());
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
                throw new RemoteDriveException("The drive rejected the request (HTTP %s)"
                        .formatted(e.getStatusCode().value()), e);
            }
            log.info("Graph returned 401, refreshing the access token and retrying once");
            tokenProvider.invalidate();
            try {
                return call.get();
            } catch (RestClientException retryFailure) {
                throw new RemoteDriveException("The drive rejected the request after a token refresh", retryFailure);
            }
        } catch (RestClientException e) {
            // The client's own message embeds the full request URL — for a delta
            // cursor that is a credential-bearing URL. Never let it escape.
            throw new RemoteDriveException("The drive could not be reached", e);
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
