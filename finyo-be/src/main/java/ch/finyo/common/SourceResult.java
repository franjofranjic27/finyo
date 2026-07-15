package ch.finyo.common;

/**
 * The outcome of asking an external source for something — and the reason this is a type
 * rather than an {@code Optional}.
 *
 * "The source does not have this" and "the source could not be reached" are entirely
 * different facts, and collapsing both into an empty Optional is how finyo's oldest bug
 * gets rebuilt: a guess written down as though it were a fact.
 *
 * <ul>
 *   <li>{@link NotFound} is a real answer about the world. The unlisted CSIF funds behind
 *       VIAC and finpension genuinely are in no provider's catalogue, so falling back to a
 *       heuristic is correct and the result is durable.</li>
 *   <li>{@link Unavailable} says nothing about the thing asked for — only about the network.
 *       Treating it like NotFound freezes a guess into the database during an outage and
 *       never looks again.</li>
 * </ul>
 *
 * Generic because the same distinction governs every external source finyo has: security
 * master data and quotes today, exchange rates and Swiss tax data next. See ADR-008.
 *
 * @param <T> what the source returns when it does have an answer
 */
public sealed interface SourceResult<T> {

    record Found<T>(T value) implements SourceResult<T> {
    }

    /** The source answered, and it does not have this. A durable answer. */
    record NotFound<T>() implements SourceResult<T> {
    }

    /** Could not be reached: timeout, open circuit, exhausted rate limit, broken payload. */
    record Unavailable<T>(String reason) implements SourceResult<T> {
    }

    static <T> SourceResult<T> found(T value) {
        return new Found<>(value);
    }

    static <T> SourceResult<T> notFound() {
        return new NotFound<>();
    }

    static <T> SourceResult<T> unavailable(String reason) {
        return new Unavailable<>(reason);
    }
}
