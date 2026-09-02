import type {
  Alert,
  DashboardData,
  FipeOption,
  FipeSelection,
  FipeVehicleType,
  SimulationScenario,
  SimulationProfile,
  SimulationStatus,
  SimulatedObdSnapshot,
  TelemetryBatchResponse,
  TelemetryInput,
  Trip,
  Vehicle,
  VehicleNote,
  VehicleNoteInput,
  VehiclePhoto,
  VehicleProfile,
  VehicleProfileKey,
  VehicleStatistics,
} from '../../shared-types/src'

export class EnharaApiClient {
  constructor(private readonly baseUrl = '', private readonly fetcher?: typeof fetch) {}

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const multipart = typeof FormData !== 'undefined' && init?.body instanceof FormData
    const response = await (this.fetcher ?? fetch)(`${this.baseUrl}${path}`, {
      ...init,
      headers: multipart ? init?.headers : { 'Content-Type': 'application/json', ...init?.headers },
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null
      throw new Error(error?.message ?? `A API respondeu com status ${response.status}`)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }

  listVehicles = () => this.request<Vehicle[]>('/api/vehicles')
  dashboard = (vehicleId: string) => this.request<DashboardData>(`/api/vehicles/${vehicleId}/dashboard?historyLimit=60`)
  setSimulation = (vehicleId: string, running: boolean) =>
    this.request<SimulationStatus>(`/api/vehicles/${vehicleId}/simulation/${running ? 'start' : 'stop'}`, { method: 'POST' })
  setScenario = (vehicleId: string, scenario: SimulationScenario) =>
    this.request<SimulationStatus>(`/api/vehicles/${vehicleId}/simulation/scenario/${scenario}`, { method: 'POST' })
  setSimulationProfile = (vehicleId: string, profile: SimulationProfile) =>
    this.request<SimulationStatus>(`/api/vehicles/${vehicleId}/simulation/profile/${profile}`, { method: 'POST' })
  simulatedObdState = (vehicleId: string) =>
    this.request<SimulatedObdSnapshot>(`/api/vehicles/${vehicleId}/simulation/obd`)
  tick = (vehicleId: string) => this.request(`/api/vehicles/${vehicleId}/simulation/tick`, { method: 'POST' })
  acknowledge = (_vehicleId: string, alertId: string) =>
    this.request<Alert>(`/api/alerts/${alertId}/acknowledge`, { method: 'PATCH' })
  ingestBatch = (vehicleId: string, samples: TelemetryInput[], obdSnapshot?: SimulatedObdSnapshot | null) =>
    this.request<TelemetryBatchResponse>('/api/telemetry/batches', {
      method: 'POST', body: JSON.stringify({ vehicleId, samples, ...(obdSnapshot ? { obdSnapshot } : {}) }),
    })
  listTrips = (vehicleId: string, limit = 10) =>
    this.request<Trip[]>(`/api/vehicles/${vehicleId}/trips?limit=${limit}`)
  statistics = (vehicleId: string) =>
    this.request<VehicleStatistics>(`/api/vehicles/${vehicleId}/statistics`)
  listNotes = (vehicleId: string, includeCompleted = false) =>
    this.request<VehicleNote[]>(`/api/vehicles/${vehicleId}/notes?includeCompleted=${includeCompleted}`)
  createNote = (vehicleId: string, note: VehicleNoteInput) =>
    this.request<VehicleNote>(`/api/vehicles/${vehicleId}/notes`, {
      method: 'POST', body: JSON.stringify(note),
    })
  updateNote = (vehicleId: string, noteId: string, note: VehicleNoteInput) =>
    this.request<VehicleNote>(`/api/vehicles/${vehicleId}/notes/${noteId}`, {
      method: 'PUT', body: JSON.stringify(note),
    })
  completeNote = (vehicleId: string, noteId: string) =>
    this.request<VehicleNote>(`/api/vehicles/${vehicleId}/notes/${noteId}/complete`, { method: 'POST' })
  reopenNote = (vehicleId: string, noteId: string) =>
    this.request<VehicleNote>(`/api/vehicles/${vehicleId}/notes/${noteId}/reopen`, { method: 'POST' })
  deleteNote = (vehicleId: string, noteId: string) =>
    this.request<void>(`/api/vehicles/${vehicleId}/notes/${noteId}`, { method: 'DELETE' })
  vehicleProfile = (vehicleId: string) =>
    this.request<VehicleProfile>(`/api/vehicles/${vehicleId}/profile`)
  updateVehicleProfile = (vehicleId: string, fields: Partial<Record<VehicleProfileKey, string>>) =>
    this.request<VehicleProfile>(`/api/vehicles/${vehicleId}/profile/manual`, {
      method: 'PUT', body: JSON.stringify({ fields }),
    })
  confirmVehicleProfile = (vehicleId: string, fields: VehicleProfileKey[]) =>
    this.request<VehicleProfile>(`/api/vehicles/${vehicleId}/profile/confirm`, {
      method: 'POST', body: JSON.stringify({ fields }),
    })
  enrichVehicleProfile = (vehicleId: string, fipeCode?: string, fipeSelection?: FipeSelection,
                          forceRefresh = false) =>
    this.request<VehicleProfile>(`/api/vehicles/${vehicleId}/profile/enrich`, {
      method: 'POST', body: JSON.stringify({ fipeCode: fipeCode || null, fipeSelection: fipeSelection ?? null,
        forceRefresh }),
    })
  listFipeBrands = (vehicleType: FipeVehicleType) =>
    this.request<FipeOption[]>(`/api/vehicle-data/fipe/brands?vehicleType=${vehicleType}`)
  listFipeModels = (vehicleType: FipeVehicleType, brandCode: string) =>
    this.request<FipeOption[]>(`/api/vehicle-data/fipe/brands/${brandCode}/models?vehicleType=${vehicleType}`)
  listFipeYears = (vehicleType: FipeVehicleType, brandCode: string, modelCode: string) =>
    this.request<FipeOption[]>(`/api/vehicle-data/fipe/brands/${brandCode}/models/${modelCode}/years?vehicleType=${vehicleType}`)
  listVehiclePhotos = (vehicleId: string) =>
    this.request<VehiclePhoto[]>(`/api/vehicles/${vehicleId}/photos`)
  uploadVehiclePhoto = (vehicleId: string, file: File, caption?: string) => {
    const form = new FormData()
    form.append('file', file)
    if (caption?.trim()) form.append('caption', caption.trim())
    return this.request<VehiclePhoto>(`/api/vehicles/${vehicleId}/photos`, { method: 'POST', body: form })
  }
  deleteVehiclePhoto = (vehicleId: string, photoId: string) =>
    this.request<void>(`/api/vehicles/${vehicleId}/photos/${photoId}`, { method: 'DELETE' })
  vehiclePhotoUrl = (photo: VehiclePhoto) => `${this.baseUrl}${photo.contentPath}`
  startTrip = (vehicleId: string) =>
    this.request<Trip>(`/api/vehicles/${vehicleId}/trips/start`, { method: 'POST' })
  finishTrip = (vehicleId: string) =>
    this.request<Trip>(`/api/vehicles/${vehicleId}/trips/finish`, { method: 'POST' })
  streamUrl = (vehicleId: string) => `${this.baseUrl}/api/vehicles/${vehicleId}/events`
}
