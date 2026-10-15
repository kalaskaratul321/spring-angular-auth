import { courtTreeToGraph } from './courts';

describe('courtTreeToGraph', () => {
  it('links each forum to the court its appeals go to', () => {
    const g = courtTreeToGraph([
      {
        court: { id: '1', code: 'BHC-MUM', name: 'Bombay HC', level: 'HIGH_COURT', categories: [] },
        subordinates: [
          {
            court: { id: '2', code: 'DSC-PUNE', name: 'Pune Sessions', level: 'SESSIONS_COURT', categories: ['CRIMINAL'] },
            subordinates: [
              { court: { id: '3', code: 'JMFC-PUNE', name: 'JMFC Pune', level: 'JUDICIAL_MAGISTRATE', categories: ['CRIMINAL'] }, subordinates: [] },
            ],
          },
        ],
      },
    ]);
    expect(g.nodes.map((n) => n.id)).toEqual(['COURT:BHC-MUM', 'COURT:DSC-PUNE', 'COURT:JMFC-PUNE']);
    expect(g.edges).toEqual([
      { source: 'COURT:DSC-PUNE', target: 'COURT:BHC-MUM', relation: 'APPEALS_TO' },
      { source: 'COURT:JMFC-PUNE', target: 'COURT:DSC-PUNE', relation: 'APPEALS_TO' },
    ]);
  });
});
