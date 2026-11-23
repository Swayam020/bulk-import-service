package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.customer.Customer;
import com.swayam.bulkimport.customer.CustomerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ImportBatchWriter {

	private final ImportJobRepository jobs;
	private final CustomerRepository customers;
	private final ImportRowErrorRepository rowErrors;

	public ImportBatchWriter(ImportJobRepository jobs, CustomerRepository customers,
			ImportRowErrorRepository rowErrors) {
		this.jobs = jobs;
		this.customers = customers;
		this.rowErrors = rowErrors;
	}

	/**
	 * Each batch is its own transaction, so its rows and the job's running counts commit
	 * together and a later failure never rolls back a batch that already committed; it is a
	 * separate bean because {@code @Transactional} only applies to calls that come through the
	 * Spring proxy, not from inside {@link CsvImporter}. The batch's row errors commit in the
	 * same transaction, so the error report always matches {@code rowsFailed}. The job also
	 * records the batch's last file line in this transaction, which is where an interrupted
	 * import resumes.
	 */
	@Transactional
	public void write(UUID jobId, List<Customer> rows, List<ImportRowError> errors, long lastLine) {
		customers.saveAll(rows);
		rowErrors.saveAll(errors);
		jobs.findById(jobId).orElseThrow(() -> new ImportJobNotFoundException(jobId))
				.recordProgress(rows.size(), errors.size(), lastLine);
	}

}
