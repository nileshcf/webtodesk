package com.example.user_service.exception;

import com.example.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	// ─────────────────────────────────────────
	// Auth Exceptions
	// ─────────────────────────────────────────

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
		log.warn("Bad credentials attempt on {} {}", request.getMethod(), request.getRequestURI());
		return ResponseEntity
				.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse(
						"INVALID_CREDENTIALS",
						"Invalid email or password",
						HttpStatus.UNAUTHORIZED.value()
				));
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUserNotFound(UsernameNotFoundException e, HttpServletRequest request) {
		log.warn("User not found on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse(
						"USER_NOT_FOUND",
						"User not found",
						HttpStatus.NOT_FOUND.value()
				));
	}

	@ExceptionHandler(DisabledException.class)
	public ResponseEntity<ErrorResponse> handleDisabled(DisabledException e, HttpServletRequest request) {
		log.warn("Disabled account login attempt on {} {}", request.getMethod(), request.getRequestURI());
		return ResponseEntity
				.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse(
						"ACCOUNT_DISABLED",
						"Your account has been disabled",
						HttpStatus.FORBIDDEN.value()
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

	// ─────────────────────────────────────────
	// Validation Exceptions
	// ─────────────────────────────────────────

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
		String message = e.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining(", "));

		log.warn("Validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), message);
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(
						"VALIDATION_FAILED",
						message,
						HttpStatus.BAD_REQUEST.value()
				));
	}

	// ─────────────────────────────────────────
	// Runtime Exceptions
	// ─────────────────────────────────────────

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException e, HttpServletRequest request) {
		log.error("Runtime exception on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
		String msg = e.getMessage() == null ? "Request failed" : e.getMessage();
		HttpStatus status = HttpStatus.BAD_REQUEST;
		String code = "BAD_REQUEST";

		if (msg.toLowerCase().contains("not found")) {
			status = HttpStatus.NOT_FOUND;
			code = "NOT_FOUND";
		} else if (msg.toLowerCase().contains("already taken")) {
			status = HttpStatus.CONFLICT;
			code = "CONFLICT";
		}

		return ResponseEntity
				.status(status)
				.body(new ErrorResponse(
						code,
						msg,
						status.value()
				));
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e, HttpServletRequest request) {
		log.error("Data access failure on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
		return ResponseEntity
				.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse(
						"DATA_STORE_UNAVAILABLE",
						"Database or cache is temporarily unavailable",
						HttpStatus.SERVICE_UNAVAILABLE.value()
				));
	}

	// ─────────────────────────────────────────
	// Fallback
	// ─────────────────────────────────────────

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