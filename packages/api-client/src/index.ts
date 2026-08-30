import type {
  Alert,
  DashboardData,
  SimulationScenario,
  SimulationStatus,
  TelemetryBatchResponse,
  TelemetryInput,
  Trip,
  Vehicle,
} from '../../shared-types/src'

export class EnharaApiClient {
  constructor(private readonly baseUrl = '', private readonly fetcher?: typeof fetch) {}

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await (this.fetcher ?? fetch)(`${this.baseUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null
      throw new Error(error?.message ?? `A API respondeu com status ${response.status}`)
    }
    return response.json() as Promise<T>
  }

  listVehicles = () => this.request<Vehicle[]>('/api/vehicles')
  dashboard = (vehicleId: string) => this.request<DashboardData>(`/api/vehicles/${vehicleId}/dashboard?historyLimit=60`)
  setSimulation = (vehicleId: string, running: boolean) =>
    this.request<SimulationStatus>(`/api/vehicles/${vehicleId}/simulation/${running ? 'start' : 'stop'}`, { method: 'POST' })
  setScenario = (vehicleId: string, scenario: SimulationScenario) =>
    this.request<SimulationStatus>(`/api/vehicles/${vehicleId}/simulation/scenario/${scenario}`, { method: 'POST' })
  tick = (vehicleId: string) => this.request(`/api/vehicles/${vehicleId}/simulation/tick`, { method: 'POST' })
  acknowledge = (_vehicleId: string, alertId: string) =>
    this.request<Alert>(`/api/alerts/${alertId}/acknowledge`, { method: 'PATCH' })
  ingestBatch = (vehicleId: string, samples: TelemetryInput[]) =>
    this.request<TelemetryBatchResponse>('/api/telemetry/batches', {
      method: 'POST', body: JSON.stringify({ vehicleId, samples }),
    })
  listTrips = (vehicleId: string, limit = 10) =>
    this.request<Trip[]>(`/api/vehicles/${vehicleId}/trips?limit=${limit}`)
  startTrip = (vehicleId: string) =>
    this.request<Trip>(`/api/vehicles/${vehicleId}/trips/start`, { method: 'POST' })
  finishTrip = (vehicleId: string) =>
    this.request<Trip>(`/api/vehicles/${vehicleId}/trips/finish`, { method: 'POST' })
  streamUrl = (vehicleId: string) => `${this.baseUrl}/api/vehicles/${vehicleId}/events`
}
