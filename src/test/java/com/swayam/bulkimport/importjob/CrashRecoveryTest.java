package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.TestcontainersConfiguration;
import com.swayam.bulkimport.customer.Customer;
import com.swayam.bulkimport.customer.CustomerCsv;
import com.swayam.bulkimport.customer.CustomerRepository;
import com.swayam.bulkimport.customer.InvalidRowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CrashRecoveryTest {

	@Autowired ImportJobRepository jobs;
	@Autowired CustomerRepository customers;
	@Autowired ImportRowErrorRepository rowErrors;
	@Autowired ImportService importService;
	@Autowired ImportBatchWriter batchWriter;
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

	private static String badRow(int i) {
		return i + ",user" + i + ",nope,1";
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

	// what a process killed mid-import leaves behind: the job RUNNING, and file lines 2..lastLine
	// committed by the real batch writer in one batch, exactly as CsvImporter would have written them
	private void crashAfter(UUID id, long lastLine) throws IOException {
		ImportJob job = jobs.findById(id).orElseThrow();
		job.start();
		jobs.save(job);
		List<String> lines = Files.readAllLines(Path.of(job.getStoredPath()));
		List<Customer> rows = new ArrayList<>();
		List<ImportRowError> errors = new ArrayList<>();
		for (int n = 2; n <= lastLine; n++) {
			if (lines.get(n - 1).isBlank()) {
				continue;
			}
			try {
				rows.add(CustomerCsv.parseRow(id, lines.get(n - 1), n));
			} catch (InvalidRowException e) {
				errors.add(new ImportRowError(id, e.getLineNumber(), e.getReason()));
			}
		}
		batchWriter.write(id, rows, errors, lastLine);
	}

	private ImportJob job(UUID id) {
		return jobs.findById(id).orElseThrow();
	}

	// other test classes leave jobs behind, so recovery's return value is never asserted, only
	// each job's own state

	@Test
	void resumesAfterTheLastCommittedBatchWithoutDuplicates() throws IOException {
		// the crash point is the first batch boundary
		assertThat(CsvImporter.BATCH_SIZE).isEqualTo(500);
		UUID id = queueFile(1200, i -> i == 100 ? "" : (i == 298 || i == 898) ? badRow(i) : row(i));

		// the blank line is why the batch ends on line 502, not 501, so resuming from rowsProcessed
		// instead of lastCommittedLine would save row 500 twice
		crashAfter(id, 502);
		ImportJob crashed = job(id);
		assertThat(crashed.getStatus()).isEqualTo(ImportStatus.RUNNING);
		assertThat(crashed.getRowsSucceeded()).isEqualTo(499);
		assertThat(crashed.getRowsFailed()).isEqualTo(1);
		assertThat(crashed.getLastCommittedLine()).isEqualTo(502);
		assertThat(customers.countByImportJobId(id)).isEqualTo(499);
		Instant startedAt = crashed.getStartedAt();

		importService.recoverInterrupted();
		assertThat(job(id).getStatus()).isEqualTo(ImportStatus.QUEUED);
		assertThat(job(id).getRowsProcessed()).isEqualTo(500);

		drainQueue();

		ImportJob done = job(id);
		assertThat(done.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(done.getAttempts()).isEqualTo(2);
		assertThat(done.getStartedAt()).isEqualTo(startedAt);
		assertThat(done.getRowsSucceeded()).isEqualTo(1197);
		assertThat(done.getRowsFailed()).isEqualTo(2);
		assertThat(done.getLastCommittedLine()).isEqualTo(1201);
		assertThat(customers.findByImportJobIdOrderByExternalIdAsc(id).stream().map(Customer::getExternalId).toList())
				.containsExactlyElementsOf(
						LongStream.range(0, 1200).filter(i -> i != 100 && i != 298 && i != 898).boxed().toList());
		assertThat(rowErrors.findByImportJobIdAndLineNumberGreaterThanOrderByLineNumberAsc(id, 0, Limit.of(10))
				.stream().map(ImportRowError::getLineNumber).toList()).containsExactly(300L, 900L);
	}

	@Test
	void aJobInterruptedAfterItsLastBatchCompletesWithoutAddingRows() throws IOException {
		UUID id = queueFile(3, CrashRecoveryTest::row);
		crashAfter(id, 4);

		importService.recoverInterrupted();
		drainQueue();

		ImportJob done = job(id);
		assertThat(done.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(done.getRowsSucceeded()).isEqualTo(3);
		assertThat(done.getRowsFailed()).isZero();
		assertThat(customers.countByImportJobId(id)).isEqualTo(3);
	}

	@Test
	void aResumedJobWhoseFileIsGoneFailsCleanly() throws IOException {
		UUID id = queueFile(1200, CrashRecoveryTest::row);
		crashAfter(id, 501);
		Files.delete(Path.of(job(id).getStoredPath()));

		importService.recoverInterrupted();
		drainQueue();

		ImportJob failed = job(id);
		assertThat(failed.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(failed.getErrorMessage()).isEqualTo("stored file could not be read");
		assertThat(failed.getRowsSucceeded()).isEqualTo(500);
		assertThat(customers.countByImportJobId(id)).isEqualTo(500);
	}

	@Test
	void aJobInterruptedTooOftenIsFailedInsteadOfResumed() throws IOException {
		assertThat(ImportService.MAX_ATTEMPTS).isEqualTo(3);
		UUID id = queueFile(3, CrashRecoveryTest::row);
		ImportJob job = job(id);
		job.start();
		job.requeue();
		job.start();
		job.requeue();
		job.start();
		jobs.save(job);
		assertThat(job(id).getAttempts()).isEqualTo(3);

		importService.recoverInterrupted();

		ImportJob failed = job(id);
		assertThat(failed.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(failed.getErrorMessage()).isEqualTo("import was interrupted 3 times; giving up");
		assertThat(failed.getFinishedAt()).isNotNull();

		drainQueue();
		assertThat(job(id).getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(customers.countByImportJobId(id)).isZero();
	}

	@Test
	void recoveryLeavesQueuedAndFinishedJobsAlone() {
		UUID queued = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22)).getId();
		UUID completed = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22)).getId();
		ImportJob job = job(completed);
		job.start();
		job.complete();
		jobs.save(job);

		importService.recoverInterrupted();

		assertThat(job(queued).getStatus()).isEqualTo(ImportStatus.QUEUED);
		assertThat(job(queued).getAttempts()).isZero();
		assertThat(job(completed).getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job(completed).getAttempts()).isEqualTo(1);
	}

	@Test
	void pollRecoversInterruptedJobsBeforeClaimingNewOnes() throws IOException {
		// the only test that calls poll(), because its recovery flag lives on the shared worker bean
		// and runs once per context
		UUID id = queueFile(1200, CrashRecoveryTest::row);
		crashAfter(id, 501);

		worker.poll();

		ImportJob done = job(id);
		assertThat(done.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(done.getRowsSucceeded()).isEqualTo(1200);
		assertThat(customers.countByImportJobId(id)).isEqualTo(1200);
	}

}
