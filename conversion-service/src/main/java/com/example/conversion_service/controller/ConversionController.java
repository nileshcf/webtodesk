package com.example.conversion_service.controller;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.service.ConversionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversions")
@RequiredArgsConstructor
public class ConversionController {

    private final ConversionService conversionService;

    @PostMapping
    public ResponseEntity<ConversionResponse> create(
            @RequestBody @Valid CreateConversionRequest request,
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(conversionService.create(request, userEmail));
    }

    @GetMapping
    public ResponseEntity<List<ConversionResponse>> list(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(conversionService.listByUser(userEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversionResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(conversionService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversionResponse> update(
            @PathVariable String id,
            @RequestBody UpdateConversionRequest request) {
        return ResponseEntity.ok(conversionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        conversionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<ElectronConfigResponse> generate(@PathVariable String id) {
        return ResponseEntity.ok(conversionService.generateElectronProject(id));
    }
}
