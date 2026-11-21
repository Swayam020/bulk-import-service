package com.swayam.bulkimport.importjob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The stored name is a random UUID, never the client's filename, so a crafted name like
 * {@code ../../etc/x.csv} can never choose where the file is written.
 */
@Component
public class FileStorage {

	private final Path storageDir;

	public FileStorage(@Value("${app.import.storage-dir}") Path storageDir) {
		this.storageDir = storageDir.toAbsolutePath();
		try {
			Files.createDirectories(this.storageDir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public Path store(MultipartFile file) {
		Path target = storageDir.resolve(UUID.randomUUID() + ".csv");
		try {
			file.transferTo(target);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return target;
	}

	public void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			// only used on a failure path that is already throwing
		}
	}

}
