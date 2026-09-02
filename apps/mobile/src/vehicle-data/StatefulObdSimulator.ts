import type {
  ObdCapability,
  ObdDtcStatus,
  ObdLivePidValue,
  ObdReadiness,
  SimulatedObdDtc,
  SimulatedObdSnapshot,
  SimulationProfile,
  TelemetryInput,
  VehicleScenario,
} from '../types';

const PIDS = [
  ['CALCULATED_ENGINE_LOAD', '01', '04', '%'],
  ['ENGINE_COOLANT_TEMPERATURE', '01', '05', '°C'],
  ['SHORT_TERM_FUEL_TRIM_BANK_1', '01', '06', '%'],
  ['LONG_TERM_FUEL_TRIM_BANK_1', '01', '07', '%'],
  ['INTAKE_MANIFOLD_ABSOLUTE_PRESSURE', '01', '0B', 'kPa'],
  ['ENGINE_SPEED', '01', '0C', 'rpm'],
  ['VEHICLE_SPEED', '01', '0D', 'km/h'],
  ['INTAKE_AIR_TEMPERATURE', '01', '0F', '°C'],
  ['MAF_AIR_FLOW_RATE', '01', '10', 'g/s'],
  ['THROTTLE_POSITION', '01', '11', '%'],
  ['OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1', '01', '14', 'V'],
  ['FUEL_LEVEL_INPUT', '01', '2F', '%'],
  ['BAROMETRIC_PRESSURE', '01', '33', 'kPa'],
  ['CONTROL_MODULE_VOLTAGE', '01', '42', 'V'],
  ['COMMANDED_EQUIVALENCE_RATIO', '01', '44', 'lambda'],
  ['ENGINE_OIL_TEMPERATURE', '01', '5C', '°C'],
  ['VEHICLE_IDENTIFICATION_NUMBER', '09', '02', null],
] as const;

type PidKey = typeof PIDS[number][0];

interface Profile {
  id: SimulationProfile;
  massKg: number;
  wheelRadiusM: number;
  finalDriveRatio: number;
  gearRatios: readonly number[];
  idleRpm: number;
  redlineRpm: number;
  peakTorqueNm: number;
  displacementLiters: number;
  tankCapacityLiters: number;
  supported: ReadonlySet<PidKey>;
  unknown: ReadonlySet<PidKey>;
  simulatedVin: string | null;
}

interface FaultMemory {
  code: string;
  description: string;
  monitor: ObdReadiness['monitor'];
  permanentApplicable: boolean;
  statuses: Set<ObdDtcStatus>;
  consecutiveFailures: number;
  consecutivePasses: number;
  active: boolean;
  firstDetectedAt: string | null;
  lastDetectedAt: string | null;
  freezeFrame: SimulatedObdDtc['freezeFrame'];
}

const fullSupported: PidKey[] = [
  'CALCULATED_ENGINE_LOAD', 'ENGINE_COOLANT_TEMPERATURE', 'SHORT_TERM_FUEL_TRIM_BANK_1',
  'LONG_TERM_FUEL_TRIM_BANK_1', 'INTAKE_MANIFOLD_ABSOLUTE_PRESSURE', 'ENGINE_SPEED',
  'VEHICLE_SPEED', 'INTAKE_AIR_TEMPERATURE', 'MAF_AIR_FLOW_RATE', 'THROTTLE_POSITION',
  'FUEL_LEVEL_INPUT', 'BAROMETRIC_PRESSURE', 'CONTROL_MODULE_VOLTAGE',
  'COMMANDED_EQUIVALENCE_RATIO', 'VEHICLE_IDENTIFICATION_NUMBER',
];

const limitedSupported: PidKey[] = [
  'CALCULATED_ENGINE_LOAD', 'ENGINE_COOLANT_TEMPERATURE', 'ENGINE_SPEED', 'VEHICLE_SPEED',
  'INTAKE_AIR_TEMPERATURE', 'THROTTLE_POSITION', 'FUEL_LEVEL_INPUT', 'CONTROL_MODULE_VOLTAGE',
];

