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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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

	// row 1450 is corrupted mid-write: a valid id/name prefix, an invalid UTF-8 byte pair, then
	// the rest of the row, so the reader fails while decoding this exact line
	private static byte[] brokenRow(int i) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes((i + ",user").getBytes(StandardCharsets.UTF_8));
		out.write(0xC3);
		out.write(0x28);
		out.writeBytes((i + ",user" + i + "@example.com,1").getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	private UUID queueBytes(int rows, IntFunction<byte[]> rowFor) throws IOException {
		Path path = Files.createTempFile("bulk-import-", ".csv");
		storedFiles.add(path);
		try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
			out.write("id,name,email,amount\n".getBytes(StandardCharsets.UTF_8));
			for (int i = 0; i < rows; i++) {
				out.write(rowFor.apply(i));
				out.write('\n');
			}
		}
		return jobs.save(new ImportJob("people.csv", path.toString(), Files.size(path))).getId();
	}

	private UUID queueFile(int rows, IntFunction<String> rowFor) throws IOException {
		return queueBytes(rows, i -> rowFor.apply(i).getBytes(StandardCharsets.UTF_8));
	}

	private void drainQueue() {
		while (worker.processNext()) {
		}
	}

	@Test
	void importsAFileSpanningSeveralBatches() throws IOException {
		// a changed BATCH_SIZE would quietly stop this test spanning a batch boundary
		assertThat(CsvImporter.BATCH_SIZE).isEqualTo(500);

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
	void badRowsFailAloneIncludingAtBatchEdges() throws IOException {
		// row 499 is the last line of the first batch and row 1200 is in the final partial
		// batch, so failures at both edges are counted
		UUID id = queueFile(1201,
				i -> i == 499 || i == 800 || i == 1200 ? i + ",user" + i + ",user" + i + "@example.com,oops" : row(i));

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isEqualTo(1198);
		assertThat(job.getRowsFailed()).isEqualTo(3);
		assertThat(job.getErrorMessage()).isNull();
		assertThat(customers.countByImportJobId(id)).isEqualTo(1198);

		List<Long> externalIds = customers.findByImportJobIdOrderByExternalIdAsc(id).stream()
				.map(Customer::getExternalId).toList();
		assertThat(externalIds).doesNotContain(499L, 800L, 1200L);
		assertThat(externalIds).contains(498L, 500L, 1199L);
	}

	@Test
	void aFailurePartWayThroughKeepsTheBatchesAlreadyCommitted() throws IOException {
		// the reader decodes ahead of what it returns, so it throws somewhere between rows
		// 1000 and 1449 (measured on JDK 25: after row 1448); either way only the first two
		// batches committed and the third was still in memory
		UUID id = queueBytes(1501, i -> i == 1450 ? brokenRow(i) : row(i).getBytes(StandardCharsets.UTF_8));

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(job.getErrorMessage()).isEqualTo("file is not valid UTF-8");
		assertThat(job.getRowsSucceeded()).isEqualTo(1000);
		assertThat(job.getRowsFailed()).isZero();
		assertThat(customers.countByImportJobId(id)).isEqualTo(1000);

		List<Customer> saved = customers.findByImportJobIdOrderByExternalIdAsc(id);
		assertThat(saved.get(saved.size() - 1).getExternalId()).isEqualTo(999);
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
