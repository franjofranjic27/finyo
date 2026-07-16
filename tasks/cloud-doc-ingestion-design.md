# Cloud-Dokumentenablage → finyo: Architekturvorschlag

**Status:** Vorschlag (2026-07-14) · **Branch:** `worktree-cloud-doc-ingestion`

## Ziel

Dokumente, die in OneDrive/SharePoint landen (manuell hochgeladen oder per ScanSnap
gescannt), sollen ohne weiteres Zutun in finyo erscheinen und den richtigen
Steuerjahr-Feldern zugeordnet werden. Ordnerkonvention: `.../Finanzen/Steuern/STE-2025/Lohnausweise/…`

---

## 1. Ausgangslage im Code (erhoben, nicht vermutet)

| Baustein | Stand heute |
|---|---|
| `ch.finyo.taxdocument` | **Preview-only.** Javadoc: *"Uploaded files are processed in memory only and never persisted."* |
| Dokument-Persistenz | **existiert nicht** — kein Entity, keine Tabelle, kein Blob-Store |
| Klassifizierung | `DocumentClassifier`: gewichtetes Keyword-Scoring, Threshold 0.25, sonst `UNKNOWN`. Kein ML/LLM |
| Extraktion | `TaxDocumentExtractor`-Strategy (5 Impls), rein Regex/zeilenbasiert |
| PDF-Parsing | PDFBox 3.0.5, in-memory, Semaphore mit **2 parallelen Slots**, Scan-Heuristik → 422 |
| Dedup | Für Dokumente **keins**. Für Transaktionen: `external_ref` + partieller Unique-Index (V31) |
| Scheduling / Async / Queue | **nichts.** Null Treffer für `@Scheduled`, `@Async`, Outbox, Quartz, ShedLock |
| Secrets externer APIs | Muster existiert: `SixMarketDataProperties` (`@ConfigurationProperties`, Env-Var) |
| Höchste Migration | **V32** → neue Migration wäre **V33** |

**Konsequenz:** Es fehlen drei Bausteine, und der Cloud-Connector ist nur einer davon:
1. Dokument-Persistenz (Katalog + Status)
2. Asynchrone Verarbeitung (Scheduler + Worker)
3. Der Cloud-Connector selbst

---

## 2. Der Elefant im Raum: gescannte PDFs sind heute **nicht** verarbeitbar

`PdfTextExtractionService` wirft bewusst **422** bei Dokumenten mit zu wenig Text
(Scan-Heuristik: < max(200, 100×Seiten) Zeichen). Ein ScanSnap-Bild-PDF ohne OCR hat
**null** extrahierbaren Text. Der gesamte Extraktions-Stack (Keyword-Classifier + Regex)
läuft auf `PDFTextStripper` — er ist blind für Pixel.

**Ohne OCR ist der Scan-Teil des Workflows tot**, egal wie gut der Connector ist.

Drei Wege, in Reihenfolge der Vernunft:

1. **ScanSnap Home auf durchsuchbare PDFs umstellen** (OCR beim Scannen). Ein Häkchen in
   der Software, kostet null Code, löst das Problem an der Quelle. **Das würde ich zuerst tun.**
2. **OCR-Sidecar** (OCRmyPDF/Tesseract als eigener Container), den die Pipeline bei
   Text-Armut vorschaltet. Sauber, aber ein weiterer Service im Compose-Stack.
3. **Cloud-OCR** (Azure Document Intelligence o.ä.) — beste Qualität, aber Steuerdokumente
   verlassen die eigene Infrastruktur. Für Lohnausweise/Veranlagungen heikel.

Weiterer Realismus-Punkt: Die Extractors sind gegen **vollsynthetische** Fixtures getestet.
Gegen echte Belege der Swisscanto/Helvetia/kantonalen Steuerämter werden sie zunächst
schlecht treffen. Das ist kein Architektur-, sondern ein Kalibrierungsproblem — es
spricht aber massiv für eine **Review-Inbox statt Blind-Import** (siehe §4).

