export type Severity = 'INFO' | 'WARNING' | 'CRITICAL'
export type TelemetrySource = 'SIMULATOR' | 'MOBILE' | 'API'
export type VehicleScenario = 'NORMAL' | 'OVERHEAT' | 'LOW_BATTERY'
export type SimulationScenario = VehicleScenario

export interface Vehicle {
  id: string
  name: string
  vin: string
  manufacturer: string
  model: string
  modelYear: number
  licensePlate: string
  odometerKm: number
  createdAt: string
}

export interface Telemetry {
  id: number
  vehicleId: string
  recordedAt: string
  speedKph: number
  rpm: number
  engineTempC: number
  engineLoadPercent: number
  throttlePositionPercent: number
  batteryVoltage: number
  fuelLevelPercent: number
  latitude: number | null
  longitude: number | null
  source: TelemetrySource
}

export type TelemetryInput = Omit<Telemetry, 'id' | 'vehicleId'>

export interface Diagnostic {
  id: string
  vehicleId: string
  telemetryId: number | null
  code: string
  description: string
  severity: Severity
  status: 'ACTIVE' | 'RESOLVED'
  detectedAt: string
  resolvedAt: string | null
}

export interface Alert {
  id: string
  vehicleId: string
  telemetryId: number | null
  type: 'ENGINE_OVERHEAT' | 'LOW_BATTERY' | 'LOW_FUEL' | 'ENGINE_OVERSPEED'
  severity: Severity
  title: string
  message: string
  status: 'OPEN' | 'ACKNOWLEDGED'
  createdAt: string
  acknowledgedAt: string | null
}

export interface DashboardData {
  vehicle: Vehicle
  latestTelemetry: Telemetry | null
  telemetryHistory: Telemetry[]
  activeDiagnostics: Diagnostic[]
  openAlerts: Alert[]
  simulationRunning: boolean
  simulationScenario: SimulationScenario
  health: VehicleHealth
  activeTrip: Trip | null
  recentTrips: Trip[]
}

export type VehicleHealthStatus = 'GOOD' | 'ATTENTION' | 'CRITICAL'

export interface VehicleHealth {
  score: number
  status: VehicleHealthStatus
  label: string
  explanation: string
  observations: string[]
  recommendation: string
}

export interface Trip {
  id: string
  vehicleId: string
  startedAt: string
  endedAt: string | null
  distanceKm: number
  averageSpeedKph: number
  maxSpeedKph: number
  harshAccelerationCount: number
  harshBrakingCount: number
  highRpmSeconds: number
  drivingScore: number
  experimentalMetrics: true
}

export interface SimulationStatus {
  vehicleId: string
  running: boolean
  scenario: SimulationScenario
  generatedSamples: number
}

export interface TelemetryBatchResponse {
  vehicleId: string
  acceptedSamples: number
}
