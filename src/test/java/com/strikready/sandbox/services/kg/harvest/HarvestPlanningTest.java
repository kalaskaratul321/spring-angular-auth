package com.strikready.sandbox.services.kg.harvest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import org.junit.Test;

public class HarvestPlanningTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

	private static HarvestSource dated(LocalDate start, LocalDate next) {
		HarvestSource s = new HarvestSource();
		s.setUrlTemplate("https://court.gov.in/judgments?from={from}&to={to}");
		s.setStartDate(start);
		s.setNextWindowStart(next);
		s.setWindowDays(7);
		s.setOverlapDays(10);
		s.setMaxWindowsPerRun(3);
		return s;
	}

	@Test
	public void backfillStartsAtOldestDateAndIsBoundedPerRun() {
		List<HarvestWindow> w = HarvestWindowPlanner.plan(dated(LocalDate.of(1990, 1, 1), null), TODAY);
		assertEquals(3, w.size());
		assertEquals(LocalDate.of(1990, 1, 1), w.get(0).getStart());
		assertEquals(LocalDate.of(1990, 1, 7), w.get(0).getEnd());
		assertEquals(LocalDate.of(1990, 1, 8), w.get(1).getStart());
		assertEquals(LocalDate.of(1990, 1, 21), w.get(2).getEnd());
	}

	@Test
	public void backfillResumesFromCheckpoint() {
		List<HarvestWindow> w = HarvestWindowPlanner.plan(dated(LocalDate.of(1990, 1, 1), LocalDate.of(2005, 3, 1)),
				TODAY);
		assertEquals(LocalDate.of(2005, 3, 1), w.get(0).getStart());
		assertEquals(HarvestSource.Mode.BACKFILL,
				HarvestWindowPlanner.modeFor(dated(null, LocalDate.of(2005, 3, 1)), TODAY));
	}

	@Test
	public void incrementalRechecksOverlapAndEndsToday() {
		HarvestSource s = dated(LocalDate.of(1990, 1, 1), TODAY.plusDays(1));
		s.setMaxWindowsPerRun(30);
		List<HarvestWindow> w = HarvestWindowPlanner.plan(s, TODAY);
		assertEquals(TODAY.minusDays(9), w.get(0).getStart());
		assertEquals(TODAY, w.get(w.size() - 1).getEnd());
		assertEquals(HarvestSource.Mode.INCREMENTAL, HarvestWindowPlanner.modeFor(s, TODAY));
	}

	@Test
	public void afterDowntimeIncrementalCoversTheGap() {
		HarvestSource s = dated(LocalDate.of(1990, 1, 1), TODAY.minusDays(25));
		s.setMaxWindowsPerRun(30);
		List<HarvestWindow> w = HarvestWindowPlanner.plan(s, TODAY);
		assertEquals(TODAY.minusDays(25), w.get(0).getStart());
		assertEquals(TODAY, w.get(w.size() - 1).getEnd());
	}

	@Test
	public void overlapNeverGoesBeforeStartDate() {
		HarvestSource s = dated(TODAY.minusDays(3), TODAY.plusDays(1));
		assertEquals(TODAY.minusDays(3), HarvestWindowPlanner.plan(s, TODAY).get(0).getStart());
	}

	@Test
	public void undatedAndUrlListSources() {
		HarvestSource latest = new HarvestSource();
		latest.setUrlTemplate("https://court.gov.in/latest?page={page}");
		assertEquals(1, HarvestWindowPlanner.plan(latest, TODAY).size());
		assertTrue(!HarvestWindowPlanner.plan(latest, TODAY).get(0).isDated());

		HarvestSource list = new HarvestSource();
		list.setType(HarvestSource.Type.URL_LIST);
		assertEquals(1, HarvestWindowPlanner.plan(list, TODAY).size());
		list.setUrlListDone(true);
		assertTrue(HarvestWindowPlanner.plan(list, TODAY).isEmpty());
	}

	@Test
	public void dedupKeyIgnoresCosmeticUrlDifferences() {
		String k = UrlKeys.dedupKey("https://BombayHighCourt.nic.in/data/judgements/2024/a.pdf");
		assertEquals(k, UrlKeys.dedupKey("https://bombayhighcourt.nic.in:443/data/./judgements/2024/a.pdf#page=2"));
		assertEquals(k, UrlKeys.dedupKey("http://bombayhighcourt.nic.in/data/judgements/2024/a.pdf"));
		assertNotEquals(k, UrlKeys.dedupKey("https://bombayhighcourt.nic.in/data/judgements/2024/b.pdf"));
		assertNotEquals(UrlKeys.dedupKey("https://x.gov.in/view?id=1"), UrlKeys.dedupKey("https://x.gov.in/view?id=2"));
	}

	@Test
	public void backoffGrowsAndIsCapped() {
		Duration first = HarvestQueue.backoff(1, 300, 43200);
		Duration third = HarvestQueue.backoff(3, 300, 43200);
		Duration tenth = HarvestQueue.backoff(10, 300, 43200);
		assertTrue(first.getSeconds() >= 240 && first.getSeconds() <= 360);
		assertTrue(third.getSeconds() >= 960 && third.getSeconds() <= 1440);
		assertTrue(tenth.getSeconds() <= 43200 * 1.2);
	}
}
