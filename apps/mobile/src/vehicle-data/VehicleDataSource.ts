import type { TelemetryInput, VehicleScenario } from '../types';

export type ReadingListener = (reading: TelemetryInput) => void;

export interface VehicleDataSource {
  readonly name: string;
  start(listener: ReadingListener): void;
  stop(): void;
  setScenario(scenario: VehicleScenario): void;
  isRunning(): boolean;
}
