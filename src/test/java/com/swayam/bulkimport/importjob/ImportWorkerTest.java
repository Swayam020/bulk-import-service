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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ImportWorkerTest {

	@Autowired MockMvc mvc;
	@Autowired ImportJobRepository jobs;
	@Autowired CustomerRepository customers;
	@Autowired ImportWorker worker;
	@Autowired ObjectMapper json;

	private final List<Path> storedFiles = new ArrayList<>();

	@AfterEach
	void deleteStoredFiles() throws IOException {
		for (Path path : storedFiles) {
			Files.deleteIfExists(path);
		}
	}

	private UUID upload(byte[] content) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "people.csv", "text/csv", content);

		String body = mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();

		UUID jobId = UUID.fromString(json.readTree(body).get("jobId").asString());
		storedFiles.add(Path.of(jobs.findById(jobId).get().getStoredPath()));
		return jobId;
	}

	private UUID upload(String content) throws Exception {
		return upload(content.getBytes(StandardCharsets.UTF_8));
	}

	// other test classes leave QUEUED jobs behind (their files are deleted, so the worker
	// fails them); draining processes those too, which is fine
	private void drainQueue() {
		while (worker.processNext()) {
		}
	}

	@Test
	void importsASmallCleanFileEndToEnd() throws Exception {
		UUID id = upload(
				"id,name,email,amount\n1,Alice,alice@example.com,10.50\n2,Bob,bob@example.com,0.99\n3,Carol,carol@example.com,1200\n\n");

		drainQueue();

		String body = mvc.perform(get("/api/imports/" + id))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.rowsSucceeded").value(3))
				.andExpect(jsonPath("$.rowsFailed").value(0))
				.andExpect(jsonPath("$.rowsProcessed").value(3))
				.andReturn().getResponse().getContentAsString();

		JsonNode root = json.readTree(body);
		assertThat(root.get("startedAt").isNull()).isFalse();
		assertThat(root.get("finishedAt").isNull()).isFalse();
		assertThat(root.get("errorMessage").isNull()).isTrue();

		List<Customer> saved = customers.findByImportJobIdOrderByExternalIdAsc(id);
		assertThat(saved).hasSize(3);
		assertThat(saved.get(0).getExternalId()).isEqualTo(1);
		assertThat(saved.get(0).getName()).isEqualTo("Alice");
		assertThat(saved.get(0).getEmail()).isEqualTo("alice@example.com");
		assertThat(saved.get(0).getAmount()).isEqualByComparingTo("10.50");
		assertThat(saved.get(2).getExternalId()).isEqualTo(3);
		assertThat(saved.get(2).getAmount()).isEqualByComparingTo("1200");
	}

	@Test
	void aBadRowFailsAloneAndTheGoodRowsPersist() throws Exception {
		UUID id = upload("id,name,email,amount\n1,Alice,alice@example.com,10.50\n2,Bob,bob@example.com,ten\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isEqualTo(1);
		assertThat(job.getRowsFailed()).isEqualTo(1);
		assertThat(job.getErrorMessage()).isNull();
		List<Customer> saved = customers.findByImportJobIdOrderByExternalIdAsc(id);
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getExternalId()).isEqualTo(1);
		assertThat(saved.get(0).getName()).isEqualTo("Alice");
	}

	@Test
	void aFileOfOnlyBadRowsCompletesWithThemCounted() throws Exception {
		UUID id = upload("id,name,email,amount\n1,Alice,alice@example.com,ten\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isZero();
		assertThat(job.getRowsFailed()).isEqualTo(1);
		assertThat(customers.countByImportJobId(id)).isZero();
	}

	@Test
	void aNulCharacterFailsOnlyThatRow() throws Exception {
		UUID id = upload("id,name,email,amount\n1,Alice,alice@example.com,10.50\n2,B\u0000b,bob@example.com,1\n3,Carol,carol@example.com,2\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isEqualTo(2);
		assertThat(job.getRowsFailed()).isEqualTo(1);
		assertThat(customers.countByImportJobId(id)).isEqualTo(2);
	}

	@Test
	void aWrongHeaderFailsTheJob() throws Exception {
		UUID id = upload("name,email\nAlice,alice@example.com\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(job.getErrorMessage()).isEqualTo("line 1: header must be id,name,email,amount");
	}

	@Test
	void aHeaderOnlyFileCompletesWithZeroRows() throws Exception {
		UUID id = upload("id,name,email,amount\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isZero();
	}

	@Test
	void invalidUtf8FailsTheJob() throws Exception {
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		content.write("id,name,email,amount\n1,Al".getBytes(StandardCharsets.UTF_8));
		content.write(0xC3);
		content.write(0x28);
		content.write("ce,alice@example.com,1\n".getBytes(StandardCharsets.UTF_8));

		UUID id = upload(content.toByteArray());

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(job.getErrorMessage()).isEqualTo("file is not valid UTF-8");
	}

	@Test
	void aMissingStoredFileFailsTheJob() {
		ImportJob job = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22));

		drainQueue();

		ImportJob reloaded = jobs.findById(job.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ImportStatus.FAILED);
		assertThat(reloaded.getErrorMessage()).isEqualTo("stored file could not be read");
	}

	@Test
	void processNextReturnsFalseWhenNothingIsQueued() {
		drainQueue();

		assertThat(worker.processNext()).isFalse();
	}

}
