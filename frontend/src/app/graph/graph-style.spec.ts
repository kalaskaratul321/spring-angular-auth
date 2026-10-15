import { GraphView } from '../core/api.models';
import { mergeGraphs } from './graph-merge';
import { CATEGORY_COLORS, caseNodeSize, NODE_TYPE_STYLES, shorten, toElements } from './graph-style';

const VIEW: GraphView = {
  nodes: [
    { id: 'CASE:hc', type: 'CASE', label: 'B. Kulkarni v. A. Traders (revision)', category: 'CRIMINAL', properties: { courtLevel: 'HIGH_COURT' } },
    { id: 'CASE:jmfc', type: 'CASE', label: 'A. Traders v. B. Kulkarni', category: 'CRIMINAL', properties: { courtLevel: 'JUDICIAL_MAGISTRATE' } },
    { id: 'STATUTE:NI_ACT:138', type: 'STATUTE', label: 'NI ACT s. 138', properties: {} },
    { id: 'CITATION:2019_4_scc_197', type: 'CITATION', label: '2019 4 SCC 197', properties: { inGraph: false } },
  ],
  edges: [
    { source: 'CASE:hc', target: 'CASE:jmfc', relation: 'APPEAL_FROM' },
    { source: 'CASE:hc', target: 'STATUTE:NI_ACT:138', relation: 'INVOKES' },
    { source: 'CASE:hc', target: 'CITATION:2019_4_scc_197', relation: 'FOLLOWS' },
    { source: 'CASE:hc', target: 'COURT:BHC-MUM', relation: 'HEARD_IN' },
  ],
};

describe('graph-style', () => {
  it('maps nodes with category colours, shapes and court-level sizes', () => {
    const els = toElements(VIEW);
    const hc = els.find((e) => e.data['id'] === 'CASE:hc')!;
    expect(hc.data['color']).toBe(CATEGORY_COLORS.CRIMINAL);
    expect(hc.data['size']).toBe(caseNodeSize('HIGH_COURT'));
    expect(caseNodeSize('HIGH_COURT')).toBeGreaterThan(caseNodeSize('JUDICIAL_MAGISTRATE'));
    const statute = els.find((e) => e.data['id'] === 'STATUTE:NI_ACT:138')!;
    expect(statute.data['shape']).toBe(NODE_TYPE_STYLES.STATUTE.shape);
  });

  it('drops edges to nodes that are not in the view', () => {
    const edges = toElements(VIEW).filter((e) => e.group === 'edges');
    expect(edges.length).toBe(3);
    expect(edges.some((e) => e.data['target'] === 'COURT:BHC-MUM')).toBeFalse();
  });

  it('styles precedent and appeal edges with arrows and labels', () => {
    const edges = toElements(VIEW).filter((e) => e.group === 'edges');
    const appeal = edges.find((e) => e.data['relation'] === 'APPEAL_FROM')!;
    expect(appeal.data['arrow']).toBe('triangle');
    expect(appeal.data['label']).toBe('appeal from');
    const invokes = edges.find((e) => e.data['relation'] === 'INVOKES')!;
    expect(invokes.data['arrow']).toBe('none');
    expect(invokes.data['label']).toBe('');
  });

  it('shortens long labels', () => {
    expect(shorten('x'.repeat(60), 10).length).toBe(10);
    expect(shorten('short')).toBe('short');
  });
});

describe('mergeGraphs', () => {
  it('unions nodes and edges without duplicates and keeps richer node data', () => {
    const extra: GraphView = {
      nodes: [
        { id: 'CASE:hc', type: 'CASE', label: 'bare', properties: {} },
        { id: 'ISSUE:sentencing', type: 'ISSUE', label: 'Sentencing', properties: {} },
      ],
      edges: [
        { source: 'CASE:hc', target: 'CASE:jmfc', relation: 'APPEAL_FROM' },
        { source: 'CASE:hc', target: 'ISSUE:sentencing', relation: 'RAISES_ISSUE' },
      ],
    };
    const merged = mergeGraphs(VIEW, extra);
    expect(merged.nodes.length).toBe(5);
    expect(merged.edges.length).toBe(5);
    expect(merged.nodes.find((n) => n.id === 'CASE:hc')!.label).toContain('Kulkarni');
  });
});
