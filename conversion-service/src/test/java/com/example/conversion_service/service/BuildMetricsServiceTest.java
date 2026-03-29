package com.example.conversion_service.service;

import com.example.conversion_service.entity.BuildRecord;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.repository.BuildRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BuildMetricsService Micrometer instrumentation.
 * Uses SimpleMeterRegistry — no Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class BuildMetricsServiceTest {

    @Mock
    private BuildRecordRepository buildRecordRepository;

    @Mock
    private BuildQueueService buildQueueService;

    private SimpleMeterRegistry meterRegistry;
    private BuildMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        BuildQueueService.QueueStatus emptyStatus =
                new BuildQueueService.QueueStatus(0, 0, 0.0, 0.0);
        lenient().when(buildQueueService.getQueueStatus()).thenReturn(emptyStatus);

        service = new BuildMetricsService(buildRecordRepository, buildQueueService, meterRegistry);
        service.registerQueueGauges();
    }

    // ─── Queue depth gauges ───────────────────────────────

    @Test
    void gauges_registeredOnInit() {
        Gauge normal = meterRegistry.find("build.queue.depth").tag("queue", "normal").gauge();
        Gauge priority = meterRegistry.find("build.queue.depth").tag("queue", "priority").gauge();
        assertThat(normal).isNotNull();
        assertThat(priority).isNotNull();
    }

    @Test
    void gauge_normal_reflectsQueueStatus() {
        when(buildQueueService.getQueueStatus())
                .thenReturn(new BuildQueueService.QueueStatus(3, 1, 30.0, 5.0));

        double value = meterRegistry.find("build.queue.depth").tag("queue", "normal").gauge().value();
        assertThat(value).isEqualTo(3.0);
    }

    @Test
    void gauge_priority_reflectsQueueStatus() {
        when(buildQueueService.getQueueStatus())
                .thenReturn(new BuildQueueService.QueueStatus(0, 2, 0.0, 10.0));

        double value = meterRegistry.find("build.queue.depth").tag("queue", "priority").gauge().value();
        assertThat(value).isEqualTo(2.0);
    }

    // ─── builds.started counter ───────────────────────────

    @Test
    void recordBuildStarted_incrementsCounter() {
        service.recordBuildStarted(LicenseTier.PRO, "linux");

        Counter counter = meterRegistry.find("builds.started")
                .tag("tier", "PRO").tag("os", "linux").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordBuildStarted_nullTier_usesUnknownTag() {
        service.recordBuildStarted(null, "win");

        Counter counter = meterRegistry.find("builds.started")
                .tag("tier", "UNKNOWN").tag("os", "win").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordBuildStarted_multipleCallsAccumulate() {
        service.recordBuildStarted(LicenseTier.TRIAL, "linux");
        service.recordBuildStarted(LicenseTier.TRIAL, "linux");
        service.recordBuildStarted(LicenseTier.TRIAL, "linux");

        Counter counter = meterRegistry.find("builds.started")
                .tag("tier", "TRIAL").tag("os", "linux").counter();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    // ─── builds.completed counter ─────────────────────────

    @Test
    void save_successRecord_incrementsCompletedCounterWithReadyStatus() {
        BuildRecord record = buildRecord("READY", LicenseTier.STARTER, "linux", 45_000L);
        when(buildRecordRepository.save(any())).thenReturn(record);

        service.save(record);

        Counter counter = meterRegistry.find("builds.completed")
                .tag("tier", "STARTER").tag("os", "linux").tag("status", "READY").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void save_failedRecord_incrementsCompletedCounterWithFailedStatus() {
        BuildRecord record = buildRecord("FAILED", LicenseTier.TRIAL, "win", 5_000L);
        when(buildRecordRepository.save(any())).thenReturn(record);

        service.save(record);

        Counter counter = meterRegistry.find("builds.completed")
                .tag("tier", "TRIAL").tag("os", "win").tag("status", "FAILED").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void save_nullTierAndOs_usesUnknownTags() {
        BuildRecord record = buildRecord("FAILED", null, null, 1_000L);
        when(buildRecordRepository.save(any())).thenReturn(record);

        service.save(record);

        Counter counter = meterRegistry.find("builds.completed")
                .tag("tier", "UNKNOWN").tag("os", "unknown").tag("status", "FAILED").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ─── builds.duration timer ────────────────────────────

    @Test
    void save_successRecord_recordsDurationTimer() {
        BuildRecord record = buildRecord("READY", LicenseTier.PRO, "linux", 120_000L);
        when(buildRecordRepository.save(any())).thenReturn(record);

        service.save(record);

        Timer timer = meterRegistry.find("builds.duration")
                .tag("tier", "PRO").tag("os", "linux").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(120_000.0);
    }

    @Test
    void save_zeroDuration_doesNotRecordTimer() {
        BuildRecord record = buildRecord("FAILED", LicenseTier.TRIAL, "linux", 0L);
        when(buildRecordRepository.save(any())).thenReturn(record);

        service.save(record);

        Timer timer = meterRegistry.find("builds.duration")
                .tag("tier", "TRIAL").tag("os", "linux").timer();
        assertThat(timer == null || timer.count() == 0).isTrue();
    }

    @Test
    void save_multipleDifferentTiers_separateCounters() {
        BuildRecord trial = buildRecord("READY", LicenseTier.TRIAL,   "linux", 30_000L);
        BuildRecord pro   = buildRecord("READY", LicenseTier.PRO,     "linux", 60_000L);
        when(buildRecordRepository.save(any())).thenReturn(trial).thenReturn(pro);

        service.save(trial);
        service.save(pro);

        double trialCount = meterRegistry.find("builds.completed")
                .tag("tier", "TRIAL").tag("os", "linux").tag("status", "READY")
                .counter().count();
        double proCount = meterRegistry.find("builds.completed")
                .tag("tier", "PRO").tag("os", "linux").tag("status", "READY")
                .counter().count();
        assertThat(trialCount).isEqualTo(1.0);
        assertThat(proCount).isEqualTo(1.0);
    }

    // ─── OS tag normalisation ─────────────────────────────

    @Test
    void osTag_winNormalisedToWin() {
        service.recordBuildStarted(LicenseTier.STARTER, "win");
        Counter c = meterRegistry.find("builds.started").tag("os", "win").counter();
        assertThat(c).isNotNull();
    }

    @Test
    void osTag_macNormalisedToMac() {
        service.recordBuildStarted(LicenseTier.LIFETIME, "mac");
        Counter c = meterRegistry.find("builds.started").tag("os", "mac").counter();
        assertThat(c).isNotNull();
    }

    // ─── Helpers ─────────────────────────────────────────

    private static BuildRecord buildRecord(String result, LicenseTier tier,
                                           String buildTarget, long durationMs) {
        return BuildRecord.builder()
                .id("test-id")
                .projectId("proj-1")
                .projectName("Test Project")
                .result(result)
                .tier(tier)
                .buildTarget(buildTarget)
                .durationMs(durationMs)
                .build();
    }
}
