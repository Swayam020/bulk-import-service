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

	public ImportBatchWriter(ImportJobRepository jobs, CustomerRepository customers) {
		this.jobs = jobs;
		this.customers = customers;
	}

	/**
	 * Each batch is its own transaction, so its rows and the job's running counts commit
	 * together and a later failure never rolls back a batch that already committed; it is a
	 * separate bean because {@code @Transactional} only applies to calls that come through the
	 * Spring proxy, not from inside {@link CsvImporter}.
	 */
	@Transactional
	public void write(UUID jobId, List<Customer> rows, int failed) {
		customers.saveAll(rows);
		jobs.findById(jobId).orElseThrow(() -> new ImportJobNotFoundException(jobId)).recordProgress(rows.size(), failed);
	}

}
