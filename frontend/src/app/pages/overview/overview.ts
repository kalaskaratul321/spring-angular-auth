import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  CategorySummary,
  COURT_LEVEL_LABELS,
  COURT_LEVEL_ORDER,
  CourtLevel,
  VERDICT_LABELS,
  VerdictResult,
} from '../../core/api.models';
import { KgApiService } from '../../core/kg-api.service';
import { CATEGORY_COLORS } from '../../graph/graph-style';

interface LevelBar {
  level: CourtLevel;
  label: string;
  count: number;
  pct: number;
}

/** Category dashboard: case counts per category, by court level and verdict. */
@Component({
  selector: 'app-overview',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
})
export class Overview {
  private readonly api = inject(KgApiService);

  protected readonly summaries = signal<CategorySummary[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly colors = CATEGORY_COLORS;

  protected readonly total = computed(() => (this.summaries() ?? []).reduce((s, c) => s + c.totalCases, 0));
  protected readonly withCases = computed(() =>
    (this.summaries() ?? []).filter((c) => c.totalCases > 0).sort((a, b) => b.totalCases - a.totalCases),
  );
  protected readonly empty = computed(() =>
    (this.summaries() ?? []).filter((c) => c.totalCases === 0 && c.category !== 'OTHER'),
  );

  constructor() {
    this.api.categorySummary().subscribe({
      next: (s) => this.summaries.set(s),
      error: () => this.error.set('Could not load the category summary.'),
    });
  }

  protected levels(s: CategorySummary): LevelBar[] {
    const max = Math.max(1, ...Object.values(s.casesByCourtLevel).map((v) => v ?? 0));
    return COURT_LEVEL_ORDER.filter((l) => (s.casesByCourtLevel[l] ?? 0) > 0).map((l) => ({
      level: l,
      label: COURT_LEVEL_LABELS[l],
      count: s.casesByCourtLevel[l] ?? 0,
      pct: Math.round(((s.casesByCourtLevel[l] ?? 0) / max) * 100),
    }));
  }

  protected verdicts(s: CategorySummary): { label: string; count: number }[] {
    return (Object.entries(s.casesByVerdict) as [VerdictResult, number][])
      .filter(([, n]) => n > 0)
      .sort((a, b) => b[1] - a[1])
      .map(([v, n]) => ({ label: VERDICT_LABELS[v], count: n }));
  }

  protected top(map: Record<string, number>, n = 5): { key: string; count: number }[] {
    return Object.entries(map)
      .slice(0, n)
      .map(([key, count]) => ({ key: key.replace(/_/g, ' ').replace(':', ' s. '), count }));
  }
}
