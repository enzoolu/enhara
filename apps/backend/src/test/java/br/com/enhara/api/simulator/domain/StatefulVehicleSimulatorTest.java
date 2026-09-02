package br.com.enhara.api.simulator.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId.COMPACT_GASOLINE;
import static br.com.enhara.api.simulator.domain.SimulationVehicleProfile.ProfileId.COMPACT_GASOLINE_LIMITED;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.CapabilityStatus.SUPPORTED;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.CapabilityStatus.UNKNOWN;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.CapabilityStatus.UNSUPPORTED;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.AvailabilityStatus.STALE;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.AvailabilityStatus.SUPPORTED_NO_DATA;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.DtcStatus.CONFIRMED;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.DtcStatus.PENDING;
import static br.com.enhara.api.simulator.domain.StatefulVehicleSimulator.DtcStatus.PERMANENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class StatefulVehicleSimulatorTest {
    private static final Instant START = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void accelerationAndTransmissionProduceVisibleRpmDropWithoutLosingSpeed() {
        var simulator = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        List<StatefulVehicleSimulator.Frame> frames = ticks(simulator, 40);

        int shiftIndex = -1;
        for (int index = 1; index < frames.size(); index++) {
            if (frames.get(index).snapshot().vehicleState().shifting()
                    && frames.get(index).snapshot().vehicleState().shiftedToGear()
                    > frames.get(index).snapshot().vehicleState().shiftedFromGear()) {
                shiftIndex = index;
                break;
            }
        }

        assertThat(shiftIndex).as("uma troca ascendente deve ocorrer").isPositive();
        var before = frames.get(shiftIndex - 1).snapshot().vehicleState();
        var after = frames.get(shiftIndex).snapshot().vehicleState();
        assertThat(after.gear()).isGreaterThan(before.gear());
        assertThat(after.rpm()).isLessThan(before.rpm());
        assertThat(after.speedKph()).isGreaterThanOrEqualTo(before.speedKph());
        assertThat(frames.subList(shiftIndex + 1, Math.min(frames.size(), shiftIndex + 5)))
                .extracting(frame -> frame.snapshot().vehicleState().rpm())
                .anyMatch(value -> value > after.rpm());
    }

    @Test
    void brakingDeceleratesToIdleAndCompletesReadinessWithoutBreakingCausality() {
        var simulator = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        List<StatefulVehicleSimulator.Frame> frames = ticks(simulator, 55);

        int brakingIndex = -1;
        for (int index = 1; index < frames.size(); index++) {
            if (frames.get(index).snapshot().driverInput().brakePercent() > 0) {
                brakingIndex = index;
                break;
            }
        }

        assertThat(brakingIndex).isPositive();
        assertThat(frames.get(brakingIndex).speedKph()).isLessThan(frames.get(brakingIndex - 1).speedKph());
        var stopped = frames.stream().map(StatefulVehicleSimulator.Frame::snapshot)
                .filter(snapshot -> snapshot.vehicleState().speedKph() == 0
                        && snapshot.driverInput().throttlePercent() == 0)
                .findFirst().orElseThrow();
        assertThat(stopped.vehicleState().gear()).isEqualTo(1);
        assertThat(stopped.vehicleState().rpm()).isEqualTo(800);
        assertThat(stopped.vehicleState().engineLoadPercent()).isBetween(8.0, 15.0);
        assertThat(stopped.readiness()).allMatch(item -> item.status() == StatefulVehicleSimulator.ReadinessStatus.READY);
    }

    @Test
    void profilesExposeDifferentSupportedUnsupportedAndUnknownPidSets() {
        var full = new StatefulVehicleSimulator(COMPACT_GASOLINE).snapshot();
        var limited = new StatefulVehicleSimulator(COMPACT_GASOLINE_LIMITED).snapshot();

        assertThat(capability(full, ObdPid.MAF_AIR_FLOW_RATE).status()).isEqualTo(SUPPORTED);
        assertThat(capability(limited, ObdPid.MAF_AIR_FLOW_RATE).status()).isEqualTo(UNSUPPORTED);
        assertThat(capability(full, ObdPid.OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1).status()).isEqualTo(UNKNOWN);
        assertThat(full.capabilities()).extracting(StatefulVehicleSimulator.Capability::status)
                .contains(SUPPORTED, UNSUPPORTED, UNKNOWN);
        assertThat(capability(full, ObdPid.ENGINE_SPEED).availability()).isEqualTo(SUPPORTED_NO_DATA);
        assertThat(capability(full, ObdPid.VEHICLE_IDENTIFICATION_NUMBER).availability())
                .isEqualTo(StatefulVehicleSimulator.AvailabilityStatus.SUPPORTED);

        var limitedAfterTick = new StatefulVehicleSimulator(COMPACT_GASOLINE_LIMITED);
        limitedAfterTick.tick(START);
        assertThat(limitedAfterTick.snapshot().liveData())
                .noneMatch(value -> value.key().equals(ObdPid.MAF_AIR_FLOW_RATE.key()));
    }

    @Test
    void overheatAndLowVoltageModifyPhysicalStateGradually() {
        var overheat = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        assertThat(overheat.snapshot().vehicleState().coolantTemperatureC()).isBetween(24.0, 30.0);
        overheat.setScenario(SimulationScenario.OVERHEAT);
        List<StatefulVehicleSimulator.Frame> hotFrames = ticks(overheat, 36);

        assertThat(hotFrames).extracting(StatefulVehicleSimulator.Frame::engineTempC).isSorted();
        assertThat(hotFrames.getLast().engineTempC()).isGreaterThanOrEqualTo(105);
        assertThat(hotFrames.getLast().snapshot().dtcs())
                .anyMatch(dtc -> dtc.code().equals("P0217") && dtc.statuses().contains(PENDING));

        var lowVoltage = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        lowVoltage.setScenario(SimulationScenario.LOW_VOLTAGE);
        List<StatefulVehicleSimulator.Frame> voltageFrames = ticks(lowVoltage, 12);
        assertThat(voltageFrames).extracting(StatefulVehicleSimulator.Frame::controlModuleVoltage)
                .isSortedAccordingTo((left, right) -> Double.compare(right, left));
        assertThat(voltageFrames.getLast().controlModuleVoltage()).isLessThan(11.8);
        assertThat(voltageFrames.getLast().snapshot().dtcs()).anyMatch(dtc -> dtc.code().equals("P0562"));
    }

    @Test
    void supportedLiveDataBecomesStaleWithoutChangingCapabilityDiscovery() {
        var simulator = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        simulator.tick(START);

        var fresh = simulator.snapshot(START.plusSeconds(5));
        var stale = simulator.snapshot(START.plusSeconds(6));

        assertThat(capability(fresh, ObdPid.ENGINE_SPEED).availability())
                .isEqualTo(StatefulVehicleSimulator.AvailabilityStatus.SUPPORTED);
        assertThat(capability(stale, ObdPid.ENGINE_SPEED).availability()).isEqualTo(STALE);
        assertThat(stale.liveData()).filteredOn(value -> value.key().equals(ObdPid.ENGINE_SPEED.key()))
                .singleElement().extracting(StatefulVehicleSimulator.LivePidValue::availability).isEqualTo(STALE);
        assertThat(capability(stale, ObdPid.ENGINE_OIL_TEMPERATURE).availability())
                .isEqualTo(StatefulVehicleSimulator.AvailabilityStatus.UNSUPPORTED);
        assertThat(capability(stale, ObdPid.OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1).availability())
                .isEqualTo(StatefulVehicleSimulator.AvailabilityStatus.UNKNOWN);
    }

    @Test
    void misfireQualifiesPendingConfirmedPermanentDtcMilAndFreezeFrame() {
        var simulator = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        simulator.setScenario(SimulationScenario.MISFIRE);

        simulator.tick(START);
        assertThat(simulator.snapshot().dtcs()).isEmpty();
        simulator.tick(START.plusSeconds(1));
        assertThat(singleDtc(simulator).statuses()).containsExactly(PENDING);
        assertThat(singleDtc(simulator).freezeFrame()).isNotNull();

        simulator.tick(START.plusSeconds(2));
        simulator.tick(START.plusSeconds(3));
        assertThat(singleDtc(simulator).statuses()).contains(PENDING, CONFIRMED);
        assertThat(simulator.snapshot().milOn()).isTrue();

        simulator.tick(START.plusSeconds(4));
        simulator.tick(START.plusSeconds(5));
        assertThat(singleDtc(simulator).statuses()).contains(PENDING, CONFIRMED, PERMANENT);

        simulator.setScenario(SimulationScenario.NORMAL);
        ticks(simulator, 1);
        assertThat(simulator.snapshot().milOn()).isTrue();
        ticks(simulator, 2);
        assertThat(singleDtc(simulator).statuses()).containsExactly(PERMANENT);
        assertThat(simulator.snapshot().milOn()).isFalse();
        ticks(simulator, 3);
        assertThat(simulator.snapshot().dtcs()).isEmpty();
    }

    @Test
    void clearedDtcRecurrenceStartsNewEcuEventAndCapturesNewFreezeFrame() {
        var simulator = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        simulator.setScenario(SimulationScenario.MISFIRE);
        for (int second = 0; second < 6; second++) {
            simulator.tick(START.plusSeconds(second));
        }
        Instant originalFirstDetection = singleDtc(simulator).firstDetectedAt();
        Instant originalFreezeFrame = singleDtc(simulator).freezeFrame().capturedAt();

        simulator.setScenario(SimulationScenario.NORMAL);
        for (int second = 6; second < 12; second++) {
            simulator.tick(START.plusSeconds(second));
        }
        assertThat(simulator.snapshot().dtcs()).isEmpty();

        simulator.setScenario(SimulationScenario.MISFIRE);
        simulator.tick(START.plusSeconds(12));
        simulator.tick(START.plusSeconds(13));

        assertThat(singleDtc(simulator).firstDetectedAt()).isAfter(originalFirstDetection);
        assertThat(singleDtc(simulator).freezeFrame().capturedAt()).isAfter(originalFreezeFrame);
        assertThat(singleDtc(simulator).statuses()).containsExactly(PENDING);
    }

    @Test
    void livePidValuesAreDerivedFromTheSameEngineState() {
        var simulator = new StatefulVehicleSimulator(COMPACT_GASOLINE);
        var frame = ticks(simulator, 8).getLast();
        var snapshot = frame.snapshot();

        assertThat(liveValue(snapshot, ObdPid.ENGINE_SPEED)).isEqualTo(snapshot.vehicleState().rpm());
        assertThat(liveValue(snapshot, ObdPid.VEHICLE_SPEED)).isCloseTo(snapshot.vehicleState().speedKph(), offset(0.1));
        assertThat(liveValue(snapshot, ObdPid.CALCULATED_ENGINE_LOAD))
                .isCloseTo(snapshot.vehicleState().engineLoadPercent(), offset(0.1));
        assertThat(liveValue(snapshot, ObdPid.MAF_AIR_FLOW_RATE)).isPositive();
        assertThat(liveValue(snapshot, ObdPid.INTAKE_MANIFOLD_ABSOLUTE_PRESSURE)).isBetween(22.0, 101.3);
        assertThat(snapshot.vehicleInformation().source()).isEqualTo("SIMULATED_OBD");
    }

    private List<StatefulVehicleSimulator.Frame> ticks(StatefulVehicleSimulator simulator, int count) {
        List<StatefulVehicleSimulator.Frame> frames = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            frames.add(simulator.tick(START.plusSeconds(index)));
        }
        return frames;
    }

    private StatefulVehicleSimulator.Capability capability(StatefulVehicleSimulator.Snapshot snapshot, ObdPid pid) {
        return snapshot.capabilities().stream().filter(item -> item.key().equals(pid.key())).findFirst().orElseThrow();
    }

    private double liveValue(StatefulVehicleSimulator.Snapshot snapshot, ObdPid pid) {
        return snapshot.liveData().stream().filter(item -> item.key().equals(pid.key()))
                .findFirst().orElseThrow().value();
    }

    private StatefulVehicleSimulator.SimulatedDtc singleDtc(StatefulVehicleSimulator simulator) {
        return simulator.snapshot().dtcs().getFirst();
    }
}
