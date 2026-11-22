package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.TestcontainersConfiguration;
import com.swayam.bulkimport.customer.Customer;
import com.swayam.bulkimport.customer.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LargeImportTest {

	@Autowired ImportJobRepository jobs;
	@Autowired CustomerRepository customers;
	@Autowired ImportWorker worker;

	private final List<Path> storedFiles = new ArrayList<>();

	@AfterEach
	void deleteStoredFiles() throws IOException {
		for (Path path : storedFiles) {
			Files.deleteIfExists(path);
		}
	}

	// matches the README's test-file generator: id,name,email,amount
	private static String row(int i) {
		return i + ",user" + i + ",user" + i + "@example.com," + (i % 997) + "."
				+ String.format(Locale.ROOT, "%02d", i % 100);
	}

	private UUID queueFile(int rows, IntFunction<String> rowFor) throws IOException {
		Path path = Files.createTempFile("bulk-import-", ".csv");
		storedFiles.add(path);
		try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			writer.write("id,name,email,amount\n");
			for (int i = 0; i < rows; i++) {
				writer.write(rowFor.apply(i) + "\n");
			}
		}
		return jobs.save(new ImportJob("people.csv", path.toString(), Files.size(path))).getId();
	}

	private void drainQueue() {
		while (worker.processNext()) {
		}
	}

	@Test
	void importsAFileSpanningSeveralFlushes() throws IOException {
		// a changed FLUSH_SIZE would quietly stop this test from spanning a flush boundary
		assertThat(CsvImporter.FLUSH_SIZE).isEqualTo(500);

		UUID id = queueFile(1201, LargeImportTest::row);

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isEqualTo(1201);
		assertThat(job.getRowsFailed()).isZero();
		assertThat(customers.countByImportJobId(id)).isEqualTo(1201);

		List<Customer> saved = customers.findByImportJobIdOrderByExternalIdAsc(id);
		assertThat(saved.get(0).getExternalId()).isEqualTo(0);
		assertThat(saved.get(0).getAmount()).isEqualByComparingTo("0.00");
		assertThat(saved.get(saved.size() - 1).getExternalId()).isEqualTo(1200);
		assertThat(saved.get(saved.size() - 1).getName()).isEqualTo("user1200");
		assertThat(saved.get(saved.size() - 1).getAmount()).isEqualByComparingTo("203.00");
	}

	@Test
	void aBadRowAfterAFlushRollsBackTheRowsAlreadyWritten() throws IOException {
		// rows 0-499 were flushed before line 802, so this only passes if the whole import
		// is still one transaction
		UUID id = queueFile(1201, i -> i == 800 ? "800,user800,user800@example.com,oops" : row(i));

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(job.getErrorMessage()).isEqualTo("line 802: amount must be a number");
		assertThat(job.getRowsSucceeded()).isZero();
		assertThat(customers.countByImportJobId(id)).isZero();
	}

	@Test
	void imports200kRows() throws IOException {
		UUID id = queueFile(200_000, LargeImportTest::row);

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isEqualTo(200_000);
		assertThat(customers.countByImportJobId(id)).isEqualTo(200_000);
	}

}
