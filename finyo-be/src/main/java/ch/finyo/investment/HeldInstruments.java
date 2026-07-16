package ch.finyo.investment;

import ch.finyo.marketdata.HeldIsinsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Answers the market-data module's question about which securities are worth pricing. */
@Component
@RequiredArgsConstructor
class HeldInstruments implements HeldIsinsQuery {

    private final InstrumentRepository instrumentRepository;

    @Override
    public List<String> findAll() {
        return instrumentRepository.findDistinctIsins();
    }
}
