package com.swayam.bulkimport.importjob;

public enum ImportStatus {
	QUEUED, RUNNING, COMPLETED, FAILED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}

}
