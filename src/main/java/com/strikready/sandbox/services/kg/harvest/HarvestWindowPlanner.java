package com.strikready.sandbox.services.kg.harvest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;

/**
 * Decides which date windows a run should discover, oldest first.
 *
 * <ul>
 * <li>Backfill: from the checkpoint ({@code nextWindowStart}, initially
 * {@code startDate}) forward, at most {@code maxWindowsPerRun} windows per run,
 * so years of history are worked through in steady, resumable steps.</li>
 * <li>Incremental (checkpoint within {@code overlapDays} of today): the last
 * {@code overlapDays} days are re-read every run to catch judgments uploaded
 * late, plus any days since the last run. Re-reading is cheap because already
 * queued URLs are skipped.</li>
 * </ul>
 */
public final class HarvestWindowPlanner {

	private HarvestWindowPlanner() {
	}

	public static List<HarvestWindow> plan(HarvestSource source, LocalDate today) {
		if (source.getType() == HarvestSource.Type.URL_LIST) {
			return source.isUrlListDone() ? Collections.<HarvestWindow>emptyList()
					: Collections.singletonList(HarvestWindow.undated());
		}
		if (!isDated(source)) {
			return Collections.singletonList(HarvestWindow.undated());
		}
		LocalDate first = source.getStartDate() != null ? source.getStartDate() : today;
		LocalDate next = source.getNextWindowStart() != null ? source.getNextWindowStart() : first;
		int overlap = Math.max(1, source.getOverlapDays());
		LocalDate recheckFrom = today.minusDays(overlap - 1L);

		LocalDate start = next;
		if (next.isAfter(recheckFrom)) {
			start = recheckFrom.isBefore(first) ? first : recheckFrom;
		}

		int size = Math.max(1, source.getWindowDays());
		int max = Math.max(1, source.getMaxWindowsPerRun());
		List<HarvestWindow> windows = new ArrayList<>();
		LocalDate s = start;
		while (!s.isAfter(today) && windows.size() < max) {
			LocalDate e = s.plusDays(size - 1L);
			if (e.isAfter(today)) {
				e = today;
			}
			windows.add(new HarvestWindow(s, e));
			s = e.plusDays(1);
		}
		return windows;
	}

	/** Mode implied by a checkpoint: incremental once backfill reached the overlap period. */
	public static HarvestSource.Mode modeFor(HarvestSource source, LocalDate today) {
		LocalDate next = source.getNextWindowStart();
		int overlap = Math.max(1, source.getOverlapDays());
		return next != null && next.isAfter(today.minusDays(overlap)) ? HarvestSource.Mode.INCREMENTAL
				: HarvestSource.Mode.BACKFILL;
	}

	public static boolean isDated(HarvestSource source) {
		if (source.getType() == HarvestSource.Type.SEARCH_FORM) {
			return true;
		}
		String t = source.getUrlTemplate();
		return t != null && (t.contains("{from}") || t.contains("{to}"));
	}
}
