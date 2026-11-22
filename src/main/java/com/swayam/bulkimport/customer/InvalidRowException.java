package com.swayam.bulkimport.customer;

public class InvalidRowException extends RuntimeException {

	private final long lineNumber;
	private final String reason;

	public InvalidRowException(long lineNumber, String reason) {
		super("line " + lineNumber + ": " + reason);
		this.lineNumber = lineNumber;
		this.reason = reason;
	}

	public long getLineNumber() {
		return lineNumber;
	}

	public String getReason() {
		return reason;
	}

}