const profiles: Record<SimulationProfile, Profile> = {
  COMPACT_GASOLINE: {
    id: 'COMPACT_GASOLINE', massKg: 1350, wheelRadiusM: 0.31, finalDriveRatio: 4.1,
    gearRatios: [3.55, 1.95, 1.3, 0.95, 0.76], idleRpm: 800, redlineRpm: 6500,
    peakTorqueNm: 180, displacementLiters: 1.6, tankCapacityLiters: 50,
    supported: new Set(fullSupported), unknown: new Set(['OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1']),
    simulatedVin: 'ENH4R4S1M00000001',
  },
  COMPACT_GASOLINE_LIMITED: {
    id: 'COMPACT_GASOLINE_LIMITED', massKg: 1180, wheelRadiusM: 0.3, finalDriveRatio: 4.3,
    gearRatios: [3.73, 2.05, 1.32, 0.97, 0.76], idleRpm: 780, redlineRpm: 6300,
    peakTorqueNm: 150, displacementLiters: 1.4, tankCapacityLiters: 45,
    supported: new Set(limitedSupported), unknown: new Set(['VEHICLE_IDENTIFICATION_NUMBER']),
    simulatedVin: null,
  },
};

const round = (value: number) => Math.round(value * 10) / 10;
const roundTwo = (value: number) => Math.round(value * 100) / 100;
const clamp = (value: number, minimum: number, maximum: number) => Math.max(minimum, Math.min(maximum, value));
const LIVE_DATA_STALE_AFTER_MS = 5_000;

/** Espelho mobile do modelo stateful usado pelo fallback backend. */
export class StatefulObdSimulator {
  private profile: Profile;
  private scenario: VehicleScenario = 'NORMAL';
  private elapsedSeconds = 0;
  private speedMps = 0;
  private rpm: number;
  private gear = 1;
  private coolantC = 26;
  private intakeC = 27;
  private voltage = 12.6;
  private fuelPercent = 72;
  private longTermFuelTrim = 0;
  private driverInput = { throttlePercent: 0, brakePercent: 0 };
  private vehicleState: SimulatedObdSnapshot['vehicleState'];
  private liveData: ObdLivePidValue[] = [];
  private faults = new Map<string, FaultMemory>();
  private readiness = new Map<ObdReadiness['monitor'], ObdReadiness['status']>();
  private oxygenMonitorSeconds = 0;
  private catalystMonitorSeconds = 0;
  private lastObservedAt: string | null = null;

  constructor(profileId: SimulationProfile = 'COMPACT_GASOLINE') {
    this.profile = profiles[profileId];
    this.rpm = this.profile.idleRpm;
    this.vehicleState = this.currentVehicleState(false, null, null, 12);
    this.resetReadiness();
  }

  setScenario(scenario: VehicleScenario): void {
    this.scenario = scenario;
  }

  setProfile(profileId: SimulationProfile): void {
    const replacement = new StatefulObdSimulator(profileId);
    Object.assign(this, replacement);
  }

  tick(now = new Date()): { telemetry: TelemetryInput; snapshot: SimulatedObdSnapshot } {
    this.elapsedSeconds += 1;
    const observedAt = now.toISOString();
    this.lastObservedAt = observedAt;
    this.driverInput = this.nextDriverInput();
    const fromGear = this.gear;
    const shifting = this.selectGear();
    const load = this.updateDynamics(shifting);
    this.updateThermal(load);
    this.updateElectrical();
    const mapKpa = this.manifoldPressure();
    const mafGps = this.massAirFlow(mapKpa);
    const lambda = this.scenario === 'MISFIRE' ? 1.07 : 1;
    const shortTermFuelTrim = this.scenario === 'MISFIRE' ? 8 : -this.longTermFuelTrim * 0.35;
    this.longTermFuelTrim += (shortTermFuelTrim - this.longTermFuelTrim) * 0.015;
    this.consumeFuel(mafGps, lambda);
    this.vehicleState = this.currentVehicleState(shifting, shifting ? fromGear : null, shifting ? this.gear : null, load);
    this.liveData = this.buildLiveData(observedAt, load, mapKpa, mafGps, shortTermFuelTrim, lambda);
    this.updateReadiness();
    this.updateFaults(observedAt);

    return {
      telemetry: {
        recordedAt: observedAt,
        speedKph: round(this.speedMps * 3.6),
        rpm: this.rpm,
        engineTempC: round(this.coolantC),
        engineLoadPercent: round(load),
        throttlePositionPercent: round(this.driverInput.throttlePercent),
        batteryVoltage: round(this.voltage),
        fuelLevelPercent: round(this.fuelPercent),
        latitude: null,
        longitude: null,
        source: 'SIMULATED_OBD',
      },
      snapshot: this.snapshot(now),
    };
  }

