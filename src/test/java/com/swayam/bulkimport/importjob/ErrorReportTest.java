package com.swayam.bulkimport.importjob;

import com.swayam.bulkimport.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ErrorReportTest {

	@Autowired MockMvc mvc;
	@Autowired ImportJobRepository jobs;
	@Autowired ImportRowErrorRepository rowErrors;
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
	void listsEachBadRowByLineNumber() throws Exception {
		UUID id = upload(
				"id,name,email,amount\n1,Alice,alice@example.com,10.50\n2,Bob,bob@example.com,ten\n\n3,Carol,not-an-email,1\n4,Dave,dave@example.com\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
		assertThat(job.getRowsSucceeded()).isEqualTo(1);
		assertThat(job.getRowsFailed()).isEqualTo(3);
		assertThat(rowErrors.countByImportJobId(id)).isEqualTo(3);

		mvc.perform(get("/api/imports/" + id + "/errors"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/csv;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"" + id + "-errors.csv\""))
				.andExpect(content().string(
						"line,error\n3,\"amount must be a number\"\n5,\"email is not valid\"\n6,\"expected 4 fields but found 3\"\n"));
	}

	@Test
	void aCleanImportHasAHeaderOnlyReport() throws Exception {
		UUID id = upload("id,name,email,amount\n1,Alice,alice@example.com,10.50\n");

		drainQueue();

		mvc.perform(get("/api/imports/" + id + "/errors"))
				.andExpect(status().isOk())
				.andExpect(content().string("line,error\n"));
	}

	@Test
	void aFailedJobStillServesItsReport() throws Exception {
		UUID id = upload("name,email\nAlice,alice@example.com\n");

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);

		mvc.perform(get("/api/imports/" + id + "/errors"))
				.andExpect(status().isOk())
				.andExpect(content().string("line,error\n"));
	}

	@Test
	void pagesThroughMoreErrorsThanOnePage() throws Exception {
		// a changed page size would quietly stop this test crossing a page boundary
		assertThat(ImportService.ERROR_PAGE_SIZE).isEqualTo(1000);

		StringBuilder csv = new StringBuilder("id,name,email,amount\n");
		for (int i = 0; i < 1200; i++) {
			csv.append(i).append(",user").append(i).append(",nope,1\n");
		}
		UUID id = upload(csv.toString());

		drainQueue();

		ImportJob job = jobs.findById(id).orElseThrow();
		assertThat(job.getRowsFailed()).isEqualTo(1200);

		String body = mvc.perform(get("/api/imports/" + id + "/errors"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String[] lines = body.split("\n");
		assertThat(lines[0]).isEqualTo("line,error");
		assertThat(lines).hasSize(1201);
		List<Long> lineNumbers = new ArrayList<>();
		for (int i = 1; i < lines.length; i++) {
			assertThat(lines[i]).endsWith(",\"email is not valid\"");
			lineNumbers.add(Long.parseLong(lines[i].substring(0, lines[i].indexOf(','))));
		}
		assertThat(lineNumbers).containsExactlyElementsOf(LongStream.rangeClosed(2, 1201).boxed().toList());
	}

	@Test
	void returns409WhileTheJobHasNotFinished() throws Exception {
		ImportJob job = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22));

		mvc.perform(get("/api/imports/" + job.getId() + "/errors"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message")
						.value("import is QUEUED; the error report is available once it has finished"))
				.andExpect(header().doesNotExist("Content-Disposition"));
	}

	@Test
	void returns404ForAnUnknownJob() throws Exception {
		UUID id = UUID.randomUUID();

		mvc.perform(get("/api/imports/" + id + "/errors"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("import job not found: " + id));
	}

	@Test
	void csvRowQuotesTheErrorAndDoublesInnerQuotes() {
		assertThat(ImportService.csvRow(7, "say \"hi\", ok")).isEqualTo("7,\"say \"\"hi\"\", ok\"\n");
	}

}
