package br.com.enhara.api.trips.domain;

public record TripMetrics(double distanceKm, double averageSpeedKph, double maxSpeedKph,
                          int harshAccelerationCount, int harshBrakingCount,
                          long highRpmSeconds, int drivingScore) {
}
