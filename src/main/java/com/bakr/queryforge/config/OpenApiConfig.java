package com.bakr.queryforge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI queryForgeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QueryForge API")
                        .version("v1")
                        .description("PostgreSQL Performance Engineering Service — experiment, run, and compare controlled workloads."));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .build();
    }
}
