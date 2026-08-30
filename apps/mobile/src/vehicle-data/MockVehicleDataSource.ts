import type { TelemetryInput, VehicleScenario } from '../types';
import type { ReadingListener, VehicleDataSource } from './VehicleDataSource';

const round = (value: number) => Math.round(value * 10) / 10;

export class MockVehicleDataSource implements VehicleDataSource {
  readonly name = 'ECU simulada';
  private interval: ReturnType<typeof setInterval> | null = null;
  private scenario: VehicleScenario = 'NORMAL';
  private sequence = 0;
  private scenarioTicks = 0;

  start(listener: ReadingListener): void {
    if (this.interval) return;
    const emit = () => listener(this.nextReading());
    emit();
    this.interval = setInterval(emit, 1_000);
  }

  stop(): void {
    if (this.interval) clearInterval(this.interval);
    this.interval = null;
  }

  setScenario(scenario: VehicleScenario): void {
    this.scenario = scenario;
    this.scenarioTicks = 0;
  }

  isRunning(): boolean {
    return this.interval !== null;
  }

  private nextReading(): TelemetryInput {
    this.sequence += 1;
    const scenarioTick = this.scenarioTicks++;
    const speedKph = Math.max(0, 52 + Math.sin(this.sequence / 2.2) * 37);
    const engineTempC = this.scenario === 'OVERHEAT'
      ? Math.min(119, 90 + scenarioTick * 3.2)
      : 90 + Math.sin(this.sequence / 3) * 4;
    const batteryVoltage = this.scenario === 'LOW_BATTERY'
      ? Math.max(10.6, 13.8 - scenarioTick * 0.45)
      : 13.8 + Math.sin(this.sequence) * 0.2;

    return {
      recordedAt: new Date().toISOString(),
      speedKph: round(speedKph),
      rpm: Math.round(Math.min(4_700, 850 + speedKph * 40)),
      engineTempC: round(engineTempC),
      engineLoadPercent: round(Math.min(92, 20 + speedKph * 0.62)),
      throttlePositionPercent: round(Math.min(85, 8 + speedKph * 0.55)),
      batteryVoltage: round(batteryVoltage),
      fuelLevelPercent: round(Math.max(35, 72 - this.sequence * 0.08)),
      latitude: -23.55052 + Math.sin(this.sequence / 20) * 0.002,
      longitude: -46.633308 + Math.cos(this.sequence / 20) * 0.002,
      source: 'MOBILE',
    };
  }
}
