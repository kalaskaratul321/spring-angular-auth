# MahaLegal case knowledge graph

A graph of judgments from the Bombay High Court down to Sessions, Magistrate,
Civil Judge and quasi-judicial forums, grouped by practice-area category.

## What a case holds

| Field | Meaning |
|---|---|
| `title`, `caseNumber`, `cnr`, `caseType` | Identification; `title` is the node label |
| `issues[]` | `{topic, question, finding}`: each question decided and the court's answer. `topic` is a short canonical label; cases with the same topic share one ISSUE node |
| `verdict` | `{result, summary, reliefGranted, sentence}`; `result` is one of `ALLOWED, PARTLY_ALLOWED, DISMISSED, CONVICTED, ACQUITTED, BAIL_GRANTED, BAIL_REJECTED, DECREED, REMANDED, SETTLED, WITHDRAWN, DISPOSED_OF, PENDING, OTHER` |
| `references[]` | `{caseId or citation, title, court, treatment, paragraph}`: judgments relied on; `treatment` is `CITES / FOLLOWS / DISTINGUISHES / OVERRULES` |
| `citations[]` | This judgment's own reported citations, e.g. `2024 SCC OnLine Bom 1234` |
| `statutes[]`, `judges[]`, `petitioners[]`, `respondents[]`, `appealFromCaseId` | As below |

A reference with a `caseId` links straight to that case. A reference by
`citation` links to the in-graph case reporting that citation if there is one,
otherwise to a shared `CITATION` node (e.g. a Supreme Court judgment). When a
case carrying that citation is ingested later, the existing links are moved
onto it automatically. Citations are compared in normalised form, so
`(2019) 4 SCC 197` and `2019 (4) SCC 197` match.

## Importing from official websites

Judgments and reference files are read directly from official government
websites; **no copy of the file is kept**. Each import:

1. Checks the URL is `https` on an allowed host (default: any `*.gov.in` or
   `*.nic.in`, e.g. `bombayhighcourt.nic.in`, `judgments.ecourts.gov.in`,
   `maharera.maharashtra.gov.in`, `indiacode.nic.in`). Redirects are re-checked
   at every hop, hosts resolving to private addresses are refused, and the body
   is capped (25 MB by default). Configure in `application.properties`
   (`kg.import.*`).
2. Downloads the PDF/HTML **into memory only**, extracts the text (PDFBox /
   jsoup) and parses it: case number and type, CNR, parties (-> title), coram,
   decision date, statutes (`Section 138 of the N.I. Act`, `ss. 302, 307 r/w 34
   IPC`, `Code of Criminal Procedure, 1973`...), issues (`Whether ...?`),
   verdict (from the operative order), the judgment's own neutral citation
   (`2025:BHC-AS:12345`) and the judgments it cites (SCC, SCC OnLine, AIR, SCR,
   INSC, Mh.L.J., Bom.C.R., BHC neutral citations), with treatment guessed
   from the sentence (followed / distinguished / overruled / cited). The court
   is inferred for Bombay HC benches, MahaRERA, the RERA Appellate Tribunal,
   MRT, MAT and the Mumbai City Civil & Sessions Court.
3. Saves only the structured case plus `source`:
   `{url, resolvedUrl, host, contentType, contentLength, sha256, fetchedAt,
   lastModified, documentTitle, pageCount}`. The document bytes and text are
   discarded. Graph nodes and reference lists expose `sourceUrl` so the UI can
   link to the official copy.

Anything you pass in `metadata` overrides what was extracted (useful for
issue `topic`s, `courtCode` of district courts, `appealFromCaseId`). The
response lists `extractedFields` and `warnings` (e.g. court or verdict not
recognised, scanned PDF without text layer - OCR is not done).

Re-importing the same URL updates the same case. `POST /cases/{id}/refresh`
re-fetches the source: if the SHA-256 is unchanged only `fetchedAt` changes;
if the official copy changed, extracted fields are re-parsed while title,
court, category, summary, topical issues and the appeal link are kept.

Reference files work the same way: import the cited judgment's official URL
and any `CITATION` links pointing to its citation move onto it. References
and statutes also carry an optional `sourceUrl` (e.g. India Code).

| Method | Path | Purpose |
|---|---|---|
| GET | `/import/allowed-hosts` | Hosts imports may read from |
| POST | `/import` | `{url, metadata?}` -> import one document (Admin) |
| POST | `/import/bulk` | List of the above (Admin) |
| POST | `/cases/{id}/refresh` | Re-read the official source (Admin) |
| GET | `/cases/{id}/source` | Source URL and fetch metadata |

```bash
curl -XPOST localhost:8080/api/kg/import -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "url": "https://<official-host>.gov.in/path/to/judgment.pdf",
    "metadata": { "issues": [ { "topic": "Cheque dishonour - sentencing" } ] }
  }'
```

eCourts and some court portals put judgments behind a captcha or a session;
use the direct PDF link of the judgment (the URL the browser downloads from).

## Model

**Categories** (`CaseCategory`): `CIVIL`, `CRIMINAL`, `LAND`, `RERA`, `FAMILY`,
`CONSUMER`, `LABOUR_SERVICE`, `CONSTITUTIONAL`, `COMMERCIAL`, `TAX`, `OTHER`.
Each case also carries a free-text `subCategory` (e.g. Bail, NDPS, Cheque
Dishonour, Land Revenue, Specific Performance).

