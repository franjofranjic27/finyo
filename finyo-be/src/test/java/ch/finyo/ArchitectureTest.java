package ch.finyo;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The integration boundary, enforced as a test.
 *
 * Every one of finyo's external sources is unofficial (SIX FQS, the ESTV calculator)
 * or has terms that will eventually force a swap (SIX's "personal use only"). The
 * architecture is built so that losing one of them is a configuration change rather
 * than a refactoring — but only as long as vendor concepts stay behind the adapter.
 *
 * That property decays silently under normal maintenance: someone imports an FQS
 * record into a service "just to get the ProductLine", and a year later SIX is
 * wired through half the codebase. These rules are cheap, run in seconds, and are
 * the reason the boundary is still there in six months.
 */
@DisplayName("Architecture")
class ArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ch.finyo");
    }

    @Test
    @DisplayName("vendor code stays inside ch.finyo.integration")
    void integrationIsAOneWayStreet() {
        // The adapters implement the ports and are wired in by Spring; nobody needs to
        // name them. A SIX field name or an ESTV wire type must not be reachable from
        // the domain — the point of the whole layer is that only one package changes
        // when a vendor does.
        //
        // No exception for config either: it configures resilience *policies*, it does not
        // reach into the adapters. A pre-emptive hole in the one rule that keeps this
        // boundary alive is how the boundary dies.
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("ch.finyo.integration..")
                .should().dependOnClassesThat().resideInAPackage("ch.finyo.integration..")
                .because("vendor formats must not leak out of the adapters — see ADR-007");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("marketdata does not depend on feature modules")
    void marketDataIsStandalone() {
        // marketdata holds tenant-free market facts and is consumed by investment (and
        // later by wealth and tax). If it depended back on a feature module, the
        // dependency would be circular and the module would not be reusable — the very
        // reason it was split out of investment in the first place.
        ArchRule rule = noClasses()
                .that().resideInAPackage("ch.finyo.marketdata..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "ch.finyo.investment..",
                        "ch.finyo.tax..",
                        "ch.finyo.pillar3..",
                        "ch.finyo.transaction..",
                        "ch.finyo.account..",
                        "ch.finyo.wealth..",
                        "ch.finyo.budget..")
                .because("marketdata is a standalone module — the dependency runs the other way");

        rule.check(productionClasses);
    }
}
