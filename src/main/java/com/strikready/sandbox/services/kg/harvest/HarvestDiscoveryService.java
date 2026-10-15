package com.strikready.sandbox.services.kg.harvest;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.repositories.kg.harvest.HarvestSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Walks each source's date windows oldest-first and queues every document
 * link found. Safe to run on several instances: a source is discovered by
 * only one instance at a time (lease on the source document), and the
 * checkpoint is saved after every window so a crash loses at most one window,
 * which is simply re-read next run (duplicates are dropped by the queue).
 */
@Service
public class HarvestDiscoveryService {

	private static final Logger log = LoggerFactory.getLogger(HarvestDiscoveryService.class);
	private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private HarvestSourceRepository sourceRepository;

	@Autowired
	private HarvestQueue queue;

	@Autowired
	private List<SourceConnector> connectors;

	@Value("${kg.harvest.lease-seconds:900}")
	private long leaseSeconds;

	@Value("${kg.harvest.source-retry-max-seconds:21600}")
	private long sourceRetryMaxSeconds;

	private Clock clock = Clock.system(INDIA);

	/** Runs discovery for every enabled source that is due; returns tasks enqueued. */
	public long discoverAll() {
		long total = 0;
		for (HarvestSource s : sourceRepository.findByEnabledTrue()) {
			total += discover(s.getId(), false);
		}
		return total;
	}

	/**
	 * Discovers one source. {@code force} ignores the failure backoff
	 * ({@code nextRunAt}) but never another instance's lease.
	 */
	public long discover(String sourceId, boolean force) {
		HarvestSource source = acquire(sourceId, force);
		if (source == null) {
			return 0;
		}
		SourceConnector connector = connectorFor(source);
		LocalDate today = LocalDate.now(clock);
		long enqueued = 0;
		HarvestWindow current = null;
		try {
			for (HarvestWindow window : HarvestWindowPlanner.plan(source, today)) {
				current = window;
				List<DiscoveredDocument> docs = connector.discover(source, window);
				long added = 0;
				for (DiscoveredDocument d : docs) {
					if (queue.enqueue(source.getId(), d, window.getStart())) {
						added++;
					}
				}
				enqueued += added;
				checkpoint(source, window, docs.size(), added, today);
				log.info("Harvest source '{}' window {}: {} links, {} new", source.getName(), window, docs.size(), added);
			}
			release(source, null);
		} catch (IOException | RuntimeException e) {
			log.warn("Harvest source '{}' failed at window {}: {}", source.getName(), current, e.getMessage());
			release(source, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
		return enqueued;
	}

	/**
	 * Dry run: what the connector finds for one window on the live site,
	 * without queueing anything or touching the checkpoint.
	 */
	public Map<String, Object> preview(String sourceId, LocalDate from, LocalDate to, int limit) {
		HarvestSource source = sourceRepository.findById(sourceId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown source: " + sourceId));
		SourceConnector connector = connectorFor(source);
		Map<String, Object> out = new LinkedHashMap<>();
		HarvestWindow window = HarvestWindowPlanner.isDated(source) ? new HarvestWindow(from, to)
				: HarvestWindow.undated();
		out.put("window", window.toString());
		try {
			out.put("form", connector.diagnose(source));
			List<DiscoveredDocument> docs = connector.discover(source, window);
			out.put("found", docs.size());
			out.put("documents", docs.subList(0, Math.min(docs.size(), Math.max(1, limit))));
		} catch (IOException | RuntimeException e) {
			out.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
		}
		return out;
	}

	/** Atomically takes the discovery lease if the source is enabled, due and not leased elsewhere. */
	private HarvestSource acquire(String sourceId, boolean force) {
		Instant now = Instant.now();
		// Two $or conditions must be combined with $and: as sibling keys one would overwrite the other.
		List<Criteria> conditions = new ArrayList<>();
		conditions.add(new Criteria().orOperator(Criteria.where("leaseUntil").is(null),
				Criteria.where("leaseUntil").lt(now), Criteria.where("leaseOwner").is(HarvestQueue.INSTANCE_ID)));
		if (!force) {
			conditions.add(new Criteria().orOperator(Criteria.where("nextRunAt").is(null),
					Criteria.where("nextRunAt").lte(now)));
		}
		Query q = Query.query(Criteria.where("_id").is(sourceId).and("enabled").is(true)
				.andOperator(conditions.toArray(new Criteria[0])));
		Update u = new Update().set("leaseOwner", HarvestQueue.INSTANCE_ID)
				.set("leaseUntil", now.plusSeconds(leaseSeconds)).set("lastRunAt", now);
		return mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), HarvestSource.class);
	}

	/** Saves progress after a window and renews the lease. */
	private void checkpoint(HarvestSource source, HarvestWindow window, long discovered, long added, LocalDate today) {
		Update u = new Update().inc("discoveredCount", discovered).inc("enqueuedCount", added)
				.set("leaseUntil", Instant.now().plusSeconds(leaseSeconds));
		if (source.getType() == HarvestSource.Type.URL_LIST) {
			u.set("urlListDone", true);
		} else if (window.isDated()) {
			LocalDate next = window.getEnd().plusDays(1);
			if (source.getNextWindowStart() == null || next.isAfter(source.getNextWindowStart())) {
				source.setNextWindowStart(next);
				u.set("nextWindowStart", next);
			}
			u.set("mode", HarvestWindowPlanner.modeFor(source, today));
		}
		mongo.updateFirst(owned(source), u, HarvestSource.class);
	}

	private void release(HarvestSource source, String error) {
		Instant now = Instant.now();
		Update u = new Update().unset("leaseOwner").unset("leaseUntil");
		if (error == null) {
			u.set("lastSuccessAt", now).set("lastError", null).set("consecutiveFailures", 0).set("nextRunAt", null);
		} else {
			int failures = source.getConsecutiveFailures() + 1;
			u.set("lastError", error).set("consecutiveFailures", failures).set("nextRunAt",
					now.plus(HarvestQueue.backoff(failures, 300, sourceRetryMaxSeconds)));
		}
		mongo.updateFirst(owned(source), u, HarvestSource.class);
	}

	private static Query owned(HarvestSource source) {
		return Query.query(Criteria.where("_id").is(source.getId()).and("leaseOwner").is(HarvestQueue.INSTANCE_ID));
	}

	private SourceConnector connectorFor(HarvestSource source) {
		for (SourceConnector c : connectors) {
			if (c.supports(source.getType())) {
				return c;
			}
		}
		throw new IllegalStateException("No connector for source type " + source.getType());
	}

	void setClock(Clock clock) {
		this.clock = clock;
	}
}
