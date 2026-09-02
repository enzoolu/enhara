import type { SimulatedObdSnapshot, SimulationProfile, VehicleScenario } from '../types';
import type { ReadingListener, VehicleDataSource } from './VehicleDataSource';
import { StatefulObdSimulator } from './StatefulObdSimulator';

export class MockVehicleDataSource implements VehicleDataSource {
  readonly name = 'ECU/OBD simulada stateful';
  readonly simulation = {
    setScenario: (scenario: VehicleScenario) => this.simulator.setScenario(scenario),
    setProfile: (profile: SimulationProfile) => this.simulator.setProfile(profile),
  };
  private interval: ReturnType<typeof setInterval> | null = null;
  private readonly simulator = new StatefulObdSimulator();

  start(listener: ReadingListener): void {
    if (this.interval) return;
    const emit = () => listener(this.simulator.tick().telemetry);
    emit();
    this.interval = setInterval(emit, 1_000);
  }

  stop(): void {
    if (this.interval) clearInterval(this.interval);
    this.interval = null;
  }

  obdSnapshot(): SimulatedObdSnapshot {
    return this.simulator.snapshot();
  }

  isRunning(): boolean {
    return this.interval !== null;
  }
}
