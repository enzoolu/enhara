export type Severity = 'INFO' | 'WARNING' | 'CRITICAL'
export type TelemetrySource = 'SIMULATED_OBD' | 'SIMULATOR' | 'MOBILE' | 'API'
export type VehicleScenario = 'NORMAL' | 'OVERHEAT' | 'LOW_VOLTAGE' | 'MISFIRE' | 'LOW_BATTERY'
export type SimulationScenario = VehicleScenario
export type SimulationProfile = 'COMPACT_GASOLINE' | 'COMPACT_GASOLINE_LIMITED'

export interface Vehicle {
  id: string
  name: string
  vin: string | null
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

export interface TelemetryInput {
  recordedAt?: string
  speedKph: number
  rpm: number
  engineTempC: number
  engineLoadPercent: number
  throttlePositionPercent: number
  batteryVoltage: number
  fuelLevelPercent: number
  latitude?: number | null
  longitude?: number | null
  source?: TelemetrySource
}

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
  vehicleDataConnected: boolean
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

export type ConsumptionAvailability = 'AVAILABLE' | 'INSUFFICIENT_DATA'

export interface VehicleStatistics {
  distanceTrackedKm: number
  maxRecordedSpeedKph: number | null
  averageConsumptionKmPerLiter: number | null
  consumptionAvailability: ConsumptionAvailability
  completedTrips: number
}

export type VehicleNoteCategory = 'MAINTENANCE' | 'DOCUMENTATION' | 'GENERAL'
export type VehicleNoteStatus = 'OPEN' | 'COMPLETED'

export interface VehicleNoteInput {
  title: string
  description: string
  category: VehicleNoteCategory
  dueAt: string | null
}

export interface VehicleNote extends VehicleNoteInput {
  id: string
  vehicleId: string
  status: VehicleNoteStatus
  overdue: boolean
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type VehicleProfileKey =
  | 'VIN'
  | 'MANUFACTURER'
  | 'MODEL'
  | 'MODEL_YEAR'
  | 'VERSION'
  | 'ENGINE'
  | 'FUEL_TYPE'
  | 'TRANSMISSION'
  | 'FIPE_CODE'
  | 'FIPE_VALUE'
  | 'FIPE_REFERENCE_MONTH'

export type VehicleDataSource =
  | 'VEHICLE_REGISTRATION'
  | 'ECU_OBD'
  | 'BRASILAPI_FIPE'
  | 'NHTSA_VPIC'
  | 'USER_PROVIDED'

export type VehicleProviderState = 'LIVE' | 'CACHE_FRESH' | 'CACHE_STALE' | 'UNAVAILABLE' | 'CONFLICT' | 'NOT_REQUESTED'

export type FipeVehicleType = 'CAR' | 'MOTORCYCLE' | 'TRUCK'

export interface FipeOption {
  code: string
  label: string
}

export interface FipeSelection {
  vehicleType: FipeVehicleType
  brandCode: string
  modelCode: string
  yearCode: string
}

export interface VehicleProfileEnrichmentInput {
  fipeCode: string | null
  fipeSelection: FipeSelection | null
  forceRefresh: boolean
}

export interface VehicleFieldProvenance {
  source: VehicleDataSource
  provider: string | null
  sourceUrl: string | null
  observedAt: string | null
  retrievedAt: string
  cacheExpiresAt: string | null
  cached: boolean
  stale: boolean
  confirmedAt: string | null
}

export interface VehicleProfileField {
  key: VehicleProfileKey
  value: string
  provenance: VehicleFieldProvenance
}

export interface VehicleProviderStatus {
  provider: string
  state: VehicleProviderState
  message: string
  checkedAt: string
  dataFetchedAt: string | null
}

export interface VehicleProfile {
  vehicleId: string
  fields: VehicleProfileField[]
  providers: VehicleProviderStatus[]
  updatedAt: string
}

export interface VehiclePhoto {
  id: string
  vehicleId: string
  originalFilename: string
  mediaType: 'image/jpeg' | 'image/png'
  sizeBytes: number
  widthPixels: number
  heightPixels: number
  caption: string | null
  createdAt: string
  source: 'USER_PROVIDED'
  contentPath: string
}

export interface SimulationStatus {
  vehicleId: string
  running: boolean
  scenario: SimulationScenario
  generatedSamples: number
  profile: SimulationProfile
}

export type ObdCapabilityStatus = 'SUPPORTED' | 'UNSUPPORTED' | 'UNKNOWN'
export type ObdAvailabilityStatus = ObdCapabilityStatus | 'SUPPORTED_NO_DATA' | 'STALE'
export type ObdDtcStatus = 'PENDING' | 'CONFIRMED' | 'PERMANENT'
export type ObdReadinessStatus = 'READY' | 'NOT_READY' | 'NOT_SUPPORTED'

export interface ObdCapability {
  key: string
  service: string
  pid: string
  unit: string | null
  status: ObdCapabilityStatus
  availability: ObdAvailabilityStatus
}

export interface ObdLivePidValue {
  key: string
  service: string
  pid: string
  value: number
  unit: string
  availability: ObdAvailabilityStatus
  observedAt: string
}

export interface ObdDriverInput {
  throttlePercent: number
  brakePercent: number
}

export interface SimulatedVehicleState {
  speedKph: number
  rpm: number
  gear: number
  shifting: boolean
  shiftedFromGear: number | null
  shiftedToGear: number | null
  engineLoadPercent: number
  coolantTemperatureC: number
  intakeAirTemperatureC: number
  controlModuleVoltage: number
  fuelLevelPercent: number
}

export interface ObdFreezeFrame {
  capturedAt: string
  values: ObdLivePidValue[]
}

export interface SimulatedObdDtc {
  code: string
  description: string
  statuses: ObdDtcStatus[]
  active: boolean
  firstDetectedAt: string
  lastDetectedAt: string
  freezeFrame: ObdFreezeFrame | null
}

export interface ObdReadiness {
  monitor: 'MISFIRE' | 'FUEL_SYSTEM' | 'COMPREHENSIVE_COMPONENT' | 'CATALYST' | 'OXYGEN_SENSOR'
  status: ObdReadinessStatus
}

export interface ObdSnapshot {
  capabilities: ObdCapability[]
  liveData: ObdLivePidValue[]
  dtcs: SimulatedObdDtc[]
  milOn: boolean
  readiness: ObdReadiness[]
  vehicleInformation: { vin: string; source: 'SIMULATED_OBD' } | null
}

export interface SimulatedObdSnapshot extends ObdSnapshot {
  profile: SimulationProfile
  scenario: SimulationScenario
  driverInput: ObdDriverInput
  vehicleState: SimulatedVehicleState
  elapsedSeconds: number
}

export interface TelemetryBatchResponse {
  vehicleId: string
  acceptedSamples: number
  results: IngestionResult[]
}

export interface IngestionResult {
  telemetry: Telemetry
  diagnostics: Diagnostic[]
  newAlerts: Alert[]
}
