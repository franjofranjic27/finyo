package ch.finyo.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Money.
 *
 * The one behaviour that matters is {@link Money#plus}: it must refuse to add two currencies. That
 * refusal is the guardrail for the bug this whole PR exists to fix — a USD amount summed into a CHF
 * total. A test that only proved same-currency addition would miss the entire point of the type.
 */
@DisplayName("Money")
class MoneyTest {

    @Test
    void adds_two_amounts_of_the_same_currency() {
        Money sum = Money.chf(new BigDecimal("100.00")).plus(Money.chf(new BigDecimal("50.00")));

        assertThat(sum.amount()).isEqualByComparingTo("150.00");
        assertThat(sum.currency()).isEqualTo(CurrencyCode.CHF);
    }

    @Test
    void refuses_to_add_two_different_currencies() {
        // No implicit rate exists, and inventing one is exactly the bug. The addition must fail
        // loudly rather than produce a number that is wrong in a way nobody can see.
        Money chf = Money.chf(new BigDecimal("100.00"));
        Money usd = Money.of(new BigDecimal("100.00"), new CurrencyCode("USD"));

        assertThatThrownBy(() -> chf.plus(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHF")
                .hasMessageContaining("USD");
    }

    @Test
    void a_zero_starts_a_sum_in_a_named_currency() {
        Money total = Money.zero(CurrencyCode.CHF).plus(Money.chf(new BigDecimal("42.00")));

        assertThat(total.amount()).isEqualByComparingTo("42.00");
    }
}
