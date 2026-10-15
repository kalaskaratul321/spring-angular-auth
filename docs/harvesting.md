# Bulk harvesting from eCourts / court websites

Imports millions of judgments from official sites **oldest first**, then keeps
up with new uploads on a schedule. Only metadata and the source URL are kept
(see `knowledge-graph.md`, "Importing from official websites").

## How it works

```
 HarvestSource (config + checkpoint)          kg_harvest_sources
        |  discovery (cron, one instance per source via lease)
        v
 date windows, oldest -> newest  --links-->  kg_harvest_tasks (unique dedupKey)
                                                    |  workers (every instance, leased claims)
                                                    v
                                        JudgmentImportService -> kg_cases (+ source.url, sha256)
```

**Old -> new, incrementally.** Each source walks date windows from `startDate`
forward (`windowDays` per window, at most `maxWindowsPerRun` windows per run),
saving `nextWindowStart` after every window. Once it reaches the last
`overlapDays` days it is in `INCREMENTAL` mode: every scheduled run re-reads
the last `overlapDays` days (late uploads) plus any days since the previous
run, so documents added after the last schedule are picked up. If the service
was down for weeks, the gap is covered automatically. The queue is worked
oldest window first.

**No duplicates**, at four levels:
1. Task `dedupKey` = hash of the normalised URL (host case, default port,
   fragment, `./`, http vs https ignored); enqueue is an atomic upsert on a
   unique index, so overlapping windows, re-runs, several instances or two
   sources listing the same file never create a second task.
2. URLs already imported (`source.url`) are not queued.
3. The same file under a different URL is detected by SHA-256 and reported as
   `DUPLICATE` without saving.
4. Judgments with the same CNR update the same case.

**Failover.**
- Any number of instances can run with `kg.harvest.enabled=true`.
- Discovery of a source is guarded by a lease on the source document; tasks
  are claimed with `findAndModify` and a lease (`kg.harvest.lease-seconds`).
  If an instance dies, its leases expire and other instances carry on.
- A task only completes if its lease is still held by the same instance.
- A crash during discovery loses at most one window, which is re-read on the
  next run (its links de-duplicate).
- Failed imports retry with exponential backoff and jitter
  (`retry-base-seconds` doubling up to `retry-max-seconds`, `max-attempts`).
  Permanent failures (404/403, host not allowed, unknown court code) and
  exhausted retries go to `DEAD` for review and can be re-queued.
- A failing source (listing site down) backs off up to 6 hours and resumes
  from its checkpoint.

**Politeness.** Requests to the same host are spaced by
`kg.import.per-host-delay-ms` (default 1 s) per instance. At 1 request/second
a single host yields roughly 80,000 documents a day per instance; plan
backfills (and the number of instances) accordingly and check each site's
terms of use / robots.txt.

## Configuration (`application.properties`)

| Property | Default | |
|---|---|---|
| `kg.harvest.enabled` | `false` | Turn on scheduling on this instance |
| `kg.harvest.discovery-cron` | `0 7 */6 * * *` | Discovery schedule (Asia/Kolkata) |
| `kg.harvest.worker-delay-ms` | `30000` | Pause between worker polls when the queue is empty |
| `kg.harvest.worker-batch` | `50` | Tasks per worker batch |
| `kg.harvest.max-attempts` | `6` | Attempts before a task is DEAD |
| `kg.harvest.lease-seconds` | `900` | Lease on a task / source |
| `kg.harvest.retry-base-seconds` / `retry-max-seconds` | `300` / `43200` | Backoff |
| `kg.import.per-host-delay-ms` | `1000` | Gap between requests to one host |

## Defining a source

Sources are configured through the API, because every court site lays out its
listings differently. `urlTemplate` placeholders: `{from}`, `{to}` (formatted
with `dateFormat`) and `{page}`. `linkSelector` is a CSS selector and
`linkPattern` a regex the document URL must match.

```bash
curl -XPOST localhost:8080/api/kg/harvest/sources -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "name": "example-court-judgments",
    "type": "HTML_LISTING",
    "urlTemplate": "https://<official-host>.gov.in/judgments?from={from}&to={to}&page={page}",
    "dateFormat": "dd-MM-yyyy",
    "linkSelector": "table a[href]",
    "linkPattern": "(?i)\\.pdf(\\?.*)?$",
    "startDate": "1990-01-01",
    "windowDays": 7,
    "overlapDays": 10,
    "maxWindowsPerRun": 30,
    "defaultCourtCode": "BHC-MUM"
  }'
```

A `URL_LIST` source (`"type": "URL_LIST", "urls": [...]`) queues a fixed list
once, e.g. an exported index.

**Limits.** The listing connector reads plain GET HTML pages. Portals that
require a captcha, a login or a POST/JavaScript search (the eCourts judgment
search is one) cannot be crawled this way; use their direct document links
in a `URL_LIST`, an official bulk-data channel, or add a connector for that
site (implement `SourceConnector`). Scanned PDFs without a text layer are
imported with metadata only (no OCR).

