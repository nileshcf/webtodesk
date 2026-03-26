package com.example.conversion_service.exception;

import com.example.conversion_service.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("Validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        HttpStatus.BAD_REQUEST.value()
                ));
    }

    @ExceptionHandler({AuthenticationCredentialsNotFoundException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleAuthHeader(Exception e, HttpServletRequest request) {
        log.warn("Missing authentication context on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        "UNAUTHORIZED",
                        "Authentication context is missing or invalid",
                        HttpStatus.UNAUTHORIZED.value()
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e, HttpServletRequest request) {
        log.error("Data access failure on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "DATA_STORE_UNAVAILABLE",
                        "Database is temporarily unavailable",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException e, HttpServletRequest request) {
        log.error("Runtime exception on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        String msg = e.getMessage() == null ? "Request failed" : e.getMessage();
        HttpStatus status = msg.toLowerCase().contains("not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String code = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "BAD_REQUEST";
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(
                        code,
                        msg,
                        status.value()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "Something went wrong. Please try again later",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }
}

