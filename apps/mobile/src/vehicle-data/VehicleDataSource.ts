import type { SimulatedObdSnapshot, SimulationProfile, TelemetryInput, VehicleScenario } from '../types';

export type ReadingListener = (reading: TelemetryInput) => void;

export interface VehicleSimulationControls {
  setScenario(scenario: VehicleScenario): void;
  setProfile(profile: SimulationProfile): void;
}

export interface VehicleDataSource {
  readonly name: string;
  readonly simulation?: VehicleSimulationControls;
  start(listener: ReadingListener): void;
  stop(): void;
  obdSnapshot(): SimulatedObdSnapshot | null;
  isRunning(): boolean;
}
