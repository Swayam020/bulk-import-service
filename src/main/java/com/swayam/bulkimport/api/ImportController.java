package com.swayam.bulkimport.api;

import com.swayam.bulkimport.importjob.ImportJob;
import com.swayam.bulkimport.importjob.ImportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

	private final ImportService importService;

	public ImportController(ImportService importService) {
		this.importService = importService;
	}

	/**
	 * 202 rather than 201/200 because the import has only been queued, and {@code Location} is
	 * where M2's status endpoint will report progress.
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Dtos.ImportAccepted> upload(@RequestParam("file") MultipartFile file) {
		ImportJob job = importService.accept(file);
		return ResponseEntity.accepted()
				.location(URI.create("/api/imports/" + job.getId()))
				.body(Dtos.ImportAccepted.from(job));
	}

	@GetMapping("/{id}")
	public Dtos.ImportJobView status(@PathVariable UUID id) {
		return Dtos.ImportJobView.from(importService.find(id));
	}

	/**
	 * The job is checked before any header is set, so a 404/409 leaves as the usual JSON error
	 * rather than a CSV download; the filename is the job id, never the client's filename; the
	 * report is written page by page straight to the response.
	 */
	@GetMapping("/{id}/errors")
	public void errors(@PathVariable UUID id, HttpServletResponse response) throws IOException {
		importService.requireFinished(id);
		response.setContentType("text/csv");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(id + "-errors.csv").build().toString());
		importService.writeErrorReport(id, response.getWriter());
	}

}
