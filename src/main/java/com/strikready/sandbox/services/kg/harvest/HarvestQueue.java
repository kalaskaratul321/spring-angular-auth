package com.strikready.sandbox.services.kg.harvest;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.mongodb.client.result.UpdateResult;
import com.strikready.sandbox.models.kg.harvest.HarvestTask;
import com.strikready.sandbox.repositories.kg.LegalCaseRepository;
import com.strikready.sandbox.services.kg.importer.ImportResult;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Persistent work queue for harvesting, stored in MongoDB so it survives
 * restarts and is shared by every application instance.
 *
 * <ul>
 * <li>No duplicates: enqueue is an upsert on the unique {@code dedupKey}; a URL
 * already queued (or already imported) is skipped.</li>
 * <li>Failover: a claim atomically moves a task to IN_PROGRESS with a lease.
 * If the worker dies, the lease expires and another instance claims it.</li>
 * <li>Retries: failures back off exponentially; permanent failures and tasks
 * out of attempts go to DEAD (dead letter) and can be re-queued.</li>
 * </ul>
 */
@Service
public class HarvestQueue {

	/** Identifies this JVM in lease fields. */
	public static final String INSTANCE_ID = ManagementFactory.getRuntimeMXBean().getName() + "/"
			+ UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private LegalCaseRepository caseRepository;

	@Value("${kg.harvest.max-attempts:6}")
	private int maxAttempts;

	@Value("${kg.harvest.lease-seconds:900}")
	private long leaseSeconds;

	@Value("${kg.harvest.retry-base-seconds:300}")
	private long retryBaseSeconds;

	@Value("${kg.harvest.retry-max-seconds:43200}")
	private long retryMaxSeconds;

	/** @return true if a new task was created, false if the URL was already known. */
	public boolean enqueue(String sourceId, DiscoveredDocument doc, LocalDate windowStart) {
		String normalised = UrlKeys.normalise(doc.getUrl());
		if (caseRepository.existsBySourceUrl(doc.getUrl()) || caseRepository.existsBySourceUrl(normalised)) {
			return false;
		}
		Instant now = Instant.now();
		Query q = Query.query(Criteria.where("dedupKey").is(UrlKeys.dedupKey(doc.getUrl())));
		Update u = new Update().setOnInsert("url", doc.getUrl()).setOnInsert("sourceId", sourceId)
				.setOnInsert("windowStart", windowStart).setOnInsert("titleHint", doc.getTitleHint())
				.setOnInsert("status", HarvestTask.Status.PENDING).setOnInsert("attempts", 0)
				.setOnInsert("nextAttemptAt", now).setOnInsert("createdAt", now).setOnInsert("updatedAt", now);
		try {
			UpdateResult r = mongo.upsert(q, u, HarvestTask.class);
			return r.getUpsertedId() != null;
		} catch (org.springframework.dao.DuplicateKeyException race) {
			// Another instance inserted the same key concurrently.
			return false;
		}
	}

