package br.com.enhara.api.simulator.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record SimulationVehicleProfile(
        ProfileId id,
        double massKg,
        double wheelRadiusM,
        double finalDriveRatio,
        List<Double> gearRatios,
        int idleRpm,
        int redlineRpm,
        double peakTorqueNm,
        double engineDisplacementLiters,
        double tankCapacityLiters,
        Set<ObdPid> supportedPids,
        Set<ObdPid> unsupportedPids,
        Set<ObdPid> unknownPids,
        String simulatedVin
) {
    public enum ProfileId { COMPACT_GASOLINE, COMPACT_GASOLINE_LIMITED }

    public SimulationVehicleProfile {
        gearRatios = List.copyOf(gearRatios);
        supportedPids = Set.copyOf(supportedPids);
        unsupportedPids = Set.copyOf(unsupportedPids);
        unknownPids = Set.copyOf(unknownPids);
        EnumSet<ObdPid> classified = EnumSet.noneOf(ObdPid.class);
        classified.addAll(supportedPids);
        classified.addAll(unsupportedPids);
        classified.addAll(unknownPids);
        if (classified.size() != ObdPid.values().length
                || !disjoint(supportedPids, unsupportedPids)
                || !disjoint(supportedPids, unknownPids)
                || !disjoint(unsupportedPids, unknownPids)) {
            throw new IllegalArgumentException("Todo PID deve possuir exatamente um estado de capability");
        }
    }

    private static boolean disjoint(Set<ObdPid> first, Set<ObdPid> second) {
        return first.stream().noneMatch(second::contains);
    }

    public static SimulationVehicleProfile of(ProfileId id) {
        return switch (id) {
            case COMPACT_GASOLINE -> fullProfile();
            case COMPACT_GASOLINE_LIMITED -> limitedProfile();
        };
    }

    private static SimulationVehicleProfile fullProfile() {
        EnumSet<ObdPid> supported = EnumSet.of(
                ObdPid.CALCULATED_ENGINE_LOAD, ObdPid.ENGINE_COOLANT_TEMPERATURE,
                ObdPid.SHORT_TERM_FUEL_TRIM_BANK_1, ObdPid.LONG_TERM_FUEL_TRIM_BANK_1,
                ObdPid.INTAKE_MANIFOLD_ABSOLUTE_PRESSURE, ObdPid.ENGINE_SPEED, ObdPid.VEHICLE_SPEED,
                ObdPid.INTAKE_AIR_TEMPERATURE, ObdPid.MAF_AIR_FLOW_RATE, ObdPid.THROTTLE_POSITION,
                ObdPid.FUEL_LEVEL_INPUT, ObdPid.BAROMETRIC_PRESSURE, ObdPid.CONTROL_MODULE_VOLTAGE,
                ObdPid.COMMANDED_EQUIVALENCE_RATIO, ObdPid.VEHICLE_IDENTIFICATION_NUMBER);
        EnumSet<ObdPid> unsupported = EnumSet.of(ObdPid.ENGINE_OIL_TEMPERATURE);
        EnumSet<ObdPid> unknown = EnumSet.of(ObdPid.OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1);
        return new SimulationVehicleProfile(ProfileId.COMPACT_GASOLINE, 1_350, 0.31, 4.10,
                List.of(3.55, 1.95, 1.30, 0.95, 0.76), 800, 6_500, 180, 1.6, 50,
                supported, unsupported, unknown, "ENH4R4S1M00000001");
    }

    private static SimulationVehicleProfile limitedProfile() {
        EnumSet<ObdPid> supported = EnumSet.of(
                ObdPid.CALCULATED_ENGINE_LOAD, ObdPid.ENGINE_COOLANT_TEMPERATURE,
                ObdPid.ENGINE_SPEED, ObdPid.VEHICLE_SPEED, ObdPid.INTAKE_AIR_TEMPERATURE,
                ObdPid.THROTTLE_POSITION, ObdPid.FUEL_LEVEL_INPUT, ObdPid.CONTROL_MODULE_VOLTAGE);
        EnumSet<ObdPid> unknown = EnumSet.of(ObdPid.VEHICLE_IDENTIFICATION_NUMBER);
        EnumSet<ObdPid> unsupported = EnumSet.allOf(ObdPid.class);
        unsupported.removeAll(supported);
        unsupported.removeAll(unknown);
        return new SimulationVehicleProfile(ProfileId.COMPACT_GASOLINE_LIMITED, 1_180, 0.30, 4.30,
                List.of(3.73, 2.05, 1.32, 0.97, 0.76), 780, 6_300, 150, 1.4, 45,
                supported, unsupported, unknown, null);
    }
}
