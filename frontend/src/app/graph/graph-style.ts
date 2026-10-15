import type { ElementDefinition, StylesheetJson } from 'cytoscape';

import { CaseCategory, CourtLevel, GraphView, NodeType, RelationType } from '../core/api.models';

/** One colour per category; used for case nodes, category hubs and the legend. */
export const CATEGORY_COLORS: Record<CaseCategory, string> = {
  CIVIL: '#4e79a7',
  CRIMINAL: '#e15759',
  LAND: '#9c755f',
  RERA: '#f28e2b',
  FAMILY: '#b07aa1',
  CONSUMER: '#59a14f',
  LABOUR_SERVICE: '#d4a90b',
  CONSTITUTIONAL: '#76b7b2',
  COMMERCIAL: '#ff9da7',
  TAX: '#2f4b7c',
  OTHER: '#9ca3af',
};

export interface NodeTypeStyle {
  label: string;
  color: string;
  shape: string;
}

export const NODE_TYPE_STYLES: Record<NodeType, NodeTypeStyle> = {
  CASE: { label: 'Case', color: '#4e79a7', shape: 'ellipse' },
  COURT: { label: 'Court', color: '#64748b', shape: 'round-rectangle' },
  CATEGORY: { label: 'Category', color: '#334155', shape: 'hexagon' },
  STATUTE: { label: 'Statute', color: '#3b82f6', shape: 'diamond' },
  JUDGE: { label: 'Judge', color: '#8b5cf6', shape: 'triangle' },
  PARTY: { label: 'Party', color: '#94a3b8', shape: 'ellipse' },
  ISSUE: { label: 'Issue', color: '#f59e0b', shape: 'star' },
  VERDICT: { label: 'Verdict', color: '#10b981', shape: 'tag' },
  CITATION: { label: 'Cited judgment', color: '#6b7280', shape: 'rectangle' },
};

export interface RelationStyle {
  label: string;
  color: string;
  width: number;
  dashed: boolean;
  arrow: boolean;
}

const QUIET: RelationStyle = { label: '', color: '#cbd5e1', width: 1, dashed: false, arrow: false };

export const RELATION_STYLES: Record<RelationType, RelationStyle> = {
  HEARD_IN: { ...QUIET, label: 'heard in' },
  BELONGS_TO: { ...QUIET, label: 'category' },
  INVOKES: { ...QUIET, label: 'invokes', color: '#93c5fd' },
  DECIDED_BY: { ...QUIET, label: 'decided by' },
  INVOLVES_PARTY: { ...QUIET, label: 'party' },
  RAISES_ISSUE: { ...QUIET, label: 'issue', color: '#fcd34d' },
  HAS_VERDICT: { ...QUIET, label: 'verdict', color: '#6ee7b7' },
  APPEAL_FROM: { label: 'appeal from', color: '#dc2626', width: 3.5, dashed: false, arrow: true },
  CITES: { label: 'cites', color: '#64748b', width: 1.5, dashed: false, arrow: true },
  FOLLOWS: { label: 'follows', color: '#16a34a', width: 2, dashed: false, arrow: true },
  DISTINGUISHES: { label: 'distinguishes', color: '#ea580c', width: 2, dashed: true, arrow: true },
  OVERRULES: { label: 'overrules', color: '#7f1d1d', width: 2.5, dashed: true, arrow: true },
  APPEALS_TO: { label: 'appeals to', color: '#64748b', width: 2, dashed: false, arrow: true },
};

/** Bigger nodes for more senior forums. */
export function caseNodeSize(level: CourtLevel | null | undefined): number {
  switch (level) {
    case 'HIGH_COURT':
      return 48;
    case 'APPELLATE_TRIBUNAL':
      return 42;
    case 'DISTRICT_COURT':
    case 'SESSIONS_COURT':
    case 'FAMILY_COURT':
    case 'STATE_COMMISSION':
      return 38;
    default:
      return 32;
  }
}

export function nodeColor(type: NodeType, category?: CaseCategory | null): string {
  if ((type === 'CASE' || type === 'CATEGORY') && category) {
    return CATEGORY_COLORS[category];
  }
  return NODE_TYPE_STYLES[type].color;
}

export function shorten(text: string, max = 42): string {
  return text.length > max ? text.slice(0, max - 1).trimEnd() + '…' : text;
}

/** Converts an API graph to Cytoscape elements (nodes first, edges only between known nodes). */
export function toElements(view: GraphView): ElementDefinition[] {
  const ids = new Set(view.nodes.map((n) => n.id));
  const nodes: ElementDefinition[] = view.nodes.map((n) => ({
    group: 'nodes',
    data: {
      id: n.id,
      type: n.type,
      label: shorten(n.label ?? n.id),
      fullLabel: n.label ?? n.id,
      category: n.category ?? null,
      color: nodeColor(n.type, n.category),
      shape: NODE_TYPE_STYLES[n.type].shape,
      size: n.type === 'CASE' ? caseNodeSize(n.properties?.['courtLevel'] as CourtLevel | undefined) : n.type === 'PARTY' ? 18 : 26,
      properties: n.properties ?? {},
    },
  }));
  const edges: ElementDefinition[] = view.edges
    .filter((e) => ids.has(e.source) && ids.has(e.target))
    .map((e) => {
      const s = RELATION_STYLES[e.relation];
      return {
        group: 'edges',
        data: {
          id: `${e.source}|${e.relation}|${e.target}`,
          source: e.source,
          target: e.target,
          relation: e.relation,
          label: s.arrow ? s.label : '',
          color: s.color,
          width: s.width,
          lineStyle: s.dashed ? 'dashed' : 'solid',
          arrow: s.arrow ? 'triangle' : 'none',
        },
      };
    });
  return [...nodes, ...edges];
}

/** Cytoscape stylesheet; colours come from element data, text colours from the page theme. */
export function graphStylesheet(textColor: string, haloColor: string): StylesheetJson {
  return [
    {
      selector: 'node',
      style: {
        'background-color': 'data(color)',
        shape: 'data(shape)' as never,
        width: 'data(size)',
        height: 'data(size)',
        label: 'data(label)',
        color: textColor,
        'font-size': 10,
        'text-valign': 'bottom',
        'text-margin-y': 4,
        'text-wrap': 'ellipsis',
        'text-max-width': '140px',
        'text-outline-color': haloColor,
        'text-outline-width': 2,
        'border-width': 0,
      },
    },
    {
      selector: 'node[type = "CITATION"]',
      style: { 'border-width': 2, 'border-style': 'dashed', 'border-color': '#9ca3af', 'background-opacity': 0.5 },
    },
    {
      selector: 'node[type = "CATEGORY"]',
      style: { 'font-size': 12, 'font-weight': 'bold' },
    },
    {
      selector: 'edge',
      style: {
        width: 'data(width)',
        'line-color': 'data(color)',
        'line-style': 'data(lineStyle)' as never,
        'target-arrow-color': 'data(color)',
        'target-arrow-shape': 'data(arrow)' as never,
        'curve-style': 'bezier',
        label: 'data(label)',
        'font-size': 8,
        color: textColor,
        'text-rotation': 'autorotate',
        'text-outline-color': haloColor,
        'text-outline-width': 2,
      },
    },
    {
      selector: 'node:selected',
      style: { 'border-width': 4, 'border-color': '#facc15', 'border-style': 'solid' },
    },
    {
      selector: '.faded',
      style: { opacity: 0.15 },
    },
    {
      selector: 'node.chain',
      style: { 'border-width': 4, 'border-color': '#dc2626', 'border-style': 'solid' },
    },
  ];
}
