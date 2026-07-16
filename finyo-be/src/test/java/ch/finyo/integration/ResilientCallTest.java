package ch.finyo.integration;

import ch.finyo.config.ResilienceConfig;
import ch.finyo.marketdata.MarketDataProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ResilientCall.
 *
 * Runs against the real Resilience4j registries from ResilienceConfig rather than
 * mocks: the whole value of this class is that rate limiter, circuit breaker and
 * retry are composed in the right order with the right policies. A mocked registry
 * would assert that the code calls the library, not that the library then behaves.
 *
 * Fresh registries per test — Resilience4j registries are stateful (circuit windows,
 * rate-limit buckets), and sharing them would couple test outcomes to test order.
 *
 * Focus areas:
 *   1. The contract: a vendor failure becomes CallOutcome.Unavailable, never an
 *      exception — and never a Success that the caller could mistake for data.
 *   2. Errors are not vendor failures. An OutOfMemoryError must not be laundered into
 *      "SIX is down", because the caller would then write a guess for every instrument
 *      it touches on the way out.
 *   3. Secrets never reach the reason string. EODHD takes its key in the query, and
 *      Spring puts the full URL into the exception message.
 *   4. The retry policy: server-side failures get a second attempt, 4xx do not.
 */
@DisplayName("ResilientCall")
class ResilientCallTest {

    private final ResilienceConfig config = new ResilienceConfig();
    private final CircuitBreakerRegistry circuitBreakers = config.circuitBreakerRegistry();
    private final RateLimiterRegistry rateLimiters = config.rateLimiterRegistry(properties(null));
    private final ResilientCall resilientCall =
            new ResilientCall(circuitBreakers, config.retryRegistry(), rateLimiters);

    /** OpenFIGI's rate limit depends on whether a key is configured, so it is a parameter. */
    private static MarketDataProperties properties(String openFigiApiKey) {
        return new MarketDataProperties(
                List.of("six", "openfigi"),
                List.of("six"),
                List.of("six"),
                new MarketDataProperties.SixProperties(true, "http://six.invalid"),
                new MarketDataProperties.OpenFigiProperties(true, "http://openfigi.invalid", openFigiApiKey),
                new MarketDataProperties.EodhdProperties(false, null, null));
    }

