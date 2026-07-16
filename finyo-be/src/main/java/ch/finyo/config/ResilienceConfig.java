package ch.finyo.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import ch.finyo.marketdata.MarketDataProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.Map;

/**
 * Resilience policies for the external data sources.
 *
 * All of SIX, OpenFIGI and (later) ESTV are unofficial or rate-limited endpoints
 * that can break without notice. Every one of them gets a circuit breaker, a retry
 * and — where the vendor publishes a limit — a rate limiter.
 *
 * Configured programmatically rather than via resilience4j-spring-boot3: that
 * starter brings AOP aspects and auto-configuration whose Boot 4 / Spring 7
 * compatibility is unverified. Decorating the call inside the adapter costs a few
 * lines and removes the question.
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    public static final String SIX = "six";
    public static final String OPENFIGI = "openfigi";

    /**
     * Retrying a 4xx is pointless — the request is wrong, not the moment. Only
     * server-side and transport failures are worth a second attempt.
     */
    private static final RetryConfig NETWORK_RETRY = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(500))
            .ignoreExceptions(HttpClientErrorException.class)
            .build();

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(20)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreakerRegistry.of(Map.of(
                SIX, config,
                OPENFIGI, config));
    }

    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.of(Map.of(
                SIX, NETWORK_RETRY,
                OPENFIGI, RetryConfig.from(NETWORK_RETRY).maxAttempts(1).build()));
    }

    /**
     * OpenFIGI's limit depends on whether a key is present: 25 requests per 6 seconds with
     * one, but only 25 per <em>minute</em> without. The key is optional and unset by
     * default, so hard-coding the 6-second window would let the limiter wave through ten
     * times the real allowance and produce exactly the HTTP 429 — and plausibly the IP
     * block — that it exists to prevent, on a source we are only tolerated on.
     *
     * SIX publishes no limit at all; its cap is courtesy, and it keeps a runaway loop from
     * hammering the endpoint.
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry(MarketDataProperties properties) {
        boolean openFigiHasKey = StringUtils.hasText(properties.openfigi().apiKey());
        Duration openFigiWindow = openFigiHasKey ? Duration.ofSeconds(6) : Duration.ofMinutes(1);
        log.info("OpenFIGI rate limit: 25 requests per {}", openFigiWindow);

        return RateLimiterRegistry.of(Map.of(
                OPENFIGI, RateLimiterConfig.custom()
                        .limitForPeriod(25)
                        .limitRefreshPeriod(openFigiWindow)
                        // Below the HTTP read timeout (5s) so the two waits cannot stack up
                        // into a request the user experiences as a hang.
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build(),
                SIX, RateLimiterConfig.custom()
                        .limitForPeriod(30)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build()));
    }

    /** Circuit state and retry counts land on /actuator/prometheus, which is already scraped. */
    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry,
                                                             MeterRegistry meterRegistry) {
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedRetryMetrics retryMetrics(RetryRegistry registry, MeterRegistry meterRegistry) {
        TaggedRetryMetrics metrics = TaggedRetryMetrics.ofRetryRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedRateLimiterMetrics rateLimiterMetrics(RateLimiterRegistry registry, MeterRegistry meterRegistry) {
        TaggedRateLimiterMetrics metrics = TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }
}
