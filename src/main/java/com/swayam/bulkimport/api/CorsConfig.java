package com.swayam.bulkimport.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend runs on its own origin, so the browser hides every response from it unless we
 * name that origin back. One configured origin, not a wildcard: any page could otherwise read
 * job status and import files on a visitor's behalf.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String frontendOrigin;

	public CorsConfig(@Value("${app.frontend-origin}") String frontendOrigin) {
		this.frontendOrigin = frontendOrigin;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOrigins(frontendOrigin).allowedMethods("GET", "POST");
	}

}
