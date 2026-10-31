package com.bakr.queryforge.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The API envelope must classify client mistakes as 4xx. A malformed body used
 * to reach the catch-all handler and surface as a 500, which made a bad request
 * look like a server failure.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/runs");

    private static HttpInputMessage emptyBody() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }

    @Test
    void malformedBodyIsBadRequestNotServerError() {
        HttpMessageNotReadableException error =
                new HttpMessageNotReadableException("Cannot deserialize", emptyBody());

        ResponseEntity<ProblemDetail> response = handler.unreadable(error, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType().toString())
                .isEqualTo("https://problem.org/validation-error");
        assertThat(response.getBody().getDetail()).isNotEqualTo("Unexpected server error");
    }

    @Test
    void unsupportedContentTypeIsFourFifteen() {
        HttpMediaTypeNotSupportedException error =
                new HttpMediaTypeNotSupportedException("text/plain");

        ResponseEntity<ProblemDetail> response = handler.unsupportedMediaType(error, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void wrongHttpVerbIsMethodNotAllowed() {
        HttpRequestMethodNotSupportedException error =
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST"));

        ResponseEntity<ProblemDetail> response = handler.methodNotAllowed(error, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
    }
}
