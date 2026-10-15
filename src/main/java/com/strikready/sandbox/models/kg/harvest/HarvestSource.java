package com.strikready.sandbox.models.kg.harvest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.strikready.sandbox.models.kg.CaseCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An official listing of judgments that is harvested incrementally, oldest
 * first, together with its checkpoint.
 *
 * For {@link Type#HTML_LISTING} the {@code urlTemplate} may contain
 * {@code {from}}, {@code {to}} (formatted with {@code dateFormat}) and
 * {@code {page}}; every link on the listing page that matches
 * {@code linkSelector} and {@code linkPattern} becomes a document to import.
 * Templates without date placeholders (e.g. a "latest judgments" page) are
 * read once per run. For {@link Type#SEARCH_FORM} the form on
 * {@code formPageUrl} is submitted with each window's dates instead.
 */
@Document(collection = "kg_harvest_sources")
public class HarvestSource {

	public enum Type {
		/** Date-paged HTML listing pages on an official site. */
		HTML_LISTING,
		/** A fixed list of document URLs, imported once. */
		URL_LIST,
		/** A search form (GET or POST) submitted with a date range, e.g. the Bombay High Court search. */
		SEARCH_FORM
	}

	public enum Mode {
		/** Walking forward from {@code startDate}. */
		BACKFILL,
		/** Caught up; each run re-checks the last {@code overlapDays} days plus anything new. */
		INCREMENTAL
	}

	@Id
	private String id;
	@Indexed(unique = true)
	private String name;
	private Type type = Type.HTML_LISTING;
	private boolean enabled = true;

	// ---- what to read
	private String urlTemplate;
	private String dateFormat = "dd-MM-yyyy";
	private String linkSelector = "a[href]";
	private String linkPattern = "(?i)\\.pdf(\\?.*)?$";
	private int firstPage = 1;
	private int maxPages = 50;
	private List<String> urls = new ArrayList<>();

	// ---- SEARCH_FORM
	/** Page that contains the search form. */
	private String formPageUrl;
	/** CSS selector of the form; empty = the first form that has from/to date fields. */
	private String formSelector;
	/** Names of the date inputs; empty = detected from names, ids, placeholders and labels. */
	private String fromField;
	private String toField;
	/** Extra values to set (e.g. bench or judgment type selects), overriding the form's defaults. */
	private Map<String, String> extraFields = new LinkedHashMap<>();
	/** CSS selector of the "next page" link in results. */
	private String nextPageSelector = "a[rel=next], a:matchesOwn((?i)^\\s*(next|›|»|>>)\\s*$)";
	/** Regex on the result page meaning "narrow your search"; the window is then split in half. */
	private String tooManyResultsPattern = "(?i)too many (results|records)|refine your search";

	/** Version of the built-in seed this source was created from (see HarvestSourceSeeder). */
	private int seedVersion;

	// ---- how to walk time
	/** Oldest date to harvest from. */
	private LocalDate startDate;
	private int windowDays = 7;
	/** In incremental mode, how many recent days are re-checked for late uploads. */
	private int overlapDays = 7;
	private int maxWindowsPerRun = 30;

	// ---- defaults applied to every imported case
	private String defaultCourtCode;
	private CaseCategory defaultCategory;

	// ---- checkpoint
	private Mode mode = Mode.BACKFILL;
	/** First day not yet fully discovered. */
	private LocalDate nextWindowStart;
	private boolean urlListDone;
	private Instant lastRunAt;
	private Instant lastSuccessAt;
	private String lastError;
	private int consecutiveFailures;
	/** Not run again before this time (backoff after failures). */
	private Instant nextRunAt;
	private long discoveredCount;
	private long enqueuedCount;

	// ---- discovery lease (one instance per source at a time)
	private String leaseOwner;
	private Instant leaseUntil;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUrlTemplate() {
		return urlTemplate;
	}

	public void setUrlTemplate(String urlTemplate) {
		this.urlTemplate = urlTemplate;
	}

	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public String getLinkSelector() {
		return linkSelector;
	}

	public void setLinkSelector(String linkSelector) {
		this.linkSelector = linkSelector;
	}

	public String getLinkPattern() {
		return linkPattern;
	}

	public void setLinkPattern(String linkPattern) {
		this.linkPattern = linkPattern;
	}

	public int getFirstPage() {
		return firstPage;
	}

	public void setFirstPage(int firstPage) {
		this.firstPage = firstPage;
	}

	public int getMaxPages() {
		return maxPages;
	}

	public void setMaxPages(int maxPages) {
		this.maxPages = maxPages;
	}

	public List<String> getUrls() {
		return urls;
	}

	public void setUrls(List<String> urls) {
		this.urls = urls;
	}

	public String getFormPageUrl() {
		return formPageUrl;
	}

	public void setFormPageUrl(String formPageUrl) {
		this.formPageUrl = formPageUrl;
	}

	public String getFormSelector() {
		return formSelector;
	}

	public void setFormSelector(String formSelector) {
		this.formSelector = formSelector;
	}

	public String getFromField() {
		return fromField;
	}

	public void setFromField(String fromField) {
		this.fromField = fromField;
	}

	public String getToField() {
		return toField;
	}

	public void setToField(String toField) {
		this.toField = toField;
	}

	public Map<String, String> getExtraFields() {
		return extraFields;
	}

	public void setExtraFields(Map<String, String> extraFields) {
		this.extraFields = extraFields;
	}

	public String getNextPageSelector() {
		return nextPageSelector;
	}

	public void setNextPageSelector(String nextPageSelector) {
		this.nextPageSelector = nextPageSelector;
	}

	public String getTooManyResultsPattern() {
		return tooManyResultsPattern;
	}

	public void setTooManyResultsPattern(String tooManyResultsPattern) {
		this.tooManyResultsPattern = tooManyResultsPattern;
	}

	public int getSeedVersion() {
		return seedVersion;
	}

	public void setSeedVersion(int seedVersion) {
		this.seedVersion = seedVersion;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public int getWindowDays() {
		return windowDays;
	}

	public void setWindowDays(int windowDays) {
		this.windowDays = windowDays;
	}

	public int getOverlapDays() {
		return overlapDays;
	}

	public void setOverlapDays(int overlapDays) {
		this.overlapDays = overlapDays;
	}

	public int getMaxWindowsPerRun() {
		return maxWindowsPerRun;
	}

	public void setMaxWindowsPerRun(int maxWindowsPerRun) {
		this.maxWindowsPerRun = maxWindowsPerRun;
	}

	public String getDefaultCourtCode() {
		return defaultCourtCode;
	}

	public void setDefaultCourtCode(String defaultCourtCode) {
		this.defaultCourtCode = defaultCourtCode;
	}

	public CaseCategory getDefaultCategory() {
		return defaultCategory;
	}

	public void setDefaultCategory(CaseCategory defaultCategory) {
		this.defaultCategory = defaultCategory;
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public LocalDate getNextWindowStart() {
		return nextWindowStart;
	}

	public void setNextWindowStart(LocalDate nextWindowStart) {
		this.nextWindowStart = nextWindowStart;
	}

	public boolean isUrlListDone() {
		return urlListDone;
	}

	public void setUrlListDone(boolean urlListDone) {
		this.urlListDone = urlListDone;
	}

	public Instant getLastRunAt() {
		return lastRunAt;
	}

	public void setLastRunAt(Instant lastRunAt) {
		this.lastRunAt = lastRunAt;
	}

	public Instant getLastSuccessAt() {
		return lastSuccessAt;
	}

	public void setLastSuccessAt(Instant lastSuccessAt) {
		this.lastSuccessAt = lastSuccessAt;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public int getConsecutiveFailures() {
		return consecutiveFailures;
	}

	public void setConsecutiveFailures(int consecutiveFailures) {
		this.consecutiveFailures = consecutiveFailures;
	}

	public Instant getNextRunAt() {
		return nextRunAt;
	}

	public void setNextRunAt(Instant nextRunAt) {
		this.nextRunAt = nextRunAt;
	}

	public long getDiscoveredCount() {
		return discoveredCount;
	}

	public void setDiscoveredCount(long discoveredCount) {
		this.discoveredCount = discoveredCount;
	}

	public long getEnqueuedCount() {
		return enqueuedCount;
	}

	public void setEnqueuedCount(long enqueuedCount) {
		this.enqueuedCount = enqueuedCount;
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
}
