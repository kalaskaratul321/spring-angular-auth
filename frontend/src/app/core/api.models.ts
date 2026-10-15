// Shapes returned by the Spring Boot knowledge-graph API (/api/kg).

export type CaseCategory =
  | 'CIVIL'
  | 'CRIMINAL'
  | 'LAND'
  | 'RERA'
  | 'FAMILY'
  | 'CONSUMER'
  | 'LABOUR_SERVICE'
  | 'CONSTITUTIONAL'
  | 'COMMERCIAL'
  | 'TAX'
  | 'OTHER';

export type CourtLevel =
  | 'HIGH_COURT'
  | 'APPELLATE_TRIBUNAL'
  | 'DISTRICT_COURT'
  | 'SESSIONS_COURT'
  | 'FAMILY_COURT'
  | 'STATE_COMMISSION'
  | 'TRIBUNAL'
  | 'REVENUE_APPELLATE'
  | 'CIVIL_JUDGE_SENIOR_DIVISION'
  | 'DISTRICT_COMMISSION'
  | 'CIVIL_JUDGE_JUNIOR_DIVISION'
  | 'JUDICIAL_MAGISTRATE'
  | 'REVENUE_AUTHORITY';

export type NodeType =
  | 'CASE'
  | 'COURT'
  | 'CATEGORY'
  | 'STATUTE'
  | 'JUDGE'
  | 'PARTY'
  | 'ISSUE'
  | 'VERDICT'
  | 'CITATION';

export type RelationType =
  | 'HEARD_IN'
  | 'BELONGS_TO'
  | 'INVOKES'
  | 'DECIDED_BY'
  | 'INVOLVES_PARTY'
  | 'APPEAL_FROM'
  | 'RAISES_ISSUE'
  | 'HAS_VERDICT'
  | 'CITES'
  | 'FOLLOWS'
  | 'DISTINGUISHES'
  | 'OVERRULES'
  | 'APPEALS_TO';

export type VerdictResult =
  | 'ALLOWED'
  | 'PARTLY_ALLOWED'
  | 'DISMISSED'
  | 'CONVICTED'
  | 'ACQUITTED'
  | 'BAIL_GRANTED'
  | 'BAIL_REJECTED'
  | 'DECREED'
  | 'REMANDED'
  | 'SETTLED'
  | 'WITHDRAWN'
  | 'DISPOSED_OF'
  | 'PENDING'
  | 'OTHER';

export interface GraphNode {
  id: string;
  type: NodeType;
  label: string;
  category?: CaseCategory | null;
  properties: Record<string, unknown>;
}

export interface GraphEdge {
  source: string;
  target: string;
  relation: RelationType;
}

