package ch.finyo.common.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An amount in a currency — and a {@link #plus} that refuses to add two currencies.
 *
 * This is the guardrail for finyo's oldest and most expensive bug: a USD position summed into a
 * CHF total as though francs and dollars were the same number. No review catches that as reliably
 * as a type that will not compile the mistake into silence — where FX is needed, the developer
 * must convert first and say so. There is no implicit conversion here on purpose. See ADR-009.
 *
 * <p>Used as a value object at the aggregation boundary (the portfolio total), not mapped onto
 * entities: retrofitting {@code @Embedded Money} across Instrument, Position and the rest is a
 * larger change than this PR wants, and deliberately deferred.
 */
public record Money(BigDecimal amount, CurrencyCode currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(BigDecimal amount, CurrencyCode currency) {
        return new Money(amount, currency);
    }

    public static Money chf(BigDecimal amount) {
        return new Money(amount, CurrencyCode.CHF);
    }

    public static Money zero(CurrencyCode currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * @throws IllegalArgumentException when the currencies differ — there is no implicit rate, and
     *         inventing one is the very bug this type exists to prevent
     */
    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add %s and %s without an explicit conversion".formatted(currency, other.currency));
        }
        return new Money(amount.add(other.amount), currency);
    }
}