  snapshot(now = new Date()): SimulatedObdSnapshot {
    const stale = this.lastObservedAt !== null
      && now.getTime() - Date.parse(this.lastObservedAt) > LIVE_DATA_STALE_AFTER_MS;
    const liveData = this.liveData.map((value) => ({
      ...value,
      availability: stale ? 'STALE' as const : 'SUPPORTED' as const,
    }));
    const capabilities: ObdCapability[] = PIDS.map(([key, service, pid, unit]) => {
      const status = this.profile.supported.has(key) ? 'SUPPORTED' : this.profile.unknown.has(key) ? 'UNKNOWN' : 'UNSUPPORTED';
      const value = liveData.find((item) => item.key === key);
      const availability = status !== 'SUPPORTED'
        ? status
        : key === 'VEHICLE_IDENTIFICATION_NUMBER' && this.profile.simulatedVin
          ? 'SUPPORTED'
          : value?.availability ?? 'SUPPORTED_NO_DATA';
      return { key, service, pid, unit, status, availability };
    });
    const dtcs: SimulatedObdDtc[] = [...this.faults.values()]
      .filter((fault) => fault.statuses.size > 0)
      .map((fault) => ({
        code: fault.code,
        description: fault.description,
        statuses: [...fault.statuses].sort(),
        active: fault.active,
        firstDetectedAt: fault.firstDetectedAt!,
        lastDetectedAt: fault.lastDetectedAt!,
        freezeFrame: fault.freezeFrame,
      }));
    return {
      profile: this.profile.id,
      scenario: this.scenario,
      capabilities,
      driverInput: this.driverInput,
      vehicleState: this.vehicleState,
      liveData,
      dtcs,
      milOn: dtcs.some((dtc) => dtc.statuses.includes('CONFIRMED')),
      readiness: [...this.readiness.entries()].map(([monitor, status]) => ({ monitor, status })),
      vehicleInformation: this.profile.simulatedVin ? { vin: this.profile.simulatedVin, source: 'SIMULATED_OBD' } : null,
      elapsedSeconds: this.elapsedSeconds,
    };
  }

  private resetReadiness(): void {
    this.readiness.set('MISFIRE', 'NOT_READY');
    this.readiness.set('FUEL_SYSTEM', 'NOT_READY');
    this.readiness.set('COMPREHENSIVE_COMPONENT', 'NOT_READY');
    this.readiness.set('CATALYST', 'NOT_READY');
    this.readiness.set('OXYGEN_SENSOR', this.profile.supported.has('COMMANDED_EQUIVALENCE_RATIO') ? 'NOT_READY' : 'NOT_SUPPORTED');
  }

  private nextDriverInput(): SimulatedObdSnapshot['driverInput'] {
    const phase = this.elapsedSeconds % 60;
    const speedKph = this.speedMps * 3.6;
    if (phase < 20) return { throttlePercent: clamp(28 + (76 - speedKph) * 0.45, 28, 62), brakePercent: 0 };
    if (phase < 36) {
      const error = 72 - speedKph;
      return error >= 0
        ? { throttlePercent: clamp(12 + error * 0.7, 10, 30), brakePercent: 0 }
        : { throttlePercent: 5, brakePercent: clamp(-error * 1.5, 0, 20) };
    }
    if (phase < 51) return { throttlePercent: 0, brakePercent: speedKph > 3 ? clamp(30 + speedKph * 0.35, 30, 65) : 0 };
    return { throttlePercent: speedKph < 1 ? 0 : 4, brakePercent: speedKph < 1 ? 0 : 24 };
  }

  private selectGear(): boolean {
    if (this.rpm > 3250 && this.driverInput.throttlePercent > 12 && this.gear < this.profile.gearRatios.length) {
      this.gear += 1;
      return true;
    }
    if (this.rpm < 1350 && this.gear > 1 && (this.driverInput.brakePercent > 0 || this.driverInput.throttlePercent > 10)) {
      this.gear -= 1;
      return true;
    }
    return false;
  }