export interface GraphView {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface CategoryInfo {
  code: CaseCategory;
  name: string;
  description: string;
}

export interface CategorySummary {
  category: CaseCategory;
  displayName: string;
  totalCases: number;
  casesByCourtLevel: Partial<Record<CourtLevel, number>>;
  casesByVerdict: Partial<Record<VerdictResult, number>>;
  topStatutes: Record<string, number>;
  topIssues: Record<string, number>;
}

export interface StatuteReference {
  act: string;
  section?: string;
  sourceUrl?: string;
}

export interface LegalIssue {
  topic?: string;
  question?: string;
  finding?: string;
}

export interface Verdict {
  result?: VerdictResult;
  summary?: string;
  reliefGranted?: string;
  sentence?: string;
}

export interface SourceDocument {
  url: string;
  resolvedUrl?: string;
  host?: string;
  contentType?: string;
  sha256?: string;
  fetchedAt?: string;
  documentTitle?: string;
  pageCount?: number;
}

export interface LegalCase {
  id: string;
  cnr?: string;
  caseNumber?: string;
  caseType?: string;
  title?: string;
  courtCode?: string;
  courtLevel?: CourtLevel;
  category?: CaseCategory;
  subCategory?: string;
  categoryConfidence?: number;
  filingYear?: number;
  decisionDate?: string;
  status?: string;
  verdict?: Verdict | null;
  judges?: string[];
  petitioners?: string[];
  respondents?: string[];
  statutes?: StatuteReference[];
  issues?: LegalIssue[];
  citations?: string[];
  appealFromCaseId?: string;
  summary?: string;
  source?: SourceDocument | null;
}

export interface ReferenceView {
  treatment: RelationType;
  citation?: string;
  title?: string;
  paragraph?: string;
  sourceUrl?: string;
  caseId?: string;
  courtCode?: string;
  courtLevel?: CourtLevel;
  verdict?: VerdictResult;
}

export interface Court {
  id: string;
  code: string;
  name: string;
  level: CourtLevel;
  district?: string;
  bench?: string;
  appellateCourtCode?: string;
  categories: CaseCategory[];
}

export interface CourtTreeNode {
  court: Court;
  subordinates: CourtTreeNode[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface LoginResponse {
  username: string;
  token: string;
  role: string;
  organizations?: string;
  firstname?: string;
  lastname?: string;
}

export const COURT_LEVEL_LABELS: Record<CourtLevel, string> = {
  HIGH_COURT: 'High Court',
  APPELLATE_TRIBUNAL: 'Appellate Tribunal',
  DISTRICT_COURT: 'District Court',
  SESSIONS_COURT: 'Sessions Court',
  FAMILY_COURT: 'Family Court',
  STATE_COMMISSION: 'State Commission',
  TRIBUNAL: 'Tribunal / Authority',
  REVENUE_APPELLATE: 'Revenue Appellate',
  CIVIL_JUDGE_SENIOR_DIVISION: 'Civil Judge (Sr. Div.)',
  DISTRICT_COMMISSION: 'District Commission',
  CIVIL_JUDGE_JUNIOR_DIVISION: 'Civil Judge (Jr. Div.)',
  JUDICIAL_MAGISTRATE: 'JMFC',
  REVENUE_AUTHORITY: 'Revenue Authority',
};

/** High Court first, as on the backend (CourtLevel rank). */
export const COURT_LEVEL_ORDER: CourtLevel[] = [
  'HIGH_COURT',
  'APPELLATE_TRIBUNAL',
  'DISTRICT_COURT',
  'SESSIONS_COURT',
  'FAMILY_COURT',
  'STATE_COMMISSION',
  'TRIBUNAL',
  'REVENUE_APPELLATE',
  'CIVIL_JUDGE_SENIOR_DIVISION',
  'DISTRICT_COMMISSION',
  'CIVIL_JUDGE_JUNIOR_DIVISION',
  'JUDICIAL_MAGISTRATE',
  'REVENUE_AUTHORITY',
];

export const VERDICT_LABELS: Record<VerdictResult, string> = {
  ALLOWED: 'Allowed',
  PARTLY_ALLOWED: 'Partly allowed',
  DISMISSED: 'Dismissed',
  CONVICTED: 'Convicted',
  ACQUITTED: 'Acquitted',
  BAIL_GRANTED: 'Bail granted',
  BAIL_REJECTED: 'Bail rejected',
  DECREED: 'Decreed',
  REMANDED: 'Remanded',
  SETTLED: 'Settled',
  WITHDRAWN: 'Withdrawn',
  DISPOSED_OF: 'Disposed of',
  PENDING: 'Pending',
  OTHER: 'Other',
};

export const RELATION_LABELS: Record<RelationType, string> = {
  HEARD_IN: 'heard in',
  BELONGS_TO: 'category',
  INVOKES: 'invokes',
  DECIDED_BY: 'decided by',
  INVOLVES_PARTY: 'party',
  APPEAL_FROM: 'appeal from',
  RAISES_ISSUE: 'issue',
  HAS_VERDICT: 'verdict',
  CITES: 'cites',
  FOLLOWS: 'follows',
  DISTINGUISHES: 'distinguishes',
  OVERRULES: 'overrules',
  APPEALS_TO: 'appeals to',
};
