package com.swayam.bulkimport.customer;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parses the {@code id,name,email,amount} CSV rows the controller's upload accepts into
 * {@link Customer} rows. No quoted-field support: a field containing a comma is not handled.
 */
public final class CustomerCsv {

	public static final String HEADER = "id,name,email,amount";

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private CustomerCsv() {
	}

	public static void checkHeader(String line) {
		String withoutBom = line.startsWith("\uFEFF") ? line.substring(1) : line;
		String[] parts = withoutBom.split(",", -1);
		StringBuilder normalized = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				normalized.append(',');
			}
			normalized.append(parts[i].trim().toLowerCase(Locale.ROOT));
		}
		if (!normalized.toString().equals(HEADER)) {
			throw new InvalidRowException(1, "header must be " + HEADER);
		}
	}

	public static Customer parseRow(UUID importJobId, String line, long lineNumber) {
		// Postgres text columns reject NUL, and it would fail the whole batch at insert instead
		// of this one row
		if (line.indexOf('\0') >= 0) {
			throw new InvalidRowException(lineNumber, "row contains a NUL character");
		}
		String[] fields = line.split(",", -1);
		if (fields.length != 4) {
			throw new InvalidRowException(lineNumber, "expected 4 fields but found " + fields.length);
		}
		String idField = fields[0].trim();
		String nameField = fields[1].trim();
		String emailField = fields[2].trim();
		String amountField = fields[3].trim();

		long externalId;
		try {
			externalId = Long.parseLong(idField);
		} catch (NumberFormatException e) {
			throw new InvalidRowException(lineNumber, "id must be a whole number");
		}

		if (nameField.isEmpty()) {
			throw new InvalidRowException(lineNumber, "name is required");
		}
		if (nameField.length() > 255) {
			throw new InvalidRowException(lineNumber, "name must be at most 255 characters");
		}

		if (emailField.length() > 255) {
			throw new InvalidRowException(lineNumber, "email must be at most 255 characters");
		}
		if (!EMAIL.matcher(emailField).matches()) {
			throw new InvalidRowException(lineNumber, "email is not valid");
		}

		BigDecimal amount;
		try {
			amount = new BigDecimal(amountField);
		} catch (NumberFormatException e) {
			throw new InvalidRowException(lineNumber, "amount must be a number");
		}
		if (amount.scale() > 2) {
			throw new InvalidRowException(lineNumber, "amount must have at most 2 decimal places");
		}
		if (amount.precision() - amount.scale() > 10) {
			throw new InvalidRowException(lineNumber, "amount is too large");
		}

		return new Customer(importJobId, externalId, nameField, emailField, amount);
	}

}
