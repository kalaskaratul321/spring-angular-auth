package com.strikready.sandbox.services.kg.harvest;

import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.models.kg.harvest.HarvestTask;
import com.strikready.sandbox.repositories.kg.harvest.HarvestSourceRepository;
import com.strikready.sandbox.services.kg.importer.ImportRequest;
import com.strikready.sandbox.services.kg.importer.ImportResult;
import com.strikready.sandbox.services.kg.importer.JudgmentImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Takes tasks off the queue (oldest first) and imports them through
 * {@link JudgmentImportService}, which keeps only the source URL and metadata.
 */
@Service
public class HarvestWorker {

	private static final Logger log = LoggerFactory.getLogger(HarvestWorker.class);

	@Autowired
	private HarvestQueue queue;

	@Autowired
	private JudgmentImportService importService;

	@Autowired
	private HarvestSourceRepository sourceRepository;

	@Value("${kg.harvest.worker-batch:50}")
	private int batchSize;

	/** Processes up to one batch; returns the number of tasks handled. */
	public int runBatch() {
		int handled = 0;
		while (handled < batchSize && !Thread.currentThread().isInterrupted()) {
			HarvestTask task = queue.claim();
			if (task == null) {
				break;
			}
			process(task);
			handled++;
		}
		return handled;
	}

	void process(HarvestTask task) {
		try {
			ImportResult result = importService.importFromUrl(new ImportRequest(task.getUrl(), defaults(task)));
			if (result.getStatus() == ImportResult.Status.FAILED) {
				HarvestTask.Status s = queue.fail(task, result.getError(), result.isRetryable());
				log.info("Harvest task {} {} (attempt {}): {}", task.getUrl(), s, task.getAttempts(), result.getError());
			} else {
				queue.complete(task, result);
			}
		} catch (RuntimeException e) {
			// Unexpected (e.g. database briefly unavailable): retry later.
			log.warn("Harvest task {} errored: {}", task.getUrl(), e.toString());
			queue.fail(task, e.toString(), true);
		}
	}

	private LegalCase defaults(HarvestTask task) {
		HarvestSource source = task.getSourceId() == null ? null
				: sourceRepository.findById(task.getSourceId()).orElse(null);
		if (source == null || (source.getDefaultCourtCode() == null && source.getDefaultCategory() == null)) {
			return null;
		}
		LegalCase m = new LegalCase();
		m.setCourtCode(source.getDefaultCourtCode());
		m.setCategory(source.getDefaultCategory());
		return m;
	}
}