---

## 3. Kernidee der Architektur

> Der Cloud-Ordner ist **keine** Import-Trigger-Quelle, sondern eine **Dokumentquelle**.
> finyo führt einen eigenen Dokument-Katalog; Quellen liefern hinein, die Pipeline arbeitet ihn ab.

Das entkoppelt Microsoft von finyo. Der bestehende manuelle Upload wird dabei einfach
eine weitere Quelle und schreibt in denselben Katalog — heute sind das zwei getrennte Silos.

```
┌─────────────────┐
│ DocumentSource  │  Interface: listChanges(cursor) → Page<RemoteDocument>
│  (Abstraktion)  │             fetch(itemId)       → byte[]
└────────┬────────┘
         ├── GraphDocumentSource     (OneDrive/SharePoint, Delta-Query)
         ├── ManualUploadSource      (der heutige Upload-Dialog)
         └── … (Nextcloud/WebDAV/lokal später, ohne Pipeline-Änderung)
                     │
                     ▼
         ┌───────────────────────┐
         │  document (V33)       │  Katalog + Status-Maschine
         └───────────┬───────────┘
                     ▼
   DISCOVERED → FETCHED → CLASSIFIED → EXTRACTED → ┬→ NEEDS_REVIEW → (User bestätigt) ─┐
                                                    └→ AUTO_APPLIED ───────────────────┤
                                                                                        ▼
                                                                                   TaxYear-Felder
```

### 3.1 Datenmodell (Migration V33)

`document`
- `id`, `user_id` (Row-Level-Tenancy wie überall)
- `source_type` (GRAPH | MANUAL | …), `source_item_id`, `source_path`, `source_etag`
- `filename`, `size`, `mime_type`, `content_hash` (SHA-256)
- `detected_type` (`TaxDocumentType`), `confidence`, `tax_year`
- `status`, `failure_reason`
- `extracted_fields` **JSONB** (die bestehende `List<ExtractedField>`)
- `created_at`, `updated_at`

`document_sync_state` — pro (user, source): der Graph-**deltaLink** als Cursor, `last_sync_at`.

**Dedup** — zwei Ebenen, analog zum bewährten Transaktions-Muster:
- Partieller Unique-Index auf `(user_id, source_item_id) WHERE source_item_id IS NOT NULL`
- Unique auf `content_hash` pro User → fängt den Fall "ScanSnap scannt dasselbe Blatt nochmal
  unter neuem Dateinamen", den eine reine ID-Prüfung durchlässt.

### 3.2 Datei-Inhalt: **nicht** in finyo speichern

SharePoint ist bereits die Ablage — und zwar die, der du vertraust und die gebackupt wird.
finyo speichert nur Metadaten, Hash, Quell-Referenz und das Extraktionsergebnis. Das Original
wird bei Bedarf (Vorschau) per Graph nachgeladen.

Spart DB-Grösse, hält Backups klein, vermeidet eine zweite Kopie von Steuerdokumenten auf
dem Hetzner-Server. (Das Repo *hätte* mit `InstrumentFactsheet`/`BYTEA` (V21) ein Muster für
Blobs — hier ist es aber schlicht nicht nötig.)

### 3.3 Ordnerkonvention als Kontext-Hint — der eigentliche Trick

Der Pfad `STE-2025/Lohnausweise/xyz.pdf` trägt zwei Informationen, die der Keyword-Classifier
mühsam erraten muss:
- **Steuerjahr** aus `STE-(20\d\d)`
- **Erwarteter Typ** aus dem Unterordner (`Lohnausweise` → `SALARY_CERTIFICATE`)

