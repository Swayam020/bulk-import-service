package com.swayam.bulkimport.importjob;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportJobTest {

	@Test
	void newJobIsQueuedWithZeroCountsAndNoTimestamps() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);

		assertThat(job.getStatus()).isEqualTo(ImportStatus.QUEUED);
		assertThat(job.getRowsSucceeded()).isZero();
		assertThat(job.getRowsFailed()).isZero();
		assertThat(job.getRowsProcessed()).isZero();
		assertThat(job.getStartedAt()).isNull();
		assertThat(job.getFinishedAt()).isNull();
		assertThat(job.getErrorMessage()).isNull();
	}

	@Test
	void startMovesQueuedToRunningAndStampsStartedAt() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);

		job.start();

		assertThat(job.getStatus()).isEqualTo(ImportStatus.RUNNING);
		assertThat(job.getStartedAt()).isNotNull();
		assertThat(job.getFinishedAt()).isNull();
	}

	@Test
	void recordProgressAddsAcrossBatches() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		job.start();

		job.recordProgress(8, 2, 11);
		job.recordProgress(5, 0, 16);

		assertThat(job.getRowsSucceeded()).isEqualTo(13);
		assertThat(job.getRowsFailed()).isEqualTo(2);
		assertThat(job.getRowsProcessed()).isEqualTo(15);
		assertThat(job.getLastCommittedLine()).isEqualTo(16);
	}

	@Test
	void startCountsAttemptsAndKeepsTheFirstStartedAt() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		job.start();
		Instant first = job.getStartedAt();

		job.requeue();
		job.start();

		assertThat(job.getAttempts()).isEqualTo(2);
		assertThat(job.getStartedAt()).isEqualTo(first);
	}

	@Test
	void requeueKeepsProgress() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		job.start();
		job.recordProgress(8, 2, 11);

		job.requeue();

		assertThat(job.getStatus()).isEqualTo(ImportStatus.QUEUED);
		assertThat(job.getRowsSucceeded()).isEqualTo(8);
		assertThat(job.getRowsFailed()).isEqualTo(2);
		assertThat(job.getLastCommittedLine()).isEqualTo(11);
		assertThat(job.getFinishedAt()).isNull();
	}

	@Test
	void recordProgressRejectsALineAlreadyCommitted() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		job.start();
		job.recordProgress(5, 0, 6);

		assertThatThrownBy(() -> job.recordProgress(1, 0, 6)).isInstanceOf(IllegalStateException.class)
				.hasMessage("batch ends at line 6 but line 6 is already committed");
		assertThat(job.getRowsSucceeded()).isEqualTo(5);
	}

	@Test
	void completeMovesRunningToCompletedAndStampsFinishedAt() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		job.start();

		job.complete();

		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getFinishedAt()).isNotNull();
		assertThat(job.getStatus().isTerminal()).isTrue();
	}

	@Test
	void failWorksFromQueuedAndFromRunning() {
		ImportJob jobA = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		jobA.fail("file is not valid UTF-8");

		assertThat(jobA.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(jobA.getErrorMessage()).isEqualTo("file is not valid UTF-8");
		assertThat(jobA.getFinishedAt()).isNotNull();
		assertThat(jobA.getStartedAt()).isNull();

		ImportJob jobB = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		jobB.start();
		jobB.fail("boom");

		assertThat(jobB.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(jobB.getStartedAt()).isNotNull();
	}

	@Test
	void failKeepsOnlyTheFirst1000CharsOfTheMessage() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);

		job.fail("x".repeat(1500));

		assertThat(job.getErrorMessage()).hasSize(1000);
	}

	@Test
	void illegalTransitionsThrowWithTheCurrentStatus() {
		ImportJob newJob = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		assertThatThrownBy(newJob::complete).isInstanceOf(IllegalStateException.class)
				.hasMessage("cannot complete a QUEUED job");

		ImportJob freshJob = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		assertThatThrownBy(() -> freshJob.recordProgress(1, 0, 2)).isInstanceOf(IllegalStateException.class)
				.hasMessage("cannot record progress on a QUEUED job");

		ImportJob runningJob = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		runningJob.start();
		assertThatThrownBy(runningJob::start).isInstanceOf(IllegalStateException.class)
				.hasMessage("cannot start a RUNNING job");

		ImportJob completedJob = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		completedJob.start();
		completedJob.complete();
		assertThatThrownBy(() -> completedJob.fail("x")).isInstanceOf(IllegalStateException.class)
				.hasMessage("cannot fail a COMPLETED job");

		ImportJob failedJob = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		failedJob.fail("boom");
		assertThatThrownBy(failedJob::start).isInstanceOf(IllegalStateException.class)
				.hasMessage("cannot start a FAILED job");

		ImportJob queuedJob = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		assertThatThrownBy(queuedJob::requeue).isInstanceOf(IllegalStateException.class)
				.hasMessage("cannot requeue a QUEUED job");
	}

	@Test
	void recordProgressRejectsNegativeCounts() {
		ImportJob job = new ImportJob("people.csv", "/nonexistent/people.csv", 22);
		job.start();

		assertThatThrownBy(() -> job.recordProgress(-1, 0, 2)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("row counts must not be negative");
	}

}
