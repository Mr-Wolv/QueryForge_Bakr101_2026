package com.bakr.queryforge.controller;

import com.bakr.queryforge.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return buildProblem(HttpStatus.NOT_FOUND, "Not Found", e.getMessage(), null);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return buildProblem(HttpStatus.BAD_REQUEST, "Bad Request", e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validationError(MethodArgumentNotValidException e) {
        Map<String, java.util.List<String>> fieldErrors = new HashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(fe.getField(), k -> new java.util.ArrayList<>()).add(fe.getDefaultMessage());
        }
        return buildProblem(HttpStatus.BAD_REQUEST, "Validation Failed", "Invalid request parameters", fieldErrors);
    }

    private ResponseEntity<Map<String, Object>> buildProblem(HttpStatus status, String title, String detail, Map<String, java.util.List<String>> errors) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://problem.org/" + status.value());
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        if (errors != null && !errors.isEmpty()) {
            problem.put("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
