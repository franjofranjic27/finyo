package ch.finyo.marketdata.spi;

/**
 * The outcome of a master-data lookup — and the reason this is a type rather than an
 * {@code Optional}.
 *
 * "The provider does not know this security" and "the provider could not be reached"
 * are entirely different facts, and collapsing both into an empty Optional is how the
 * bug this whole module exists to fix gets rebuilt one level up:
 *
 * <ul>
 *   <li>{@link NotFound} is a real answer about the world. The unlisted CSIF funds
 *       behind VIAC and finpension genuinely are not in any provider's catalogue, so
 *       falling back to the name heuristic is correct and the result is durable.</li>
 *   <li>{@link Unavailable} says nothing at all about the security — only about the
 *       network. Treating it like NotFound would freeze a guess into the database
 *       during an outage and never look again, because the instrument then exists and
 *       is found by ISIN on every subsequent import.</li>
 * </ul>
 *
 * So the distinction is not pedantry: it decides whether a value may be persisted as
 * fact or must be marked for another attempt.
 */
public sealed interface LookupResult {

    LookupResult NOT_FOUND = new NotFound();

    record Found(SecurityReference reference) implements LookupResult {
    }

    /** Providers answered, and none of them knows this security. A durable answer. */
    record NotFound() implements LookupResult {
    }

    /** A provider could not be reached (timeout, open circuit, rate limit, broken payload). */
    record Unavailable(String reason) implements LookupResult {
    }

    static LookupResult found(SecurityReference reference) {
        return new Found(reference);
    }

    static LookupResult notFound() {
        return NOT_FOUND;
    }

    static LookupResult unavailable(String reason) {
        return new Unavailable(reason);
    }
}
