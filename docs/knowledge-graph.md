# MahaLegal case knowledge graph

A graph of judgments from the Bombay High Court down to Sessions, Magistrate,
Civil Judge and quasi-judicial forums, grouped by practice-area category.

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

**Edges** (`kg_edges`): `HEARD_IN`, `BELONGS_TO`, `INVOKES`, `DECIDED_BY`,
`INVOLVES_PARTY`, `APPEAL_FROM` (higher case -> lower case),
`CITES` / `FOLLOWS` / `DISTINGUISHES` / `OVERRULES` (case -> precedent).
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
| GET | `/categories/summary` | Per category: total, counts by court level, top statutes |
| GET | `/categories/{cat}/cases?courtLevel=&page=&size=` | Cases in a category |
| GET | `/categories/{cat}/graph?courtLevel=&limit=` | Category subgraph |
| GET | `/courts`, `/courts/hierarchy` | Seeded Maharashtra forums, as list or appeal tree |
| GET | `/courts/{code}/graph?category=` | Cases heard in a court |
| POST | `/cases`, `/cases/bulk` | Ingest (Admin) |
| POST | `/classify` | Preview the category for a case without saving |
| GET | `/cases/{id}` | One case |
| DELETE | `/cases/{id}` | Remove case and its edges (Admin) |
| GET | `/cases/{id}/graph?depth=2&category=` | Neighbourhood (max depth 4, 500 nodes) |
| GET | `/cases/{id}/appeal-chain` | Trial forum -> ... -> High Court |
| GET | `/statutes/{act}/{section}/graph?category=` | Cases invoking a provision |

Graph responses are `{ nodes: [{id, type, label, category, properties}], edges: [{source, target, relation}] }`,
which maps directly onto vis-network / cytoscape / ngx-graph on the Angular side.

## Try it

```bash
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@sandbox.com","password":"..."}' | jq -r .token)

curl -XPOST localhost:8080/api/kg/cases/bulk -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d @src/main/resources/kg/sample-cases.json

curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/kg/categories/summary
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/kg/cases/demo-hc-ni138-revision/appeal-chain
```

The sample cases are illustrative, not real judgments. Order matters in bulk
ingest: a case must come after the case it appeals from.

Court seed data lives in `src/main/resources/kg/maharashtra-courts.json`; add
districts/forums there and they are inserted on the next start.
