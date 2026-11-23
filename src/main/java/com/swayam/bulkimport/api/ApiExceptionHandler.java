package com.swayam.bulkimport.api;

import com.swayam.bulkimport.importjob.ImportJobNotFoundException;
import com.swayam.bulkimport.importjob.ImportNotFinishedException;
import com.swayam.bulkimport.importjob.ImportRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ImportRejectedException.class)
	ResponseEntity<Dtos.ApiError> handleImportRejected(ImportRejectedException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(ImportJobNotFoundException.class)
	ResponseEntity<Dtos.ApiError> handleImportJobNotFound(ImportJobNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ImportNotFinishedException.class)
	ResponseEntity<Dtos.ApiError> handleImportNotFinished(ImportNotFinishedException e) {
		return error(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Dtos.ApiError> handleBadJobId(MethodArgumentTypeMismatchException e) {
		return error(HttpStatus.BAD_REQUEST, "invalid job id");
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<Dtos.ApiError> handleMissingPart(MissingServletRequestPartException e) {
		return error(HttpStatus.BAD_REQUEST, "file part is required");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<Dtos.ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
		return error(HttpStatus.CONTENT_TOO_LARGE, "file exceeds the 50MB upload limit");
	}

	private static ResponseEntity<Dtos.ApiError> error(HttpStatus status, String message) {
		return ResponseEntity.status(status)
				.body(new Dtos.ApiError(status.getReasonPhrase(), message, Instant.now()));
	}

}
