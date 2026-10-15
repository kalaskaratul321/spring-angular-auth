import { GraphView } from '../core/api.models';

/** Union of two graphs; node data from {@code b} wins, edges are de-duplicated. */
export function mergeGraphs(a: GraphView, b: GraphView): GraphView {
  const nodes = new Map(a.nodes.map((n) => [n.id, n]));
  for (const n of b.nodes) {
    const prev = nodes.get(n.id);
    // Keep richer data: a case node from a neighbourhood call has properties, a bare reference may not.
    nodes.set(n.id, prev && Object.keys(n.properties ?? {}).length === 0 ? prev : n);
  }
  const edgeKey = (e: { source: string; relation: string; target: string }) =>
    `${e.source}|${e.relation}|${e.target}`;
  const edges = new Map(a.edges.map((e) => [edgeKey(e), e]));
  for (const e of b.edges) {
    edges.set(edgeKey(e), e);
  }
  return { nodes: [...nodes.values()], edges: [...edges.values()] };
}

export const EMPTY_GRAPH: GraphView = { nodes: [], edges: [] };
