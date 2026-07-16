# Externe Datenquellen für finyo

Analyse vom 2026-07-14. Alle Endpunkte wurden live geprüft, nicht aus Blogposts übernommen.
Ziel: manuelle Datenpflege durch automatisierte Abfragen ersetzen, wo das rechtlich und
technisch tragfähig ist — und ehrlich benennen, wo es das nicht ist.

## Zusammenfassung

| Bereich | Heute | Empfohlene Quelle | Kosten | Status |
|---|---|---|---|---|
| Wertpapier-Stammdaten (ISIN/Valor → Name, Typ, Währung) | manuell + Namens-Heuristik | **SIX FQS** `ref.json`, Fallback **OpenFIGI** / **ESMA FIRDS** | 0 | inoffiziell (SIX) / offiziell (OpenFIGI, ESMA) |
| Kurse ETF/Aktien (aktuell + Historie) | manuell / CSV / Kaufpreis | **SIX FQS** `movie.json` + `charts.json` | 0 | inoffiziell, 15 Min. Delay |
| Wechselkurse (Bewertung) | **existiert nicht** | **Frankfurter** (self-hosted) | 0 | offiziell, MIT |
| Wechselkurse (offiziell CH) | — | **BAZG** `/api/rates` | 0 | offiziell, CHF-Basis |
| Steuerfüsse aller Gemeinden | Flyway-Seed von Hand, nur SG | **ESTV** `API_exportManySimpleRates` | 0 | inoffiziell, aber vollständig |
| Steuerberechnung inkl. 3a | eigene Java-Implementierung, nur SG | **ESTV** `calculateDetailedTaxes` | 0 | inoffiziell |
| 3a-Maximalbeträge | hardcodiert (2×) | **ESTV** `API_exportManyDeductions` | 0 | inoffiziell |
| 3a-Zinssätze (Benchmark) | — | **SNB** Cube `zikrepro` / `GVG_S3A` | 0 | offiziell |
| 3a-Fondskatalog (ISIN, TER) | Admin-Handpflege | **keine Quelle vorhanden** | — | bleibt manuell |
| Steuerwerte Wertschriften (ICTax) | — | **keine nutzbare API** | — | bleibt manuell |
| Kontoauszüge | CSV / camt.053 Upload | kein Open Banking in CH verfügbar | — | bleibt Upload |

---

## 1. Ausgangslage im Code

Was der Codebase-Scan ergeben hat — relevant, weil einige Annahmen im Repo nicht stimmen:

- **`SixMarketDataClient` ist ein Platzhalter, keine Integration.** Der Pfad
  `/instruments/{id}/eod-closing-prices/latest` ist geraten, die Response-Feldnamen ebenso,
  `SIX_API_KEY` ist per Default leer. Ohne Key fällt die Preis-Kette in `PortfolioService`
  still auf `PURCHASE` zurück — das Portfolio zeigt dann Kaufpreis = aktueller Kurs und
  Gewinn/Verlust = 0, ohne sichtbaren Fehler.
- **Lookup-by-ISIN fehlt.** `PositionService.createInstrument()` legt Instrumente mit Name +
  ISIN an; die Anlageklasse wird von `AssetClassifier` aus dem Namens-String *geraten*.
- **`Instrument` hat kein Währungsfeld.** FX ist damit strukturell unmöglich; `CamtParser`
  verwirft Fremdwährungsbeträge deshalb bewusst, statt sie umzurechnen.
- **`Pillar3ReturnModel` erfindet die Rendite:** `1% + Aktienquote × 0.05`. Keine echte
  Fondsperformance — der gesamte Produktvergleich steht darauf.
- **Steuerdaten werden von Hand abgetippt** (V10/V11/V15/V17, teils mit `TODO-verify`),
  nur Kanton SG. Abzugslimiten und die Vermögenssteuer sind Java-Konstanten.

---

## 2. Wertpapiere: Identifikation und Kurse

### 2.1 SIX FQS — die einzige Gratis-Quelle mit echter Schweizer Abdeckung

Auth-frei, kein Key, kein beobachtetes Rate-Limit. Lookup per **ISIN, Valorennummer und Symbol**.

