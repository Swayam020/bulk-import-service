package com.swayam.bulkimport;

import org.springframework.boot.SpringApplication;

public class TestBulkimportApplication {

	public static void main(String[] args) {
		SpringApplication.from(BulkimportApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
