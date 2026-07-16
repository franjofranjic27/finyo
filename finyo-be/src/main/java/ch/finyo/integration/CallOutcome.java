package ch.finyo.integration;

/**
 * The outcome of one guarded call to an external source.
 *
 * {@link ResilientCall} used to return an {@code Optional}, which made "the vendor
 * answered, and the answer was nothing" indistinguishable from "the vendor could not
 * be reached". Downstream that difference decides whether a value may be written to
 * the database as a fact — so it has to survive the resilience layer.
 *
 * @param <T> whatever the adapter parsed out of the response
 */
public sealed interface CallOutcome<T> {

    /** The source answered. {@code value} may still be an empty result — that is an answer. */
    record Success<T>(T value) implements CallOutcome<T> {
    }

    /**
     * The source could not be reached, or spoke a language we no longer understand:
     * timeout, open circuit, exhausted rate limit, HTTP error, unparseable payload.
     * All of these say something about the vendor and nothing about the security.
     */
    record Unavailable<T>(String reason) implements CallOutcome<T> {
    }
}
