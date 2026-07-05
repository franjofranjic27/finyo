package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxFederalRateRepository extends JpaRepository<TaxFederalRate, Long> {

    List<TaxFederalRate> findByTaxYearAndTariffOrderByIncomeFromAsc(int taxYear, String tariff);
}
