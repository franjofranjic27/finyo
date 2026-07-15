package ch.finyo.fx.spi;

import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.fx.FxRate;
import ch.finyo.fx.FxRateType;

import java.time.LocalDate;
import java.util.List;

/**
 * A source of exchange rates, behind a port in the consuming module.
 *
 * The vendor adapters ({@code integration.frankfurter}, {@code integration.bazg}) implement this;
 * the {@code fx} module never names them. Each provider fixes a single {@link #type()} — Frankfurter
 * is {@link FxRateType#MID}, BAZG is {@link FxRateType#OFFICIAL_CH} — because the two rates are not
 * interchangeable and a provider that could return either would hide the distinction the type
 * system is meant to force.
 *
 * <p>A returned {@link FxRate} is always normalised to {@code chfPerUnit}. Whatever direction the
 * vendor speaks in stays inside the adapter.
 */
public interface FxRateProvider {

    /** Stable name, used in {@code fx_rate.source} and in logs: {@code frankfurter} | {@code bazg}. */
    String name();

    /** The one kind of rate this provider yields. */
    FxRateType type();

    /**
     * The rate for one currency on one day. {@code NotFound} when the source has no rate for that
     * day (a weekend, say), {@code Unavailable} when it could not be reached — the distinction that
     * decides whether anything may be written down. See {@link SourceResult}.
     */
    SourceResult<FxRate> rate(CurrencyCode currency, LocalDate on);

    /**
     * Every rate for one currency across a date range, oldest first — for backfilling history in
     * one call. Missing days (weekends, holidays) are simply absent, never interpolated. Empty when
     * the provider does not support ranges (BAZG serves one day per request).
     */
    List<FxRate> rates(CurrencyCode currency, LocalDate from, LocalDate to);
}
