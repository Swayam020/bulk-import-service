package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.customer.Customer;
import com.swayam.bulkimport.customer.CustomerCsv;
import com.swayam.bulkimport.customer.InvalidRowException;
import org.springframework.stereotype.Component;

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

	// lines per transaction; matches hibernate.jdbc.batch_size, so each batch's inserts go to
	// Postgres as one JDBC batch
	static final int BATCH_SIZE = 500;

	private final ImportJobRepository jobs;
	private final ImportBatchWriter batchWriter;

	public CsvImporter(ImportJobRepository jobs, ImportBatchWriter batchWriter) {
		this.jobs = jobs;
		this.batchWriter = batchWriter;
	}

	/**
	 * Streams the file and commits every {@code BATCH_SIZE} lines in its own transaction via
	 * {@link ImportBatchWriter}, so a row that fails validation is counted and skipped instead
	 * of rolling back the file, and memory stays flat; there is deliberately no transaction
	 * around this method; if the file itself becomes unreadable part-way, the batches already
	 * committed stay and the job's counts say how many. A job that ends COMPLETED has processed
	 * every line; {@code rowsFailed} counts the lines that were not saved.
	 */
	public void importFile(UUID jobId) {
		Path path = Path.of(jobs.findById(jobId).orElseThrow(() -> new ImportJobNotFoundException(jobId)).getStoredPath());
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			CustomerCsv.checkHeader(header == null ? "" : header);

			List<Customer> batch = new ArrayList<>(BATCH_SIZE);
			List<ImportRowError> errors = new ArrayList<>();
			long lineNumber = 1;
			String line;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) {
					continue;
				}
				try {
					batch.add(CustomerCsv.parseRow(jobId, line, lineNumber));
				} catch (InvalidRowException e) {
					errors.add(new ImportRowError(jobId, e.getLineNumber(), e.getReason()));
				}
				if (batch.size() + errors.size() == BATCH_SIZE) {
					batchWriter.write(jobId, batch, errors);
					batch.clear();
					errors.clear();
				}
			}
			if (!batch.isEmpty() || !errors.isEmpty()) {
				batchWriter.write(jobId, batch, errors);
			}
		} catch (MalformedInputException e) {
			throw new ImportFailedException("file is not valid UTF-8");
		} catch (IOException e) {
			throw new ImportFailedException("stored file could not be read");
		}
	}

}
