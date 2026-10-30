package com.bakr.queryforge.controller;

import com.bakr.queryforge.experiment.DuplicateRequestException;
import com.bakr.queryforge.experiment.ParameterValidationException;
import com.bakr.queryforge.experiment.RunConflictException;
import com.bakr.queryforge.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFC 7807 error envelope for every API failure.
 *
 * <p>{@code type}, {@code title}, {@code status}, {@code detail} and
 * {@code instance} are standard ProblemDetail fields; field-level validation
 * failures add an {@code errors} map keyed by parameter name.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String VALIDATION_TYPE = "https://problem.org/validation-error";
    private static final String NOT_FOUND_TYPE = "https://problem.org/not-found";
    private static final String CONFLICT_TYPE = "https://problem.org/conflict";
    private static final String INTERNAL_TYPE = "https://problem.org/internal";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Not Found", e.getMessage(), null, request);
    }

    @ExceptionHandler(ParameterValidationException.class)
    public ResponseEntity<ProblemDetail> parameterValidation(ParameterValidationException e,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_TYPE, "Validation Failed",
                e.getMessage(), e.errors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validationError(MethodArgumentNotValidException e,
                                                         HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(fe.getField(), k -> new ArrayList<>())
                    .add(fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, VALIDATION_TYPE, "Validation Failed",
                "Invalid request parameters", fieldErrors, request);
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ProblemDetail> duplicate(DuplicateRequestException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, CONFLICT_TYPE, "Duplicate Request", e.getMessage(), null, request);
    }

    @ExceptionHandler(RunConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(RunConflictException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, CONFLICT_TYPE, "Run Conflict", e.getMessage(), null, request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_TYPE, "Bad Request", e.getMessage(), null, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String type = switch (status) {
            case NOT_FOUND -> NOT_FOUND_TYPE;
            case CONFLICT -> CONFLICT_TYPE;
            case INTERNAL_SERVER_ERROR -> INTERNAL_TYPE;
            default -> VALIDATION_TYPE;
        };
        String detail = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        return build(status, type, status.getReasonPhrase(), detail, null, request);
    }

    /**
     * Unmapped paths reach the advice as a resource/handler lookup failure. Left
     * to the catch-all below they would be reported as 500, so map them to a
     * proper 404 instead.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> noResource(Exception e, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "unknown path";
        return build(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Not Found",
                "No endpoint for " + path, null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        // Never swallow an unexpected failure silently: the envelope stays generic for the
        // client, but the stack trace has to reach the log or it is un-diagnosable.
        LOG.error("Unhandled exception for {} {}", request != null ? request.getMethod() : "?",
                request != null ? request.getRequestURI() : "?", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_TYPE, "Internal Server Error",
                "Unexpected server error", null, request);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String type, String title,
                                                String detail, Map<String, List<String>> errors,
                                                HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
