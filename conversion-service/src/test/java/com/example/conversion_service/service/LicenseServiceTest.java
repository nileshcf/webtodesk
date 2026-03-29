package com.example.conversion_service.service;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.exception.LicenseViolationException;
import com.example.conversion_service.repository.ConversionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private ConversionRepository repository;

    @InjectMocks
    private LicenseService licenseService;

    private ConversionProject trialProject;

    @BeforeEach
    void setUp() {
        trialProject = ConversionProject.builder()
                .id("proj-1")
                .projectName("Test App")
                .createdBy("user@example.com")
                .tier(LicenseTier.TRIAL)
                .buildCount(0)
                .maxBuilds(4)
                .licenseExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .licenseId("license-uuid-1")
                .build();
    }

    // ─── getCurrentLicense ───────────────────────────────────────────────

    @Test
    void getCurrentLicense_returnsTrialInfo_forNewUser() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject));

        LicenseInfoResponse info = licenseService.getCurrentLicense("user@example.com");

        assertThat(info.tier()).isEqualTo(LicenseTier.TRIAL);
        assertThat(info.buildsAllowed()).isEqualTo(4);
        assertThat(info.buildsUsed()).isEqualTo(0);
    }

    @Test
    void getCurrentLicense_picksHighestTier_acrossProjects() {
        ConversionProject proProject = ConversionProject.builder()
                .id("proj-2").createdBy("user@example.com")
                .tier(LicenseTier.PRO).buildCount(5).maxBuilds(3000)
                .licenseExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .licenseId("license-uuid-2")
                .build();

        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject, proProject));

        LicenseInfoResponse info = licenseService.getCurrentLicense("user@example.com");

        assertThat(info.tier()).isEqualTo(LicenseTier.PRO);
        assertThat(info.buildsAllowed()).isEqualTo(LicenseService.maxBuildsFor(LicenseTier.PRO));
    }

    @Test
    void getCurrentLicense_returnsTrialDefaults_whenNoProjects() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("new@example.com"))
                .thenReturn(List.of());

        LicenseInfoResponse info = licenseService.getCurrentLicense("new@example.com");

        assertThat(info.tier()).isEqualTo(LicenseTier.TRIAL);
        assertThat(info.buildsAllowed()).isEqualTo(4);
    }

    // ─── validateBuildRequest ────────────────────────────────────────────

    @Test
    void validateBuildRequest_passes_forTrialUnderQuota() {
        trialProject.setBuildCount(3);
        assertThatCode(() -> licenseService.validateBuildRequest(trialProject))
                .doesNotThrowAnyException();
    }

    @Test
    void validateBuildRequest_throws_whenTrialQuotaExceeded() {
        trialProject.setBuildCount(4);

        assertThatThrownBy(() -> licenseService.validateBuildRequest(trialProject))
                .isInstanceOf(LicenseViolationException.class)
                .hasMessageContaining("TRIAL limit reached");
    }

    @Test
    void validateBuildRequest_throws_whenStarterQuotaExceeded() {
        ConversionProject starter = ConversionProject.builder()
                .id("proj-s").tier(LicenseTier.STARTER).buildCount(120).maxBuilds(120).build();

        assertThatThrownBy(() -> licenseService.validateBuildRequest(starter))
                .isInstanceOf(LicenseViolationException.class)
                .hasMessageContaining("STARTER limit reached");
    }

    @Test
    void validateBuildRequest_throws_whenLicenseExpired() {
        trialProject.setLicenseExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> licenseService.validateBuildRequest(trialProject))
                .isInstanceOf(LicenseViolationException.class)
                .hasMessageContaining("License expired");
    }

    @Test
    void validateBuildRequest_lifetimeTier_neverHitsQuota() {
        ConversionProject lifetime = ConversionProject.builder()
                .id("proj-l").tier(LicenseTier.LIFETIME).buildCount(99999).maxBuilds(Integer.MAX_VALUE).build();

        assertThatCode(() -> licenseService.validateBuildRequest(lifetime))
                .doesNotThrowAnyException();
    }

    // ─── validateLicense (operation) ────────────────────────────────────

    @Test
    void validateLicense_returnsCantBuild_whenQuotaFull() {
        trialProject.setBuildCount(4);
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject));

        LicenseValidationResponse resp = licenseService.validateLicense("user@example.com", "build");

        assertThat(resp.canBuild()).isFalse();
        assertThat(resp.message()).contains("quota reached");
    }

    @Test
    void validateLicense_returnsTrueForDownload_evenWhenBuildQuotaFull() {
        trialProject.setBuildCount(4);
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject));

        LicenseValidationResponse resp = licenseService.validateLicense("user@example.com", "download");

        assertThat(resp.valid()).isTrue();
        assertThat(resp.canBuild()).isTrue();
    }

    // ─── getUpgradeOptions ───────────────────────────────────────────────

    @Test
    void getUpgradeOptions_marksCurrentTierCorrectly() {
        List<UpgradeOptionResponse> opts = licenseService.getUpgradeOptions(LicenseTier.PRO);

        UpgradeOptionResponse proPlan = opts.stream()
                .filter(o -> o.tier() == LicenseTier.PRO).findFirst().orElseThrow();
        assertThat(proPlan.current()).isTrue();

        UpgradeOptionResponse starterPlan = opts.stream()
                .filter(o -> o.tier() == LicenseTier.STARTER).findFirst().orElseThrow();
        assertThat(starterPlan.current()).isFalse();
    }

    @Test
    void getUpgradeOptions_returnsAllFourTiers() {
        List<UpgradeOptionResponse> opts = licenseService.getUpgradeOptions(LicenseTier.TRIAL);
        assertThat(opts).hasSize(4);
    }

    // ─── isFeatureAvailable ──────────────────────────────────────────────

    @Test
    void isFeatureAvailable_trialCanUseBasicBuild() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject));

        assertThat(licenseService.isFeatureAvailable("user@example.com", "basic-build")).isTrue();
    }

    @Test
    void isFeatureAvailable_trialCannotUseScreenProtect() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject));

        assertThat(licenseService.isFeatureAvailable("user@example.com", "screen-protect")).isFalse();
    }

    @Test
    void isFeatureAvailable_proCanUseAllFeatures() {
        ConversionProject pro = ConversionProject.builder()
                .id("p").createdBy("user@example.com").tier(LicenseTier.PRO)
                .buildCount(0).maxBuilds(3000)
                .licenseExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .licenseId("lic").build();
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(pro));

        assertThat(licenseService.isFeatureAvailable("user@example.com", "screen-protect")).isTrue();
        assertThat(licenseService.isFeatureAvailable("user@example.com", "biometric")).isTrue();
    }

    // ─── maxBuildsFor ────────────────────────────────────────────────────

    @Test
    void maxBuildsFor_returnsCorrectLimits() {
        assertThat(LicenseService.maxBuildsFor(LicenseTier.TRIAL)).isEqualTo(4);
        assertThat(LicenseService.maxBuildsFor(LicenseTier.STARTER)).isEqualTo(120);
        assertThat(LicenseService.maxBuildsFor(LicenseTier.PRO)).isEqualTo(3000);
        assertThat(LicenseService.maxBuildsFor(LicenseTier.LIFETIME)).isEqualTo(Integer.MAX_VALUE);
    }

    // ─── getDashboard ────────────────────────────────────────────────────

    @Test
    void getDashboard_returnsAllSections() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(trialProject));

        LicenseDashboardResponse dash = licenseService.getDashboard("user@example.com");

        assertThat(dash.currentLicense()).isNotNull();
        assertThat(dash.upgradeOptions()).isNotEmpty();
        assertThat(dash.usageStats()).isNotNull();
    }
}