    private static HttpServerErrorException serverError() {
        return HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, null, null);
    }

    private static HttpClientErrorException notFound() {
        return HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);
    }

    private static String reasonOf(CallOutcome<?> outcome) {
        assertThat(outcome).isInstanceOf(CallOutcome.Unavailable.class);
        return ((CallOutcome.Unavailable<?>) outcome).reason();
    }

    // =========================================================================
    // The contract: failures degrade to a typed outcome, they do not propagate
    // =========================================================================

    @Nested
    @DisplayName("a vendor failure becomes Unavailable, not an exception")
    class TheContract {

        @Test
        void returns_the_value_of_a_successful_call() {
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> "NESN");

            assertThat(result).isEqualTo(new CallOutcome.Success<>("NESN"));
        }

        @Test
        void reports_Unavailable_when_the_call_throws_instead_of_propagating_the_exception() {
            // An unreachable vendor is an expected state for finyo, not an exceptional
            // one: the caller has a persistent cache to fall back on. Throwing here would
            // fail the user's request over a third party being down.
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.OPENFIGI, () -> {
                throw serverError();
            });

            assertThat(reasonOf(result)).contains("500");
        }

        @Test
        void reports_Unavailable_on_a_transport_failure() {
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.OPENFIGI, () -> {
                throw new ResourceAccessException("connection refused", new IOException("boom"));
            });

            assertThat(reasonOf(result)).contains("connection refused");
        }

        @Test
        void reports_Unavailable_when_the_circuit_is_open_without_touching_the_call() {
            AtomicInteger attempts = new AtomicInteger();
            // Two-argument lookup, exactly as ResilientCall does it — otherwise this test
            // would create the instance under the registry's default config and the
            // production code would then reuse that one.
            circuitBreakers.circuitBreaker(ResilienceConfig.SIX, ResilienceConfig.SIX)
                    .transitionToOpenState();

            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> {
                attempts.incrementAndGet();
                return "must not be reached";
            });

            assertThat(reasonOf(result)).contains("CallNotPermitted");
            assertThat(attempts).hasValue(0);
        }

        @Test
        void treats_a_null_body_as_a_successful_answer_of_nothing() {
            // RestClient answers a 204 or an empty body with null. That is the vendor
            // answering — "no data" — and not the vendor being unreachable, so it must not
            // become Unavailable and license a retry that would never succeed. The adapters
            // wrap their result in an Optional, so the null never travels further.
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.OPENFIGI, () -> null);

            assertThat(result).isEqualTo(new CallOutcome.Success<String>(null));
        }
    }

    // =========================================================================
    // An Error is not a vendor problem
    // =========================================================================

    @Nested
    @DisplayName("an Error is rethrown, never laundered into 'the vendor is down'")
    class ErrorsArePropagated {

        @Test
        void rethrows_an_OutOfMemoryError_instead_of_reporting_the_vendor_as_unavailable() {
            // Catching Throwable would turn a broken JVM into a lookup miss, and the caller
            // would then happily write name-derived guesses for every instrument in the
            // import — quietly, and with a source column that looks plausible. Let it kill
            // the request instead.
            assertThatThrownBy(() -> resilientCall.execute(ResilienceConfig.OPENFIGI, () -> {
                throw new OutOfMemoryError("simulated heap exhaustion");
            })).isInstanceOf(OutOfMemoryError.class);
        }

        @Test
        void rethrows_a_LinkageError_too() {
            assertThatThrownBy(() -> resilientCall.execute(ResilienceConfig.OPENFIGI, () -> {
                throw new NoClassDefFoundError("tools/jackson/databind/ObjectMapper");
            })).isInstanceOf(NoClassDefFoundError.class);
        }
    }

    // =========================================================================
    // Secrets never reach the reason string
    // =========================================================================

    @Nested
    @DisplayName("an API key in the failing URL is scrubbed out of the reason")
    class SecretScrubbing {

        /**
         * EODHD passes its key in the query string, and Spring puts the whole request URL
         * into the message of a failed call. The reason string is logged and travels into
         * CallOutcome.Unavailable — so without scrubbing, the first timeout writes the key
         * into the log file.
         */
        @Test
        void does_not_leak_an_api_token_from_the_query_string_of_a_failed_request() {
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> {
                throw new ResourceAccessException(
                        "I/O error on GET request for "
                                + "\"https://eodhd.com/api/eod/NESN.SW?api_token=s3cr3t-live-key&fmt=json\": "
                                + "connect timed out");
            });

            String reason = reasonOf(result);
            assertThat(reason).doesNotContain("s3cr3t-live-key");
            assertThat(reason).contains("api_token=***");
            // The rest of the message survives: a scrubbed reason still has to be useful
            // for diagnosing why the source was unreachable.
            assertThat(reason).contains("connect timed out").contains("eodhd.com");
        }

        @Test
        void scrubs_the_other_key_parameter_names_vendors_use() {
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> {
                throw new ResourceAccessException(
                        "GET \"https://x.invalid/q?apikey=AAA&access_token=BBB&token=CCC\": timeout");
            });

            String reason = reasonOf(result);
            assertThat(reason).doesNotContain("AAA").doesNotContain("BBB").doesNotContain("CCC");
        }

        @Test
        void keeps_a_message_that_carries_no_secret_intact() {
            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> {
                throw new ResourceAccessException("connection refused");
            });

            assertThat(reasonOf(result)).contains("connection refused");
        }
    }

    // =========================================================================
    // Retry policy
    // =========================================================================

    @Nested
    @DisplayName("retry policy")
    class Retries {

        @Test
        void retries_a_server_error_and_returns_the_value_of_the_second_attempt() {
            AtomicInteger attempts = new AtomicInteger();

            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw serverError();
                }
                return "NESN";
            });

            assertThat(result).isEqualTo(new CallOutcome.Success<>("NESN"));
            assertThat(attempts).hasValue(2);
        }

        @Test
        void does_not_retry_a_client_error() {
            // A 404 means the security does not exist — the request is wrong, not the
            // moment. Retrying it only doubles the load on an endpoint we are tolerated on.
            AtomicInteger attempts = new AtomicInteger();

            CallOutcome<String> result = resilientCall.execute(ResilienceConfig.SIX, () -> {
                attempts.incrementAndGet();
                throw notFound();
            });

            assertThat(reasonOf(result)).contains("404");
            assertThat(attempts).hasValue(1);
        }
    }

    // =========================================================================
    // The policies are actually bound
    // =========================================================================

    @Nested
    @DisplayName("the configured policies are the ones that run")
    class PoliciesAreBound {

        @Test
        void binds_a_source_to_its_own_rate_limiter_config_rather_than_the_registry_default() {
            // Regression test. The registries are built from a Map of *configurations*, so
            // looking an instance up by name alone hands back one built from the registry's
            // DEFAULT config — 50 permits per 500 ns, i.e. no limit worth the name — and
            // silently drops OpenFIGI's published limit. The symptom only shows up in
            // production, as an HTTP 429 halfway through a bulk import of 30 positions.
            //
            // Asserted on the instance execute() actually used, not on one built here.
            RateLimiterRegistry withKey = config.rateLimiterRegistry(properties("a-key"));
            ResilientCall call = new ResilientCall(
                    config.circuitBreakerRegistry(), config.retryRegistry(), withKey);

            call.execute(ResilienceConfig.OPENFIGI, () -> "ok");

            RateLimiterConfig effective = withKey.find(ResilienceConfig.OPENFIGI)
                    .orElseThrow()
                    .getRateLimiterConfig();

            assertThat(effective.getLimitForPeriod()).isEqualTo(25);
            assertThat(effective.getLimitRefreshPeriod()).isEqualTo(Duration.ofSeconds(6));
        }

        @Test
        void uses_the_slow_openfigi_limit_when_no_api_key_is_configured() {
            // The key is optional and unset by default. Without one OpenFIGI allows 25
            // requests per MINUTE, not per 6 seconds — hard-coding the 6-second window
            // would let the limiter wave through ten times the real allowance and produce
            // exactly the 429 (and plausibly the IP block) it exists to prevent.
            RateLimiterRegistry withoutKey = config.rateLimiterRegistry(properties(null));

            RateLimiterConfig effective = withoutKey.rateLimiter(
                            ResilienceConfig.OPENFIGI, ResilienceConfig.OPENFIGI)
                    .getRateLimiterConfig();

            assertThat(effective.getLimitForPeriod()).isEqualTo(25);
            assertThat(effective.getLimitRefreshPeriod()).isEqualTo(Duration.ofMinutes(1));
        }

        @Test
        void treats_a_blank_api_key_as_no_key_at_all() {
            // The env var is present but empty in every deployment that has not set it —
            // an unset key must not be mistaken for a configured one.
            RateLimiterRegistry blankKey = config.rateLimiterRegistry(properties("   "));

            RateLimiterConfig effective = blankKey.rateLimiter(
                            ResilienceConfig.OPENFIGI, ResilienceConfig.OPENFIGI)
                    .getRateLimiterConfig();

            assertThat(effective.getLimitRefreshPeriod()).isEqualTo(Duration.ofMinutes(1));
        }
    }
}
