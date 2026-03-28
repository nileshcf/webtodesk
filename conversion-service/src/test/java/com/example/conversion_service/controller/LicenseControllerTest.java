package com.example.conversion_service.controller;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.service.LicenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseControllerTest {

    @Mock
    private LicenseService licenseService;

    @InjectMocks
    private LicenseController licenseController;

    private static final String USER_EMAIL = "user@example.com";

    private LicenseInfoResponse trialInfo;

    @BeforeEach
    void setUp() {
        trialInfo = LicenseInfoResponse.of(
                "license-uuid",
                LicenseTier.TRIAL,
                Instant.now().plus(30, ChronoUnit.DAYS),
                0, 4, 0,
                Instant.now()
        );
    }

    // ─── GET /license/current ────────────────────────────────────────────

    @Test
    void getCurrentLicense_returns200WithLicenseInfo() {
        when(licenseService.getCurrentLicense(USER_EMAIL)).thenReturn(trialInfo);

        ResponseEntity<LicenseInfoResponse> resp = licenseController.getCurrentLicense(USER_EMAIL);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().tier()).isEqualTo(LicenseTier.TRIAL);
        assertThat(resp.getBody().buildsAllowed()).isEqualTo(4);
        verify(licenseService).getCurrentLicense(USER_EMAIL);
    }

    // ─── GET /license/dashboard ──────────────────────────────────────────

    @Test
    void getDashboard_returns200WithDashboard() {
        LicenseDashboardResponse dashboard = new LicenseDashboardResponse(
                trialInfo,
                List.of(),
                new LicenseUsageStatsResponse(0, 0, 0, 0, 0),
                null
        );
        when(licenseService.getDashboard(USER_EMAIL)).thenReturn(dashboard);

        ResponseEntity<LicenseDashboardResponse> resp = licenseController.getDashboard(USER_EMAIL);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().currentLicense().tier()).isEqualTo(LicenseTier.TRIAL);
    }

    // ─── POST /license/validate ──────────────────────────────────────────

    @Test
    void validateLicense_returns200WithValidationResult() {
        LicenseRestrictionsResponse restrictions = LicenseRestrictionsResponse.forTier(LicenseTier.TRIAL);
        LicenseValidationResponse validation = new LicenseValidationResponse(
                true, LicenseTier.TRIAL, restrictions, true, null
        );
        when(licenseService.validateLicense(USER_EMAIL, "build")).thenReturn(validation);

        ResponseEntity<LicenseValidationResponse> resp =
                licenseController.validateLicense(USER_EMAIL, new ValidateLicenseRequest("build"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().valid()).isTrue();
        assertThat(resp.getBody().canBuild()).isTrue();
    }

    @Test
    void validateLicense_returnsCannotBuild_whenQuotaExceeded() {
        LicenseRestrictionsResponse restrictions = LicenseRestrictionsResponse.forTier(LicenseTier.TRIAL);
        LicenseValidationResponse validation = new LicenseValidationResponse(
                true, LicenseTier.TRIAL, restrictions, false, "Build quota reached"
        );
        when(licenseService.validateLicense(USER_EMAIL, "build")).thenReturn(validation);

        ResponseEntity<LicenseValidationResponse> resp =
                licenseController.validateLicense(USER_EMAIL, new ValidateLicenseRequest("build"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().canBuild()).isFalse();
        assertThat(resp.getBody().message()).isEqualTo("Build quota reached");
    }

    // ─── GET /license/upgrade-options ───────────────────────────────────

    @Test
    void getUpgradeOptions_returns200WithOptions() {
        List<UpgradeOptionResponse> opts = List.of(
                new UpgradeOptionResponse(LicenseTier.STARTER, 9.99, "USD", "monthly", List.of(), false, false),
                new UpgradeOptionResponse(LicenseTier.PRO, 29.99, "USD", "monthly", List.of(), true, false)
        );
        when(licenseService.getCurrentLicense(USER_EMAIL)).thenReturn(trialInfo);
        when(licenseService.getUpgradeOptions(LicenseTier.TRIAL)).thenReturn(opts);

        ResponseEntity<List<UpgradeOptionResponse>> resp = licenseController.getUpgradeOptions(USER_EMAIL);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
    }

    // ─── POST /license/upgrade ───────────────────────────────────────────

    @Test
    void initiateUpgrade_returns200WithUpgradeUrl() {
        InitiateUpgradeResponse upgradeResp = new InitiateUpgradeResponse(
                "https://buy.stripe.com/test", "session-123"
        );
        when(licenseService.initiateUpgrade(USER_EMAIL, "PRO", "monthly")).thenReturn(upgradeResp);

        ResponseEntity<InitiateUpgradeResponse> resp = licenseController.initiateUpgrade(
                USER_EMAIL, new InitiateUpgradeRequest("PRO", "monthly")
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().upgradeUrl()).contains("stripe.com");
        assertThat(resp.getBody().sessionId()).isEqualTo("session-123");
    }

    // ─── POST /license/upgrade/complete ─────────────────────────────────

    @Test
    void completeUpgrade_returns200WithNewLicenseInfo() {
        LicenseInfoResponse proInfo = LicenseInfoResponse.of(
                "license-uuid-pro", LicenseTier.PRO,
                Instant.now().plus(30, ChronoUnit.DAYS),
                0, 3000, 0, Instant.now()
        );
        when(licenseService.completeUpgrade(USER_EMAIL, "session-123")).thenReturn(proInfo);

        ResponseEntity<LicenseInfoResponse> resp = licenseController.completeUpgrade(
                USER_EMAIL, new CompleteUpgradeRequest("session-123")
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().tier()).isEqualTo(LicenseTier.PRO);
        assertThat(resp.getBody().buildsAllowed()).isEqualTo(3000);
    }

    // ─── GET /license/usage ──────────────────────────────────────────────

    @Test
    void getUsageStats_returns200WithStats() {
        LicenseUsageStatsResponse stats = new LicenseUsageStatsResponse(5, 120.5, 0.95, 2.3, 3);
        when(licenseService.getUsageStats(USER_EMAIL)).thenReturn(stats);

        ResponseEntity<LicenseUsageStatsResponse> resp = licenseController.getUsageStats(USER_EMAIL, "current");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().buildsThisMonth()).isEqualTo(5);
        assertThat(resp.getBody().activeProjects()).isEqualTo(3);
    }

    // ─── GET /license/features/{featureId}/availability ─────────────────

    @Test
    void checkFeatureAvailability_returnsTrue_forAllowedFeature() {
        when(licenseService.isFeatureAvailable(USER_EMAIL, "basic-build")).thenReturn(true);

        ResponseEntity<Map<String, Boolean>> resp =
                licenseController.checkFeatureAvailability(USER_EMAIL, "basic-build");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("available")).isTrue();
    }

    @Test
    void checkFeatureAvailability_returnsFalse_forBlockedFeature() {
        when(licenseService.isFeatureAvailable(USER_EMAIL, "screen-protect")).thenReturn(false);

        ResponseEntity<Map<String, Boolean>> resp =
                licenseController.checkFeatureAvailability(USER_EMAIL, "screen-protect");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("available")).isFalse();
    }

    // ─── GET /license/restrictions ───────────────────────────────────────

    @Test
    void getRestrictions_returnsTrialRestrictions() {
        when(licenseService.getCurrentLicense(USER_EMAIL)).thenReturn(trialInfo);

        ResponseEntity<LicenseRestrictionsResponse> resp = licenseController.getRestrictions(USER_EMAIL);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().maxBuilds()).isEqualTo(4);
        assertThat(resp.getBody().priorityQueue()).isFalse();
        assertThat(resp.getBody().crossPlatformBuilds()).isFalse();
    }

    // ─── POST /license/refresh ───────────────────────────────────────────

    @Test
    void refreshLicense_returns200WithFreshInfo() {
        when(licenseService.refreshLicense(USER_EMAIL)).thenReturn(trialInfo);

        ResponseEntity<LicenseInfoResponse> resp = licenseController.refreshLicense(USER_EMAIL);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().tier()).isEqualTo(LicenseTier.TRIAL);
        verify(licenseService).refreshLicense(USER_EMAIL);
    }
}