**Court levels** (`CourtLevel`), ranked so an appeal chain sorts bottom-up:
`HIGH_COURT` > `APPELLATE_TRIBUNAL` > `DISTRICT_COURT`/`SESSIONS_COURT` >
`FAMILY_COURT`/`STATE_COMMISSION` > `TRIBUNAL` > `REVENUE_APPELLATE` >
`CIVIL_JUDGE_SENIOR_DIVISION` > `DISTRICT_COMMISSION` >
`CIVIL_JUDGE_JUNIOR_DIVISION`/`JUDICIAL_MAGISTRATE` > `REVENUE_AUTHORITY`.

**Nodes** are addressed as `TYPE:id`:

| Type | Example key | Source |
|---|---|---|
| CASE | `CASE:demo-hc-ni138-revision` | `kg_cases` |
| COURT | `COURT:BHC-MUM` | `kg_courts` (seeded) |
| CATEGORY | `CATEGORY:CRIMINAL` | enum |
| STATUTE | `STATUTE:NI_ACT:138` | from `statutes[]` |
| JUDGE | `JUDGE:r_k_patil` | from `judges[]` |
| PARTY | `PARTY:a_traders` | petitioners/respondents |
| ISSUE | `ISSUE:delay_in_possession_interest` | `issues[].topic` (or question) |
| VERDICT | `VERDICT:DISMISSED` | `verdict.result` |
| CITATION | `CITATION:2019_4_scc_197` | `references[].citation` not in the graph |

**Edges** (`kg_edges`): `HEARD_IN`, `BELONGS_TO`, `INVOKES`, `DECIDED_BY`,
`INVOLVES_PARTY`, `APPEAL_FROM` (higher case -> lower case), `RAISES_ISSUE`,
`HAS_VERDICT`, and `CITES` / `FOLLOWS` / `DISTINGUISHES` / `OVERRULES`
(case -> referred case or citation).
Each edge stores the source case's category, so any traversal can be limited
to one category.

## Ingest and classification

`POST /api/kg/cases` (Admin only) saves a case and rebuilds its edges. If
`category` is omitted, `CaseCategoryClassifier` picks it from, in order of
weight: specific sections (CrPC 125 -> Family, CrPC 439 -> Criminal/Bail,
NI Act 138 -> Criminal/Cheque Dishonour), the forum (a court that only hears
RERA/Family/Land), the act (IPC/BNS, CPC, MLRC, MTAL, RERA, MOFA, HMA...), the
case type (Sessions Case, Second Appeal, RTS Appeal...) and keywords in the
title/summary. The score share of the winner is saved as `categoryConfidence`
so low-confidence cases can be routed for review or to an LLM classifier.

An `appealFromCaseId` must point to an existing case in a forum that is not
more senior than the new one.

## API (all under `/api/kg`, JWT required)

| Method | Path | Purpose |
|---|---|---|
| GET | `/categories` | Category list |
| GET | `/categories/summary` | Per category: total, counts by court level and verdict, top statutes and issues |
| GET | `/categories/{cat}/cases?courtLevel=&page=&size=` | Cases in a category |
| GET | `/categories/{cat}/graph?courtLevel=&limit=` | Category subgraph |
| GET | `/courts`, `/courts/hierarchy` | Seeded Maharashtra forums, as list or appeal tree |
| GET | `/courts/{code}/graph?category=` | Cases heard in a court |
| POST | `/cases`, `/cases/bulk` | Ingest (Admin) |
| POST | `/classify` | Preview the category for a case without saving |
| GET | `/cases/search?q=&category=&verdict=` | Search title, case number, citation and issue text |
| GET | `/cases/{id}` | One case |
| GET | `/cases/{id}/references` | Judgments it refers to, with treatment, resolved to graph cases where possible |
| GET | `/cases/{id}/cited-by` | Later judgments that cite / follow / distinguish / overrule it |
| GET | `/issues/graph?topic=&category=` | All cases deciding an issue, with their courts and verdicts |
| GET | `/citations/graph?citation=` | Cases reported as, or referring to, a citation |
| GET | `/verdicts` | Verdict result codes |
| DELETE | `/cases/{id}` | Remove case and its edges (Admin) |
| GET | `/cases/{id}/graph?depth=2&category=` | Neighbourhood (max depth 4, 500 nodes) |
| GET | `/cases/{id}/appeal-chain` | Trial forum -> ... -> High Court |
| GET | `/statutes/{act}/{section}/graph?category=` | Cases invoking a provision |

Case nodes in graph responses carry `verdict`, `verdictSummary` and
`citations` in `properties`. Graph responses are `{ nodes: [{id, type, label, category, properties}], edges: [{source, target, relation}] }`,
which maps directly onto vis-network / cytoscape / ngx-graph on the Angular side.

## Try it

```bash
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@sandbox.com","password":"..."}' | jq -r .token)

curl -XPOST localhost:8080/api/kg/cases/bulk -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d @src/main/resources/kg/sample-cases.json

curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/kg/categories/summary
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/kg/cases/demo-hc-ni138-revision/appeal-chain
curl -H "Authorization: Bearer $TOKEN" "localhost:8080/api/kg/citations/graph?citation=(2019)%204%20SCC%20197"
curl -H "Authorization: Bearer $TOKEN" "localhost:8080/api/kg/issues/graph?topic=Cheque%20dishonour%20-%20legally%20enforceable%20debt"
```

The sample cases are illustrative, not real judgments (the Supreme Court
citations they refer to are real). Order matters in bulk
ingest: a case must come after the case it appeals from.

Court seed data lives in `src/main/resources/kg/maharashtra-courts.json`; add
districts/forums there and they are inserted on the next start.
