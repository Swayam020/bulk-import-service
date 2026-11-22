package com.swayam.bulkimport.importjob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

	// SKIP LOCKED lets another worker take the next job instead of blocking on this one's row lock
	@Query(value = "select * from import_job where status = 'QUEUED' order by created_at limit 1 for update skip locked",
			nativeQuery = true)
	Optional<ImportJob> lockNextQueued();

}
