# Umsetzungsplan: Externe Datenquellen

Grundlage: `docs/DATENQUELLEN.md` (Recherche vom 2026-07-14, alle Endpunkte live verifiziert).
Leitlinie: **saubere Lösung vor kurzem Weg**. Aufwand ist ausdrücklich zweitrangig.

Stand: Migrationen bis **V32**, Backend `ch.finyo` (Boot 4.1, Java 25).

---

## Die Leitentscheidung

Zwei Dinge im Ist-Zustand sind **keine fehlenden Features, sondern Fehler, die still falsche
Zahlen produzieren**:

1. `PortfolioService.price()` fällt ohne SIX-Key auf `PriceSource.PURCHASE` zurück → Gewinn/Verlust
   ist 0.00, ohne jedes Signal an den Nutzer.
2. Eine USD-Position wird aufsummiert, als wäre sie CHF → das Portfoliototal ist schlicht falsch.

Beides sind **Provenienz-Probleme**, keine Integrationsprobleme. Mehr Retries oder ein besserer
Client heilen sie nicht. Die Architektur muss deshalb erzwingen, dass jeder extern abgeleitete Wert
seine **Herkunft** (`source`, `asOf`, `stale`) und seine **Währung** mitträgt. Das ist der rote
Faden durch alle neun PRs.

---

## Fünf Architekturentscheidungen

### 1. Ports & Adapter — `integration` bleibt Einbahnstrasse

Drei neue Packages. Der **Port** (Interface + neutrale DTOs) wohnt im konsumierenden Modul, der
**Adapter** in `integration`. Ein SIX-Feldname wie `ClosingPrice` oder `ProductLine=ET` darf
`ch.finyo.integration.six` niemals verlassen.

```
ch.finyo
├── common/money/     Money (@Embeddable), CurrencyCode                    [NEU]
├── config/           + SchedulingConfig, ResilienceConfig
├── sync/             nur SyncRun + SyncRunRecorder + Admin-Read           [NEU]
│
├── integration/      nur Adapter: HTTP, Vendor-DTOs, Mapping. Kein Fachwissen.   [NEU]
│   ├── six/          SixReferenceAdapter, SixQuoteAdapter, SixHistoryAdapter
│   ├── openfigi/     OpenFigiReferenceAdapter
│   ├── eodhd/        (leer — erst bei Multi-User)
│   ├── frankfurter/  FrankfurterFxAdapter
│   ├── bazg/         BazgFxAdapter
│   └── estv/         EstvTaxAdapter, EstvRateExportAdapter
│
├── marketdata/       mandantenfreie Referenzdaten                         [NEU]
│   ├── spi/          SecurityReferenceProvider, QuoteProvider, PriceHistoryProvider
│   ├── SecurityReference, InstrumentPrice (Entities)
│   ├── SecurityLookup, MarketDataService, PriceSyncJob
│
├── fx/               mandantenfrei                                        [NEU]
│   ├── spi/FxRateProvider
│   ├── FxRate, FxRateType {MID, OFFICIAL_CH}
│   └── FxConverter, FxRateSyncJob
│
├── investment/  →  marketdata, fx
└── tax/         +  spi/TaxEngine, spi/DeductionLimitProvider
```

**Warum eigene Module und nicht in `investment`:** Der Schlusskurs von `IE00B4L5Y983` ist für alle
Nutzer derselbe — Preise und Stammdaten sind **mandantenfrei**, `Instrument` mit seiner `user_id`
ist es nicht. Eine Preistabelle mit `user_id` wäre Datenduplikation mit Konsistenzrisiko. Dazu
kommt: FX braucht vier Module (investment, transaction, wealth, tax), ESTV zwei. In einem
Fachmodul wäre das immer die falsche Abhängigkeitsrichtung.

**Durchgesetzt wird das per ArchUnit**, nicht per Review-Disziplin:
```
noClasses().that().resideOutsideOfPackage("ch.finyo.integration..")
    .should().dependOnClassesThat().resideInAPackage("ch.finyo.integration..")
```
Zwei Regeln, Sekunden Laufzeit — und der einzige Grund, warum die Struktur in sechs Monaten noch
steht.

### 2. Provider-Kette — Segregation nach Capability