Als konfigurierbare `FolderConventionRules` (nicht hart kodiert) verwendet, hebt das die
Trefferquote deutlich und heilt genau die Schwäche des `UNKNOWN`-Fallbacks. Zusätzlich wird
der Pfad-Hint zum **Kreuz-Check**: Ordner sagt Lohnausweis, Classifier sagt Krankenkasse →
das ist ein starkes Signal für `NEEDS_REVIEW`, nicht für Auto-Apply.

### 3.4 Verarbeitung: Delta-Polling, nicht Webhook

Microsoft Graph liefert bei `driveItem`-Subscriptions **keine Details** — die Notification sagt
nur "irgendwas hat sich geändert", man muss danach ohnehin die Delta-Query aufrufen. Ausserdem:
Subscriptions laufen nach max. ~30 Tagen ab (Renewal-Job nötig), brauchen einen öffentlichen,
unauthentifizierten Validation-Endpunkt, und ein verpasstes Event bedeutet ein verlorenes Dokument.

→ **Delta-Query im `@Scheduled`-Job (alle 15 min) ist der Kern.** Er ist selbstheilend: ein
Ausfall wird beim nächsten Lauf einfach nachgeholt. Ein Webhook kann später als reiner
*Beschleuniger* dazukommen (Stufe 2), muss aber nie die Korrektheit tragen.

**Wichtig fürs Betriebsverhalten:** Der PDF-Parser hat nur **2 Semaphore-Slots**. Der Sync-Job
muss durch einen eigenen, auf 1 Thread begrenzten Executor laufen — sonst hungert ein Import
von 30 Scans die interaktiven Uploads aus. Single-Instance-Deployment ⇒ `@Scheduled` genügt,
ShedLock erst bei mehreren Replicas.

---

## 4. Review-Inbox statt Blind-Import

Der Wunsch ist "ich muss mich um nichts kümmern". Die ehrliche Antwort: **bei einer
Steuererklärung willst du das nicht zu Ende gedacht haben.** Ein falsch extrahierter
Bruttolohn, der ungeprüft ins Formular wandert, kostet mehr als ein Klick.

Vorschlag — automatisch bis zur Extraktion, dann differenziert:

- **Auto-Apply**, wenn Pfad-Hint **und** Classifier übereinstimmen **und** Confidence hoch ist.
  Pro Dokumenttyp opt-in schaltbar.
- **`NEEDS_REVIEW`** sonst. Die UI zeigt ein Badge ("3 neue Dokumente"), der Review ist ein
  Zwei-Klick-Vorgang mit der bereits existierenden Feld-Vorschau aus `TaxDocumentUploadDialog`.

Damit ist der Alltag "ich scanne, es erscheint, ich nicke es ab" — statt "ich scanne und hoffe".

---

## 5. Zugriff & Berechtigungen (der heikelste Teil)

| Option | Bewertung |
|---|---|
| **A. Dedizierte SharePoint-Bibliothek + `Sites.Selected` (app-only)** | **Empfohlen.** App-Registrierung in Entra ID, Client-Credentials-Flow. `Sites.Selected` beschränkt die App auf **genau diese eine Site** — sonst nichts. Kein User-Token, kein Refresh-Ablauf, läuft headless im Scheduler. Du bist Tenant-Admin (FRAMA), kannst das selbst vergeben. |
| B. Persönliches OneDrive, delegated Flow | Refresh-Token muss persistiert und rotiert werden; stirbt bei Passwortwechsel/CA-Policy → der Job fällt **still** aus. Genau das Gegenteil von "um nichts kümmern". |
| C. `Files.ReadWrite.AppFolder` | Least-Privilege perfekt, aber die App bekommt einen *eigenen* Ordner — du müsstest deine gewachsene Struktur aufgeben. Widerspricht dem Ziel. |
| D. `Files.Read.All` (app-only) | **Nein.** Das ist Lesezugriff auf *sämtliche* OneDrives des Tenants, für einen self-hosted Dienst auf einem Hetzner-Server. Unverhältnismässig. |

