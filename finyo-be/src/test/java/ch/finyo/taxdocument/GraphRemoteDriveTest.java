package ch.finyo.taxdocument;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a local HTTP stub, like {@code SixMarketDataClientTest} — the repo
 * has no WireMock. Pins the two Graph behaviours that would otherwise only surface
 * in production: the download must not carry the bearer token, and an expired delta
 * cursor must be reported as "resync required" rather than as a generic failure.
 */
class GraphRemoteDriveTest {

    private HttpServer server;
    private GraphRemoteDrive drive;

    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> downloadAuthHeaders = new ArrayList<>();

    private String deltaResponse = "{}";
    private int deltaStatus = 200;
    private long tokenExpiresIn = 3600;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/tenant-1/oauth2/v2.0/token", exchange -> {
            tokenCalls.incrementAndGet();
            respond(exchange, 200, """
                    {"access_token":"token-%d","expires_in":%d}
                    """.formatted(tokenCalls.get(), tokenExpiresIn));
        });

        server.createContext("/drives/drive-1/root/delta", exchange ->
                respond(exchange, deltaStatus, deltaResponse));

        server.createContext("/content/lohnausweis.pdf", exchange -> {
            downloadAuthHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            respond(exchange, 200, "%PDF-1.4 payload");
        });

        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        GraphProperties properties = new GraphProperties(
                true, "tenant-1", "client-1", "secret-1", baseUrl, baseUrl, 10_485_760L);
        drive = new GraphRemoteDrive(new GraphTokenProvider(properties), properties);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void mapsDeltaItemsIncludingPathAndDownloadUrl() {
        deltaResponse = """
                {
                  "value": [
                    {
                      "id": "item-1",
                      "name": "lohnausweis.pdf",
                      "size": 1234,
                      "cTag": "ctag-1",
                      "file": {"mimeType": "application/pdf"},
                      "parentReference": {"path": "/drive/root:/Steuern/STE-2025/Lohnausweise"},
                      "@microsoft.graph.downloadUrl": "https://storage.example/blob"
                    }
                  ],
                  "@odata.deltaLink": "https://graph.example/delta?token=abc"
                }
                """;

        DeltaPage page = drive.listChanges("drive-1", null);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.itemId()).isEqualTo("item-1");
            assertThat(item.filename()).isEqualTo("lohnausweis.pdf");
            assertThat(item.path()).isEqualTo("/Steuern/STE-2025/Lohnausweise");
            assertThat(item.ctag()).isEqualTo("ctag-1");
            assertThat(item.size()).isEqualTo(1234);
            assertThat(item.downloadUrl()).isEqualTo("https://storage.example/blob");
            assertThat(item.deleted()).isFalse();
        });
        assertThat(page.deltaLink()).isEqualTo("https://graph.example/delta?token=abc");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void reportsDeletedItems() {
        deltaResponse = """
                {"value": [{"id": "item-1", "name": "gone.pdf", "deleted": {"state": "deleted"}}]}
                """;

        DeltaPage page = drive.listChanges("drive-1", null);

        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item.deleted()).isTrue());
    }

    @Test
    void signalsThatPagingContinues() {
        deltaResponse = """
                {"value": [], "@odata.nextLink": "https://graph.example/next?token=xyz"}
                """;

        DeltaPage page = drive.listChanges("drive-1", null);

        assertThat(page.hasMore()).isTrue();
        assertThat(page.deltaLink()).isNull();
    }

    /** An expired cursor is recoverable — but only if it is recognized as such. */
    @Test
    void translatesGoneIntoAResyncRequest() {
        deltaStatus = 410;
        deltaResponse = """
                {"error": {"code": "resyncRequired"}}
                """;

        assertThatThrownBy(() -> drive.listChanges("drive-1", null))
                .isInstanceOf(DeltaResyncRequiredException.class)
                .hasMessageContaining("resync");
    }

    /**
     * The pre-authenticated storage URL rejects requests that still carry the Graph
     * bearer token. This is the failure that only shows up against real Graph.
     */
    @Test
    void downloadsWithoutSendingTheBearerToken() {
        RemoteDocument document = new RemoteDocument(
                "item-1", "lohnausweis.pdf", "/Steuern", "ctag-1", 16,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/content/lohnausweis.pdf", false);

        byte[] content = drive.download(document);

        assertThat(new String(content, StandardCharsets.UTF_8)).startsWith("%PDF-");
        assertThat(downloadAuthHeaders).containsExactly("null");
    }

    @Test
    void refusesToDownloadFilesBeyondTheSizeCap() {
        RemoteDocument huge = new RemoteDocument(
                "item-1", "huge.pdf", "/Steuern", "ctag-1", 20_000_000L, "http://127.0.0.1/blob", false);

        assertThatThrownBy(() -> drive.download(huge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum ingestion size");
    }

    @Test
    void reusesTheCachedTokenAcrossCalls() {
        deltaResponse = """
                {"value": [], "@odata.deltaLink": "https://graph.example/delta"}
                """;

        drive.listChanges("drive-1", null);
        drive.listChanges("drive-1", null);

        assertThat(tokenCalls).hasValue(1);
    }

    @Test
    void fetchesAFreshTokenOnceTheCachedOneExpired() {
        tokenExpiresIn = 30; // below the 60s skew, so it is already considered expired
        deltaResponse = """
                {"value": [], "@odata.deltaLink": "https://graph.example/delta"}
                """;

        drive.listChanges("drive-1", null);
        drive.listChanges("drive-1", null);

        assertThat(tokenCalls).hasValue(2);
    }

    /**
     * A cached token can be revoked while it still looks valid. Graph then answers
     * 401, and the only way out is to drop the token and try again — once.
     */
    @Test
    void refreshesTheTokenAndRetriesOnceAfterA401() {
        AtomicInteger deltaCalls = new AtomicInteger();
        server.removeContext("/drives/drive-1/root/delta");
        server.createContext("/drives/drive-1/root/delta", exchange -> {
            if (deltaCalls.incrementAndGet() == 1) {
                respond(exchange, 401, """
                        {"error": {"code": "InvalidAuthenticationToken"}}
                        """);
            } else {
                respond(exchange, 200, """
                        {"value": [], "@odata.deltaLink": "https://graph.example/delta"}
                        """);
            }
        });

        DeltaPage page = drive.listChanges("drive-1", null);

        assertThat(page.items()).isEmpty();
        assertThat(deltaCalls).hasValue(2);
        assertThat(tokenCalls).as("cached token dropped, a fresh one fetched").hasValue(2);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