Ein einziges `MarketDataProvider`-Interface wäre falsch: OpenFIGI müsste `latestQuote()` mit
`Optional.empty()` beantworten. Das ist ein ISP-Bruch, der sich als "der Provider liefert halt
nichts" tarnt. Also drei Ports plus ein `supports()`-Prädikat für die Asymmetrie:

```java
public sealed interface SecurityId {
    record Isin(String value)               implements SecurityId {}
    record Valor(String value)              implements SecurityId {}
    record Ticker(String value, String mic) implements SecurityId {}
}

public interface SecurityReferenceProvider {
    String name();                              // "six" | "openfigi" | "eodhd"
    boolean supports(SecurityId id);            // OpenFIGI kennt kein Valor
    Optional<SecurityReference> lookup(SecurityId id);
}
public interface QuoteProvider {
    String name();
    boolean supports(SecurityId id);
    Map<SecurityId, Quote> latestQuotes(Collection<SecurityId> ids);   // Batch ist Pflicht
}
public interface PriceHistoryProvider {
    String name();
    List<PriceBar> history(SecurityId id, LocalDate from, LocalDate to);
}
```

**Die Kettenreihenfolge kommt aus der Config, nicht aus `@Order`** — sie ist eine Betriebs-, keine
Code-Entscheidung:

```yaml
finyo:
  marketdata:
    reference-providers: [six, openfigi]   # Reihenfolge = Fallback-Kette
    quote-providers:     [six]
    six:      { enabled: true }
    openfigi: { enabled: true, api-key: "${OPENFIGI_API_KEY:}" }
    eodhd:    { enabled: false, api-key: "${EODHD_API_KEY:}" }
  fx:
    provider: frankfurter
```

Der Multi-User-Tag ist damit: `six.enabled: false`, `eodhd.enabled: true`. **Drei YAML-Zeilen, null
Code.** Keine Spring Profiles — die beschreiben Umgebungen, nicht Provider-Wahl; sie als
Feature-Switch zu missbrauchen sprengt die Testmatrix.

**Dazu eine Startup-Assertion:** `if (six.enabled && userCount > 1) → log.error(...)`. Damit wird
die "personal use"-Klausel von SIX von einer Notiz in `docs/` zu einer ausführbaren Zusicherung.

### 3. Caching — Caffeine cacht Antworten, Postgres speichert Daten

Faustregel: **Wenn der Verlust des Eintrags bei Ausfall der Quelle das Feature bricht, gehört er
nach Postgres.**

| Postgres (System of Record, mandantenfrei) | Caffeine (Hot Path, TTL, nie autoritativ) |
|---|---|
| `security_reference` — überlebt SIX-Ausfall | Intraday-Quote, 15 Min. (= SIX-Delay) |
| `instrument_price` (isin, price_date) | ESTV-`calculateDetailedTaxes`, Key = Hash des normalisierten Requests, 1 h |
| `fx_rate` (currency, date, type) | |
| `tax_*` — ab jetzt ESTV-gesynct | |
| `tax_deduction_limit` | |

Der ESTV-Cache ist der wertvollste neue: `TaxScenarioService.list()` rechnet **pro Szenario** neu —
eine Szenarien-Seite mit fünf Chips wären sonst fünf ESTV-Roundtrips pro Seitenaufruf.

**Der Lesepfad blockiert nie auf einer externen Quelle:**
```
MarketDataService.currentPrice(SecurityId)
  → Caffeine (≤15 min)         → hit: return
  → instrument_price (latest)  → return Quote(price, asOf, stale = älter als 1 Handelstag)
                                 + asynchroner Refresh über die Provider-Kette
  → nichts vorhanden           → Optional.empty()   (Aufrufer entscheidet, kein stiller Fallback)
```
Heute macht `PortfolioService.fetchLivePrices()` N synchrone HTTP-Calls im Nutzer-Request — bei
hängendem SIX ist das N × Timeout. **Das ist die grösste Resilienzverbesserung im ganzen Vorhaben,
und sie ist strukturell, nicht bibliotheksbasiert.**

