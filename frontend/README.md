# MahaLegal knowledge graph UI

Angular 20 front end for the case knowledge graph served by the Spring Boot
backend (`/api/kg`). Graphs are drawn with [Cytoscape.js](https://js.cytoscape.org/).

## Screens

- **Overview**: one card per category (Civil, Criminal, Land, RERA, ...) with
  case counts by court level (High Court → trial courts), verdicts, top
  statutes and frequent issues. A card opens its graph.
- **Graph explorer**: category subgraph, optionally one court level.
  - Click a node for details; double-click a case, court, statute, issue or
    cited judgment to pull in its connections.
  - Case panel: court, category (with auto-classification confidence), coram,
    verdict, issues, statutes, appeal chain ("Show chain in graph" outlines
    it), references with treatment (cites / follows / distinguishes /
    overrules), cited-by, and a link to the **official judgment** (`source.url`).
  - Search by title, case number, citation or issue text.
  - Toggle node types (parties are hidden by default); legend for link types.
  - Deep links: `/explorer?category=CRIMINAL`, `/explorer?case=<id>`,
    `/explorer?node=COURT:BHC-MUM`.
- **Courts**: the court hierarchy (each forum points to its appellate court);
  a court opens its cases in the explorer.

Sign-in uses the existing `/api/auth/login` JWT; the token is kept in
`sessionStorage` and sent as `Authorization: Bearer`. Light and dark themes
follow the OS setting.

## Run

```bash
# backend on :8080 (MongoDB on :27017)
./gradlew bootRun            # from the repository root

cd frontend
npm install
npm start                    # http://localhost:4200, /api proxied to :8080 (proxy.conf.json)
```

Load some data first, e.g. the sample cases:
`POST /api/kg/cases/bulk` with `src/main/resources/kg/sample-cases.json`.

## Build and test

```bash
npm run build                # dist/frontend/browser
npm test                     # Karma, watch mode
npm run test:ci              # headless Chrome, single run (uses --no-sandbox for containers/CI)
```

For production, serve `dist/frontend/browser` behind the same origin as the
API (or route `/api` to the backend in your web server) so no CORS
configuration is needed.

## Code map

| Path | |
|---|---|
| `src/app/core/api.models.ts` | Types mirroring the backend DTOs |
| `src/app/core/kg-api.service.ts` | `/api/kg` client, incl. node-id → neighbourhood endpoint mapping |
| `src/app/core/auth.*` | Login, JWT interceptor (401 → login), route guard |
| `src/app/graph/graph-style.ts` | Colours per category, shapes per node type, edge styles per relation |
| `src/app/graph/graph-canvas.ts` | Cytoscape wrapper: incremental updates, selection, expand, hover focus |
| `src/app/pages/*` | Login, overview, explorer, courts |
