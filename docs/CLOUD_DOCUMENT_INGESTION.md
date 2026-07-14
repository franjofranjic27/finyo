# Cloud Document Ingestion

finyo can watch a SharePoint document library and import tax documents from it
automatically: classify them, extract the field values, and either write them into
the tax year or put them in a review inbox.

The files stay in SharePoint. finyo stores metadata, a reference and the extraction
result — never a copy of the document.

---

## Before anything else: OCR

`PdfTextExtractionService` reads the **text layer** of a PDF. A scan without OCR has
none, and is rejected with a clear error (`FAILED`, "no extractable text").

If you scan documents (ScanSnap or similar), **enable OCR / "searchable PDF" in the
scanner software**. It costs nothing and is the difference between the feature
working and not working for scanned documents. Documents downloaded from a bank or
an insurer already carry a text layer.

---

## One-time setup

### 1. Register the application in Entra ID

In the Microsoft Entra admin center → *App registrations* → *New registration*:

- Name: `finyo-document-ingestion`
- Supported account types: *Accounts in this organizational directory only*
- No redirect URI (this is a daemon app, there is no user sign-in)

Note down **Directory (tenant) ID** and **Application (client) ID**.

Under *Certificates & secrets*, create a client secret and note its **value**
(it is shown only once). Secrets expire after at most 24 months — put the expiry in
your calendar, or switch to a certificate credential.

### 2. Grant access to one site only

Under *API permissions* → *Add a permission* → *Microsoft Graph* →
**Application permissions** → `Sites.Selected` → *Grant admin consent*.

`Sites.Selected` grants **nothing** on its own. Access is handed out per site,
which is exactly the point: the app can reach the one library you name and nothing
else.

> Do not use `Files.Read.All`. That is read access to *every* OneDrive and every
> site in the tenant, granted to a self-hosted service. `Sites.Selected` gives the
> same capability for one site.

Then grant the app read access to the site (as a tenant admin, e.g. via Graph
Explorer):

```http
POST https://graph.microsoft.com/v1.0/sites/{site-id}/permissions
Content-Type: application/json

{
  "roles": ["read"],
  "grantedToIdentities": [{
    "application": { "id": "<client-id>", "displayName": "finyo-document-ingestion" }
  }]
}
```

You can find `{site-id}` with:
`GET /sites/{hostname}:/sites/{site-name}` — for example
`GET /sites/contoso.sharepoint.com:/sites/Finanzen`.

### 3. Find the drive id

```http
GET https://graph.microsoft.com/v1.0/sites/{site-id}/drives
```

Take the `id` of the document library you want to ingest from.

### 4. Configure finyo

Environment variables for the backend:

```bash
GRAPH_ENABLED=true
GRAPH_TENANT_ID=<directory-tenant-id>
GRAPH_CLIENT_ID=<application-client-id>
GRAPH_CLIENT_SECRET=<secret-value>
```

Without `GRAPH_ENABLED=true` no Graph bean is created at all and no sync runs — that
is the default, including in tests.

### 5. Register the folder

Cloud folders are operator-configured, not user-configured (granting site access
needs an Entra admin, so there is nothing an end user could set up alone). Create
the source once via Swagger (`/swagger-ui.html`) as an admin:

```http
POST /api/v1/admin/document-sources
{
  "driveId": "<drive-id>",
  "rootFolderPath": "/Steuern",
  "enabled": true
}
```

`rootFolderPath` is a prefix filter applied on our side: Graph's delta feed returns
the whole drive and cannot filter by path.

---

## Folder convention

The folder path tells finyo the year and the type of a document *before* anything
looks inside it:

```
/Steuern/STE-2025/Lohnausweise/lohnausweis-2025.pdf
         ^^^^^^^^ ^^^^^^^^^^^^
         year     type
```

Configured under `finyo.documents` in `application.yaml`:

| Folder           | Document type          |
|------------------|------------------------|
| `Lohnausweise`   | `SALARY_CERTIFICATE`   |
| `Krankenkasse`   | `HEALTH_INSURANCE`     |
| `Wertschriften`  | `SECURITIES_STATEMENT` |
| `Saeule3a`       | `PILLAR_3A`            |
| `Veranlagung`    | `ASSESSMENT`           |

Folder names are matched case- and umlaut-insensitively (`Säule 3a` == `saeule3a`).
The year comes from the pattern `STE-(20\d\d)`.

---

## When does finyo apply a value on its own?

Only when **all** of these hold:

1. The folder type and the detected type agree.
2. The folder year and the year named in the document agree.
3. The target field in the tax year is still empty.
4. The tax year is still `OPEN` — a filed or assessed year is never touched by a
   background job.

Otherwise the document goes to the inbox as `NEEDS_REVIEW` and waits for you.

Two of these deserve an explanation.

**The year check** is what stops the worst thing this feature could do: a 2024 salary
certificate accidentally filed under `STE-2025/` would otherwise silently overwrite
your 2025 income.

**The classifier's confidence is never used as a threshold.** The score is normalized
per document type against the sum of that type's keyword weights, and those maxima
differ (salary certificate 10, assessment 14). A score of 0.5 therefore means
something different for each type, and a global threshold would lock some types out
of automation permanently. The folder — which you maintain deliberately — is the
better signal.

---

## The library's access list is now a security boundary

Anyone who can **write** into the watched folder can put a document in front of the
importer. The guards above (folder/type/year must agree, empty fields only, an
already filed year is left alone) keep that from quietly rewriting an existing
figure — but the first document of a fresh year lands in an empty field, and that
is exactly the case auto-apply is built for.

So: keep the library private, and do not hand out edit rights or sharing links to it
casually. Read-only sharing is fine. If several people share one library, give each
their own source folder — the folder scope is enforced on folder boundaries, so
`/Steuern/Anna` never picks up `/Steuern/Anna2`.

A source always belongs to the admin who registers it; there is no way to point one
at another user's tax year through the API.

## Operating it

- The sync runs every 15 minutes (`GRAPH_SYNC_INTERVAL`, default `PT15M`) and can be
  triggered from the UI ("sync now") or via `POST /api/v1/documents/sync`.
- **Unchanged files are not downloaded again.** Graph's `cTag` changes only when the
  content changes, and that is what triggers re-processing.
- **A failed document repairs itself.** Upload an OCR'd version over a scan that
  failed: the `cTag` changes, and the next sync processes it again. No retry button
  needed.
- **Deleting a file in SharePoint never revokes a value already applied.** The
  document stays as the audit trail for where that number came from.
- Transient trouble (a busy parser, a Graph 5xx) does not fail a document — it is
  retried on the next run, and only given up on after five attempts.

---

## Why polling and not webhooks

Graph change notifications for files carry no detail: they say "something changed",
so a delta query has to follow anyway. Subscriptions also expire after about 30 days
and need renewing, and a missed notification means a document silently never arrives.

A 15-minute poll is self-healing: whatever one run misses, the next one picks up. A
webhook could be added later purely to cut latency, but it must never be the thing
correctness depends on.
