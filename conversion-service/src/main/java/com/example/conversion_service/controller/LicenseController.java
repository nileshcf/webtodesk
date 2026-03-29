package com.example.conversion_service.controller;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.service.LicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * License management endpoints.
 * Gateway routes: /conversion/license/** → StripPrefix=1 → /license/**
 */
@Tag(name = "License", description = "License tier info, expiry, usage stats, upgrade flow, and feature availability checks")
@Slf4j
@RestController
@RequestMapping("/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    /** GET /license/current — returns current license info for the authenticated user */
    @GetMapping("/current")
    public ResponseEntity<LicenseInfoResponse> getCurrentLicense(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(licenseService.getCurrentLicense(userEmail));
    }

    /** GET /license/dashboard — full dashboard: info + upgrade options + usage stats + expiry warning */
    @GetMapping("/dashboard")
    public ResponseEntity<LicenseDashboardResponse> getDashboard(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(licenseService.getDashboard(userEmail));
    }

    /** POST /license/validate — validate license for a specific operation (build/download/update) */
    @PostMapping("/validate")
    public ResponseEntity<LicenseValidationResponse> validateLicense(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody @Valid ValidateLicenseRequest request) {
        return ResponseEntity.ok(licenseService.validateLicense(userEmail, request.operation()));
    }

    /** GET /license/upgrade-options — returns list of available upgrade tiers */
    @GetMapping("/upgrade-options")
    public ResponseEntity<List<UpgradeOptionResponse>> getUpgradeOptions(
            @RequestHeader("X-User-Email") String userEmail) {
        LicenseInfoResponse info = licenseService.getCurrentLicense(userEmail);
        return ResponseEntity.ok(licenseService.getUpgradeOptions(info.tier()));
    }

    /** POST /license/upgrade — initiates an upgrade and returns a checkout URL */
    @PostMapping("/upgrade")
    public ResponseEntity<InitiateUpgradeResponse> initiateUpgrade(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody @Valid InitiateUpgradeRequest request) {
        return ResponseEntity.ok(
                licenseService.initiateUpgrade(userEmail, request.tier(), request.billingCycle()));
    }

    /** POST /license/upgrade/complete — finalises upgrade after payment */
    @PostMapping("/upgrade/complete")
    public ResponseEntity<LicenseInfoResponse> completeUpgrade(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody @Valid CompleteUpgradeRequest request) {
        return ResponseEntity.ok(licenseService.completeUpgrade(userEmail, request.sessionId()));
    }

    /** GET /license/usage?period=current|last30|last90 — usage statistics */
    @GetMapping("/usage")
    public ResponseEntity<LicenseUsageStatsResponse> getUsageStats(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestParam(value = "period", defaultValue = "current") String period) {
        return ResponseEntity.ok(licenseService.getUsageStats(userEmail));
    }

    /** GET /license/features/{featureId}/availability — check feature availability */
    @GetMapping("/features/{featureId}/availability")
    public ResponseEntity<Map<String, Boolean>> checkFeatureAvailability(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable("featureId") String featureId) {
        boolean available = licenseService.isFeatureAvailable(userEmail, featureId);
        return ResponseEntity.ok(Map.of("available", available));
    }

    /** GET /license/restrictions — tier-based feature restrictions */
    @GetMapping("/restrictions")
    public ResponseEntity<LicenseRestrictionsResponse> getRestrictions(
            @RequestHeader("X-User-Email") String userEmail) {
        LicenseInfoResponse info = licenseService.getCurrentLicense(userEmail);
        return ResponseEntity.ok(LicenseRestrictionsResponse.forTier(info.tier()));
    }

    /** POST /license/refresh — evict cache and return fresh license data */
    @PostMapping("/refresh")
    public ResponseEntity<LicenseInfoResponse> refreshLicense(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(licenseService.refreshLicense(userEmail));
    }
}
