package br.com.enhara.api.simulator.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId;

/**
 * Modelo determinístico de veículo -> ECU -> OBD. Não usa números aleatórios,
 * senoides ou sinais independentes: cada saída deriva do estado físico anterior.
 */
public final class StatefulVehicleSimulator {
    public enum CapabilityStatus { SUPPORTED, UNSUPPORTED, UNKNOWN }
    public enum AvailabilityStatus { SUPPORTED, SUPPORTED_NO_DATA, UNSUPPORTED, UNKNOWN, STALE }
    public enum DtcStatus { PENDING, CONFIRMED, PERMANENT }
    public enum ReadinessStatus { READY, NOT_READY, NOT_SUPPORTED }
    public enum ReadinessMonitor {
        MISFIRE, FUEL_SYSTEM, COMPREHENSIVE_COMPONENT, CATALYST, OXYGEN_SENSOR
    }

    public record DriverInput(double throttlePercent, double brakePercent) {}

    public record VehicleState(double speedKph, int rpm, int gear, boolean shifting,
                               Integer shiftedFromGear, Integer shiftedToGear,
                               double engineLoadPercent, double coolantTemperatureC,
                               double intakeAirTemperatureC, double controlModuleVoltage,
                               double fuelLevelPercent) {}

    public record Capability(String key, String service, String pid, String unit,
                             CapabilityStatus status, AvailabilityStatus availability) {}

    public record LivePidValue(String key, String service, String pid, double value,
                               String unit, AvailabilityStatus availability, Instant observedAt) {}

    public record FreezeFrame(Instant capturedAt, List<LivePidValue> values) {
        public FreezeFrame {
            values = List.copyOf(values);
        }
    }

    public record SimulatedDtc(String code, String description, List<DtcStatus> statuses,
                               boolean active, Instant firstDetectedAt, Instant lastDetectedAt,
                               FreezeFrame freezeFrame) {
        public SimulatedDtc {
            statuses = List.copyOf(statuses);
        }
    }

    public record Readiness(ReadinessMonitor monitor, ReadinessStatus status) {}

    public record VehicleInformation(String vin, String source) {}

    public record Snapshot(ProfileId profile, SimulationScenario scenario,
                           List<Capability> capabilities, DriverInput driverInput,
                           VehicleState vehicleState, List<LivePidValue> liveData,
                           List<SimulatedDtc> dtcs, boolean milOn,
                           List<Readiness> readiness, VehicleInformation vehicleInformation,
                           long elapsedSeconds) {
        public Snapshot {
            capabilities = List.copyOf(capabilities);
            liveData = List.copyOf(liveData);
            dtcs = List.copyOf(dtcs);
            readiness = List.copyOf(readiness);
        }
    }

    public record Frame(Snapshot snapshot, double speedKph, int rpm, double engineTempC,
                        double engineLoadPercent, double throttlePositionPercent,
                        double controlModuleVoltage, double fuelLevelPercent) {}

    private static final double STEP_SECONDS = 1.0;
    private static final double AMBIENT_TEMPERATURE_C = 24.0;
    public static final Duration LIVE_DATA_STALE_AFTER = Duration.ofSeconds(5);
    private static final double AIR_DENSITY_GRAMS_PER_LITER = 1.18;
    private static final double STOICHIOMETRIC_AFR_GASOLINE = 14.7;
    private static final double GASOLINE_DENSITY_GRAMS_PER_LITER = 745.0;
    private static final double BAROMETRIC_PRESSURE_KPA = 101.3;

    private final SimulationVehicleProfile profile;
    private final EnumMap<ReadinessMonitor, ReadinessStatus> readiness = new EnumMap<>(ReadinessMonitor.class);
    private final Map<String, FaultMemory> faultMemory = new LinkedHashMap<>();
    private SimulationScenario scenario = SimulationScenario.NORMAL;
    private long elapsedSeconds;
    private double speedMetersPerSecond;
    private int rpm;
    private int gear = 1;
    private double coolantTemperatureC = AMBIENT_TEMPERATURE_C + 2.0;
    private double intakeAirTemperatureC = AMBIENT_TEMPERATURE_C + 3.0;
    private double controlModuleVoltage = 12.6;
    private double fuelLevelPercent = 72.0;
    private double longTermFuelTrimPercent;
    private DriverInput lastDriverInput = new DriverInput(0, 0);
    private VehicleState lastVehicleState;
    private List<LivePidValue> lastLiveData = List.of();
    private Instant lastObservedAt;
    private int oxygenMonitorSeconds;
    private int catalystMonitorSeconds;