**`instrument_price` und `portfolio_snapshot` sind komplementär, nicht redundant.** Ersteres ist ein
Marktfaktum (global, unveränderlich, rückwirkend befüllbar). Letzteres ein Nutzerfaktum, das vom
*Bestand zu diesem Zeitpunkt* abhängt — und `Position` ist mutabel (`merge()` überschreibt Menge und
Durchschnittskaufpreis beim Zukauf). **Es gibt keine Bestandshistorie**, der Bestand vom 01.03. ist
rückwirkend nicht rekonstruierbar. Snapshots bleiben also — aber der Schreibzeitpunkt wandert vom
Read (`getPortfolio()` mutiert heute beim GET!) in einen nächtlichen Job.

### 4. Money — der Typ erzwingt die Entscheidung

```java
@Embeddable
public record Money(BigDecimal amount, CurrencyCode currency) {
    public Money plus(Money other);   // wirft bei Währungsungleichheit — KEINE implizite Umrechnung
}
```

**Regel 1: `Money.plus()` wirft.** Es gibt keine stille Umrechnung. Wo FX nötig ist, *muss* der
Entwickler es hinschreiben. Kein Review erzwingt das so zuverlässig wie ein Typ, der sich weigert.

**Regel 2: Konvertiert wird an genau einer Stelle pro Use Case — an der Aggregationsgrenze.**
Gespeichert wird immer in Originalwährung, umgerechnet beim Lesen. Für das Portfolio ist das
`PortfolioService` (es summiert bereits — es *ist* die Grenze).

**Regel 3: `FxRateType` ist Pflichtparameter, kein Default.** Das ist die Antwort auf die
BAZG-Falle: `MID` (Frankfurter/EZB) für Bewertung und Charts, `OFFICIAL_CH` (BAZG, Verkaufskurs) nur
für steuerliche Kontexte. 1.2 % Spread auf ein sechsstelliges Portfolio ist echtes Geld — ein
Default-Overload würde diese Entscheidung verstecken.

**Die Richtungsfalle wird per Schema gelöst, nicht per Konvention:**
`fx_rate(currency, rate_date, chf_per_unit, rate_type, source)` — strukturell ist nur *eine*
Richtung speicherbar: **CHF pro 1 Einheit Fremdwährung**. Frankfurter (`base=CHF` → EUR pro CHF)
wird im Adapter invertiert, SNB-`JPY100` im Adapter durch 100 geteilt. Beide Fallen sind damit im
Adapter eingesperrt und erreichen das Domänenmodell nie.

`FxConverter.convertAll()` gibt die **angewandten Kurse zurück** — die `PortfolioResponse` kann
sagen: "USD 1'000 @ 0.8123 (EZB-Mittelkurs, 11.07.2026)". Ohne das kann der Nutzer die Zahl nicht
nachrechnen und wird ihr nicht trauen.

**Kein Big-Bang-Refactoring.** Money kommt für Instrumente, Positionen, Transaktionen, Kontosalden —
nicht für Budget, Steuern oder 3a (beide per Gesetz CHF). `Transaction` hat bereits `amount` +
`currency` als zwei Spalten; ein `@Embedded Money` mit `@AttributeOverrides` darauf ist ein
**Zero-Migration-Change**.

### 5. ESTV — Strategy + Anti-Corruption Layer, eingeführt über Shadow Mode

```java
public interface TaxEngine {
    String name();                       // "estv" | "local"
    Set<String> supportedCantons();
    TaxAssessment assess(TaxAssessmentRequest request);
    CapitalWithdrawalTax capitalWithdrawalTax(CapitalWithdrawalRequest request);
}
public interface DeductionLimitProvider {          // ersetzt die 3× hardcodierte 7258
    BigDecimal pillar3aMax(int taxYear, Pillar3aScheme scheme);
}
```

Die gesamte ESTV-Pathologie — der Zweischritt `API_calculateTaxBudget` → `PRAEMIEN3A` setzen →
`API_calculateDetailedTaxes` mit den **vollständigen zurückgegebenen Budget-Objekten** (blosse
`{Ident, Value}`-Paare werden still ignoriert) — lebt vollständig in `ch.finyo.integration.estv`.
Die Domäne stellt eine Frage und bekommt eine Antwort.

**Eingeführt wird in zwei Schritten, nicht in einem:**

