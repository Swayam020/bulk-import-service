package com.swayam.bulkimport.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer", indexes = @Index(name = "idx_customer_import_job_id", columnList = "import_job_id"))
public class Customer {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "import_job_id", nullable = false)
	private UUID importJobId;

	@Column(name = "external_id", nullable = false)
	private long externalId;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Customer() {
	}

	public Customer(UUID importJobId, long externalId, String name, String email, BigDecimal amount) {
		this.importJobId = importJobId;
		this.externalId = externalId;
		this.name = name;
		this.email = email;
		this.amount = amount;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getImportJobId() {
		return importJobId;
	}

	public long getExternalId() {
		return externalId;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
