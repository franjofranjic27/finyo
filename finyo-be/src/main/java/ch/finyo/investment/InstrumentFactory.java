package ch.finyo.investment;

import ch.finyo.marketdata.SecurityLookup;
import ch.finyo.common.SourceResult;
import ch.finyo.marketdata.spi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * Builds an {@link Instrument} from what the user typed, enriched with master data
 * looked up by ISIN or valor.
 *
 * Before this existed, an auto-created instrument got its asset class from
 * {@link AssetClassifier} — a guess derived from substrings of the name ("contains
 * 'etf' ⇒ it is an ETF"). The currency was not stored at all.
 *
 * The heuristic nevertheless survives as the last link in the chain, and deliberately
 * so: the CSIF funds behind VIAC and finpension are unlisted institutional share classes
 * that SIX does not know ({@code totalRows: 0}, verified). Dropping the heuristic would
 * regress exactly the 3a instruments this project cares most about.
 *
 * <p>What it will <em>not</em> do is dress a guess up as a fact. Two rules:
 * <ul>
 *   <li>An unknown currency stays {@code null}. It is never defaulted to CHF — doing so
 *       would recreate, one level up, the very bug this module exists to kill (a USD ETF
 *       summed into the portfolio total as francs).</li>
 *   <li>A provider outage is recorded as {@link DataSource#UNRESOLVED}, not as
 *       {@link DataSource#HEURISTIC}. The first is a to-do and gets retried; the second
 *       is a final answer. Conflating them would freeze a guess into the database for
 *       good, because the instrument is found by ISIN on every subsequent import and
 *       never re-resolved.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentFactory {

    private final SecurityLookup securityLookup;

    /**
     * Resolves master data. Called <em>outside</em> the caller's transaction — it does
     * network I/O, and a database connection must not be held open across it.
     */
    SourceResult<SecurityReference> lookup(String isin, String valor) {
        return identifierFor(isin, valor)
                .map(securityLookup::resolve)
                .orElseGet(SourceResult::notFound);
    }

    Instrument create(SourceResult<SecurityReference> lookup, String name, String isin, String valor, String userId) {
        return switch (lookup) {
            case SourceResult.Found(SecurityReference reference) ->
                    fromReference(reference, name, isin, valor, userId);
            case SourceResult.NotFound _ ->
                    unverified(name, isin, valor, userId, DataSource.HEURISTIC);
            case SourceResult.Unavailable(String reason) -> {
                log.warn("Providers unavailable while creating instrument isin={} valor={} ({}) — "
                        + "storing it UNRESOLVED so it gets another chance", isin, valor, reason);
                yield unverified(name, isin, valor, userId, DataSource.UNRESOLVED);
            }
        };
    }

    /**
     * Gives an instrument whose master data was never verified another chance, now that the
     * providers have answered.
     *
     * @return the updated instrument, or empty when nothing changed — the providers are
     *         still unreachable, so it stays UNRESOLVED and will be asked again next time
     */
    Optional<Instrument> enrich(Instrument instrument, SourceResult<SecurityReference> lookup) {
        return switch (lookup) {
            case SourceResult.Found(SecurityReference reference) -> {
                log.info("Re-resolved previously unresolved instrument id={} via {}",
                        instrument.getId(), reference.source());
                yield Optional.of(instrument.toBuilder()
                        .name(firstNonBlank(instrument.getName(), reference.name()))
                        .isin(firstNonBlank(reference.isin(), instrument.getIsin()))
                        .valor(firstNonBlank(reference.valor(), instrument.getValor()))
                        .ticker(firstNonBlank(reference.ticker(), instrument.getTicker()))
                        .currency(reference.currency())
                        .instrumentType(toInstrumentType(reference.type()))
                        .assetClass(toAssetClass(reference.type(), reference.name(), reference.isin()))
                        .source(reference.source())
                        .build());
            }
            // The providers answered this time, and none of them knows the security. That is
            // a final answer, so the instrument stops being a to-do and settles as HEURISTIC
            // — otherwise it would be re-queried on every single touch, forever.
            case SourceResult.NotFound _ -> {
                log.info("Instrument id={} is unknown to every provider — settling it as HEURISTIC",
                        instrument.getId());
                yield Optional.of(instrument.toBuilder().source(DataSource.HEURISTIC).build());
            }
            // Still unreachable. Leave it UNRESOLVED; it gets another chance next time.
            case SourceResult.Unavailable _ -> Optional.empty();
        };
    }

    /**
     * ISIN is the better key — every provider resolves it, while only SIX knows valor
     * numbers. But a malformed ISIN must not discard a perfectly good valor: a typo in one
     * field would otherwise cost the lookup entirely, and the instrument would be filed as
     * HEURISTIC, which is final and never retried.
     */
    private static Optional<SecurityId> identifierFor(String isin, String valor) {
        return parse(isin, SecurityId.Isin::new)
                .or(() -> parse(valor, SecurityId.Valor::new));
    }

    private static Optional<SecurityId> parse(String value, Function<String, SecurityId> factory) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(factory.apply(value));
        } catch (IllegalArgumentException e) {
            // Malformed, so not worth a provider round trip. The instrument is still created
            // — the user may have their reasons for that string — just not verified from it.
            log.info("Skipping provider lookup: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Instrument fromReference(SecurityReference reference, String name, String isin, String valor, String userId) {
        log.info("Resolved instrument isin={} valor={} name='{}' currency={} via {}",
                reference.isin(), reference.valor(), reference.name(), reference.currency(), reference.source());

        return Instrument.builder()
                .userId(userId)
                // The provider's name is the canonical one, but a user who typed a name keeps
                // it — they may have a reason, and overwriting user input with vendor data is
                // a surprise, not a service.
                .name(firstNonBlank(name, reference.name()))
                .isin(firstNonBlank(reference.isin(), isin))
                .valor(firstNonBlank(reference.valor(), valor))
                .ticker(reference.ticker())
                // Null when the provider does not publish one — OpenFIGI never does. Left null
                // rather than defaulted, so "unknown" stays distinguishable from "CHF".
                .currency(reference.currency())
                .instrumentType(toInstrumentType(reference.type()))
                .assetClass(toAssetClass(reference.type(), firstNonBlank(reference.name(), name), reference.isin()))
                .source(reference.source())
                .sortOrder(0)
                .build();
    }

    /**
     * Nothing about this instrument has been verified — either no provider knows it
     * (HEURISTIC, the normal outcome for unlisted 3a funds) or none could be reached
     * (UNRESOLVED, a to-do). The name heuristic is the best available guess for the asset
     * class, and the currency stays unknown rather than being invented.
     */
    private Instrument unverified(String name, String isin, String valor, String userId, DataSource source) {
        return Instrument.builder()
                .userId(userId)
                .name(name)
                .isin(isin)
                .valor(valor)
                .currency(null)
                .instrumentType(InstrumentType.OTHER)
                .assetClass(AssetClassifier.classify(name, isin))
                .source(source)
                .sortOrder(0)
                .build();
    }

    private static InstrumentType toInstrumentType(SecurityType type) {
        return switch (type) {
            case EQUITY -> InstrumentType.STOCK;
            case ETF -> InstrumentType.ETF;
            case FUND -> InstrumentType.FUND;
            case BOND -> InstrumentType.BOND;
            case CRYPTO -> InstrumentType.CRYPTO;
            case OTHER -> InstrumentType.OTHER;
        };
    }

    /**
     * SIX's reference feed only distinguishes shares, ETFs and public funds — bonds are
     * absent from it entirely, and anything else comes back as OTHER. There the name
     * heuristic is still better than asserting STOCK, so it gets the last word.
     */
    private static AssetClass toAssetClass(SecurityType type, String name, String isin) {
        return switch (type) {
            case EQUITY -> AssetClass.STOCK;
            case ETF -> AssetClass.ETF;
            case FUND -> AssetClass.FUND;
            case BOND -> AssetClass.BOND;
            case CRYPTO -> AssetClass.CRYPTO;
            case OTHER -> AssetClassifier.classify(name, isin);
        };
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
