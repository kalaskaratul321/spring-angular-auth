import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';

import {
  CaseCategory,
  CategorySummary,
  COURT_LEVEL_LABELS,
  COURT_LEVEL_ORDER,
  CourtLevel,
  GraphNode,
  GraphView,
  LegalCase,
  NodeType,
  ReferenceView,
  RELATION_LABELS,
  VERDICT_LABELS,
  VerdictResult,
} from '../../core/api.models';
import { KgApiService } from '../../core/kg-api.service';
import { GraphCanvas } from '../../graph/graph-canvas';
import { EMPTY_GRAPH, mergeGraphs } from '../../graph/graph-merge';
import { CATEGORY_COLORS, NODE_TYPE_STYLES, RELATION_STYLES } from '../../graph/graph-style';

interface CaseDetail {
  legalCase: LegalCase;
  references: ReferenceView[];
  citedBy: ReferenceView[];
  chain: LegalCase[];
}

const EXPANDABLE: ReadonlySet<NodeType> = new Set(['CASE', 'COURT', 'STATUTE', 'ISSUE', 'CITATION']);

/**
 * Interactive explorer: load a category subgraph (optionally one court level),
 * search cases, click a node for details, double-click to pull in its
 * neighbourhood. Case details show verdict, issues, references, cited-by,
 * the appeal chain and a link to the official source document.
 */
@Component({
  selector: 'app-explorer',
  imports: [FormsModule, GraphCanvas],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './explorer.html',
  styleUrl: './explorer.scss',
})
export class Explorer {
  private readonly api = inject(KgApiService);
  private readonly router = inject(Router);
  private readonly canvas = viewChild(GraphCanvas);

  // Query parameters (bound by the router)
  readonly category = input<CaseCategory | undefined>();
  readonly case = input<string | undefined>();
  /** Any node id to open with its connections, e.g. COURT:BHC-MUM. */
  readonly node = input<string | undefined>();

  protected readonly summaries = signal<CategorySummary[]>([]);
  protected readonly selectedCategory = signal<CaseCategory | null>(null);
  protected readonly courtLevel = signal<CourtLevel | null>(null);
  protected readonly limit = signal(50);

  protected readonly graph = signal<GraphView>(EMPTY_GRAPH);
  protected readonly hidden = signal<ReadonlySet<NodeType>>(new Set<NodeType>(['PARTY']));
  protected readonly highlighted = signal<string[]>([]);
  protected readonly loading = signal(false);
  protected readonly message = signal<string | null>(null);

  protected readonly selected = signal<GraphNode | null>(null);
  protected readonly detail = signal<CaseDetail | null>(null);
  protected readonly detailLoading = signal(false);

  protected readonly query = signal('');
  protected readonly searchResults = signal<LegalCase[] | null>(null);

  protected readonly levelOptions = COURT_LEVEL_ORDER.map((l) => ({ value: l, label: COURT_LEVEL_LABELS[l] }));
  protected readonly levelLabels = COURT_LEVEL_LABELS;
  protected readonly verdictLabels = VERDICT_LABELS;
  protected readonly relationLabels = RELATION_LABELS;
  protected readonly categoryColors = CATEGORY_COLORS;
  protected readonly typeStyles = NODE_TYPE_STYLES;
  protected readonly relationStyles = RELATION_STYLES;
  protected readonly nodeTypes = (Object.keys(NODE_TYPE_STYLES) as NodeType[]).map((t) => ({
    type: t,
    ...NODE_TYPE_STYLES[t],
  }));
  protected readonly edgeLegend = (['APPEAL_FROM', 'FOLLOWS', 'DISTINGUISHES', 'OVERRULES', 'CITES'] as const).map(
    (r) => RELATION_STYLES[r],
  );

  protected readonly levelsInCategory = computed(() => {
    const s = this.summaries().find((x) => x.category === this.selectedCategory());
    return s ? this.levelOptions.filter((o) => (s.casesByCourtLevel[o.value] ?? 0) > 0) : this.levelOptions;
  });
  protected readonly stats = computed(() => {
    const g = this.graph();
    return { nodes: g.nodes.length, cases: g.nodes.filter((n) => n.type === 'CASE').length, edges: g.edges.length };
  });

  constructor() {
    this.api.categorySummary().subscribe({
      next: (s) => {
        this.summaries.set(s);
        this.init(s);
      },
      error: () => this.message.set('Could not load categories.'),
    });
  }

  private init(summaries: CategorySummary[]): void {
    const caseId = this.case();
    const fromQuery = this.category();
    const first = [...summaries].sort((a, b) => b.totalCases - a.totalCases).find((s) => s.totalCases > 0);
    this.selectedCategory.set(fromQuery ?? first?.category ?? null);
    const nodeId = this.node();
    if (caseId) {
      this.openCase(caseId);
    } else if (nodeId) {
      const request = this.api.nodeGraph(nodeId, null);
      if (request) {
        this.run(request, true, (g) => (g.nodes.length <= 1 ? 'No cases connected yet.' : null));
      }
    } else if (this.selectedCategory()) {
      this.loadCategory();
    }
  }

