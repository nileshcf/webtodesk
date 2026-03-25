package com.example.user_service.exception;

import com.example.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
	public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
		log.warn("Bad credentials attempt");
		return ResponseEntity
				.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse(
						"INVALID_CREDENTIALS",
						"Invalid email or password",
						HttpStatus.UNAUTHORIZED.value()
				));
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUserNotFound(UsernameNotFoundException e) {
		log.warn("User not found: {}", e.getMessage());
		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse(
						"USER_NOT_FOUND",
						"User not found",
						HttpStatus.NOT_FOUND.value()
				));
	}

	@ExceptionHandler(DisabledException.class)
	public ResponseEntity<ErrorResponse> handleDisabled(DisabledException e) {
		log.warn("Disabled account login attempt");
		return ResponseEntity
				.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse(
						"ACCOUNT_DISABLED",
						"Your account has been disabled",
						HttpStatus.FORBIDDEN.value()
				));
	}

	// ─────────────────────────────────────────
	// Validation Exceptions
	// ─────────────────────────────────────────

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining(", "));

		log.warn("Validation failed: {}", message);
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
	public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException e) {
		log.error("Runtime exception: {}", e.getMessage());
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(
						"BAD_REQUEST",
						e.getMessage(),
						HttpStatus.BAD_REQUEST.value()
				));
	}

	// ─────────────────────────────────────────
	// Fallback
	// ─────────────────────────────────────────

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
		log.error("Unexpected error: {}", e.getMessage());
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse(
						"INTERNAL_SERVER_ERROR",
						"Something went wrong. Please try again later",
						HttpStatus.INTERNAL_SERVER_ERROR.value()
				));
	}
}