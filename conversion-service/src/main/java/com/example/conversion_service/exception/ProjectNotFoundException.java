package com.example.conversion_service.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String id) {
        super("Conversion project not found: " + id);
    }
}
