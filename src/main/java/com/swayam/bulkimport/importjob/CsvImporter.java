package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.customer.Customer;
import com.swayam.bulkimport.customer.CustomerCsv;
import com.swayam.bulkimport.customer.CustomerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

	private final ImportJobRepository jobs;
	private final CustomerRepository customers;

	public CsvImporter(ImportJobRepository jobs, CustomerRepository customers) {
		this.jobs = jobs;
		this.customers = customers;
	}

	/**
	 * One transaction for the whole file, so the rows and the COMPLETED status commit together,
	 * or any exception rolls all of it back; M5 replaces this with per-batch transactions.
	 */
	@Transactional
	public void importFile(UUID jobId) {
		ImportJob job = jobs.findById(jobId).orElseThrow(() -> new ImportJobNotFoundException(jobId));

		List<String> lines = readLines(Path.of(job.getStoredPath()));
		CustomerCsv.checkHeader(lines.isEmpty() ? "" : lines.get(0));

		List<Customer> parsed = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.isBlank()) {
				continue;
			}
			parsed.add(CustomerCsv.parseRow(jobId, line, i + 1));
		}

		customers.saveAll(parsed);
		job.recordProgress(parsed.size(), 0);
		job.complete();
	}

	// holds the whole file in memory, fine for M3's small files; M4 streams it
	private static List<String> readLines(Path path) {
		try {
			return Files.readAllLines(path, StandardCharsets.UTF_8);
		} catch (MalformedInputException e) {
			throw new ImportFailedException("file is not valid UTF-8");
		} catch (IOException e) {
			throw new ImportFailedException("stored file could not be read");
		}
	}

}
