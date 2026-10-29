package com.bakr.queryforge.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Configuration
public class ProblemDetailConfig {

    @Bean
    public ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(org.springframework.boot.web.servlet.error.ErrorAttributes.RequestAttributes requestAttributes,
                                                          ErrorAttributeOptions options) {
                var status = (Integer) requestAttributes.getAttribute("javax.servlet.error.status_code");
                var ex = getError(requestAttributes);
                var message = (ex != null && ex.getMessage() != null) ? ex.getMessage() : "Unexpected error";

                return Map.of(
                        "type", "https://problem.org/internal",
                        "title", getTitleForStatus(status),
                        "status", status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "detail", message,
                        "instance", requestAttributes.getAttribute("javax.servlet.error.request_uri"),
                        "errors", Map.of()
                );
            }
        };
    }

    private String getTitleForStatus(Integer status) {
        if (status == null) return "Internal Server Error";
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }
}
