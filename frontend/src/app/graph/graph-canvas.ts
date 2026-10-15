import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import cytoscape, { Core, EventObject, LayoutOptions, NodeSingular } from 'cytoscape';

import { GraphNode, GraphView, NodeType } from '../core/api.models';
import { graphStylesheet, toElements } from './graph-style';

export type GraphLayout = 'cose' | 'breadthfirst' | 'concentric';

/**
 * Renders a knowledge-graph view with Cytoscape. When the input graph changes,
 * only new elements are added (and removed ones dropped), so expanding a node
 * keeps the rest of the picture where it was.
 *
 * Click selects a node, double-click asks the parent to expand it, hovering
 * dims everything outside the node's neighbourhood.
 */
@Component({
  selector: 'app-graph-canvas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="canvas" role="img" [attr.aria-label]="ariaLabel()"></div>`,
  styles: `
    :host { display: block; position: relative; min-height: 320px; }
    .canvas { position: absolute; inset: 0; }
  `,
})
export class GraphCanvas {
  readonly graph = input.required<GraphView>();
  readonly hiddenTypes = input<ReadonlySet<NodeType>>(new Set());
  /** Node ids to outline (e.g. an appeal chain). */
  readonly highlighted = input<readonly string[]>([]);
  readonly layout = input<GraphLayout>('cose');
  /** Root node ids for the breadthfirst layout (e.g. the High Court benches). */
  readonly roots = input<readonly string[]>([]);
  readonly ariaLabel = input('Knowledge graph');

  readonly nodeSelected = output<GraphNode | null>();
  readonly nodeExpand = output<string>();

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private cy: Core | null = null;

  constructor() {
    const destroyRef = inject(DestroyRef);

    afterNextRender(() => {
      const css = getComputedStyle(document.documentElement);
      const text = css.getPropertyValue('--graph-text').trim() || '#1f2937';
      const halo = css.getPropertyValue('--graph-halo').trim() || '#ffffff';
      this.cy = cytoscape({
        container: this.host().nativeElement,
        style: graphStylesheet(text, halo),
        wheelSensitivity: 0.3,
        minZoom: 0.1,
        maxZoom: 3,
      });
      this.bindEvents(this.cy);
      this.sync(this.graph(), true);
      this.applyVisibility();
      this.applyHighlight();
    });

    effect(() => {
      const view = this.graph();
      if (this.cy) {
        this.sync(view, false);
      }
    });
    effect(() => {
      this.hiddenTypes();
      this.applyVisibility();
    });
    effect(() => {
      this.highlighted();
      this.applyHighlight();
    });

    destroyRef.onDestroy(() => {
      this.cy?.destroy();
      this.cy = null;
    });
  }

  fit(): void {
    this.cy?.fit(this.cy.elements(':visible'), 40);
  }

  relayout(): void {
    this.runLayout(true);
  }

  focus(nodeId: string): void {
    const node = this.cy?.getElementById(nodeId);
    if (node && node.nonempty()) {
      this.cy!.elements().unselect();
      node.select();
      this.cy!.animate({ center: { eles: node }, zoom: Math.max(this.cy!.zoom(), 1) }, { duration: 300 });
    }
  }

  /** Adds/removes elements to match {@code view}; lays out again only if something was added. */
  private sync(view: GraphView, initial: boolean): void {
    const cy = this.cy!;
    const elements = toElements(view);
    const wanted = new Set(elements.map((e) => e.data['id'] as string));
    const stale = cy.elements().filter((el) => !wanted.has(el.id()));
    const fresh = elements.filter((e) => cy.getElementById(e.data['id'] as string).empty());

    cy.batch(() => {
      stale.remove();
      if (fresh.length) {
        // Place new nodes next to an existing neighbour so the layout starts close to its result.
        const positioned = fresh.map((e) => {
          if (e.group === 'nodes' && !initial) {
            const anchor = this.neighbourPosition(view, e.data['id'] as string);
            if (anchor) {
              return { ...e, position: { x: anchor.x + (Math.random() - 0.5) * 80, y: anchor.y + (Math.random() - 0.5) * 80 } };
            }
          }
          return e;
        });
        cy.add(positioned);
      }
    });
    if (fresh.length || initial) {
      this.applyVisibility();
      this.runLayout(initial);
    }
  }

  private neighbourPosition(view: GraphView, id: string): { x: number; y: number } | null {
    for (const e of view.edges) {
      const other = e.source === id ? e.target : e.target === id ? e.source : null;
      if (other) {
        const n = this.cy!.getElementById(other);
        if (n.nonempty()) {
          return n.position();
        }
      }
    }
    return null;
  }

  private runLayout(full: boolean): void {
    const cy = this.cy;
    if (!cy || cy.nodes().empty()) {
      return;
    }
    const visible = cy.elements(':visible');
    let options: LayoutOptions;
    switch (this.layout()) {
      case 'breadthfirst':
        options = {
          name: 'breadthfirst',
          // With explicit roots, depth is the distance from a root whatever the edge
          // direction (court edges point upwards, lower court -> appellate court).
          directed: this.roots().length === 0,
          spacingFactor: 1.1,
          padding: 30,
          animate: false,
          roots: this.roots().length ? this.roots().map((r) => `[id = "${r}"]`).join(', ') : undefined,
        } as LayoutOptions;
        break;
      case 'concentric':
        options = { name: 'concentric', padding: 30, animate: false, minNodeSpacing: 20 };
        break;
      default:
        options = {
          name: 'cose',
          animate: false,
          randomize: full,
          padding: 30,
          nodeRepulsion: () => 200000,
          idealEdgeLength: () => 110,
          edgeElasticity: () => 100,
          nodeOverlap: 20,
          gravity: 0.3,
          numIter: 1500,
          componentSpacing: 100,
        } as LayoutOptions;
    }
    visible.layout(options).run();
    cy.fit(visible, 40);
  }

  private applyVisibility(): void {
    const cy = this.cy;
    if (!cy) {
      return;
    }
    const hidden = this.hiddenTypes();
    cy.batch(() => {
      cy.nodes().forEach((n) => {
        n.style('display', hidden.has(n.data('type')) ? 'none' : 'element');
      });
    });
  }

  private applyHighlight(): void {
    const cy = this.cy;
    if (!cy) {
      return;
    }
    const ids = new Set(this.highlighted());
    cy.batch(() => {
      cy.nodes().forEach((n) => {
        if (ids.has(n.id())) {
          n.addClass('chain');
        } else {
          n.removeClass('chain');
        }
      });
    });
  }

  private bindEvents(cy: Core): void {
    cy.on('tap', 'node', (evt: EventObject) => this.nodeSelected.emit(this.toGraphNode(evt.target as NodeSingular)));
    cy.on('tap', (evt: EventObject) => {
      if (evt.target === cy) {
        this.nodeSelected.emit(null);
      }
    });
    cy.on('dbltap', 'node', (evt: EventObject) => this.nodeExpand.emit((evt.target as NodeSingular).id()));
    cy.on('mouseover', 'node', (evt: EventObject) => {
      const hood = (evt.target as NodeSingular).closedNeighborhood();
      cy.elements().not(hood).addClass('faded');
    });
    cy.on('mouseout', 'node', () => cy.elements().removeClass('faded'));
  }

  private toGraphNode(n: NodeSingular): GraphNode {
    return {
      id: n.id(),
      type: n.data('type'),
      label: n.data('fullLabel'),
      category: n.data('category'),
      properties: n.data('properties') ?? {},
    };
  }
}
