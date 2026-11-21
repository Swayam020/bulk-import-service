package com.swayam.bulkimport.importjob;

import java.util.UUID;

public class ImportJobNotFoundException extends RuntimeException {

	public ImportJobNotFoundException(UUID id) {
		super("import job not found: " + id);
	}

}
