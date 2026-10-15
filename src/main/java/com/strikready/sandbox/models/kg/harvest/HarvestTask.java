package com.strikready.sandbox.models.kg.harvest;

import java.time.Instant;
import java.time.LocalDate;

import com.strikready.sandbox.services.kg.importer.ImportResult;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One document waiting to be imported. {@code dedupKey} (a hash of the
 * normalised URL) is unique, so a document discovered again by a later run,
 * an overlapping window or another source is never queued twice.
 *
 * Workers claim tasks with a time-limited lease; a task whose lease expired
 * (the worker died) is claimed again by another instance.
 */
@Document(collection = "kg_harvest_tasks")
@CompoundIndexes({
		@CompoundIndex(name = "claim_idx", def = "{'status': 1, 'nextAttemptAt': 1, 'windowStart': 1}"),
		@CompoundIndex(name = "lease_idx", def = "{'status': 1, 'leaseUntil': 1}"),
		@CompoundIndex(name = "source_status_idx", def = "{'sourceId': 1, 'status': 1}") })
public class HarvestTask {

	public enum Status {
		PENDING, IN_PROGRESS, RETRY, DONE,
		/** Gave up: permanent error or retries exhausted. Can be re-queued from the API. */
		DEAD
	}

	@Id
	private String id;
	@Indexed(unique = true)
	private String dedupKey;
	private String url;
	private String sourceId;
	/** Start of the discovery window, so the queue is worked oldest first. */
	private LocalDate windowStart;
	private String titleHint;

	private Status status = Status.PENDING;
	private int attempts;
	private Instant nextAttemptAt;
	private String leaseOwner;
	private Instant leaseUntil;

	private Instant createdAt;
	private Instant updatedAt;
	private String lastError;
	private ImportResult.Status result;
	private String caseId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDedupKey() {
		return dedupKey;
	}

	public void setDedupKey(String dedupKey) {
		this.dedupKey = dedupKey;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

	public LocalDate getWindowStart() {
		return windowStart;
	}

	public void setWindowStart(LocalDate windowStart) {
		this.windowStart = windowStart;
	}

	public String getTitleHint() {
		return titleHint;
	}

	public void setTitleHint(String titleHint) {
		this.titleHint = titleHint;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public void setNextAttemptAt(Instant nextAttemptAt) {
		this.nextAttemptAt = nextAttemptAt;
	}

	public String getLeaseOwner() {
		return leaseOwner;
	}

	public void setLeaseOwner(String leaseOwner) {
		this.leaseOwner = leaseOwner;
	}

	public Instant getLeaseUntil() {
		return leaseUntil;
	}

	public void setLeaseUntil(Instant leaseUntil) {
		this.leaseUntil = leaseUntil;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public ImportResult.Status getResult() {
		return result;
	}

	public void setResult(ImportResult.Status result) {
		this.result = result;
	}

	public String getCaseId() {
		return caseId;
	}

	public void setCaseId(String caseId) {
		this.caseId = caseId;
	}
}
