package com.swayam.bulkimport.importjob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "import_job")
public class ImportJob {

	@Id
	@GeneratedValue
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImportStatus status;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "stored_path", nullable = false, length = 1024)
	private String storedPath;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "rows_succeeded", nullable = false)
	private long rowsSucceeded;

	@Column(name = "rows_failed", nullable = false)
	private long rowsFailed;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	protected ImportJob() {
	}

	public ImportJob(String originalFilename, String storedPath, long sizeBytes) {
		this.originalFilename = originalFilename;
		this.storedPath = storedPath;
		this.sizeBytes = sizeBytes;
		this.status = ImportStatus.QUEUED;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ImportStatus getStatus() {
		return status;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getStoredPath() {
		return storedPath;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getRowsSucceeded() {
		return rowsSucceeded;
	}

	public long getRowsFailed() {
		return rowsFailed;
	}

	public long getRowsProcessed() {
		return rowsSucceeded + rowsFailed;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void start() {
		if (status != ImportStatus.QUEUED) {
			throw new IllegalStateException("cannot start a " + status + " job");
		}
		status = ImportStatus.RUNNING;
		startedAt = Instant.now();
	}

	// adds to the running totals rather than replacing them, since M5 calls this once per
	// committed batch
	public void recordProgress(long succeeded, long failed) {
		if (succeeded < 0 || failed < 0) {
			throw new IllegalArgumentException("row counts must not be negative");
		}
		if (status != ImportStatus.RUNNING) {
			throw new IllegalStateException("cannot record progress on a " + status + " job");
		}
		rowsSucceeded += succeeded;
		rowsFailed += failed;
	}

	public void complete() {
		if (status != ImportStatus.RUNNING) {
			throw new IllegalStateException("cannot complete a " + status + " job");
		}
		status = ImportStatus.COMPLETED;
		finishedAt = Instant.now();
	}

	public void fail(String message) {
		Objects.requireNonNull(message, "message");
		if (status.isTerminal()) {
			throw new IllegalStateException("cannot fail a " + status + " job");
		}
		status = ImportStatus.FAILED;
		finishedAt = Instant.now();
		errorMessage = message.length() > 1000 ? message.substring(0, 1000) : message;
	}

}
