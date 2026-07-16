package ch.finyo.taxdocument;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * App-only access token for Microsoft Graph (client credentials).
 *
 * <p>Cached in an {@link AtomicReference} rather than via {@code @Cacheable}: the
 * shared Caffeine cache expires after 15 minutes while a Graph token lives for an
 * hour, so the cache manager would just force needless token calls and couple this
 * to an unrelated config.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "finyo.graph.enabled", havingValue = "true")
public class GraphTokenProvider {

    /** Renew early so a token cannot expire in flight. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final GraphProperties properties;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public GraphTokenProvider(GraphProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.loginBaseUrl())
                .build();
    }

    public String getToken() {
        CachedToken token = cached.get();
        if (token != null && token.isValid()) {
            return token.value();
        }
        synchronized (this) {
            CachedToken current = cached.get();
            if (current != null && current.isValid()) {
                return current.value();
            }
            CachedToken fresh = requestToken();
            cached.set(fresh);
            return fresh.value();
        }
    }

    /**
     * Drops the cached token so the next call fetches a fresh one. Used after a 401:
     * a secret can be rotated or revoked while a cached token still looks valid.
     */
    public void invalidate() {
        cached.set(null);
    }

    private CachedToken requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", "https://graph.microsoft.com/.default");

        String body = restClient.post()
                .uri("/{tenant}/oauth2/v2.0/token", properties.tenantId())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode json = MAPPER.readTree(body == null ? "{}" : body);
        String accessToken = json.path("access_token").asString();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Graph token response contained no access_token");
        }
        long expiresIn = json.path("expires_in").asLong(3600L);
        log.info("Obtained Graph access token, expires in {}s", expiresIn);
        return new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn).minus(EXPIRY_SKEW));
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    /** Visible for tests. */
    @Nullable String peekCachedToken() {
        CachedToken token = cached.get();
        return token == null ? null : token.value();
    }
}
