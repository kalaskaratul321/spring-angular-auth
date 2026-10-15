import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { COURT_LEVEL_LABELS, CourtTreeNode, GraphNode, GraphView } from '../../core/api.models';
import { KgApiService } from '../../core/kg-api.service';
import { GraphCanvas } from '../../graph/graph-canvas';

/** Flattens the court tree into a graph: lower court -> APPEALS_TO -> appellate court. */
export function courtTreeToGraph(roots: CourtTreeNode[]): GraphView {
  const view: GraphView = { nodes: [], edges: [] };
  const walk = (n: CourtTreeNode, parent: string | null) => {
    const id = `COURT:${n.court.code}`;
    view.nodes.push({
      id,
      type: 'COURT',
      label: n.court.name,
      properties: {
        code: n.court.code,
        level: n.court.level,
        district: n.court.district ?? null,
        categories: n.court.categories ?? [],
      },
    });
    if (parent) {
      view.edges.push({ source: id, target: parent, relation: 'APPEALS_TO' });
    }
    n.subordinates.forEach((s) => walk(s, id));
  };
  roots.forEach((r) => walk(r, null));
  return view;
}

@Component({
  selector: 'app-courts',
  imports: [GraphCanvas, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="page courts">
      <header>
        <h1>Court hierarchy</h1>
        <p class="muted">Appeals flow upwards: each forum points to the court its decisions are appealed to.</p>
      </header>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      <div class="layout">
        <section class="card graph">
          <app-graph-canvas
            class="canvas"
            [graph]="graph()"
            layout="breadthfirst"
            [roots]="roots()"
            ariaLabel="Court hierarchy"
            (nodeSelected)="selected.set($event)"
          />
        </section>
        <aside class="card">
          @if (selected(); as c) {
            <h2>{{ c.label }}</h2>
            <dl>
              <dt>Code</dt><dd>{{ c.properties['code'] }}</dd>
              <dt>Level</dt><dd>{{ levelLabel(c.properties['level']) }}</dd>
              @if (c.properties['district']) {
                <dt>District</dt><dd>{{ c.properties['district'] }}</dd>
              }
              <dt>Hears</dt><dd>{{ categories(c) || 'All matters' }}</dd>
            </dl>
            <a class="button primary" [routerLink]="['/explorer']" [queryParams]="{ node: c.id }">
              Cases in this court
            </a>
          } @else {
            <p class="muted">Select a court to see its details.</p>
          }
        </aside>
      </div>
    </main>
  `,
  styles: `
    header h1 { margin: 0 0 4px; font-size: 1.5rem; }
    header p { margin: 0 0 16px; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 12px; align-items: start; }
    .graph { padding: 0; height: calc(100dvh - 200px); min-height: 420px; display: flex; overflow: hidden; }
    .canvas { flex: 1; }
    dl { display: grid; grid-template-columns: 80px 1fr; gap: 4px 8px; font-size: 0.9rem; dt { color: var(--muted); } dd { margin: 0; } }
    h2 { font-size: 1.05rem; margin: 0 0 8px; }
    @media (max-width: 860px) { .layout { grid-template-columns: 1fr; } .graph { height: 60dvh; } }
  `,
})
export class Courts {
  private readonly api = inject(KgApiService);

  protected readonly tree = signal<CourtTreeNode[]>([]);
  protected readonly graph = computed(() => courtTreeToGraph(this.tree()));
  protected readonly roots = computed(() => this.tree().map((r) => `COURT:${r.court.code}`));
  protected readonly selected = signal<GraphNode | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api.courtHierarchy().subscribe({
      next: (t) => this.tree.set(t),
      error: () => this.error.set('Could not load the court hierarchy.'),
    });
  }

  protected levelLabel(level: unknown): string {
    return COURT_LEVEL_LABELS[level as keyof typeof COURT_LEVEL_LABELS] ?? String(level ?? '');
  }

  protected categories(c: GraphNode): string {
    const cats = (c.properties['categories'] as string[]) ?? [];
    return cats.map((x) => x.replace('_', ' ').toLowerCase()).join(', ');
  }
}
