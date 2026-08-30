import { Platform } from 'react-native';
import type { Alert, DashboardData, TelemetryBatchResponse, TelemetryInput, Vehicle } from './types';

const developmentHost = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
export const API_URL = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, '') || developmentHost;

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.message || `Erro ${response.status} ao acessar a API`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  vehicles: () => request<Vehicle[]>('/api/vehicles'),
  dashboard: (vehicleId: string) => request<DashboardData>(`/api/vehicles/${vehicleId}/dashboard?historyLimit=20`),
  ingestBatch: (vehicleId: string, samples: TelemetryInput[]) =>
    request<TelemetryBatchResponse>('/api/telemetry/batches', {
      method: 'POST',
      body: JSON.stringify({ vehicleId, samples }),
    }),
  acknowledge: (_vehicleId: string, alertId: string) =>
    request<Alert>(`/api/alerts/${alertId}/acknowledge`, { method: 'PATCH' }),
};
