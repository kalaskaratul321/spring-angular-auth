package com.strikready.sandbox.services.kg.harvest;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules harvesting. Off unless {@code kg.harvest.enabled=true}.
 *
 * Discovery runs on {@code kg.harvest.discovery-cron} (default every 6 hours,
 * India time) and finds documents added since the previous run; workers poll
 * the queue every {@code kg.harvest.worker-delay-ms}. Every instance may run
 * both: leases make sure a source or a task is processed by one instance.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "kg.harvest.enabled", havingValue = "true")
public class HarvestScheduler {

	private static final Logger log = LoggerFactory.getLogger(HarvestScheduler.class);

	private final AtomicBoolean discovering = new AtomicBoolean();

	@Autowired
	private HarvestDiscoveryService discovery;

	@Autowired
	private HarvestWorker worker;

	@Scheduled(cron = "${kg.harvest.discovery-cron:0 7 */6 * * *}", zone = "Asia/Kolkata")
	public void discover() {
		if (!discovering.compareAndSet(false, true)) {
			return;
		}
		try {
			long n = discovery.discoverAll();
			log.info("Harvest discovery run finished: {} new documents queued", n);
		} finally {
			discovering.set(false);
		}
	}

	@Scheduled(fixedDelayString = "${kg.harvest.worker-delay-ms:30000}", initialDelayString = "${kg.harvest.worker-initial-delay-ms:60000}")
	public void work() {
		int n;
		do {
			n = worker.runBatch();
		} while (n > 0 && !Thread.currentThread().isInterrupted());
	}
}
