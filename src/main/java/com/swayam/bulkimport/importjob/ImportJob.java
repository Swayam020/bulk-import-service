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

}
