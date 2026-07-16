package ch.finyo.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Runs an external call through rate limiter, circuit breaker and retry.
 *
 * A failure becomes {@link CallOutcome.Unavailable} rather than an exception, because
 * for finyo an unreachable vendor is an <em>expected state</em>: every source is
 * unofficial and will be down at some point. What must never happen is the old
 * behaviour — quietly substituting a plausible number and presenting it as fact. So
 * the failure is explicit, typed, and carried all the way to the caller that has to
 * decide whether anything may be written down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientCall {

    /**
     * Spring puts the full request URL into the message of a failed call, and some
     * vendors take their API key in the query string (EODHD does: {@code ?api_token=…}).
     * Logging the raw message would therefore write the key into the log file the first
     * time that source times out. Scrubbed here, once, rather than remembered later.
     */
    private static final Pattern SECRET_IN_URL = Pattern.compile(
            "(?i)([?&](?:api_?_?token|api_?key|access_?token|token|key)=)[^&\\s\"']*");

    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final RateLimiterRegistry rateLimiters;

    /**
     * @param name registry key of the source, e.g. {@code ResilienceConfig.SIX}
     * @param call the raw HTTP call including parsing — a payload we can no longer
     *             parse means the vendor changed, which is a vendor failure and should
     *             count towards the circuit breaker like any other
     */
    public <T> CallOutcome<T> execute(String name, CheckedSupplier<T> call) {
        // Composed inside-out, and the order is the whole point:
        //   retry( circuitBreaker( rateLimiter( call ) ) )
        // The rate limiter sits innermost so every physical attempt — including a
        // retried one — is counted against the vendor's quota. The circuit breaker
        // wraps it so a dead backend stops being called at all. Retry is outermost,
        // so it retries the guarded call rather than bypassing the guard.
        //
        // Both arguments are the source name, and that is not a typo: the registries
        // in ResilienceConfig are built from a Map of *configurations*, so the
        // one-argument lookup (`retry(name)`) would hand back an instance built from
        // the registry's DEFAULT config and silently discard every policy declared
        // there — a 404 retried three times, and OpenFIGI's rate limit not applied at
        // all. The second argument names the configuration to bind to.
        CheckedSupplier<T> limited = RateLimiter.decorateCheckedSupplier(
                rateLimiters.rateLimiter(name, name), call);
        CheckedSupplier<T> guarded = CircuitBreaker.decorateCheckedSupplier(
                circuitBreakers.circuitBreaker(name, name), limited);
        CheckedSupplier<T> decorated = Retry.decorateCheckedSupplier(
                retries.retry(name, name), guarded);

        try {
            return new CallOutcome.Success<>(decorated.get());
        } catch (InterruptedException e) {
            // resilience4j clears the flag on its way out; restore it or the shutdown
            // signal is lost and the thread keeps working through a terminating JVM.
            Thread.currentThread().interrupt();
            return new CallOutcome.Unavailable<>("interrupted");
        } catch (Error e) {
            // An OutOfMemoryError or a LinkageError is not "the vendor is down". Turning
            // it into a lookup miss would let a broken JVM quietly write wrong master
            // data for every instrument it touches. Let it kill the request.
            throw e;
        } catch (Throwable e) {
            String reason = scrub(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("External call to {} failed: {}", name, reason);
            return new CallOutcome.Unavailable<>(reason);
        }
    }

    private static String scrub(String message) {
        return message == null ? "unknown" : SECRET_IN_URL.matcher(message).replaceAll("$1***");
    }
}
