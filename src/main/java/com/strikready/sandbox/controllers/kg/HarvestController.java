package com.strikready.sandbox.controllers.kg;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.models.kg.harvest.HarvestTask;
import com.strikready.sandbox.repositories.kg.harvest.HarvestSourceRepository;
import com.strikready.sandbox.repositories.kg.harvest.HarvestTaskRepository;
import com.strikready.sandbox.services.kg.harvest.HarvestDiscoveryService;
import com.strikready.sandbox.services.kg.harvest.HarvestQueue;
import com.strikready.sandbox.services.kg.harvest.HarvestWindow;
import com.strikready.sandbox.services.kg.harvest.HarvestWindowPlanner;
import com.strikready.sandbox.services.kg.harvest.HarvestWorker;
import com.strikready.sandbox.services.kg.importer.OfficialSourceFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for bulk harvesting from official sources (Admin authority only,
 * see WebSecurityConfig).
 */
@CrossOrigin(origins = "*")
@RequestMapping("/api/kg/harvest")
@RestController
public class HarvestController {

	@Autowired
	private HarvestSourceRepository sourceRepository;

	@Autowired
	private HarvestTaskRepository taskRepository;

	@Autowired
	private HarvestDiscoveryService discovery;

	@Autowired
	private HarvestWorker worker;

	@Autowired
	private HarvestQueue queue;

	@Autowired
	private OfficialSourceFetcher fetcher;

	// -------------------------------------------------------------- sources

	@GetMapping("/sources")
	public List<HarvestSource> sources() {
		return sourceRepository.findAll(Sort.by("name"));
	}

	@PostMapping("/sources")
	public ResponseEntity<HarvestSource> create(@RequestBody HarvestSource source) {
		if (source.getName() == null || sourceRepository.findByName(source.getName()) != null) {
			throw new IllegalArgumentException("A unique name is required");
		}
		source.setId(null);
		resetCheckpoint(source);
		validate(source);
		return new ResponseEntity<>(sourceRepository.save(source), HttpStatus.CREATED);
	}

	/** Updates configuration; the checkpoint and counters are kept. */
	@PutMapping("/sources/{id}")
	public HarvestSource update(@PathVariable String id, @RequestBody HarvestSource changes) {
		HarvestSource s = find(id);
		s.setUrlTemplate(changes.getUrlTemplate());
		s.setDateFormat(changes.getDateFormat());
		s.setLinkSelector(changes.getLinkSelector());
		s.setLinkPattern(changes.getLinkPattern());
		s.setFirstPage(changes.getFirstPage());
		s.setMaxPages(changes.getMaxPages());
		s.setUrls(changes.getUrls());
		s.setStartDate(changes.getStartDate());
		s.setWindowDays(changes.getWindowDays());
		s.setOverlapDays(changes.getOverlapDays());
		s.setMaxWindowsPerRun(changes.getMaxWindowsPerRun());
		s.setDefaultCourtCode(changes.getDefaultCourtCode());
		s.setDefaultCategory(changes.getDefaultCategory());
		validate(s);
		return sourceRepository.save(s);
	}

	@PostMapping("/sources/{id}/pause")
	public HarvestSource pause(@PathVariable String id) {
		HarvestSource s = find(id);
		s.setEnabled(false);
		return sourceRepository.save(s);
	}

	@PostMapping("/sources/{id}/resume")
	public HarvestSource resume(@PathVariable String id) {
		HarvestSource s = find(id);
		validate(s);
		s.setEnabled(true);
		s.setNextRunAt(null);
		return sourceRepository.save(s);
	}

	/** Start the walk again from startDate (already imported documents are still skipped). */
	@PostMapping("/sources/{id}/reset")
	public HarvestSource reset(@PathVariable String id) {
		HarvestSource s = find(id);
		resetCheckpoint(s);
		return sourceRepository.save(s);
	}

	/** Runs discovery for this source now, ignoring failure backoff. */
	@PostMapping("/sources/{id}/discover")
	public Map<String, Object> discoverNow(@PathVariable String id) {
		find(id);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("enqueued", discovery.discover(id, true));
		out.put("source", find(id));
		return out;
	}

	@GetMapping("/sources/{id}/plan")
	public List<HarvestWindow> plan(@PathVariable String id) {
		return HarvestWindowPlanner.plan(find(id), LocalDate.now(ZoneId.of("Asia/Kolkata")));
	}

	// ---------------------------------------------------------------- queue

	@GetMapping("/stats")
	public Map<String, Object> stats(@RequestParam(required = false) String sourceId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("tasks", queue.countsByStatus(sourceId));
		out.put("instance", HarvestQueue.INSTANCE_ID);
		return out;
	}

	@GetMapping("/tasks")
	public Page<HarvestTask> tasks(@RequestParam HarvestTask.Status status,
			@RequestParam(required = false) String sourceId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		PageRequest p = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500),
				Sort.by(Sort.Direction.DESC, "updatedAt"));
		return sourceId == null ? taskRepository.findByStatus(status, p)
				: taskRepository.findBySourceIdAndStatus(sourceId, status, p);
	}

	@PostMapping("/tasks/{id}/retry")
	public ResponseEntity<Void> retry(@PathVariable String id) {
		return queue.retry(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@PostMapping("/tasks/retry-dead")
	public Map<String, Long> retryDead(@RequestParam(required = false) String sourceId) {
		Map<String, Long> out = new LinkedHashMap<>();
		out.put("requeued", queue.retryDead(sourceId));
		return out;
	}

	/** Processes one batch now on this instance (useful when the scheduler is off). */
	@PostMapping("/work")
	public Map<String, Integer> workNow() {
		Map<String, Integer> out = new LinkedHashMap<>();
		out.put("processed", worker.runBatch());
		return out;
	}

	// -------------------------------------------------------------- helpers

	private HarvestSource find(String id) {
		return sourceRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown source: " + id));
	}

	private static void resetCheckpoint(HarvestSource s) {
		s.setMode(HarvestSource.Mode.BACKFILL);
		s.setNextWindowStart(null);
		s.setUrlListDone(false);
		s.setNextRunAt(null);
		s.setConsecutiveFailures(0);
		s.setLastError(null);
		s.setLeaseOwner(null);
		s.setLeaseUntil(null);
	}

	private void validate(HarvestSource s) {
		if (s.getType() == HarvestSource.Type.HTML_LISTING) {
			if (s.getUrlTemplate() == null) {
				throw new IllegalArgumentException("urlTemplate is required");
			}
			fetcher.validate(s.getUrlTemplate().replace("{from}", "x").replace("{to}", "x").replace("{page}", "1"));
			if (HarvestWindowPlanner.isDated(s) && s.getStartDate() == null) {
				throw new IllegalArgumentException("startDate is required for a dated urlTemplate");
			}
			DateTimeFormatter.ofPattern(s.getDateFormat());
			Pattern.compile(s.getLinkPattern() == null ? ".*" : s.getLinkPattern());
		} else {
			for (String u : s.getUrls()) {
				fetcher.validate(u);
			}
		}
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", ex.getMessage());
		return ResponseEntity.badRequest().body(body);
	}
}