→ **Empfehlung A**: `Privat/Finanzen` in eine SharePoint-Bibliothek (z.B. „finyo-Dokumente") legen.
Der ScanSnap kann direkt dorthin scannen, der OneDrive-Sync-Client hält sie lokal verfügbar —
für dich ändert sich im Alltag praktisch nichts, aber der Zugriffs-Scope wird sauber.

**Secrets:** `finyo.graph.tenant-id / client-id / client-secret` als `@ConfigurationProperties` +
Env-Vars — exakt das Muster, das `SixMarketDataProperties` schon vorlebt.
Client-Secrets laufen nach max. 24 Monaten ab → **Certificate Credentials** vorziehen, sonst
steht der Sync eines Tages ohne Vorwarnung.

---

## 6. Geprüfte Alternativen

1. **Power Automate als Brücke.** Flow: „Datei in Ordner erstellt → HTTP POST an finyo".
   Kein Graph-Code, kein Secret in finyo, keine Delta-Logik — Microsoft triggert.
   *Sehr attraktiv als schneller Einstieg.* Aber: kein Backfill bestehender Dateien, Flow-Fehler
   sind schlecht sichtbar, Vendor-Lock — und der HTTP-Connector ist **Premium**, in M365 Business
   typischerweise nicht enthalten. **Lizenzkosten vorher prüfen.**
2. **Lokaler Sync-Ordner + Filesystem-Watcher.** finyo läuft auf Hetzner, nicht auf deinem PC —
   bräuchte einen Agent auf deinem Rechner, der hochlädt. Mehr bewegliche Teile, läuft nur wenn
   der PC an ist. Nur sinnvoll, wenn du der Cloud bewusst *keinen* API-Zugriff geben willst.
3. **rclone/WebDAV-Backend.** Cloud-agnostisch (auch Dropbox/Nextcloud), kein Graph-Code.
   Aber Token-Handling ist auch dort nicht gratis, kein sauberes Delta, Betriebskomplexität im Container.
4. **Paperless-ngx davorschalten.** Es kann Consume-Folder, OCR, Tagging, Volltext — und hat eine
   REST-API, die finyo abfragen könnte. Ernsthaft erwägenswert, *wenn du ohnehin ein DMS willst*.
   Ein zweites System zu betreiben, nur um finyo zu füttern, ist es sonst nicht wert.
5. **E-Mail-Ingestion.** Simpel, passt aber nicht zum ScanSnap→OneDrive-Workflow.

---

## 7. Stufenplan

**Stufe 0 — Fundament (der grosse Brocken, quellen-unabhängig)**
`document`-Tabelle (V33), Status-Maschine, Dedup-Hash, Review-Inbox-UI, Wiederverwendung der
bestehenden Extractors. Der heutige manuelle Upload schreibt in dieselbe Inbox.
*Ohne das bringt jeder Connector nichts — es gibt keinen Ort, an den er liefern könnte.*

**Stufe 1 — Quelle**
`DocumentSource`-Abstraktion + `GraphDocumentSource` (Delta-Query, `Sites.Selected`),
`@Scheduled`-Sync alle 15 min, Ordnerkonvention als Typ/Jahr-Hint, gedrosselter Worker.

**Stufe 2 — Komfort**
Auto-Apply bei hoher Confidence, Webhook als Latenz-Optimierung, OCR-Sidecar (falls ScanSnap-OCR
nicht reicht), weitere Sources.

**Parallel & vorab, ohne Code:** ScanSnap auf durchsuchbare PDFs (OCR) umstellen.

---

## Offene Entscheidungen

1. SharePoint-Bibliothek + `Sites.Selected` — oder anderer Weg?
2. Review-Inbox als Default, Auto-Apply opt-in — oder aggressiver?
3. Datei-Blobs wirklich nicht in finyo spiegeln?
4. Umfang jetzt: nur Stufe 0, oder 0+1 durchziehen?