    public StatefulVehicleSimulator(ProfileId profileId) {
        this.profile = SimulationVehicleProfile.of(profileId);
        this.rpm = profile.idleRpm();
        initializeReadiness();
        this.lastVehicleState = vehicleState(false, null, null, 12.0);
    }

    public SimulationScenario scenario() {
        return scenario;
    }

    public ProfileId profileId() {
        return profile.id();
    }

    public void setScenario(SimulationScenario scenario) {
        this.scenario = scenario;
    }

    public Frame tick(Instant observedAt) {
        elapsedSeconds++;

        DriverInput driverInput = driverInputFor(elapsedSeconds, speedMetersPerSecond * 3.6);
        int shiftedFrom = gear;
        boolean shifting = selectGear(driverInput);
        Integer fromGear = shifting ? shiftedFrom : null;
        Integer toGear = shifting ? gear : null;

        double engineLoad = updateDynamics(driverInput, shifting);
        updateThermalState(engineLoad);
        updateElectricalState();
        double mapKpa = manifoldPressure(driverInput.throttlePercent());
        double mafGps = massAirFlow(mapKpa);
        double lambda = scenario == SimulationScenario.MISFIRE ? 1.07 : 1.0;
        double shortTermFuelTrim = scenario == SimulationScenario.MISFIRE ? 8.0 : -longTermFuelTrimPercent * 0.35;
        longTermFuelTrimPercent += (shortTermFuelTrim - longTermFuelTrimPercent) * 0.015;
        consumeFuel(mafGps, lambda);

        this.lastDriverInput = driverInput;
        this.lastObservedAt = observedAt;
        this.lastVehicleState = vehicleState(shifting, fromGear, toGear, engineLoad);
        this.lastLiveData = buildLiveData(observedAt, engineLoad, mapKpa, mafGps, shortTermFuelTrim, lambda);
        updateReadiness();
        updateFaultMemory(observedAt);

        Snapshot snapshot = snapshot(observedAt);
        return new Frame(snapshot, round(speedMetersPerSecond * 3.6), rpm, round(coolantTemperatureC),
                round(engineLoad), round(driverInput.throttlePercent()), round(controlModuleVoltage),
                round(fuelLevelPercent));
    }

    public Snapshot snapshot() {
        return snapshot(lastObservedAt == null ? Instant.now() : lastObservedAt);
    }

