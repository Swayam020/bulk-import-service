package com.swayam.bulkimport.importjob;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImportService {

	// rows per query while writing the error report, so a file of 200k bad rows never sits in
	// memory at once
	static final int ERROR_PAGE_SIZE = 1000;

	private final ImportJobRepository jobs;
	private final FileStorage storage;
	private final ImportRowErrorRepository rowErrors;

	public ImportService(ImportJobRepository jobs, FileStorage storage, ImportRowErrorRepository rowErrors) {
		this.jobs = jobs;
		this.storage = storage;
		this.rowErrors = rowErrors;
	}

	public ImportJob accept(MultipartFile file) {
		if (file.isEmpty()) {
			throw new ImportRejectedException("file is empty");
		}
		String name = file.getOriginalFilename();
		if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			throw new ImportRejectedException("only .csv files are accepted");
		}
		Path stored = storage.store(file);
		// the file is written before the row, so a job row never points at a file that was
		// never written; the catch removes the orphan file if the insert fails
		try {
			return jobs.save(new ImportJob(name, stored.toString(), file.getSize()));
		} catch (RuntimeException e) {
			storage.deleteQuietly(stored);
			throw e;
		}
	}

	public ImportJob find(UUID id) {
		return jobs.findById(id).orElseThrow(() -> new ImportJobNotFoundException(id));
	}

	@Transactional
	public Optional<ImportJob> claimNext() {
		// dirty checking writes RUNNING when this short transaction commits, before the import starts
		Optional<ImportJob> next = jobs.lockNextQueued();
		next.ifPresent(ImportJob::start);
		return next;
	}

	@Transactional
	public void markCompleted(UUID id) {
		find(id).complete();
	}

	@Transactional
	public void markFailed(UUID id, String message) {
		find(id).fail(message);
	}

	public void requireFinished(UUID id) {
		ImportJob job = find(id);
		if (!job.getStatus().isTerminal()) {
			throw new ImportNotFinishedException(job.getStatus());
		}
	}

	/**
	 * Each page is its own short query with no surrounding transaction, so memory and DB
	 * connections stay bounded however long the download takes.
	 */
	public void writeErrorReport(UUID id, Writer out) throws IOException {
		out.write("line,error\n");
		long afterLine = 0;
		while (true) {
			List<ImportRowError> page = rowErrors.findByImportJobIdAndLineNumberGreaterThanOrderByLineNumberAsc(id,
					afterLine, Limit.of(ERROR_PAGE_SIZE));
			for (ImportRowError error : page) {
				out.write(csvRow(error.getLineNumber(), error.getReason()));
			}
			if (page.size() < ERROR_PAGE_SIZE) {
				return;
			}
			afterLine = page.get(page.size() - 1).getLineNumber();
		}
	}

	// always quoted with inner quotes doubled (RFC 4180), so a reason containing a comma or
	// quote can't shift columns
	static String csvRow(long line, String error) {
		return line + ",\"" + error.replace("\"", "\"\"") + "\"\n";
	}

}
