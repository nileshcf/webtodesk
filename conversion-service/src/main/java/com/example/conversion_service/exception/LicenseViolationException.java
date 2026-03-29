package com.example.conversion_service.exception;

public class LicenseViolationException extends RuntimeException {
    public LicenseViolationException(String message) {
        super(message);
    }
}
