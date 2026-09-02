import type { SimulatedObdSnapshot } from '../types';
import type { ReadingListener, VehicleDataSource } from './VehicleDataSource';

/** Limite explícito do MVP: integração Bluetooth/ELM327 depende de hardware e permissão nativa. */
export class BluetoothVehicleDataSource implements VehicleDataSource {
  readonly name = 'Adaptador OBD-II Bluetooth';

  start(_listener: ReadingListener): void {
    throw new Error('Bluetooth OBD-II ainda não está disponível neste MVP; use a ECU simulada.');
  }

  stop(): void {}
  obdSnapshot(): SimulatedObdSnapshot | null { return null; }
  isRunning(): boolean { return false; }
}
