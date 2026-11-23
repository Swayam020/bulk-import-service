package com.swayam.bulkimport.importjob;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, UUID> {

	long countByImportJobId(UUID importJobId);

	// keyset paging; the report asks for the next page after the last line it wrote
	List<ImportRowError> findByImportJobIdAndLineNumberGreaterThanOrderByLineNumberAsc(UUID importJobId,
			long lineNumber, Limit limit);

}
