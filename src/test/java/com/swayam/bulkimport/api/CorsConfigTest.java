package com.swayam.bulkimport.api;

import com.swayam.bulkimport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

	@Autowired MockMvc mvc;

	@Test
	void aPreflightFromTheFrontendOriginIsAllowed() throws Exception {
		mvc.perform(options("/api/imports")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void aPreflightFromAnyOtherOriginIsRefused() throws Exception {
		mvc.perform(options("/api/imports")
				.header("Origin", "http://evil.example.com")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}