**Stammdaten:**
```
GET https://www.six-group.com/fqs/ref.json
    ?select=ISIN,ValorNumber,ValorSymbol,ShortName,ProductLine,IssuerNameFull,TradingBaseCurrency
    &where=ISIN=IE00B4L5Y983
→ ["IE00B4L5Y983", 10608388, "SWDA", "iSh Cor MSCI Wld USD A", "ET", "iShares III plc", "USD"]
```
`where=ValorNumber=3886335` → Nestlé. `ProductLine`: `BC` = Aktie, `ET` = ETF, `PF` = Publikumsfonds.

**Aktueller Kurs** (15 Min. verzögert):
```
GET https://www.six-group.com/fqs/movie.json
    ?select=ISIN,ShortName,ClosingPrice,PreviousClosingPrice,Currency,ValorSymbol
    &where=ISIN=IE00B4L5Y983
```

**Historie** (Tagesschlusskurse bis 2011):
```
GET https://www.six-group.com/fqs/charts.json
    ?select=ISIN,ClosingPrice&where=ISIN=IE00B4L5Y983&netting=1440&fromdate=20110101
→ 3'770 Punkte
```

**Grenze:** nur SIX-**kotierte** Instrumente. Nicht-kotierte Anlagefonds (u. a. die 3a-CSIF-Fonds)
liefern `totalRows: 0`.

**Lizenz — das ist der Haken:** SIX' Terms of Use sagen *"exclusively for personal use"* und
*"may not be reproduced or reused in any way or used for commercial purposes"*. Solange finyo
**Einzelnutzer** ist, vertretbar. **Sobald sich ein zweiter Mensch einloggt, ist die SIX-Route
rechtlich nicht mehr haltbar.**

### 2.2 OpenFIGI (Bloomberg) — lizenzrechtlich sauber, aber ohne Kurse

```
POST https://api.openfigi.com/v3/mapping
[{"idType":"ID_ISIN","idValue":"IE00B4L5Y983"}]
→ ticker SWDA/IWDA, name "ISHARES CORE MSCI WORLD", exchCode SW/LN/NA, securityType "ETP"
```
MIT-Lizenz, Speicherung und Weitergabe **ausdrücklich erlaubt**. Kostenloser Key: 25 req/6 s.
Kein Valor-Support, keine Kurse. API v2 wird am 01.07.2026 abgeschaltet — direkt v3 nehmen.

### 2.3 ESMA FIRDS — offizieller Gratis-Fallback

```
GET https://registers.esma.europa.eu/solr/esma_registers_firds/select?q=isin:CH0032912732&wt=json
→ Name, CFI-Typ, Währung, Handelsplatz-MIC, Emittenten-LEI
```
Nur EU-zugelassene Instrumente. Reproduktion mit Quellenangabe erlaubt. Keine Kurse.

### 2.4 Bezahlte Anbieter — die Free-Tiers sind für die Schweiz wertlos