*Shadow Mode (PR 8):* `ShadowTaxEngine` ruft `LocalTaxEngine` (autoritativ) und `EstvTaxEngine`
(asynchron, best-effort) und loggt die Differenz als Metrik. Kein sichtbares Verhalten ändert sich.
**Der Testkorpus existiert bereits: die `tax_scenario`-Tabelle mit echten Eingaben.** Zwei Wochen
laufen lassen. Wenn die Deltas nicht erklärbar sind, merkt man das *vorher* — und nicht in der
Steuererklärung.

*Umschaltung (PR 9):* `FallbackTaxEngine`:
```
ESTV erreichbar          → EstvTaxEngine
ESTV down + Kanton SG    → LocalTaxEngine, Ergebnis markiert: engine=local, degraded=true
ESTV down + andere       → 503, kein erfundenes Ergebnis
```
**Der Fallback ist bewusst nur für SG erlaubt.** Einen Zürcher stillschweigend mit dem SG-Tarif zu
bedienen wäre schlimmer als ein Fehler — genau das Muster, das heute beim Preis passiert. Es darf
sich in der Steuer nicht wiederholen.

`TaxCalculationService` wird **nicht gelöscht**, sondern zu `LocalTaxEngine implements TaxEngine`
umbenannt. Seine Tests sind das Regressionsnetz *und* das Vergleichsorakel im Shadow-Mode.

---

## Resilience

**Resilience4j als reine Java-Libs** (`-circuitbreaker`, `-retry`, `-ratelimiter`), programmatisch
im Adapter dekoriert — **nicht** der Spring-Boot-Starter. Grund: Der Kompatibilitätsstand von
`resilience4j-spring-boot3` (AOP + Auto-Config) gegen Boot 4.1 / Spring 7 ist unsicher; mit den
Core-Libs stellt sich die Frage gar nicht. Kosten: ~15 Zeilen pro Adapter statt Annotationen.
Micrometer-Anbindung in zwei Zeilen, der Prometheus-Scrape existiert bereits.

Spring's eigenes `@Retryable` reicht **nicht**: es kann Retry, aber **keinen Circuit Breaker und
keinen Rate Limiter**. Letzterer ist Pflicht — OpenFIGI erlaubt 25 req/6 s, beim Bulk-Import von 30
Positionen läuft man garantiert in 429.

| Quelle | Timeout | Retry | Circuit Breaker | Rate Limiter |
|---|---|---|---|---|
| SIX | 3 s / 5 s | 2× nur auf 5xx + IO, **nie auf 4xx** | 50 % / 20 Calls / 60 s | mild |
| OpenFIGI | 5 s | 1× | ja | **25 / 6 s (Pflicht)** + Batch-Endpoint (100 IDs) |
| ESTV | 10 s | 1× | ja | + Bulkhead (der 1.1-MB-Export darf nicht neben Nutzer-Requests laufen) |
| Frankfurter (self-hosted) | 2 s | 1× | — | — |

**Health Indicators gehören in eine eigene Gruppe**, nicht in die Default-Health
(`management.endpoint.health.group.integrations`). Sonst macht ein kaputtes SIX `/actuator/health`
→ `DOWN`, und der Docker-Healthcheck killt den Container.

---

## Scheduled Sync

Jobs wohnen im **besitzenden Fachmodul** (`marketdata.PriceSyncJob`, `fx.FxRateSyncJob`,
`tax.TaxRateSyncJob`) — ein zentrales Job-Package müsste auf jedes Fachmodul zeigen und wäre ein
God-Package. Zentral ist nur `ch.finyo.sync` mit `SyncRun` + `SyncRunRecorder` (Audit).

**Eigener `ThreadPoolTaskScheduler` (pool-size 2).** Der Spring-Default ist *single-threaded* — ein
hängender ESTV-Export würde sonst den Kurs-Sync blockieren.

| Job | Cron (CET) | Umfang |
|---|---|---|
| `FxRateSyncJob` | 17:15 tgl. | EZB publiziert ~16:00; Backfill bei Lücke |
| `PriceSyncJob` | 22:30 tgl. | nur ISINs aus `instrument` (~30 Requests, kein Universum) |
| `PortfolioSnapshotJob` | 23:00 tgl. | *nach* dem Preis-Sync; ersetzt den Write-on-Read |
| `TaxRateSyncJob` | monatlich, 1., 03:00 | Steuerfüsse + Abzugslimiten in einem Job (17 Requests) |

