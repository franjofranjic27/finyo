package ch.finyo.marketdata.spi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test-side accessors for {@link LookupResult}.
 *
 * A sealed result type is precise but awkward to assert on: every test that wants a
 * field off a {@code Found} would otherwise open with an instanceof-check and a cast,
 * and a test that casts is a test that can throw ClassCastException instead of failing
 * with a readable message.
 *
 * These two helpers assert the variant first and then hand over the payload, so a test
 * expecting {@code Found} but getting {@code Unavailable} says exactly that.
 */
public final class LookupResults {

    private LookupResults() {
    }

    /** Asserts the lookup found something, and returns what. */
    public static SecurityReference foundReference(LookupResult result) {
        assertThat(result).isInstanceOf(LookupResult.Found.class);
        return ((LookupResult.Found) result).reference();
    }

    /** Asserts a provider was unreachable, and returns the reason it reported. */
    public static String unavailableReason(LookupResult result) {
        assertThat(result).isInstanceOf(LookupResult.Unavailable.class);
        return ((LookupResult.Unavailable) result).reason();
    }
}