  private updateDynamics(shifting: boolean): number {
    const throttle = this.driverInput.throttlePercent / 100;
    const brake = this.driverInput.brakePercent / 100;
    const torqueCurve = clamp(1 - Math.abs(this.rpm - 3500) / 6000, 0.58, 1);
    const combustionEfficiency = this.scenario === 'MISFIRE' ? 0.68 : 1;
    const ratio = this.profile.gearRatios[this.gear - 1] * this.profile.finalDriveRatio;
    const engineTorque = this.profile.peakTorqueNm * torqueCurve * throttle * combustionEfficiency;
    const clutchTransfer = shifting ? 0.12 : 1;
    const driveForce = engineTorque * ratio * 0.88 / this.profile.wheelRadiusM * clutchTransfer;
    const rolling = this.profile.massKg * 9.81 * 0.012;
    const drag = 0.5 * 1.225 * 0.68 * this.speedMps * this.speedMps;
    const braking = brake * this.profile.massKg * 7.2;
    let acceleration = (driveForce - rolling - drag - braking) / this.profile.massKg;
    if (this.speedMps <= 0.01 && acceleration < 0) acceleration = 0;
    this.speedMps = Math.max(0, this.speedMps + acceleration);
    const wheelRpm = this.speedMps / (2 * Math.PI * this.profile.wheelRadiusM) * 60;
    this.rpm = this.speedMps < 0.4
      ? Math.round(this.profile.idleRpm + throttle * 900)
      : Math.max(this.profile.idleRpm, Math.min(this.profile.redlineRpm, Math.round(wheelRpm * ratio)));
    if (this.scenario === 'MISFIRE' && this.rpm > this.profile.idleRpm) {
      this.rpm = Math.max(this.profile.idleRpm, this.rpm + [-110, 35, -75, 20][this.elapsedSeconds % 4]);
    }
    return clamp(10 + throttle * 72 + Math.max(0, acceleration) / 4 * 18, 8, 100);
  }

  private updateThermal(loadPercent: number): void {
    const load = loadPercent / 100;
    const coolantTarget = this.scenario === 'OVERHEAT' ? 122 : 88 + load * 8;
    const response = this.scenario === 'OVERHEAT' ? 0.055 : this.coolantC < 75 ? 0.05 : 0.035;
    this.coolantC = clamp(this.coolantC + (coolantTarget - this.coolantC) * response, 24, 124);
    const intakeTarget = 31 + loadPercent / 100 * 9 - this.speedMps * 0.16;
    this.intakeC += (intakeTarget - this.intakeC) * 0.1;
  }

  private updateElectrical(): void {
    const lowVoltage = this.scenario === 'LOW_VOLTAGE' || this.scenario === 'LOW_BATTERY';
    const target = lowVoltage ? 10.9 : 13.9;
    this.voltage += (target - this.voltage) * (lowVoltage ? 0.22 : 0.14);
  }

  private manifoldPressure(): number {
    return clamp(101.3 * (0.27 + this.driverInput.throttlePercent / 100 * 0.72), 22, 101.3);
  }

  private massAirFlow(mapKpa: number): number {
    const efficiency = clamp(0.7 + mapKpa / 101.3 * 0.18, 0.7, 0.88);
    return this.profile.displacementLiters * this.rpm / 2 * efficiency * 1.18 / 60 * (mapKpa / 101.3);
  }

  private consumeFuel(mafGps: number, lambda: number): void {
    const liters = mafGps / (14.7 * lambda) / 745;
    this.fuelPercent = Math.max(0, this.fuelPercent - liters / this.profile.tankCapacityLiters * 100);
  }

  private buildLiveData(observedAt: string, load: number, mapKpa: number, mafGps: number, shortTrim: number, lambda: number): ObdLivePidValue[] {
    const values: Partial<Record<PidKey, number>> = {
      CALCULATED_ENGINE_LOAD: load, ENGINE_COOLANT_TEMPERATURE: this.coolantC,
      SHORT_TERM_FUEL_TRIM_BANK_1: shortTrim, LONG_TERM_FUEL_TRIM_BANK_1: this.longTermFuelTrim,
      INTAKE_MANIFOLD_ABSOLUTE_PRESSURE: mapKpa, ENGINE_SPEED: this.rpm,
      VEHICLE_SPEED: this.speedMps * 3.6, INTAKE_AIR_TEMPERATURE: this.intakeC,
      MAF_AIR_FLOW_RATE: mafGps, THROTTLE_POSITION: this.driverInput.throttlePercent,
      FUEL_LEVEL_INPUT: this.fuelPercent, BAROMETRIC_PRESSURE: 101.3,
      CONTROL_MODULE_VOLTAGE: this.voltage, COMMANDED_EQUIVALENCE_RATIO: lambda,
    };
    return PIDS.flatMap(([key, service, pid, unit]) => {
      const value = values[key];
      return this.profile.supported.has(key) && value !== undefined && unit
        ? [{ key, service, pid, value: roundTwo(value), unit, availability: 'SUPPORTED' as const, observedAt }]
        : [];
    });
  }