**Kein ShedLock.** Die App ist Single-Instance. Die realen Nebenläufigkeiten (Redeploy während eines
Laufs, Admin-Trigger kreuzt den Cron-Lauf) löst ein **Postgres Advisory Lock**
(`pg_try_advisory_lock(hashtext(jobName))`) — drei Zeilen, keine Dependency, und es *ist* bereits
ein verteilter Lock, falls je eine zweite Instanz kommt. Zusammen mit `INSERT … ON CONFLICT DO
UPDATE` (das Muster existiert in `PortfolioSnapshotRepository`) ist jeder Sync idempotent.

Alle Jobs `@ConditionalOnProperty("finyo.sync.enabled")`, Default `false` im `test`-Profil — sonst
geht jeder Integrationstest ins Netz. Manuelle Trigger unter `/api/v1/admin/sync/{job}`
(`AdminController` und Admin-Rolle existieren bereits), damit man sich von einem gescheiterten
Nachtlauf ohne Redeploy erholt.

---

## PR-Schnitt

Zwingende Abhängigkeit: `Instrument.currency` blockiert FX *und* korrekte Kurse; FX blockiert camt.
**Die ESTV-Strecke (PR 6–9) ist davon völlig unabhängig und parallel mergebar** — eine echte
Dividende des Modulschnitts.

```
PR1 ──► PR2 ──► PR3
 └────► PR4 ──► PR5
PR6 ──► PR7 ──► PR8 ──► PR9
```

### PR 1 — Fundament + Stammdaten-Lookup
Migration **V33**: `security_reference`, `instrument.currency` (`char(3) NOT NULL DEFAULT 'CHF'`),
`instrument.source`.

- [ ] `ch.finyo.integration` + `ch.finyo.marketdata` + `spi`-Ports anlegen
- [ ] `SixReferenceAdapter` (`fqs/ref.json`), `OpenFigiReferenceAdapter` (v3, Batch)
- [ ] `SecurityLookup` (Provider-Kette, Postgres-first), `MarketDataProperties`
- [ ] Resilience-Dekoration (Retry, CB, RateLimiter) pro Adapter
- [ ] `ch.finyo.sync`: `SyncRun`, `SyncRunRecorder`, Advisory Lock
- [ ] ArchUnit-Regel: nichts ausserhalb `integration` darf auf `integration` zeigen
- [ ] Startup-Assertion für die SIX-"personal use"-Klausel
- [ ] `PositionService.createInstrument()` nutzt den Lookup — **`AssetClassifier` bleibt als letzter
      Fallback** (sonst regressieren die nicht-kotierten 3a-Fonds, die SIX nicht kennt)

**Nutzen für sich:** Eine Position per ISIN oder Valor anlegen füllt Name, Ticker, Valor, Währung
und Typ korrekt — statt sie aus dem Namensstring zu raten.

### PR 2 — Kurse (das grösste Delta) ✅ FERTIG (nicht committet)
Migration **V34**: `instrument_price(isin, price_date, close, currency, source, retrieved_at)` +
`sync_run`.

- [x] `SixMarketDataClient` **gelöscht** (geratener Endpoint, toter Code) samt
      `SixMarketDataProperties`, `MarketDataResponse`, Endpunkt `GET /instruments/{id}/market-data`
