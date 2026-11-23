package com.swayam.bulkimport.importjob;

public class ImportNotFinishedException extends RuntimeException {

	public ImportNotFinishedException(ImportStatus status) {
		super("import is " + status + "; the error report is available once it has finished");
	}

}
