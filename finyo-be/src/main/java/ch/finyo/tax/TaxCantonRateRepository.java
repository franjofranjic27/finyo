package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxCantonRateRepository extends JpaRepository<TaxCantonRate, Long> {

    List<TaxCantonRate> findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(
            int taxYear,
            String cantonCode,
            String tariff);
}