	/**
	 * Claims the oldest runnable task: PENDING/RETRY whose time has come, or
	 * IN_PROGRESS whose lease expired (its worker died).
	 */
	public HarvestTask claim() {
		Instant now = Instant.now();
		Criteria runnable = new Criteria().orOperator(
				Criteria.where("status").in(HarvestTask.Status.PENDING, HarvestTask.Status.RETRY)
						.and("nextAttemptAt").lte(now),
				Criteria.where("status").is(HarvestTask.Status.IN_PROGRESS).and("leaseUntil").lt(now));
		Query q = Query.query(runnable)
				.with(Sort.by(Sort.Order.asc("windowStart"), Sort.Order.asc("createdAt")));
		Update u = new Update().set("status", HarvestTask.Status.IN_PROGRESS).set("leaseOwner", INSTANCE_ID)
				.set("leaseUntil", now.plusSeconds(leaseSeconds)).set("updatedAt", now).inc("attempts", 1);
		return mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), HarvestTask.class);
	}

	public void complete(HarvestTask task, ImportResult result) {
		Update u = new Update().set("status", HarvestTask.Status.DONE).set("result", result.getStatus())
				.set("caseId", result.getLegalCase() == null ? null : result.getLegalCase().getId())
				.set("lastError", null).unset("leaseOwner").unset("leaseUntil").set("updatedAt", Instant.now());
		mongo.updateFirst(owned(task), u, HarvestTask.class);
	}

	/** Records a failure: RETRY with backoff, or DEAD if permanent or out of attempts. */
	public HarvestTask.Status fail(HarvestTask task, String error, boolean retryable) {
		boolean dead = !retryable || task.getAttempts() >= maxAttempts;
		Instant now = Instant.now();
		Update u = new Update().set("lastError", truncate(error)).unset("leaseOwner").unset("leaseUntil")
				.set("updatedAt", now);
		if (dead) {
			u.set("status", HarvestTask.Status.DEAD);
		} else {
			u.set("status", HarvestTask.Status.RETRY).set("nextAttemptAt",
					now.plus(backoff(task.getAttempts(), retryBaseSeconds, retryMaxSeconds)));
		}
		mongo.updateFirst(owned(task), u, HarvestTask.class);
		return dead ? HarvestTask.Status.DEAD : HarvestTask.Status.RETRY;
	}

	/** Puts DEAD tasks (optionally of one source) back in the queue with fresh attempts. */
	public long retryDead(String sourceId) {
		Criteria c = Criteria.where("status").is(HarvestTask.Status.DEAD);
		if (sourceId != null) {
			c = c.and("sourceId").is(sourceId);
		}
		Update u = new Update().set("status", HarvestTask.Status.PENDING).set("attempts", 0)
				.set("nextAttemptAt", Instant.now()).set("updatedAt", Instant.now());
		return mongo.updateMulti(Query.query(c), u, HarvestTask.class).getModifiedCount();
	}

	public boolean retry(String taskId) {
		Criteria c = Criteria.where("_id").is(taskId).and("status")
				.in(HarvestTask.Status.DEAD, HarvestTask.Status.RETRY);
		Update u = new Update().set("status", HarvestTask.Status.PENDING).set("attempts", 0)
				.set("nextAttemptAt", Instant.now()).set("updatedAt", Instant.now());
		return mongo.updateFirst(Query.query(c), u, HarvestTask.class).getModifiedCount() == 1;
	}

	public Map<HarvestTask.Status, Long> countsByStatus(String sourceId) {
		Aggregation agg = sourceId == null
				? Aggregation.newAggregation(Aggregation.group("status").count().as("n"))
				: Aggregation.newAggregation(Aggregation.match(Criteria.where("sourceId").is(sourceId)),
						Aggregation.group("status").count().as("n"));
		Map<HarvestTask.Status, Long> out = new EnumMap<>(HarvestTask.Status.class);
		for (HarvestTask.Status s : HarvestTask.Status.values()) {
			out.put(s, 0L);
		}
		for (Document d : mongo.aggregate(agg, HarvestTask.class, Document.class).getMappedResults()) {
			out.put(HarvestTask.Status.valueOf(d.getString("_id")), ((Number) d.get("n")).longValue());
		}
		return out;
	}

	/** Only the instance holding the lease may finish a task (guards against a revived stale worker). */
	private static Query owned(HarvestTask task) {
		return Query.query(Criteria.where("_id").is(task.getId()).and("leaseOwner").is(INSTANCE_ID)
				.and("status").is(HarvestTask.Status.IN_PROGRESS));
	}

	/** base * 2^(attempt-1), capped, with +/-20% jitter so retries of a failed batch spread out. */
	static Duration backoff(int attempt, long baseSeconds, long maxSeconds) {
		double seconds = Math.min((double) maxSeconds, baseSeconds * Math.pow(2, Math.max(0, attempt - 1)));
		double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
		return Duration.ofSeconds(Math.max(1L, (long) (seconds * jitter)));
	}

	private static String truncate(String s) {
		return s == null ? null : s.length() > 1000 ? s.substring(0, 1000) : s;
	}
}
