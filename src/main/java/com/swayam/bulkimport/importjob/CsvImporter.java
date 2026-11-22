package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.customer.Customer;
import com.swayam.bulkimport.customer.CustomerCsv;
import com.swayam.bulkimport.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CsvImporter {

	// matches hibernate.jdbc.batch_size, and bounds how many rows are held in memory at once
	static final int FLUSH_SIZE = 500;

	private final ImportJobRepository jobs;
	private final CustomerRepository customers;
	private final EntityManager entityManager;

	public CsvImporter(ImportJobRepository jobs, CustomerRepository customers, EntityManager entityManager) {
		this.jobs = jobs;
		this.customers = customers;
		this.entityManager = entityManager;
	}

	/**
	 * One transaction for the whole file, so the rows and the COMPLETED status commit together,
	 * or any exception rolls everything back, including rows already flushed; the file is
	 * streamed and flushed every {@code FLUSH_SIZE} rows so memory stays flat however big it is.
	 * M5 replaces the single transaction with per-batch transactions.
	 */
	@Transactional
	public void importFile(UUID jobId) {
		Path path = Path.of(findJob(jobId).getStoredPath());
		long imported = 0;
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			CustomerCsv.checkHeader(header == null ? "" : header);

			List<Customer> chunk = new ArrayList<>(FLUSH_SIZE);
			long lineNumber = 1;
			String line;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) {
					continue;
				}
				chunk.add(CustomerCsv.parseRow(jobId, line, lineNumber));
				if (chunk.size() == FLUSH_SIZE) {
					imported += writeChunk(chunk);
				}
			}
			imported += writeChunk(chunk);
		} catch (MalformedInputException e) {
			throw new ImportFailedException("file is not valid UTF-8");
		} catch (IOException e) {
			throw new ImportFailedException("stored file could not be read");
		}

		// clear() detached the job loaded above, so changes to that instance would never be written
		ImportJob job = findJob(jobId);
		job.recordProgress(imported, 0);
		job.complete();
	}

	private ImportJob findJob(UUID jobId) {
		return jobs.findById(jobId).orElseThrow(() -> new ImportJobNotFoundException(jobId));
	}

	private int writeChunk(List<Customer> chunk) {
		customers.saveAll(chunk);
		entityManager.flush();
		entityManager.clear();
		int written = chunk.size();
		chunk.clear();
		return written;
	}

}
