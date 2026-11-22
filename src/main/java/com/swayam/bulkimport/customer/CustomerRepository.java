package com.swayam.bulkimport.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	long countByImportJobId(UUID importJobId);

	List<Customer> findByImportJobIdOrderByExternalIdAsc(UUID importJobId);

}
