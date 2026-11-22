package com.swayam.bulkimport.customer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerCsvTest {

	private final UUID jobId = UUID.randomUUID();

	@Test
	void parsesAValidRow() {
		Customer customer = CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,10.50", 2);

		assertThat(customer.getImportJobId()).isEqualTo(jobId);
		assertThat(customer.getExternalId()).isEqualTo(1);
		assertThat(customer.getName()).isEqualTo("Alice");
		assertThat(customer.getEmail()).isEqualTo("alice@example.com");
		assertThat(customer.getAmount()).isEqualByComparingTo("10.50");
	}

	@Test
	void trimsWhitespaceAroundFields() {
		Customer customer = CustomerCsv.parseRow(jobId, " 7 , Bob , bob@example.com , 3.5 ", 2);

		assertThat(customer.getExternalId()).isEqualTo(7);
		assertThat(customer.getName()).isEqualTo("Bob");
		assertThat(customer.getEmail()).isEqualTo("bob@example.com");
		assertThat(customer.getAmount()).isEqualByComparingTo("3.5");
	}

	@Test
	void rejectsBadRowsWithTheLineNumberAndReason() {
		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: expected 4 fields but found 3");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,10.50,x", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: expected 4 fields but found 5");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "abc,Alice,alice@example.com,1", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: id must be a whole number");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1, ,alice@example.com,1", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: name is required");

		assertThatThrownBy(
				() -> CustomerCsv.parseRow(jobId, "1," + "a".repeat(256) + ",alice@example.com,1", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: name must be at most 255 characters");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,not-an-email,1", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: email is not valid");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,ten", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: amount must be a number");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,1.005", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: amount must have at most 2 decimal places");

		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,12345678901", 5))
				.isInstanceOf(InvalidRowException.class)
				.hasMessage("line 5: amount is too large");
	}

	@Test
	void exposesLineNumberAndReasonSeparately() {
		assertThatThrownBy(() -> CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,ten", 9))
				.isInstanceOfSatisfying(InvalidRowException.class, e -> {
					assertThat(e.getLineNumber()).isEqualTo(9);
					assertThat(e.getReason()).isEqualTo("amount must be a number");
				});
	}

	@Test
	void allowsTheLargestAmountThatFitsTheColumn() {
		Customer customer = CustomerCsv.parseRow(jobId, "1,Alice,alice@example.com,9999999999.99", 2);

		assertThat(customer.getAmount()).isEqualByComparingTo("9999999999.99");
	}

	@Test
	void acceptsTheHeaderIgnoringCaseSpacesAndABom() {
		assertThatCode(() -> CustomerCsv.checkHeader("id,name,email,amount")).doesNotThrowAnyException();
		assertThatCode(() -> CustomerCsv.checkHeader("ID, Name , Email,AMOUNT")).doesNotThrowAnyException();
		assertThatCode(() -> CustomerCsv.checkHeader("﻿id,name,email,amount")).doesNotThrowAnyException();
	}

	@Test
	void rejectsAWrongHeader() {
		assertThatThrownBy(() -> CustomerCsv.checkHeader("id,name,email")).isInstanceOf(InvalidRowException.class)
				.hasMessage("line 1: header must be id,name,email,amount");

		assertThatThrownBy(() -> CustomerCsv.checkHeader("")).isInstanceOf(InvalidRowException.class)
				.hasMessage("line 1: header must be id,name,email,amount");
	}

}
