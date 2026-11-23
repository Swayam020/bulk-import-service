package com.swayam.bulkimport.importjob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

// one error per file line, and the unique index is also what the report's keyset query walks
@Entity
@Table(name = "import_row_error",
		uniqueConstraints = @UniqueConstraint(name = "uk_import_row_error_job_line",
				columnNames = { "import_job_id", "line_number" }))
public class ImportRowError {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "import_job_id", nullable = false)
	private UUID importJobId;

	@Column(name = "line_number", nullable = false)
	private long lineNumber;

	@Column(nullable = false, length = 255)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ImportRowError() {
	}

	public ImportRowError(UUID importJobId, long lineNumber, String reason) {
		this.importJobId = importJobId;
		this.lineNumber = lineNumber;
		this.reason = reason;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getImportJobId() {
		return importJobId;
	}

	public long getLineNumber() {
		return lineNumber;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