  // --------------------------------------------------------------- loading

  loadCategory(): void {
    const cat = this.selectedCategory();
    if (!cat) {
      return;
    }
    this.router.navigate([], { queryParams: { category: cat, case: null }, replaceUrl: true });
    this.run(this.api.categoryGraph(cat, this.courtLevel(), this.limit()), true, (g) =>
      g.nodes.length <= 1 ? 'No cases in this selection.' : null,
    );
    this.clearSelection();
  }

  openCase(id: string): void {
    this.searchResults.set(null);
    this.run(this.api.caseGraph(id, 1), true, () => null, () => {
      const nodeId = `CASE:${id}`;
      setTimeout(() => this.canvas()?.focus(nodeId));
      const node = this.graph().nodes.find((n) => n.id === nodeId);
      if (node) {
        this.select(node);
      }
    });
  }

  expand(nodeId: string): void {
    const type = nodeId.split(':')[0] as NodeType;
    if (!EXPANDABLE.has(type)) {
      this.message.set(`${NODE_TYPE_STYLES[type].label} nodes can't be expanded; expand a case connected to it.`);
      return;
    }
    const request = this.api.nodeGraph(nodeId, null);
    if (!request) {
      this.message.set('This node cannot be expanded.');
      return;
    }
    this.run(request, false, (g) => (g.nodes.length <= 1 ? 'Nothing more connected to this node.' : null));
  }

  search(): void {
    const q = this.query().trim();
    if (!q) {
      this.searchResults.set(null);
      return;
    }
    this.api.search(q, null, null, 0, 20).subscribe({
      next: (p) => this.searchResults.set(p.content),
      error: () => this.message.set('Search failed.'),
    });
  }

  private run(
    request: Observable<GraphView>,
    replace: boolean,
    emptyMessage: (g: GraphView) => string | null,
    after?: () => void,
  ): void {
    this.loading.set(true);
    this.message.set(null);
    request.subscribe({
      next: (g) => {
        this.graph.set(replace ? g : mergeGraphs(this.graph(), g));
        this.highlighted.set([]);
        this.message.set(emptyMessage(g));
        this.loading.set(false);
        after?.();
      },
      error: (e: HttpErrorResponse) => {
        this.loading.set(false);
        this.message.set(e.status === 404 ? 'Not found.' : `Request failed (${e.status || 'network'}).`);
      },
    });
  }

  // ------------------------------------------------------------- selection

  select(node: GraphNode | null): void {
    this.selected.set(node);
    this.detail.set(null);
    if (!node || node.type !== 'CASE') {
      return;
    }
    const id = node.id.substring('CASE:'.length);
    this.detailLoading.set(true);
    forkJoin({
      legalCase: this.api.getCase(id),
      references: this.api.references(id),
      citedBy: this.api.citedBy(id),
      chain: this.api.appealChain(id),
    }).subscribe({
      next: (d) => {
        if (this.selected()?.id === node.id) {
          this.detail.set(d);
        }
        this.detailLoading.set(false);
      },
      error: () => {
        this.detailLoading.set(false);
        this.message.set('Could not load case details.');
      },
    });
  }

  clearSelection(): void {
    this.selected.set(null);
    this.detail.set(null);
  }

  /** Adds every case of the appeal chain (with its links) and outlines the chain. */
  showChain(chain: LegalCase[]): void {
    if (!chain.length) {
      return;
    }
    this.loading.set(true);
    forkJoin(chain.map((c) => this.api.caseGraph(c.id, 1))).subscribe({
      next: (views) => {
        this.graph.set(views.reduce((acc, v) => mergeGraphs(acc, v), this.graph()));
        this.highlighted.set(chain.map((c) => `CASE:${c.id}`));
        this.loading.set(false);
        setTimeout(() => this.canvas()?.fit());
      },
      error: () => {
        this.loading.set(false);
        this.message.set('Could not load the appeal chain.');
      },
    });
  }

  goToReference(ref: ReferenceView): void {
    if (ref.caseId) {
      this.expand(`CASE:${ref.caseId}`);
      setTimeout(() => this.canvas()?.focus(`CASE:${ref.caseId}`), 50);
    }
  }

  toggleType(type: NodeType): void {
    const next = new Set(this.hidden());
    if (next.has(type)) {
      next.delete(type);
    } else {
      next.add(type);
    }
    this.hidden.set(next);
  }

  fit(): void {
    this.canvas()?.fit();
  }

  relayout(): void {
    this.canvas()?.relayout();
  }

  protected prop(node: GraphNode, key: string): string | null {
    const v = node.properties?.[key];
    return v === null || v === undefined || v === '' ? null : String(v);
  }

  protected verdictLabel(v: VerdictResult | undefined | null): string {
    return v ? VERDICT_LABELS[v] : '—';
  }

  protected levelLabel(l: CourtLevel | undefined | null): string {
    return l ? COURT_LEVEL_LABELS[l] : '';
  }

  protected categoryName(c: CaseCategory | null | undefined): string {
    return this.summaries().find((s) => s.category === c)?.displayName ?? c ?? '';
  }
}
