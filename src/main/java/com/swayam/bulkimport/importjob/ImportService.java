package com.swayam.bulkimport.importjob;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImportService {

	private final ImportJobRepository jobs;
	private final FileStorage storage;

	public ImportService(ImportJobRepository jobs, FileStorage storage) {
		this.jobs = jobs;
		this.storage = storage;
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

}
