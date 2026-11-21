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

	public record ApiError(String error, String message, Instant timestamp) {
	}

}
