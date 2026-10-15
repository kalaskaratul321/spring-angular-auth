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