| Anbieter | SIX-Abdeckung | ISIN-Lookup | Günstigste brauchbare Stufe |
|---|---|---|---|
| **EODHD** | ja (`.SW`, 1'619 Ticker) | ja | **$19.99/mo** (personal), kommerziell $399/mo |
| **Marketstack** | ja | nein | **$9.99/mo** — einziger mit explizitem Commercial-Grant |
| FMP | unklar | ja | $149/mo; Multi-User-Apps laut ToS verboten |
| Twelve Data | ab Pro | Add-on | $229/mo |
| Alpha Vantage, Finnhub, Tiingo, Polygon/Massive | **keine** | — | auf **keiner** Stufe kaufbar |
| Morningstar, LSEG | ja | ja | Enterprise-Sales, kein Self-Service |

### 2.5 Graue Zone — funktioniert, ist aber vertraglich untersagt

Yahoo Finance, justETF (`/api/etfs/{isin}/quote?currency=CHF`) und extraETF liefern technisch
saubere Daten inkl. CHF-Umrechnung — alle drei verbieten den automatisierten Abruf in ihren AGB.
Yahoo fingerprintet zusätzlich TLS (JA3); aus der JVM heraus gibt es dagegen kein Mittel
(yfinance umgeht das via `curl_cffi`, wofür kein Java-Äquivalent existiert). **Nicht einbauen.**

---

## 3. Wechselkurse

### 3.1 Frankfurter — für Bewertung und Charts

EZB-Referenzkurse (Mittelkurs), kein Key, MIT-Lizenz, **self-hostbar**:
```
docker run -d -p 8080:8080 lineofflight/frankfurter

GET https://api.frankfurter.dev/v1/latest?base=CHF&symbols=EUR
GET https://api.frankfurter.dev/v1/2025-01-01..2025-12-31?base=CHF&symbols=EUR   # Backfill
```
Self-hosted heisst: keine Quota, kein Key im Repo, keine Laufzeit-Abhängigkeit von Dritten.
Passt in die bestehende `compose.yml`.

**Zeitreihen mit v1 abfragen**, nicht v2 — die v2-Syntax wirft 422.

### 3.2 BAZG — der offizielle Schweizer Kurs

Offene, offizielle API der Zollverwaltung. **CHF als Basis**, kein Key, Historie ab 2010:
```
GET https://www.backend-rates.bazg.admin.ch/api/rates?d=20260714
→ {"date":"14.07.2026","base":"CHF","rates":[{"symbol":"EUR","rate":"0.93661"},...]}
```
**Achtung: das ist der Verkaufskurs, nicht der Mittelkurs.** 0.93661 (BAZG) vs. 0.9257 (EZB) —
gut 1.2 % Spread. Für die Bewertung von Fremdwährungsvermögen systematisch zu hoch. Als
offizielle Referenz kennzeichnen, nicht mit Mid-Kursen vermischen. Keine Range-Abfrage
(ein Request pro Tag) — für Backfill Frankfurter nehmen.

### 3.3 Zwei Fallen

- **Richtung:** Frankfurter mit `base=CHF` liefert *EUR pro 1 CHF* (1.0796). SNB und BAZG liefern
  *CHF pro 1 EUR* (0.9257). Beim Mischen zwingend normalisieren.
- **SNB-Faktor 100:** Die Dimension heisst `EUR1`, aber `JPY100`, `SEK100`, `DKK100` — die Werte
  sind pro 100 Einheiten. Blind übernehmen ergibt einen Faktor-100-Bug.

### 3.4 ICTax — kein Weg hinein

Die Steuerwerte von Wertschriften per ISIN wären der Volltreffer gewesen. Sie sind nicht
zugänglich:
- Das SPA-Backend (`/extern/api/security/search.json`, `/currency`) liefert **403 ohne
  Browser-Session** (WAF-geschützt).
- XML-Selfservice und EWS-Webservices sind **login-pflichtig**; die ESTV muss die E-Mail-Domäne
  erst freischalten, EWS ist vertraglich für Banken.
- Die Jahres-Kurslisten gibt es nur als **PDF** mit instabilen Hash-URLs.

**Konsequenz:** Jahresend-Steuerkurse und Wertschriften-Steuerwerte als **manuellen
Jahres-Import** modellieren (~10 Min. Aufwand pro Jahr), nicht als Live-Integration.

---

## 4. Steuern: die ESTV-API ist der grösste Hebel

Der ESTV-Steuerrechner hat ein **vollständig funktionierendes, unauthentifiziertes JSON-Backend**:

```
POST https://swisstaxcalculator.estv.admin.ch/delegate/ost-integration/v1/lg-proxy/operation/c3b67379_ESTV
```
Kein Key, kein beobachtetes Rate-Limit, Steuerjahre **2010–2026** verfügbar.

### 4.1 Steuerfüsse aller 2'110 Gemeinden

```
API_exportManySimpleRates   {"TaxYear":2026,"TaxGroupID":99}   → 1.1 MB JSON, alle Gemeinden
```
```json
{"IncomeRateCity":138, "IncomeRateCanton":105,
 "IncomeRateRoman":26, "IncomeRateProtestant":25, "IncomeRateChrist":24,
 "FortuneRateCity":138, "FortuneRateCanton":105,
 "Location":{"BfsID":3203,"BfsName":"St. Gallen","Canton":"SG"}}
```
Gemeinde-, Kantons- und Kirchensteuerfuss nach Konfession, dazu die Vermögenssteuer-Multiplikatoren,
**verschlüsselt auf die BFS-Nummer** — die finyo bereits in `tax_commune_multiplier` führt.
Die volle 17-Jahres-Historie sind 38'729 Zeilen in 17 Requests (7.6 s). Ein nächtlicher Sync ist trivial.

Das macht die handgetippten Flyway-Seeds (V11/V15/V17) und den `TODO-verify`-Platzhalter überflüssig
— **und liefert nebenbei alle 26 Kantone statt nur SG**.

### 4.2 Exakte Steuerberechnung inklusive 3a

Zwei Calls, in dieser Reihenfolge:

1. `API_calculateTaxBudget` **mit vollem Personenkontext** (Relationship, Confession, Age,
   RevenueType1, Revenue1 …) → liefert Positionen mit ESTVs eigenen Vorbelegungen
   (AHV 5'300, ALV 1'100, NBU 400, BVG 3'538, Krankenkasse 4'560 …), darunter
   **`PRAEMIEN3A` — "Beiträge an Säule 3a"**.
2. `API_calculateDetailedTaxes` — `PRAEMIEN3A` setzen und die **kompletten Objekte** als
   `Budget`-Array zurückschicken.

Verifiziert (SG 9000, ledig, evangelisch, 100k brutto, 2026): 3a = 7'258 → steuerbares Einkommen
sinkt um exakt 7'258, Steuer 16'522 → 14'298, **Ersparnis CHF 2'224**.

**Drei Fallen, die je Stunden kosten:**
- Der naheliegende Workaround — die API zweimal mit um den 3a-Betrag reduziertem Bruttolohn
  aufrufen — ist **falsch**: er ergibt 78'858 statt 77'915 steuerbares Einkommen (CHF 943 Fehler),
  weil AHV/ALV/NBU/BVG mit dem Bruttolohn mitskalieren.
- `API_calculateTaxBudget` **ohne** Personenkontext liefert eine kürzere Form **ohne** `PRAEMIEN3A`.
- `Budget` als blosse `{Ident, Value}`-Paare zu senden wird **stillschweigend ignoriert** — es
  müssen die vollständigen zurückgegebenen Objekte sein.

**Bonus:** `API_calculateManyCapitalTaxes` berechnet die **Kapitalbezugssteuer** (SG, Alter 64:
100k → CHF 6'433 / 6.43 %; 500k → CHF 39'981 / 8.00 %). Das ermöglicht die Staffelung von
3a-Bezügen — ein Feature, das finyo heute nicht hat.

Das ist der eigentliche Gewinn: **eine gepflegte, autoritative Schweizer Steuerengine für alle
26 Kantone, gratis** — statt 26 kantonale Steuergesetze selbst zu implementieren.

### 4.3 3a-Maximalbeträge

```
API_exportManyDeductions   {"TaxYear":2026,"TaxGroupID":88}
→ "Maximalabzug Säule 3a mit Vorsorgelösung" → 7258
→ "ohne Vorsorgelösung" → 36288
```
Müssen also **nicht** hardcodiert werden (heute doppelt: `Pillar3CompareService:38` und
`TaxCalculationService:18-19`). 2025 und 2026 sind identisch; seit der BVV-3-Revision von
Nov. 2024 enthält Art. 7 gar keine Frankenbeträge mehr — beide leiten sich vom BVG-Grenzbetrag
90'720 ab. Nächste mögliche Änderung: 1.1.2027.

### 4.4 Open Data: Fehlanzeige

- **Keine einzige Steuerbehörde publiziert auf opendata.swiss.** 162 Organisationen, null
  Steuerdatensätze. Es gibt **keinen schweizweiten Steuerfuss-Datensatz**.
- **Kanton St. Gallen: nichts.** `daten.sg.ch` hat 188 Datensätze, davon **null zu Steuern**.
  SG publiziert Steuerfüsse nur als PDF. Die ESTV-API liefert SG bereits mit — das PDF ist damit
  überflüssig.
- **Keine Java-Bibliothek existiert.** Vorarbeit gibt es in Go (`ruegerj/swiss-tax-api`) und
  TypeScript (`devbrains-com/swisstaxcalculator`). Der Java-Client sind ~3 Endpunkte.

### 4.5 Rechtliche Einordnung ESTV

`estv.admin.ch/robots.txt` erlaubt alles. Der Standard-Rechtshinweis von admin.ch verlangt
nominell eine schriftliche Zustimmung zur Reproduktion. Aber: Steuerfuss-Tabellen sind
**Faktendaten ohne Werkcharakter**, und die Schweiz kennt **kein Sui-generis-Datenbankrecht**
(anders als die EU). Das **EMBAG** (in Kraft seit 1.1.2024) verpflichtet die Bundesverwaltung
zudem zu "Open by Default".

Für ein selbst-gehostetes Einzelnutzer-Tool ist das Risiko vernachlässigbar. Empfehlung:
**Exporte lokal cachen, ESTV als Quelle nennen, Requestvolumen tief halten** (ein nächtlicher
Sync sind 17 Requests). Bei kommerzieller Mehrnutzer-Nutzung vorher eine kurze schriftliche
Mitteilung an die ESTV.

---

## 5. Säule 3a: was geht und was nicht

### 5.1 Die CSIF-Fonds sind nicht börsenkotiert — bewiesen

Gegen die SIX-API mit Kontrollgruppe getestet, unabhängig zweimal wiederholt:

| ISIN | | SIX |
|---|---|---|
| CH0038863350 | Nestlé (Kontrolle) | `totalRows: 1` |
| CH0017142719 | UBS ETF SMI (Kontrolle) | `totalRows: 1` |
| CH0214967314 | CSIF (CH) Equity World ex CH | **`totalRows: 0`** |
| CH0214967157 | CSIF (CH) Equity Switzerland | **`totalRows: 0`** |
| CH0337393745 | CSIF (CH) III Equity World ex CH ESG | **`totalRows: 0`** |

Die Kontrollen beweisen, dass der Endpunkt funktioniert — **0 Zeilen heisst also: wirklich nicht
kotiert**. Es sind Anlagestiftungs-/institutionelle Anteilsklassen. **Keine generische
ISIN-Kurs-API kann sie auflösen.** Auch justETF (404, nur ETFs) und swissfunddata.ch scheitern.

Der einzige funktionierende Gratis-Pfad ist ein zweistufiger Yahoo-Trick
(`v1/finance/search?q=<ISIN>` → Morningstar-Symbol `0P0001CB37.SW` → `v8/finance/chart/<symbol>`
→ echter NAV in CHF). Undokumentiert, rate-limitiert, historisch instabil, ToS-widrig.
Bestenfalls als Best-Effort-Anreicherung hinter einem Cache — **nie im Request-Pfad**.
**Die TER ist so ohnehin nicht zu bekommen.**

### 5.2 Kein Anbieter publiziert eine Fondsliste

VIAC, finpension, frankly, Selma, PostFinance — alle nur Marketing-HTML und PDF-Factsheets, echte
Allokationen hinter Login. (`app.finpension.ch/api/funds` gibt HTTP 200 zurück, ist aber die
Angular-SPA-Shell mit `content-type: text/html` — ein False Positive.)

**comparis.ch blockt automatisierte Requests aktiv mit HTTP 403.** moneyland.ch ist technisch
offen, aber die AGB waren nicht auffindbar (404) — die Vertragslage ist also **ungeklärt**, und
eine permissive robots.txt ist keine Zustimmung. Deren Zinsdaten sind ihr monetarisiertes
Kernasset; Weiterverwendung birgt UWG-Risiko unabhängig von robots.txt.

**Fazit: Der 3a-Fondskatalog bleibt manuell.** Es gibt keine Quelle — zu keinem Preis. Das ist
genau das Muster, das finyo mit dem Admin-Katalog und den 30 recherchierten Fonds bereits hat.

**Wichtig: auf ISIN schlüsseln, nie auf den Fondsnamen.** Die CS→UBS-Umfirmierung benennt diese
Fonds aktiv um ("Blue" → "Sel NSL") — Namensabgleich verrottet.

### 5.3 Was bei 3a doch automatisierbar ist

- **Zinssätze als Benchmark:** SNB-Cube `zikrepro`, Dimension `GVG_S3A` ("Gebundene Vorsorge
  Säule 3a") — Monatsmittel und Quartile (Mai 2026: Median 0.20 %). Offiziell, kein Key.
  Sagt aber **nicht**, was VIAC oder Migros Bank zahlen — Einzelbank-Zinsen haben keine API.
- **Maximalbeträge:** siehe 4.3.
- **Kapitalbezugssteuer:** siehe 4.2.

### 5.4 Offene Lücke im Fachlichen

Die neuen **rückwirkenden 3a-Einkäufe nach BVV 3 Art. 7a** (10-Jahres-Rückblick, Lücken ab 2025,
erstmals im Steuerjahr **2026** — also jetzt) sind in finyo heute nicht abgebildet. Fachlich sehr
wahrscheinlich in Scope.

---

## 6. Vorgeschlagene Umsetzungsreihenfolge

Nach Nutzen pro Aufwand:

1. **ISIN/Valor-Lookup im Investment-Modul** (SIX `ref.json` + OpenFIGI-Fallback).
   Andockpunkt: `PositionService.createInstrument()`. Ersetzt die `AssetClassifier`-Ratefunktion,
   füllt `name`, `ticker`, `valor`, `assetClass`, `currency`, `ter`.
   → Vorbedingung: **`currency`-Spalte auf `Instrument`** (neue Migration).
2. **Kurse aus SIX** (`movie.json`) statt des toten `SixMarketDataClient`. Tagesschlusskurse pro
   ISIN+Datum in Postgres cachen, nicht pro Request abrufen. Provider-Interface einziehen, damit
   ein Wechsel auf EODHD später eine Konfigurationsfrage bleibt.
3. **ESTV-Steuerfüsse synchronisieren** statt Flyway-Seeds. Bringt alle 26 Kantone und beseitigt
   den `TODO-verify`-Platzhalter in V15.
4. **FX-Service** (Frankfurter, self-hosted in `compose.yml`). Schaltet Fremdwährungs-Positionen
   frei und repariert die Lücke im camt-Import (`CamtParser.usableAmount()` verwirft heute
   Fremdwährungsbeträge).
5. **3a-Limiten aus ESTV** statt zweifach hardcodiert.
6. **ESTV als Steuerengine** — der grösste, aber auch invasivste Schritt: ersetzt
   `TaxCalculationService` inklusive der hardcodierten Vermögenssteuer.
7. **Kurshistorie** (`charts.json`) für echte Portfolio-Charts statt der lückenhaften
   `PortfolioSnapshot`-Reihe, die nur beim Login geschrieben wird.

### Architekturhinweise

- **Alle drei kritischen Quellen (SIX, ESTV, Yahoo) sind inoffiziell.** Jede gehört hinter ein
  Interface mit Cache und Circuit Breaker, damit ein Bruch die App degradiert statt lahmlegt.
  Der bestehende Caffeine-Cache (`CacheConfig`) ist der richtige Ort — für Kurse und Steuerdaten
  zusätzlich Postgres als persistenter Cache.
- **Der Multi-User-Schwellwert ist eine Architekturentscheidung, keine Rechtsfrage im
  Nachhinein.** Solange finyo Einzelnutzer ist, ist SIX vertretbar. Für den Tag, an dem sich ein
  zweiter Mensch einloggt, sollte der Provider-Switch auf EODHD ($19.99/mo personal, $399/mo
  kommerziell) oder Marketstack ($9.99/mo, Commercial inklusive) bereits eingebaut sein.

---

## Quellen

**Wertpapiere:** [SIX Terms of Use](https://www.six-group.com/en/services/legal/terms-and-conditions/terms-of-use.html) ·
[OpenFIGI API](https://www.openfigi.com/api/documentation) · [OpenFIGI FAQ](https://www.openfigi.com/about/faq) ·
[ESMA FIRDS](https://registers.esma.europa.eu/) · [EODHD](https://eodhd.com/pricing) · [Marketstack](https://marketstack.com/product)

**Wechselkurse:** [BAZG Rates](https://www.backend-rates.bazg.admin.ch/api/rates) ·
[ESTV MWST-Fremdwährungskurse](https://www.estv.admin.ch/de/mwst-fremdwaehrungskurse) ·
[ESTV Kurslisten / ICTax](https://www.estv.admin.ch/de/kurslisten-ictax) ·
[Frankfurter](https://frankfurter.dev/) · [Frankfurter GitHub](https://github.com/lineofflight/frankfurter) ·
[ECB Data API](https://data-api.ecb.europa.eu/)

**Steuern:** [ESTV Steuerrechner](https://swisstaxcalculator.estv.admin.ch/) ·
[SNB Datenportal](https://data.snb.ch/en/help_api) · [BFS Gemeindeverzeichnis](https://agvchapp.bfs.admin.ch/) ·
[opendata.swiss CKAN](https://ckan.opendata.swiss/api/3/action/package_search) ·
[EMBAG](https://www.fedlex.admin.ch/eli/cc/2023/682/de)

**Vorarbeit:** [ruegerj/swiss-tax-api](https://github.com/ruegerj/swiss-tax-api) (Go) ·
[devbrains-com/swisstaxcalculator](https://github.com/devbrains-com/swisstaxcalculator) (TypeScript)