## Search-form sources (`SEARCH_FORM`)

For sites whose judgments are only reachable through a search form, the
`SearchFormConnector` submits the form once per date window, like a browser:

- loads `formPageUrl` with a cookie session and picks the form (`formSelector`,
  or the first form with From/To date fields);
- finds the date inputs from their name, id, placeholder, class, `<label>` or
  the neighbouring table cell ("From Date" / "To Date"), unless `fromField` /
  `toField` are set; `<input type=date>` gets ISO dates, others `dateFormat`;
- sends what a browser sends: hidden fields (CSRF tokens), selected options,
  checked boxes and the submit button, overridden by `extraFields`
  (e.g. `{"bench": "N"}`); the form page is reloaded before every submission
  so single-use tokens stay valid;
- collects links matching `linkSelector` / `linkPattern` (title hint = the
  result row text) and follows `nextPageSelector` links up to `maxPages`;
- when the page matches `tooManyResultsPattern`, splits the window in half,
  down to a single day (then fails, asking for a narrower `extraFields`);
- refuses forms with a captcha (image/recaptcha/`captcha` fields) with a clear
  error instead of retrying them.

Not supported: results loaded by JavaScript/AJAX after the page loads, and
"next page" buttons that re-POST the form (ASP.NET `__doPostBack`). Both need a
site-specific connector.

## Bombay High Court

`bombay-high-court-judgments` is seeded (disabled) as a `SEARCH_FORM` source:

| Setting | Value | Why |
|---|---|---|
| `formPageUrl` | `https://bombayhighcourt.nic.in/ord_qry.php` | The court's unified order/judgment search, which has From Date / To Date fields |
| date fields | auto-detected | Field names could not be inspected from the build environment |
| `dateFormat` | `dd-MM-yyyy` | Indian date format used on the site; change if the preview shows otherwise |
| `linkSelector` / `linkPattern` | `a[href*=generatenewauth.php]` / `generatenewauth\.php\?bhcpar=` | Judgment PDFs are served this way |
| `tooManyResultsPattern` | `too many results/records`, `refine your search` | The site asks to refine large searches |
| `startDate`, `windowDays`, `overlapDays` | 2000-01-01, 7, 10 | Adjust as needed |
| `defaultCourtCode` | empty | The parser assigns the bench from each judgment |

Judgment links carry a changing `spassphrase` token, so they are de-duplicated
by the file path + name decoded from `bhcpar`; a queued link that expired is
replaced by the fresh link on the next discovery.

The site also runs on `bombayhighcourt.gov.in` (`/bhc/judgments`). If the
`.nic.in` form moves or stops working, point `formPageUrl` there and preview
again.

**Activate it** (from a machine that can reach the court site):

```bash
ID=$(curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/kg/harvest/sources \
  | jq -r '.[] | select(.name=="bombay-high-court-judgments") | .id')

# 1. Dry run for a few days: shows the detected form fields and the links found
curl -XPOST -H "Authorization: Bearer $TOKEN" \
  "localhost:8080/api/kg/harvest/sources/$ID/preview?from=2024-01-01&to=2024-01-03"
```

Check in the response:
- `form.captcha` is `false`, and `form.fromField` / `form.toField` are the date
  inputs (otherwise set `fromField`, `toField`, `formSelector` with PUT);
- `form.fieldsSent` has sensible defaults (bench / side / judgment-only
  selects; override with `extraFields`);
- `documents` contains `generatenewauth.php?bhcpar=` links. If `found` is 0,
  open the same search in a browser and compare the date format and fields.

```bash
# 2. Optionally import one found link to check parsing
curl -XPOST localhost:8080/api/kg/import -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"url": "<one documents[].url>"}'

# 3. Start harvesting (kg.harvest.enabled=true on the crawling instance)
curl -XPOST -H "Authorization: Bearer $TOKEN" localhost:8080/api/kg/harvest/sources/$ID/resume
```

If the preview reports a captcha, this form cannot be harvested automatically;
use an official bulk source or URL lists instead.

## Admin API (`/api/kg/harvest`, Admin only)

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/sources` | List / create sources |
| PUT | `/sources/{id}` | Change config (keeps checkpoint) |
| POST | `/sources/{id}/pause`, `/resume` | Stop / restart a source |
| POST | `/sources/{id}/reset` | Restart the walk from `startDate` (imported docs still skipped) |
| POST | `/sources/{id}/discover` | Run discovery now |
| POST | `/sources/{id}/preview?from=&to=&limit=` | Dry run on the live site: detected form + documents found, nothing queued |
| GET | `/sources/{id}/plan` | Windows the next run would read |
| GET | `/stats?sourceId=` | Task counts by status |
| GET | `/tasks?status=DEAD&sourceId=` | Inspect tasks (e.g. failures with `lastError`) |
| POST | `/tasks/{id}/retry`, `/tasks/retry-dead?sourceId=` | Re-queue failed tasks |
| POST | `/work` | Process one batch now on this instance |
