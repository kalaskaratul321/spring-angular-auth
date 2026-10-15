import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CaseCategory,
  CategoryInfo,
  CategorySummary,
  Court,
  CourtLevel,
  CourtTreeNode,
  GraphView,
  LegalCase,
  Page,
  ReferenceView,
  VerdictResult,
} from './api.models';

/** Client for the knowledge-graph endpoints under /api/kg. */
@Injectable({ providedIn: 'root' })
export class KgApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/kg';

  categories(): Observable<CategoryInfo[]> {
    return this.http.get<CategoryInfo[]>(`${this.base}/categories`);
  }

  categorySummary(): Observable<CategorySummary[]> {
    return this.http.get<CategorySummary[]>(`${this.base}/categories/summary`);
  }

  categoryGraph(category: CaseCategory, courtLevel: CourtLevel | null, limit: number): Observable<GraphView> {
    let params = new HttpParams().set('limit', limit);
    if (courtLevel) {
      params = params.set('courtLevel', courtLevel);
    }
    return this.http.get<GraphView>(`${this.base}/categories/${category}/graph`, { params });
  }

  caseGraph(id: string, depth = 1, category?: CaseCategory | null): Observable<GraphView> {
    let params = new HttpParams().set('depth', depth);
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<GraphView>(`${this.base}/cases/${encodeURIComponent(id)}/graph`, { params });
  }

  /** Neighbourhood of a non-case node, using the matching endpoint for its type. */
  nodeGraph(nodeId: string, category?: CaseCategory | null): Observable<GraphView> | null {
    const [type, ...rest] = nodeId.split(':');
    const id = rest.join(':');
    let params = new HttpParams();
    if (category) {
      params = params.set('category', category);
    }
    switch (type) {
      case 'CASE':
        return this.caseGraph(id, 1, category);
      case 'COURT':
        return this.http.get<GraphView>(`${this.base}/courts/${encodeURIComponent(id)}/graph`, { params });
      case 'STATUTE': {
        const [act, section] = id.split(':');
        if (!section) {
          return null;
        }
        return this.http.get<GraphView>(
          `${this.base}/statutes/${encodeURIComponent(act)}/${encodeURIComponent(section)}/graph`,
          { params },
        );
      }
      case 'ISSUE':
        return this.http.get<GraphView>(`${this.base}/issues/graph`, {
          params: params.set('topic', id.replace(/_/g, ' ')),
        });
      case 'CITATION':
        return this.http.get<GraphView>(`${this.base}/citations/graph`, {
          params: params.set('citation', id.replace(/_/g, ' ')),
        });
      default:
        return null;
    }
  }

  getCase(id: string): Observable<LegalCase> {
    return this.http.get<LegalCase>(`${this.base}/cases/${encodeURIComponent(id)}`);
  }

  references(id: string): Observable<ReferenceView[]> {
    return this.http.get<ReferenceView[]>(`${this.base}/cases/${encodeURIComponent(id)}/references`);
  }

  citedBy(id: string): Observable<ReferenceView[]> {
    return this.http.get<ReferenceView[]>(`${this.base}/cases/${encodeURIComponent(id)}/cited-by`);
  }

  appealChain(id: string): Observable<LegalCase[]> {
    return this.http.get<LegalCase[]>(`${this.base}/cases/${encodeURIComponent(id)}/appeal-chain`);
  }

  search(
    q: string,
    category: CaseCategory | null,
    verdict: VerdictResult | null,
    page = 0,
    size = 20,
  ): Observable<Page<LegalCase>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) {
      params = params.set('q', q);
    }
    if (category) {
      params = params.set('category', category);
    }
    if (verdict) {
      params = params.set('verdict', verdict);
    }
    return this.http.get<Page<LegalCase>>(`${this.base}/cases/search`, { params });
  }

  courts(): Observable<Court[]> {
    return this.http.get<Court[]>(`${this.base}/courts`);
  }

  courtHierarchy(): Observable<CourtTreeNode[]> {
    return this.http.get<CourtTreeNode[]>(`${this.base}/courts/hierarchy`);
  }
}
