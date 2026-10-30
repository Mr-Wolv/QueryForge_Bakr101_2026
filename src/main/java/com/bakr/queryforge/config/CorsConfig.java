package com.bakr.queryforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${queryforge.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://localhost,http://127.0.0.1}")
            String allowedOriginPatterns) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(allowedOriginPatterns.split("\\s*,\\s*"))
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("Content-Type", "X-Request-Id", "X-Client-Request-Id")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}
