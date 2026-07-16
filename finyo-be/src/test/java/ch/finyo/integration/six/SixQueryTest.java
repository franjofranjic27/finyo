package ch.finyo.integration.six;

import ch.finyo.marketdata.spi.SecurityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SixQuery.
 *
 * Lives in the adapter's own package because the type is package-private — the FQS
 * query syntax must not be reachable from anywhere else, and the test respects that
 * boundary instead of widening it.
 *
 * The security half of this class matters more than the mapping half: FQS wants its
 * filter unencoded ("where=ISIN=CH0038863350"), so the identifier is spliced into a
 * URL as-is. There are two lines of defence against a crafted identifier, and this test
 * covers both — the {@link SecurityId} constructors, which refuse anything that is not
 * a well-formed ISIN or valor, and the whitelist here, at the splice itself.
 */
@DisplayName("SixQuery")
class SixQueryTest {

    @Nested
    @DisplayName("filterFor")
    class FilterFor {

        @Test
        void maps_an_isin_to_the_ISIN_column() {
            assertThat(SixQuery.filterFor(new SecurityId.Isin("CH0038863350")))
                    .isEqualTo("ISIN=CH0038863350");
        }

        @Test
        void maps_a_valor_to_the_ValorNumber_column() {
            assertThat(SixQuery.filterFor(new SecurityId.Valor("3886335")))
                    .isEqualTo("ValorNumber=3886335");
        }

        @Test
        void normalises_a_lowercase_isin_via_the_identifier_itself() {
            // SecurityId.Isin uppercases on construction, so the filter is stable no
            // matter how the user typed it — and, importantly, the string that lands in
            // the URL is the same one the format check saw.
            assertThat(SixQuery.filterFor(new SecurityId.Isin("ch0038863350")))
                    .isEqualTo("ISIN=CH0038863350");
        }
    }

    @Nested
    @DisplayName("URL injection")
    class UrlInjection {

        /**
         * Each of these would otherwise end up verbatim in an unencoded query string:
         * "&" and "?" start a new parameter, "=" forges a second condition, "../" walks
         * the path, and whitespace breaks the URI outright.
         *
         * They never get as far as SixQuery, and that is the point: the identifier types
         * refuse them at construction, so a hostile CSV cell cannot even be turned into
         * something the adapter could ask SIX about. The whitelist inside filterFor is the
         * second line — it guards the splice on the day someone relaxes the format check
         * "just for crypto".
         */
        @ParameterizedTest(name = "cannot even build a query for \"{0}\"")
        @ValueSource(strings = {
                "CH0038863350&select=*",
                "CH0038863350?x=1",
                "ISIN=CH0038863350",
                "CH0038 863350",
                "../../etc/passwd",
                "CH0038863350'",
                "CH0038863350%00",
                "CH00388633501234567890"          // 22 characters — beyond every bound
        })
        void an_identifier_that_is_not_alphanumeric_never_becomes_a_security_id(String hostile) {
            assertThatThrownBy(() -> SixQuery.filterFor(new SecurityId.Isin(hostile)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SixQuery.filterFor(new SecurityId.Valor(hostile)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void every_identifier_that_can_be_built_produces_a_purely_alphanumeric_filter_value() {
            // The invariant the URL depends on, stated directly: whatever a SecurityId
            // accepts is safe to splice. If that ever stops being true, filterFor throws
            // rather than build the request.
            assertThat(SixQuery.filterFor(new SecurityId.Isin("IE00B4L5Y983")))
                    .isEqualTo("ISIN=IE00B4L5Y983")
                    .matches("^[A-Za-z]+=[A-Za-z0-9]+$");
            assertThat(SixQuery.filterFor(new SecurityId.Valor("123456789012")))
                    .matches("^[A-Za-z]+=[A-Za-z0-9]+$");
        }
    }

    @Nested
    @DisplayName("text")
    class Text {

        @Test
        void turns_the_json_number_of_a_valor_into_a_string() {
            // FQS types its cells loosely: the valor arrives as 3886335, not "3886335".
            // Without this the domain would carry an Integer where it expects text.
            assertThat(SixQuery.text(3886335)).isEqualTo("3886335");
        }

        @Test
        void keeps_a_string_value_as_it_is() {
            assertThat(SixQuery.text("CH0038863350")).isEqualTo("CH0038863350");
        }

        @Test
        void trims_surrounding_whitespace() {
            assertThat(SixQuery.text("  NESN  ")).isEqualTo("NESN");
        }

        @Test
        void maps_a_missing_cell_to_null() {
            assertThat(SixQuery.text(null)).isNull();
        }

        @Test
        void maps_a_blank_cell_to_null_rather_than_an_empty_string() {
            // An empty currency cell must become "no currency", not "" — the latter
            // would blow up CurrencyCode's validation instead of defaulting cleanly.
            assertThat(SixQuery.text("   ")).isNull();
        }
    }
}
