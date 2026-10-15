import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { KgApiService } from './kg-api.service';

describe('KgApiService', () => {
  let api: KgApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(KgApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('builds category graph requests with optional court level', () => {
    api.categoryGraph('CRIMINAL', 'HIGH_COURT', 50).subscribe();
    const req = http.expectOne((r) => r.url === '/api/kg/categories/CRIMINAL/graph');
    expect(req.request.params.get('courtLevel')).toBe('HIGH_COURT');
    expect(req.request.params.get('limit')).toBe('50');
    req.flush({ nodes: [], edges: [] });
  });

  it('maps node ids to the matching neighbourhood endpoint', () => {
    api.nodeGraph('COURT:BHC-MUM')!.subscribe();
    http.expectOne('/api/kg/courts/BHC-MUM/graph').flush({ nodes: [], edges: [] });

    api.nodeGraph('STATUTE:NI_ACT:138')!.subscribe();
    http.expectOne('/api/kg/statutes/NI_ACT/138/graph').flush({ nodes: [], edges: [] });

    api.nodeGraph('ISSUE:delay_in_possession_interest')!.subscribe();
    const issue = http.expectOne((r) => r.url === '/api/kg/issues/graph');
    expect(issue.request.params.get('topic')).toBe('delay in possession interest');
    issue.flush({ nodes: [], edges: [] });

    api.nodeGraph('CITATION:2019_4_scc_197')!.subscribe();
    const cit = http.expectOne((r) => r.url === '/api/kg/citations/graph');
    expect(cit.request.params.get('citation')).toBe('2019 4 scc 197');
    cit.flush({ nodes: [], edges: [] });

    api.nodeGraph('CASE:abc')!.subscribe();
    http.expectOne((r) => r.url === '/api/kg/cases/abc/graph').flush({ nodes: [], edges: [] });
  });

  it('returns null for node types without a neighbourhood endpoint', () => {
    expect(api.nodeGraph('JUDGE:r_k_patil')).toBeNull();
    expect(api.nodeGraph('VERDICT:DISMISSED')).toBeNull();
    expect(api.nodeGraph('STATUTE:CONSTITUTION')).toBeNull();
  });
});
