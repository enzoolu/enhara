package br.com.enhara.api.trips.domain;

import br.com.enhara.api.telemetry.domain.TelemetrySample;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class TripMetricsCalculator {
    static final double HARSH_ACCELERATION_METERS_PER_SECOND_SQUARED = 2.8;
    static final int HIGH_RPM_THRESHOLD = 4_000;
    private static final long MAX_SAMPLE_GAP_SECONDS = 10;

    public TripMetrics calculate(List<TelemetrySample> samples) {
        if (samples.isEmpty()) {
            return new TripMetrics(0, 0, 0, 0, 0, 0, 100);
        }

        double distanceKm = 0;
        int harshAcceleration = 0;
        int harshBraking = 0;
        long highRpmSeconds = 0;

        for (int index = 1; index < samples.size(); index++) {
            TelemetrySample previous = samples.get(index - 1);
            TelemetrySample current = samples.get(index);
            double seconds = Math.min(MAX_SAMPLE_GAP_SECONDS,
                    Math.max(0, Duration.between(previous.getRecordedAt(), current.getRecordedAt()).toMillis() / 1_000.0));
            if (seconds <= 0) {
                continue;
            }

            distanceKm += ((previous.getSpeedKph() + current.getSpeedKph()) / 2.0) * seconds / 3_600.0;
            double acceleration = ((current.getSpeedKph() - previous.getSpeedKph()) / 3.6) / seconds;
            if (acceleration >= HARSH_ACCELERATION_METERS_PER_SECOND_SQUARED) {
                harshAcceleration++;
            } else if (acceleration <= -HARSH_ACCELERATION_METERS_PER_SECOND_SQUARED) {
                harshBraking++;
            }
            if (previous.getRpm() >= HIGH_RPM_THRESHOLD) {
                highRpmSeconds += Math.round(seconds);
            }
        }

        double averageSpeed = samples.stream().mapToDouble(TelemetrySample::getSpeedKph).average().orElse(0);
        double maxSpeed = samples.stream().mapToDouble(TelemetrySample::getSpeedKph).max().orElse(0);
        int scorePenalty = (harshAcceleration + harshBraking) * 8 + (int) Math.min(20, highRpmSeconds / 15);
        int drivingScore = Math.max(40, 100 - scorePenalty);
        return new TripMetrics(round(distanceKm, 2), round(averageSpeed, 1), round(maxSpeed, 1),
                harshAcceleration, harshBraking, highRpmSeconds, drivingScore);
    }

    private double round(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }
}