- [x] `SixQuoteAdapter` (`fqs/movie.json`), `MarketDataService` (Lesen nur DB, `refresh` nur Netz)
- [x] **SIX aus dem Lesepfad entfernt** — `getPortfolio()` macht keinen HTTP-Call mehr
- [x] Provenienz (`priceAsOf`, `priceSource` MARKET/MANUAL/PURCHASE, `stale`, `currency`) in die
      DTOs und ins Frontend (PURCHASE = rotes „Kein Kurs"-Badge, stale = Amber, currency null =
      „unbekannt" statt CHF)
- [x] `PriceSyncJob` (22:30) + `PortfolioSnapshotJob` (23:00); Write-on-Read aus `getPortfolio()`
      entfernt, `writeSnapshot()` vom Job gerufen
- [x] `ch.finyo.sync`: `SyncRun`-Audit, `SyncRunner` (In-Process-Lock statt Advisory-Lock — siehe
      unten), `SyncAdminController` (`GET/POST /api/v1/admin/sync`)
- [x] `SourceResult<T>` generisch (ersetzt `LookupResult`) — trägt PR 4 (FX) und PR 6 (Steuer)
- [x] 875 Unit + 304 IT grün; Live-Check inkl. Kurspfad (SIX → Postgres → gelesen) grün;
      Migrationskette V1–V34 gegen frische DB verifiziert

**Zwei bewusste Abweichungen vom Plan (beim Bauen falsch geworden):**
- **Kein `pg_try_advisory_xact_lock`.** Der gilt nur für die Transaktionsdauer — der Lock hätte
  die gesamte Sync-Transaktion inkl. ~30 HTTP-Calls offen gehalten, exakt das Anti-Pattern, das
  PR 1 aus dem Lesepfad entfernt hat. Stattdessen prozessinterner `ReentrantLock` pro Job + kurze
  Audit-Transaktionen. Kommentar sagt, ab wann (Multi-Instance) das nicht mehr reicht.
- **`SyncRunner.record()` ohne `@Transactional`.** Selbstaufruf innerhalb der Bean → Spring-Proxy
  greift nicht. `repository.save()` bringt seine eigene Transaktion mit.

### PR 3 — Kurshistorie ✅ FERTIG (committet)
- [x] `SixHistoryAdapter` (`fqs/charts.json`, `netting=1440`) — Struktur `valors[].data.{Date[],Close[]}`,
      **NICHT** spaltenbasiert; `@JsonProperty("Date"/"Close")` nötig (Jackson 3 case-sensitiv, sonst
      still leere Historie)
- [x] `PriceBar`/`PriceHistoryProvider`, `MarketDataService.backfill` (Währung aus dem Quote, da
      charts.json keine liefert) + `priceHistory` (nur DB) + `refreshOrBackfillHeld` (Nachtlauf
      backfillt Bestandspositionen bei `countByIsin < 2`)
- [x] Backfill off-read-path: beim Position-Create (ein Call: Quote + Historie) und im Nachtlauf
- [x] `GET /positions/{id}/price-history` + `PriceHistoryChart` (Fremdwährung ohne CHF-Annahme,
      Empty-State, TZ-sichere Datumsformatierung)
- [x] 924 Unit + 316 IT + 680 FE grün; Live-Backfill (749 Nestlé-Tagesschlusskurse) grün

> **Aus PR-2-Review übernommen:** Das Frontend (`PositionsTable`, Value-Spalte) formatiert Kurs
> und Wert weiterhin hart als CHF via `formatCHF`, obwohl das Backend jetzt ein nullable
> `currency` pro Position liefert. Solange es keine FX-Umrechnung gibt, ist Mehrwährungs-Anzeige
> aber halbfertig — mit diesem PR die Value-Spalte in der jeweiligen Währung formatieren und für
> das gemischtwährige Total die Konvertierung nutzen.

### PR 4 — FX-Modul (repariert eine falsche Zahl)
Migration **V35**: `fx_rate(currency, rate_date, chf_per_unit, rate_type, source)`.

- [ ] `ch.finyo.common.money`: `Money`, `CurrencyCode`
- [ ] `ch.finyo.fx`: `FxConverter`, `FxRate`, `FxRateType`
- [ ] `FrankfurterFxAdapter` (invertiert!), `BazgFxAdapter` (Verkaufskurs, `OFFICIAL_CH`)
- [ ] Frankfurter self-hosted in `compose.yml` (`lineofflight/frankfurter`)
- [ ] `FxRateSyncJob` + Backfill (ein Call pro Jahr über den Range-Endpoint)
- [ ] `PortfolioService` konvertiert an der Aggregationsgrenze, `MoneyConversion` in die Response
- [ ] Fehlender Kurs (Wochenende/Feiertag) → **letzter Kurs ≤ Datum**, nie interpolieren

**Nutzen für sich:** USD- und EUR-ETFs werden heute wie CHF summiert. Das Portfoliototal ist damit
schlicht falsch — dieser PR repariert es.

### PR 5 — camt-Fremdwährung
Migration **V36**: `transaction.original_amount`, `original_currency`, `fx_rate`.

- [ ] `CamtParser.usableAmount()` verwirft `AmtDtls/TxAmt` nicht mehr
- [ ] Import konvertiert über `FxConverter` mit dem **Buchungsdatum** (nicht heute!)
- [ ] Original *und* konvertierten Betrag speichern
- [ ] `CsvImportService:182` — hardcodiertes `.currency("CHF")` durch die gemappte Spalte ersetzen

### PR 6 — ESTV-Stammdaten *(unabhängig von PR 1–5)*
Migration **V38**: `tax_deduction_limit(tax_year, code, value)`, `source` + `synced_at` auf den
`tax_*`-Tabellen.

- [ ] `EstvRateExportAdapter` (nur die zwei Export-Endpunkte — kein Budget-Tanz, simpel)
- [ ] `TaxRateSyncJob`: `API_exportManySimpleRates` + `API_exportManyDeductions`
- [ ] `DeductionLimitProvider` — **tötet die 3× hardcodierte 7258** (`TaxCalculationService:18`,
      `Pillar3CalculationService:20`, `Pillar3CompareService:38`)
- [ ] Die handgetippten Seeds V11/V15/V17 werden obsolet; `TODO-verify` in V15 stirbt

**Nutzen für sich:** Steuerfüsse aller 2'110 Gemeinden in **allen 26 Kantonen** statt nur SG.

### PR 7 — Kapitalbezugssteuer *(neues Feature)*
- [ ] `API_calculateManyCapitalTaxes` (Achtung: Ort geht in `TaxGroupID`, nicht `TaxLocationID`)
- [ ] 3a-Bezugsstaffelung im Pillar3-Modul

**Warum vor der Engine-Ablösung:** Rein additiv, erprobt die ESTV-Integration produktiv auf einem
unkritischen Pfad — *bevor* die Steuerrechnung darauf wettet.

### PR 8 — TaxEngine-Port + Shadow Mode
- [ ] `TaxEngine`-Port, `TaxCalculationService` → `LocalTaxEngine` (Rename, Verhalten unverändert)
- [ ] `EstvTaxEngine` mit ACL (der `PRAEMIEN3A`-Zweischritt lebt hier)
- [ ] `ShadowTaxEngine` (Decorator), Differenz-Metrik
- [ ] Replay über die bestehende `tax_scenario`-Tabelle

**Kein Nutzerverhalten ändert sich** — aber es *ist* das Sicherheitsnetz für PR 9.

### PR 9 — Umschaltung
- [ ] `FallbackTaxEngine` (ESTV → SG-lokal mit `degraded=true` → 503)
- [ ] Hardcodierte Vermögenssteuer (`TaxCalculationService:310`) raus
- [ ] **Erst mergen, wenn die Shadow-Deltas erklärt sind**

### PR 10 — EODHD
**Jetzt nicht bauen.** Der Plan ist der Wert, nicht der Code. Die Provider-Kette macht es zu drei
YAML-Zeilen, wenn der Tag kommt.

---

## Dokumentation

- [ ] **ADR-006** Ports & Adapters für externe Datenquellen (ArchUnit als Fitness Function)
- [ ] **ADR-007** Zweistufiges Caching — `instrument_price` vs. `portfolio_snapshot`
- [ ] **ADR-008** Money, Währung und FX — `plus()` wirft, `FxRateType` ohne Default
- [ ] **ADR-009** ESTV als primäre Steuerengine — Shadow Mode, Fallback nur für SG, EMBAG
- [ ] **ADR-004 ergänzen** — nennt heute SIX und News als Cache-Nutzer; News ist entfernt, SIX wird
      abgelöst. Supersede-Hinweis auf ADR-007.
- [ ] `docs/ARCHITECTURE.md`: der SIX-"personal use"-Schwellwert ist eine Architekturentscheidung
      mit Startup-Assertion, keine Fussnote

---

## Ausdrücklich nicht in Scope

**`Pillar3ReturnModel`** (`1% + Aktienquote × 0.05`). Die Recherche ist eindeutig: es gibt **keine
Quelle** für 3a-Fondsperformance, zu keinem Preis — die CSIF-Fonds von VIAC und finpension sind
nicht börsenkotiert (mit Kontrollgruppe bewiesen). Der ehrliche Fix ist kein Integrations-PR,
sondern ein Domänen-/UX-PR: die Formel in der UI als **Modellannahme kennzeichnen** und Basisrendite
plus Aktienprämie zu **sichtbaren Szenario-Parametern** machen statt zu versteckten Konstanten.
Der ganze 3a-Produktvergleich steht heute auf einer erfundenen Zahl, die sich als Berechnung
ausgibt. Das ist ein Vertrauensproblem, kein Datenproblem — **separat einplanen**.

**Positions-Historisierung** (`position_transaction` statt mutablem Durchschnittspreis). Notwendig
für eine lückenlose Wert-Historie, aber nicht durch die Datenquellen ausgelöst. Als bekannte
Limitation dokumentieren.

**3a-Fondskatalog automatisieren.** Existiert nicht, zu keinem Preis. Bleibt Admin-Handpflege —
**auf ISIN schlüsseln, nie auf den Fondsnamen** (die CS→UBS-Umbenennung ist aktiv).

**ICTax-Steuerwerte.** 403 hinter WAF, XML-Selfservice login-pflichtig. Bleibt manueller
Jahres-Import.

---

## Offene Punkte aus dem Review von PR 1

Bewusst **nicht** in PR 1 gezogen, damit der Scope hält — aber als Aufgabe, nicht als Absicht:

- [ ] **`SixLicenceCheck` prüft nur beim Start.** Ein zweiter Nutzer, der zur Laufzeit dazukommt,
      fällt bis zum nächsten Neustart nicht auf, und ein `log.error` beim Boot wird leicht
      übersehen. Bei der Nutzeranlage mitprüfen.
- [ ] **Der Legacy-Preis-Call läuft weiter in der Transaktion.** `PositionService.doCreate()` ruft
      `instrumentService.refreshPriceFromSix()` innerhalb der Transaktion. PR 2 löscht den Pfad
      komplett, damit erledigt es sich — aber bis dahin steht es.
- [ ] **Negative-Cache ist nur In-Memory** (Caffeine, 1 h). Ein persistenter Tombstone mit TTL wäre
      sauberer; über einen Neustart hinweg werden unlistete 3a-Fonds erneut bei beiden Anbietern
      angefragt.
- [ ] **`security_reference` hat kein TTL.** `retrieved_at` wird geschrieben, aber nie gelesen —
      eine einmal falsch aufgelöste Referenz bleibt es für immer. Sinnvoll spätestens mit PR 2,
      wo dieselbe Frage für Kurse ohnehin beantwortet werden muss.
- [ ] **Positions-Historisierung** (`position_transaction` statt mutablem Durchschnittspreis) —
      siehe «Nicht in Scope», unverändert gültig.

### ✅ Merge mit main erledigt (2026-07-15)

`cloud-doc-ingestion` wurde gemergt (V33 = document_tables). Dieser Branch auf main gemergt:
Migrationen umnummeriert (V34 = security_reference/currency, V35 = instrument_price/sync_run),
`SchedulingConfig` vereint (ein Scheduler, aktiv bei sync ODER graph, geteilter 2-Thread-Pool),
`AppConfig` zusammengeführt, ADRs umnummeriert (Ports = ADR-007, Provenienz = ADR-008; main behält
ADR-006 cloud-doc). 977 Unit + 335 IT + 685 FE grün, Migrationskette V1–V35 gegen frische DB
verifiziert. **Folge-PRs verschieben sich: FX = V36, camt = V37, ESTV = V38.**

---

## Entschieden (2026-07-14)

1. **Reihenfolge: Investment-Strecke zuerst** (PR 1 → 2 → 3 → 4 → 5). Die ESTV-Strecke (PR 6–9)
   folgt danach; sie ist unabhängig und könnte jederzeit dazwischengeschoben werden.
2. **Frankfurter wird self-hosted** (`lineofflight/frankfurter` in `compose.yml`). Keine Quota,
   kein Key im Repo, kein Rate-Limit beim Backfill historischer Kurse.
