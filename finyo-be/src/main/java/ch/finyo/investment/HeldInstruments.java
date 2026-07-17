package ch.finyo.investment;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.fx.HeldCurrenciesQuery;
import ch.finyo.marketdata.HeldIsinsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answers the tenant-free modules' questions about what is worth fetching from a vendor: which
 * securities to price (marketdata) and which currencies need a rate (fx).
 */
@Component
@RequiredArgsConstructor
class HeldInstruments implements HeldIsinsQuery, HeldCurrenciesQuery {

    private final InstrumentRepository instrumentRepository;

    @Override
    public List<String> findAll() {
        return instrumentRepository.findDistinctIsins();
    }

    @Override
    public List<CurrencyCode> findForeign() {
        return instrumentRepository.findDistinctCurrencies().stream()
                .map(CurrencyCode::ofNullable)
                .filter(currency -> currency != null && !currency.isChf())
                .toList();
    }
}
