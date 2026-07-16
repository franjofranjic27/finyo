package ch.finyo.common;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test-side accessors for {@link SourceResult}.
 *
 * A sealed result type is precise but awkward to assert on: every test that wants the
 * payload off a {@code Found} would otherwise open with an instanceof-check and a cast,
 * and a test that casts is a test that can throw ClassCastException instead of failing
 * with a readable message.
 *
 * These helpers assert the variant first and then hand over the payload, so a test
 * expecting {@code Found} but getting {@code Unavailable} says exactly that.
 *
 * Generic, because {@code SourceResult} is: it carries security master data and quotes
 * today, exchange rates and tax data next. The predecessor ({@code LookupResults}) was
 * hard-wired to {@code SecurityReference} and would have had to be copied per payload.
 */
public final class SourceResults {

    private SourceResults() {
    }

    /** Asserts the source had an answer, and returns it. */
    public static <T> T foundValue(SourceResult<T> result) {
        assertThat(result).isInstanceOf(SourceResult.Found.class);
        return ((SourceResult.Found<T>) result).value();
    }

    /** Asserts the source could not be reached, and returns the reason it reported. */
    public static String unavailableReason(SourceResult<?> result) {
        assertThat(result).isInstanceOf(SourceResult.Unavailable.class);
        return ((SourceResult.Unavailable<?>) result).reason();
    }
}
