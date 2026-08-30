package br.com.enhara.api.trips.domain;

import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripMetricsCalculatorTest {
    private final TripMetricsCalculator calculator = new TripMetricsCalculator();

    @Test
    void calculatesDeterministicExperimentalDrivingMetrics() {
        UUID vehicleId = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-30T12:00:00Z");
        List<TelemetrySample> samples = List.of(
                sample(vehicleId, start, 0, 900),
                sample(vehicleId, start.plusSeconds(2), 30, 2_500),
                sample(vehicleId, start.plusSeconds(4), 60, 4_200),
                sample(vehicleId, start.plusSeconds(6), 10, 1_400));

        TripMetrics metrics = calculator.calculate(samples);

        assertThat(metrics.distanceKm()).isEqualTo(0.05);
        assertThat(metrics.averageSpeedKph()).isEqualTo(25.0);
        assertThat(metrics.maxSpeedKph()).isEqualTo(60.0);
        assertThat(metrics.harshAccelerationCount()).isEqualTo(2);
        assertThat(metrics.harshBrakingCount()).isEqualTo(1);
        assertThat(metrics.highRpmSeconds()).isEqualTo(2);
        assertThat(metrics.drivingScore()).isEqualTo(76);
    }

    private TelemetrySample sample(UUID vehicleId, Instant recordedAt, double speedKph, int rpm) {
        return new TelemetrySample(vehicleId, recordedAt, speedKph, rpm, 90, 40, 25, 13.8,
                70, null, null, TelemetrySample.Source.SIMULATOR);
    }
}
