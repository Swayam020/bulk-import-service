package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.customer.InvalidRowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ImportWorker {

	private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

	private final ImportService importService;
	private final CsvImporter importer;

	// set after the first poll's recovery; in the app only the scheduler thread calls poll(), one
	// run at a time (tests call it directly with the scheduler off)
	private boolean recovered;

	public ImportWorker(ImportService importService, CsvImporter importer) {
		this.importService = importService;
		this.importer = importer;
	}

	/**
	 * Recovery runs on the first poll, before anything is claimed: scheduled tasks start before the
	 * application-ready event, so a startup listener could requeue a job this worker had just started.
	 */
	@Scheduled(fixedDelayString = "${app.import.worker.poll-delay-ms:1000}")
	public void poll() {
		if (!recovered) {
			int count = importService.recoverInterrupted();
			if (count > 0) {
				log.warn("found {} import job(s) left RUNNING by a previous process; requeued them, or failed any interrupted {} times",
						count, ImportService.MAX_ATTEMPTS);
			}
			recovered = true;
		}
		while (processNext()) {
		}
	}

	/**
	 * Returns false when nothing was queued. Public so tests can drive the worker without the
	 * scheduler. The transactional work lives on ImportService/ImportBatchWriter because
	 * {@code @Transactional} only applies through the Spring proxy, i.e. on calls from another bean.
	 */
	public boolean processNext() {
		var claimed = importService.claimNext();
		if (claimed.isEmpty()) {
			return false;
		}
		UUID id = claimed.get().getId();
		try {
			importer.importFile(id);
			importService.markCompleted(id);
		} catch (InvalidRowException | ImportFailedException e) {
			importService.markFailed(id, e.getMessage());
		} catch (RuntimeException e) {
			log.error("import job {} failed unexpectedly", id, e);
			importService.markFailed(id, "import failed unexpectedly");
		}
		return true;
	}

}