    public Snapshot snapshot(Instant referenceTime) {
        List<Capability> capabilities = new ArrayList<>();
        for (ObdPid pid : ObdPid.values()) {
            CapabilityStatus status = capabilityStatus(pid);
            AvailabilityStatus availability = switch (status) {
                case SUPPORTED -> AvailabilityStatus.SUPPORTED_NO_DATA;
                case UNSUPPORTED -> AvailabilityStatus.UNSUPPORTED;
                case UNKNOWN -> AvailabilityStatus.UNKNOWN;
            };
            capabilities.add(new Capability(pid.key(), pid.service(), pid.pid(), pid.unit(), status, availability));
        }
        List<SimulatedDtc> dtcs = faultMemory.values().stream()
                .filter(FaultMemory::visible)
                .map(FaultMemory::snapshot)
                .toList();
        boolean milOn = dtcs.stream().anyMatch(dtc -> dtc.statuses().contains(DtcStatus.CONFIRMED));
        List<Readiness> readinessSnapshot = readiness.entrySet().stream()
                .map(entry -> new Readiness(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(item -> item.monitor().name()))
                .toList();
        VehicleInformation vehicleInformation = profile.supportedPids().contains(ObdPid.VEHICLE_IDENTIFICATION_NUMBER)
                ? new VehicleInformation(profile.simulatedVin(), "SIMULATED_OBD") : null;
        Snapshot snapshot = new Snapshot(profile.id(), scenario, capabilities, lastDriverInput, lastVehicleState,
                lastLiveData, dtcs, milOn, readinessSnapshot, vehicleInformation, elapsedSeconds);
        return refreshAvailability(snapshot, referenceTime);
    }

    public static Snapshot refreshAvailability(Snapshot snapshot, Instant referenceTime) {
        Map<String, LivePidValue> liveByKey = new LinkedHashMap<>();
        List<LivePidValue> liveData = snapshot.liveData().stream().map(value -> {
            AvailabilityStatus availability = availabilityAt(value.observedAt(), referenceTime);
            LivePidValue refreshed = new LivePidValue(value.key(), value.service(), value.pid(), value.value(),
                    value.unit(), availability, value.observedAt());
            liveByKey.put(refreshed.key(), refreshed);
            return refreshed;
        }).toList();
        List<Capability> capabilities = snapshot.capabilities().stream().map(capability -> {
            AvailabilityStatus availability = switch (capability.status()) {
                case UNSUPPORTED -> AvailabilityStatus.UNSUPPORTED;
                case UNKNOWN -> AvailabilityStatus.UNKNOWN;
                case SUPPORTED -> {
                    if (capability.key().equals(ObdPid.VEHICLE_IDENTIFICATION_NUMBER.key())) {
                        yield snapshot.vehicleInformation() == null
                                ? AvailabilityStatus.SUPPORTED_NO_DATA : AvailabilityStatus.SUPPORTED;
                    }
                    LivePidValue value = liveByKey.get(capability.key());
                    yield value == null ? AvailabilityStatus.SUPPORTED_NO_DATA : value.availability();
                }
            };
            return new Capability(capability.key(), capability.service(), capability.pid(), capability.unit(),
                    capability.status(), availability);
        }).toList();
        return new Snapshot(snapshot.profile(), snapshot.scenario(), capabilities, snapshot.driverInput(),
                snapshot.vehicleState(), liveData, snapshot.dtcs(), snapshot.milOn(), snapshot.readiness(),
                snapshot.vehicleInformation(), snapshot.elapsedSeconds());
    }

    private static AvailabilityStatus availabilityAt(Instant observedAt, Instant referenceTime) {
        if (observedAt == null) return AvailabilityStatus.SUPPORTED_NO_DATA;
        return observedAt.plus(LIVE_DATA_STALE_AFTER).isBefore(referenceTime)
                ? AvailabilityStatus.STALE : AvailabilityStatus.SUPPORTED;
    }

    private void initializeReadiness() {
        readiness.put(ReadinessMonitor.MISFIRE, ReadinessStatus.NOT_READY);
        readiness.put(ReadinessMonitor.FUEL_SYSTEM, ReadinessStatus.NOT_READY);
        readiness.put(ReadinessMonitor.COMPREHENSIVE_COMPONENT, ReadinessStatus.NOT_READY);
        readiness.put(ReadinessMonitor.CATALYST, ReadinessStatus.NOT_READY);
        readiness.put(ReadinessMonitor.OXYGEN_SENSOR,
                profile.supportedPids().contains(ObdPid.COMMANDED_EQUIVALENCE_RATIO)
                        ? ReadinessStatus.NOT_READY : ReadinessStatus.NOT_SUPPORTED);
    }

    private DriverInput driverInputFor(long elapsed, double speedKph) {
        long phaseSecond = elapsed % 60;
        if (phaseSecond < 20) {
            double throttle = clamp(28 + (76 - speedKph) * 0.45, 28, 62);
            return new DriverInput(throttle, 0);
        }
        if (phaseSecond < 36) {
            double error = 72 - speedKph;
            return error >= 0
                    ? new DriverInput(clamp(12 + error * 0.7, 10, 30), 0)
                    : new DriverInput(5, clamp(-error * 1.5, 0, 20));
        }
        if (phaseSecond < 51) {
            return new DriverInput(0, speedKph > 3 ? clamp(30 + speedKph * 0.35, 30, 65) : 0);
        }
        return new DriverInput(speedKph < 1 ? 0 : 4, speedKph < 1 ? 0 : 24);
    }

    private boolean selectGear(DriverInput input) {
        if (rpm > 3_250 && input.throttlePercent() > 12 && gear < profile.gearRatios().size()) {
            gear++;
            return true;
        }
        if (rpm < 1_350 && gear > 1 && (input.brakePercent() > 0 || input.throttlePercent() > 10)) {
            gear--;
            return true;
        }
        return false;
    }

    private double updateDynamics(DriverInput input, boolean shifting) {
        double throttle = input.throttlePercent() / 100.0;
        double brake = input.brakePercent() / 100.0;
        double torqueCurve = clamp(1.0 - Math.abs(rpm - 3_500) / 6_000.0, 0.58, 1.0);
        double combustionEfficiency = scenario == SimulationScenario.MISFIRE ? 0.68 : 1.0;
        double engineTorque = profile.peakTorqueNm() * torqueCurve * throttle * combustionEfficiency;
        double transmissionRatio = profile.gearRatios().get(gear - 1) * profile.finalDriveRatio();
        double clutchTransfer = shifting ? 0.12 : 1.0;
        double driveForce = engineTorque * transmissionRatio * 0.88 / profile.wheelRadiusM() * clutchTransfer;
        double rollingResistance = profile.massKg() * 9.81 * 0.012;
        double aerodynamicDrag = 0.5 * 1.225 * 0.68 * speedMetersPerSecond * speedMetersPerSecond;
        double brakeForce = brake * profile.massKg() * 7.2;
        double acceleration = (driveForce - rollingResistance - aerodynamicDrag - brakeForce) / profile.massKg();
        if (speedMetersPerSecond <= 0.01 && acceleration < 0) {
            acceleration = 0;
        }
        speedMetersPerSecond = Math.max(0, speedMetersPerSecond + acceleration * STEP_SECONDS);

        double wheelRpm = speedMetersPerSecond / (2 * Math.PI * profile.wheelRadiusM()) * 60.0;
        int coupledRpm = (int) Math.round(wheelRpm * transmissionRatio);
        if (speedMetersPerSecond < 0.4) {
            rpm = (int) Math.round(profile.idleRpm() + throttle * 900);
        } else {
            rpm = Math.max(profile.idleRpm(), Math.min(profile.redlineRpm(), coupledRpm));
        }
        if (scenario == SimulationScenario.MISFIRE && rpm > profile.idleRpm()) {
            int[] deterministicRipple = {-110, 35, -75, 20};
            rpm = Math.max(profile.idleRpm(), rpm + deterministicRipple[(int) (elapsedSeconds % deterministicRipple.length)]);
        }
        double accelerationDemand = Math.max(0, acceleration) / 4.0;
        return clamp(10 + throttle * 72 + accelerationDemand * 18, 8, 100);
    }

    private void updateThermalState(double engineLoadPercent) {
        double load = engineLoadPercent / 100.0;
        double targetTemperature = scenario == SimulationScenario.OVERHEAT
                ? 122.0 : 88.0 + load * 8.0;
        double thermalResponse = scenario == SimulationScenario.OVERHEAT
                ? 0.055 : coolantTemperatureC < 75 ? 0.05 : 0.035;
        coolantTemperatureC = clamp(coolantTemperatureC
                        + (targetTemperature - coolantTemperatureC) * thermalResponse,
                AMBIENT_TEMPERATURE_C, 124.0);

        double intakeTarget = AMBIENT_TEMPERATURE_C + 7 + load * 9 - speedMetersPerSecond * 0.16;
        intakeAirTemperatureC += (intakeTarget - intakeAirTemperatureC) * 0.10;
    }

    private void updateElectricalState() {
        double targetVoltage = scenario.isLowVoltage() ? 10.9 : 13.9;
        double response = scenario.isLowVoltage() ? 0.22 : 0.14;
        controlModuleVoltage += (targetVoltage - controlModuleVoltage) * response;
    }

    private double manifoldPressure(double throttlePercent) {
        double throttle = throttlePercent / 100.0;
        return clamp(BAROMETRIC_PRESSURE_KPA * (0.27 + throttle * 0.72), 22, BAROMETRIC_PRESSURE_KPA);
    }

    private double massAirFlow(double mapKpa) {
        double volumetricEfficiency = clamp(0.70 + mapKpa / BAROMETRIC_PRESSURE_KPA * 0.18, 0.70, 0.88);
        double intakeLitersPerMinute = profile.engineDisplacementLiters() * rpm / 2.0 * volumetricEfficiency;
        return intakeLitersPerMinute * AIR_DENSITY_GRAMS_PER_LITER / 60.0
                * (mapKpa / BAROMETRIC_PRESSURE_KPA);
    }

    private void consumeFuel(double mafGps, double lambda) {
        double fuelGrams = mafGps / (STOICHIOMETRIC_AFR_GASOLINE * lambda) * STEP_SECONDS;
        double consumedLiters = fuelGrams / GASOLINE_DENSITY_GRAMS_PER_LITER;
        fuelLevelPercent = Math.max(0,
                fuelLevelPercent - consumedLiters / profile.tankCapacityLiters() * 100.0);
    }

    private List<LivePidValue> buildLiveData(Instant observedAt, double load, double mapKpa, double mafGps,
                                             double shortTermFuelTrim, double lambda) {
        EnumMap<ObdPid, Double> values = new EnumMap<>(ObdPid.class);
        values.put(ObdPid.CALCULATED_ENGINE_LOAD, load);
        values.put(ObdPid.ENGINE_COOLANT_TEMPERATURE, coolantTemperatureC);
        values.put(ObdPid.SHORT_TERM_FUEL_TRIM_BANK_1, shortTermFuelTrim);
        values.put(ObdPid.LONG_TERM_FUEL_TRIM_BANK_1, longTermFuelTrimPercent);
        values.put(ObdPid.INTAKE_MANIFOLD_ABSOLUTE_PRESSURE, mapKpa);
        values.put(ObdPid.ENGINE_SPEED, (double) rpm);
        values.put(ObdPid.VEHICLE_SPEED, speedMetersPerSecond * 3.6);
        values.put(ObdPid.INTAKE_AIR_TEMPERATURE, intakeAirTemperatureC);
        values.put(ObdPid.MAF_AIR_FLOW_RATE, mafGps);
        values.put(ObdPid.THROTTLE_POSITION, lastDriverInput.throttlePercent());
        values.put(ObdPid.FUEL_LEVEL_INPUT, fuelLevelPercent);
        values.put(ObdPid.BAROMETRIC_PRESSURE, BAROMETRIC_PRESSURE_KPA);
        values.put(ObdPid.CONTROL_MODULE_VOLTAGE, controlModuleVoltage);
        values.put(ObdPid.COMMANDED_EQUIVALENCE_RATIO, lambda);
        return values.entrySet().stream()
                .filter(entry -> profile.supportedPids().contains(entry.getKey()))
                .map(entry -> new LivePidValue(entry.getKey().key(), entry.getKey().service(), entry.getKey().pid(),
                        roundTwo(entry.getValue()), entry.getKey().unit(), AvailabilityStatus.SUPPORTED, observedAt))
                .toList();
    }

    private void updateReadiness() {
        if (elapsedSeconds >= 2) {
            readiness.put(ReadinessMonitor.MISFIRE, ReadinessStatus.READY);
            readiness.put(ReadinessMonitor.FUEL_SYSTEM, ReadinessStatus.READY);
            readiness.put(ReadinessMonitor.COMPREHENSIVE_COMPONENT, ReadinessStatus.READY);
        }
        if (coolantTemperatureC >= 75 && speedMetersPerSecond * 3.6 >= 15) {
            oxygenMonitorSeconds++;
            if (scenario != SimulationScenario.MISFIRE) {
                catalystMonitorSeconds++;
            }
        }
        if (oxygenMonitorSeconds >= 3 && readiness.get(ReadinessMonitor.OXYGEN_SENSOR) != ReadinessStatus.NOT_SUPPORTED) {
            readiness.put(ReadinessMonitor.OXYGEN_SENSOR, ReadinessStatus.READY);
        }
        if (catalystMonitorSeconds >= 6) {
            readiness.put(ReadinessMonitor.CATALYST, ReadinessStatus.READY);
        }
    }

    private void updateFaultMemory(Instant observedAt) {
        Map<String, FaultDefinition> failing = new LinkedHashMap<>();
        if (scenario == SimulationScenario.MISFIRE) {
            failing.put("P0300", new FaultDefinition("P0300", "Random/multiple cylinder misfire detected",
                    ReadinessMonitor.MISFIRE, true));
        }
        if (scenario == SimulationScenario.OVERHEAT && coolantTemperatureC >= 105) {
            failing.put("P0217", new FaultDefinition("P0217", "Engine coolant over-temperature condition",
                    ReadinessMonitor.COMPREHENSIVE_COMPONENT, false));
        }
        if (scenario.isLowVoltage() && controlModuleVoltage < 11.8) {
            failing.put("P0562", new FaultDefinition("P0562", "System voltage low",
                    ReadinessMonitor.COMPREHENSIVE_COMPONENT, false));
        }

        var iterator = faultMemory.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            FaultMemory memory = entry.getValue();
            if (!failing.containsKey(entry.getKey())) {
                memory.pass(readiness.get(memory.definition.monitor()) == ReadinessStatus.READY);
                if (!memory.visible()) {
                    iterator.remove();
                }
            }
        }
        failing.values().forEach(definition -> faultMemory
                .computeIfAbsent(definition.code(), ignored -> new FaultMemory(definition))
                .fail(observedAt, freezeFrame(observedAt)));
    }

