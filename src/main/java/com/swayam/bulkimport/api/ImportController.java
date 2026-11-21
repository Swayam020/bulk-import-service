package com.swayam.bulkimport.api;

import com.swayam.bulkimport.importjob.ImportJob;
import com.swayam.bulkimport.importjob.ImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

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

}
