package com.swayam.bulkimport.api;

import com.swayam.bulkimport.TestcontainersConfiguration;
import com.swayam.bulkimport.importjob.ImportJob;
import com.swayam.bulkimport.importjob.ImportJobRepository;
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
class ImportStatusControllerTest {

	@Autowired MockMvc mvc;
	@Autowired ImportJobRepository jobs;
	@Autowired ObjectMapper json;

	private final List<Path> storedFiles = new ArrayList<>();

	@AfterEach
	void deleteStoredFiles() throws IOException {
		for (Path path : storedFiles) {
			Files.deleteIfExists(path);
		}
	}

	@Test
	void reportsAQueuedJob() throws Exception {
		ImportJob job = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22));

		String body = mvc.perform(get("/api/imports/" + job.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(job.getId().toString()))
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.filename").value("people.csv"))
				.andExpect(jsonPath("$.sizeBytes").value(22))
				.andExpect(jsonPath("$.rowsProcessed").value(0))
				.andExpect(jsonPath("$.rowsSucceeded").value(0))
				.andExpect(jsonPath("$.rowsFailed").value(0))
				.andReturn().getResponse().getContentAsString();

		JsonNode root = json.readTree(body);
		assertThat(root.get("createdAt").isNull()).isFalse();
		assertThat(root.get("startedAt").isNull()).isTrue();
		assertThat(root.get("finishedAt").isNull()).isTrue();
		assertThat(root.get("errorMessage").isNull()).isTrue();
	}

	@Test
	void showsStatusTransitionsOverHttp() throws Exception {
		ImportJob job = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22));

		mvc.perform(get("/api/imports/" + job.getId())).andExpect(jsonPath("$.status").value("QUEUED"));

		job.start();
		job.recordProgress(8, 2, 11);
		jobs.save(job);

		String runningBody = mvc.perform(get("/api/imports/" + job.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"))
				.andExpect(jsonPath("$.rowsSucceeded").value(8))
				.andExpect(jsonPath("$.rowsFailed").value(2))
				.andExpect(jsonPath("$.rowsProcessed").value(10))
				.andReturn().getResponse().getContentAsString();
		JsonNode runningRoot = json.readTree(runningBody);
		assertThat(runningRoot.get("startedAt").isNull()).isFalse();
		assertThat(runningRoot.get("finishedAt").isNull()).isTrue();

		job.complete();
		jobs.save(job);

		String completedBody = mvc.perform(get("/api/imports/" + job.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andReturn().getResponse().getContentAsString();
		assertThat(json.readTree(completedBody).get("finishedAt").isNull()).isFalse();
	}

	@Test
	void reportsAFailedJobWithItsMessage() throws Exception {
		ImportJob job = jobs.save(new ImportJob("people.csv", "/nonexistent/people.csv", 22));
		job.fail("file is not valid UTF-8");
		jobs.save(job);

		String body = mvc.perform(get("/api/imports/" + job.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.errorMessage").value("file is not valid UTF-8"))
				.andReturn().getResponse().getContentAsString();

		JsonNode root = json.readTree(body);
		assertThat(root.get("finishedAt").isNull()).isFalse();
		assertThat(root.get("startedAt").isNull()).isTrue();
	}

	@Test
	void returns404ForAnUnknownJob() throws Exception {
		UUID id = UUID.randomUUID();

		mvc.perform(get("/api/imports/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("import job not found: " + id));
	}

	@Test
	void returns400ForAMalformedJobId() throws Exception {
		mvc.perform(get("/api/imports/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("invalid job id"));
	}

	@Test
	void theLocationFromAnUploadServesTheJob() throws Exception {
		byte[] content = "id,name\n1,Alice\n".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "people.csv", "text/csv", content);

		var uploadResult = mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isAccepted())
				.andReturn();
		String body = uploadResult.getResponse().getContentAsString();
		UUID jobId = UUID.fromString(json.readTree(body).get("jobId").asString());
		storedFiles.add(Path.of(jobs.findById(jobId).get().getStoredPath()));

		String location = uploadResult.getResponse().getHeader("Location");

		mvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.filename").value("people.csv"))
				.andExpect(jsonPath("$.sizeBytes").value(16));
	}

}