    private FreezeFrame freezeFrame(Instant observedAt) {
        List<String> keys = List.of(ObdPid.ENGINE_SPEED.key(), ObdPid.VEHICLE_SPEED.key(),
                ObdPid.ENGINE_COOLANT_TEMPERATURE.key(), ObdPid.CALCULATED_ENGINE_LOAD.key(),
                ObdPid.THROTTLE_POSITION.key(), ObdPid.CONTROL_MODULE_VOLTAGE.key());
        return new FreezeFrame(observedAt, lastLiveData.stream().filter(value -> keys.contains(value.key())).toList());
    }

    private CapabilityStatus capabilityStatus(ObdPid pid) {
        if (profile.supportedPids().contains(pid)) return CapabilityStatus.SUPPORTED;
        if (profile.unsupportedPids().contains(pid)) return CapabilityStatus.UNSUPPORTED;
        return CapabilityStatus.UNKNOWN;
    }

    private VehicleState vehicleState(boolean shifting, Integer fromGear, Integer toGear, double load) {
        return new VehicleState(round(speedMetersPerSecond * 3.6), rpm, gear, shifting, fromGear, toGear,
                round(load), round(coolantTemperatureC), round(intakeAirTemperatureC),
                round(controlModuleVoltage), roundTwo(fuelLevelPercent));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record FaultDefinition(String code, String description, ReadinessMonitor monitor,
                                   boolean permanentApplicable) {}

    private static final class FaultMemory {
        private final FaultDefinition definition;
        private final EnumSet<DtcStatus> statuses = EnumSet.noneOf(DtcStatus.class);
        private int consecutiveFailures;
        private int consecutivePasses;
        private boolean active;
        private Instant firstDetectedAt;
        private Instant lastDetectedAt;
        private FreezeFrame freezeFrame;

        private FaultMemory(FaultDefinition definition) {
            this.definition = definition;
        }

        private void fail(Instant detectedAt, FreezeFrame currentFrame) {
            active = true;
            consecutiveFailures++;
            consecutivePasses = 0;
            if (firstDetectedAt == null) firstDetectedAt = detectedAt;
            lastDetectedAt = detectedAt;
            if (consecutiveFailures >= 2) {
                statuses.add(DtcStatus.PENDING);
                if (freezeFrame == null) freezeFrame = currentFrame;
            }
            if (consecutiveFailures >= 4) statuses.add(DtcStatus.CONFIRMED);
            if (consecutiveFailures >= 6 && definition.permanentApplicable()) statuses.add(DtcStatus.PERMANENT);
        }

        private void pass(boolean monitorCompleted) {
            active = false;
            consecutiveFailures = 0;
            consecutivePasses++;
            if (consecutivePasses >= 2) statuses.remove(DtcStatus.PENDING);
            if (consecutivePasses >= 3) statuses.remove(DtcStatus.CONFIRMED);
            if (consecutivePasses >= 6 && monitorCompleted) statuses.remove(DtcStatus.PERMANENT);
        }

        private boolean visible() {
            return !statuses.isEmpty();
        }

        private SimulatedDtc snapshot() {
            return new SimulatedDtc(definition.code(), definition.description(),
                    statuses.stream().sorted().toList(), active, firstDetectedAt, lastDetectedAt, freezeFrame);
        }
    }
}
