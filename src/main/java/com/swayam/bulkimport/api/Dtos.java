package com.swayam.bulkimport.api;

import com.swayam.bulkimport.importjob.ImportJob;

import java.time.Instant;
import java.util.UUID;

public final class Dtos {

	private Dtos() {
	}

	public record ImportAccepted(UUID jobId, String status, String filename, long sizeBytes) {

		public static ImportAccepted from(ImportJob job) {
			return new ImportAccepted(job.getId(), job.getStatus().name(), job.getOriginalFilename(),
					job.getSizeBytes());
		}

	}

	public record ImportJobView(UUID jobId, String status, String filename, long sizeBytes, long rowsProcessed,
			long rowsSucceeded, long rowsFailed, String errorMessage, Instant createdAt, Instant startedAt,
			Instant finishedAt) {

		public static ImportJobView from(ImportJob job) {
			return new ImportJobView(job.getId(), job.getStatus().name(), job.getOriginalFilename(),
					job.getSizeBytes(), job.getRowsProcessed(), job.getRowsSucceeded(), job.getRowsFailed(),
					job.getErrorMessage(), job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
		}

	}

	public record ApiError(String error, String message, Instant timestamp) {
	}

}
