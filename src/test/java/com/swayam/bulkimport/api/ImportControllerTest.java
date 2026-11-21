package com.swayam.bulkimport.api;

import com.swayam.bulkimport.TestcontainersConfiguration;
import com.swayam.bulkimport.importjob.ImportJob;
import com.swayam.bulkimport.importjob.ImportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerTest {

	@Autowired MockMvc mvc;
	@Autowired ImportJobRepository jobs;
	@Autowired ObjectMapper json;

	@Test
	void acceptsACsvAndReturns202WithAJobId() throws Exception {
		byte[] content = "id,name\n1,Alice\n2,Bob\n".getBytes(StandardCharsets.UTF_8);
		assertThat(content.length).isEqualTo(22);
		MockMultipartFile file = new MockMultipartFile("file", "people.csv", "text/csv", content);

		var result = mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.filename").value("people.csv"))
				.andExpect(jsonPath("$.sizeBytes").value(22))
				.andExpect(jsonPath("$.jobId").exists())
				.andReturn();
		String body = result.getResponse().getContentAsString();

		UUID jobId = UUID.fromString(json.readTree(body).get("jobId").asString());
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/imports/" + jobId);

		Optional<ImportJob> job = jobs.findById(jobId);
		assertThat(job).isPresent();
		assertThat(job.get().getStatus().name()).isEqualTo("QUEUED");
		assertThat(job.get().getSizeBytes()).isEqualTo(22);
		Path stored = Path.of(job.get().getStoredPath());
		assertThat(Files.readAllBytes(stored)).isEqualTo(content);

		Files.deleteIfExists(stored);
	}

	@Test
	void storesTheFileUnderARandomNameNotTheClientsName() throws Exception {
		byte[] content = "id\n1\n".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "../../evil.csv", "text/csv", content);

		String body = mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();

		UUID jobId = UUID.fromString(json.readTree(body).get("jobId").asString());
		ImportJob job = jobs.findById(jobId).orElseThrow();
		Path stored = Path.of(job.getStoredPath());

		assertThat(stored.getFileName().toString()).doesNotContain("evil");
		assertThat(stored.getFileName().toString()).endsWith(".csv");
		assertThat(stored.getParent()).isEqualTo(Path.of("data/uploads").toAbsolutePath());

		Files.deleteIfExists(stored);
	}

	@Test
	void acceptsA200kRowFileAndStoresItByteForByte() throws Exception {
		StringBuilder sb = new StringBuilder("id,name\n");
		for (int i = 0; i < 200_000; i++) {
			sb.append(i).append(",user").append(i).append('\n');
		}
		byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", content);

		String body = mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.sizeBytes").value(content.length))
				.andReturn().getResponse().getContentAsString();

		UUID jobId = UUID.fromString(json.readTree(body).get("jobId").asString());
		ImportJob job = jobs.findById(jobId).orElseThrow();
		Path stored = Path.of(job.getStoredPath());
		assertThat(Files.size(stored)).isEqualTo(content.length);

		Files.deleteIfExists(stored);
	}

	@Test
	void rejectsAnEmptyFileWith400() throws Exception {
		long before = jobs.count();
		MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

		mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("file is empty"));

		assertThat(jobs.count()).isEqualTo(before);
	}

	@Test
	void rejectsANonCsvFileWith400() throws Exception {
		long before = jobs.count();
		byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", content);

		mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("only .csv files are accepted"));

		assertThat(jobs.count()).isEqualTo(before);
	}

	@Test
	void acceptsAnUppercaseCsvExtension() throws Exception {
		byte[] content = "id\n1\n".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "PEOPLE.CSV", "text/csv", content);

		String body = mvc.perform(multipart("/api/imports").file(file))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();

		UUID jobId = UUID.fromString(json.readTree(body).get("jobId").asString());
		ImportJob job = jobs.findById(jobId).orElseThrow();
		Files.deleteIfExists(Path.of(job.getStoredPath()));
	}

	@Test
	void rejectsARequestWithNoFilePartWith400() throws Exception {
		mvc.perform(multipart("/api/imports"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("file part is required"));
	}

}
