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

## Admin API (`/api/kg/harvest`, Admin only)

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/sources` | List / create sources |
| PUT | `/sources/{id}` | Change config (keeps checkpoint) |
| POST | `/sources/{id}/pause`, `/resume` | Stop / restart a source |
| POST | `/sources/{id}/reset` | Restart the walk from `startDate` (imported docs still skipped) |
| POST | `/sources/{id}/discover` | Run discovery now |
| GET | `/sources/{id}/plan` | Windows the next run would read |
| GET | `/stats?sourceId=` | Task counts by status |
| GET | `/tasks?status=DEAD&sourceId=` | Inspect tasks (e.g. failures with `lastError`) |
| POST | `/tasks/{id}/retry`, `/tasks/retry-dead?sourceId=` | Re-queue failed tasks |
| POST | `/work` | Process one batch now on this instance |