  private updateReadiness(): void {
    if (this.elapsedSeconds >= 2) {
      this.readiness.set('MISFIRE', 'READY');
      this.readiness.set('FUEL_SYSTEM', 'READY');
      this.readiness.set('COMPREHENSIVE_COMPONENT', 'READY');
    }
    if (this.coolantC >= 75 && this.speedMps * 3.6 >= 15) {
      this.oxygenMonitorSeconds += 1;
      if (this.scenario !== 'MISFIRE') this.catalystMonitorSeconds += 1;
    }
    if (this.oxygenMonitorSeconds >= 3 && this.readiness.get('OXYGEN_SENSOR') !== 'NOT_SUPPORTED') this.readiness.set('OXYGEN_SENSOR', 'READY');
    if (this.catalystMonitorSeconds >= 6) this.readiness.set('CATALYST', 'READY');
  }

  private updateFaults(observedAt: string): void {
    const failing = new Map<string, Pick<FaultMemory, 'code' | 'description' | 'monitor' | 'permanentApplicable'>>();
    if (this.scenario === 'MISFIRE') failing.set('P0300', { code: 'P0300', description: 'Random/multiple cylinder misfire detected', monitor: 'MISFIRE', permanentApplicable: true });
    if (this.scenario === 'OVERHEAT' && this.coolantC >= 105) failing.set('P0217', { code: 'P0217', description: 'Engine coolant over-temperature condition', monitor: 'COMPREHENSIVE_COMPONENT', permanentApplicable: false });
    const lowVoltage = this.scenario === 'LOW_VOLTAGE' || this.scenario === 'LOW_BATTERY';
    if (lowVoltage && this.voltage < 11.8) failing.set('P0562', { code: 'P0562', description: 'System voltage low', monitor: 'COMPREHENSIVE_COMPONENT', permanentApplicable: false });

    for (const fault of [...this.faults.values()]) {
      if (!failing.has(fault.code)) {
        this.passFault(fault);
        if (fault.statuses.size === 0) this.faults.delete(fault.code);
      }
    }
    for (const definition of failing.values()) {
      const fault = this.faults.get(definition.code) ?? {
        ...definition, statuses: new Set<ObdDtcStatus>(), consecutiveFailures: 0, consecutivePasses: 0,
        active: false, firstDetectedAt: null, lastDetectedAt: null, freezeFrame: null,
      };
      this.failFault(fault, observedAt);
      this.faults.set(fault.code, fault);
    }
  }

  private failFault(fault: FaultMemory, observedAt: string): void {
    fault.active = true;
    fault.consecutiveFailures += 1;
    fault.consecutivePasses = 0;
    fault.firstDetectedAt ??= observedAt;
    fault.lastDetectedAt = observedAt;
    if (fault.consecutiveFailures >= 2) {
      fault.statuses.add('PENDING');
      fault.freezeFrame ??= { capturedAt: observedAt, values: this.liveData.filter((value) => [
        'ENGINE_SPEED', 'VEHICLE_SPEED', 'ENGINE_COOLANT_TEMPERATURE', 'CALCULATED_ENGINE_LOAD',
        'THROTTLE_POSITION', 'CONTROL_MODULE_VOLTAGE',
      ].includes(value.key)) };
    }
    if (fault.consecutiveFailures >= 4) fault.statuses.add('CONFIRMED');
    if (fault.consecutiveFailures >= 6 && fault.permanentApplicable) fault.statuses.add('PERMANENT');
  }

  private passFault(fault: FaultMemory): void {
    fault.active = false;
    fault.consecutiveFailures = 0;
    fault.consecutivePasses += 1;
    if (fault.consecutivePasses >= 2) fault.statuses.delete('PENDING');
    if (fault.consecutivePasses >= 3) fault.statuses.delete('CONFIRMED');
    if (fault.consecutivePasses >= 6 && this.readiness.get(fault.monitor) === 'READY') {
      fault.statuses.delete('PERMANENT');
    }
  }

  private currentVehicleState(shifting: boolean, from: number | null, to: number | null, load: number): SimulatedObdSnapshot['vehicleState'] {
    return {
      speedKph: round(this.speedMps * 3.6), rpm: this.rpm, gear: this.gear, shifting,
      shiftedFromGear: from, shiftedToGear: to, engineLoadPercent: round(load),
      coolantTemperatureC: round(this.coolantC), intakeAirTemperatureC: round(this.intakeC),
      controlModuleVoltage: round(this.voltage), fuelLevelPercent: roundTwo(this.fuelPercent),
    };
  }
}
