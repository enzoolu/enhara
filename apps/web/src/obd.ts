import type { ObdAvailabilityStatus, ObdCapability, ObdLivePidValue, SimulatedObdSnapshot, Telemetry } from './types'

export interface ObdParameterMeta {
  label: string
  shortLabel: string
  description: string
  tone: 'green' | 'blue' | 'amber' | 'purple' | 'cyan'
}

export const PARAMETER_META: Record<string, ObdParameterMeta> = {
  CALCULATED_ENGINE_LOAD: { label: 'Carga calculada do motor', shortLabel: 'Carga do motor', description: 'Carga calculada pela ECU para a condição atual.', tone: 'purple' },
  ENGINE_COOLANT_TEMPERATURE: { label: 'Temperatura do líquido de arrefecimento', shortLabel: 'Arrefecimento', description: 'Temperatura informada pela ECU no circuito de arrefecimento.', tone: 'amber' },
  SHORT_TERM_FUEL_TRIM_BANK_1: { label: 'Ajuste de combustível de curto prazo', shortLabel: 'Fuel trim curto', description: 'Correção instantânea de mistura no banco 1.', tone: 'cyan' },
  LONG_TERM_FUEL_TRIM_BANK_1: { label: 'Ajuste de combustível de longo prazo', shortLabel: 'Fuel trim longo', description: 'Correção adaptativa de mistura no banco 1.', tone: 'cyan' },
  INTAKE_MANIFOLD_ABSOLUTE_PRESSURE: { label: 'Pressão absoluta do coletor', shortLabel: 'Pressão do coletor', description: 'Pressão absoluta medida no coletor de admissão.', tone: 'blue' },
  ENGINE_SPEED: { label: 'Rotação do motor', shortLabel: 'Rotação', description: 'Velocidade de rotação informada pela ECU.', tone: 'blue' },
  VEHICLE_SPEED: { label: 'Velocidade do veículo', shortLabel: 'Velocidade', description: 'Velocidade do veículo reportada pela ECU.', tone: 'green' },
  INTAKE_AIR_TEMPERATURE: { label: 'Temperatura do ar de admissão', shortLabel: 'Ar de admissão', description: 'Temperatura do ar admitido pelo motor.', tone: 'cyan' },
  MAF_AIR_FLOW_RATE: { label: 'Fluxo de massa de ar', shortLabel: 'Fluxo de ar', description: 'Massa de ar admitida por segundo, quando o veículo expõe MAF.', tone: 'cyan' },
  THROTTLE_POSITION: { label: 'Posição do acelerador', shortLabel: 'Acelerador', description: 'Abertura calculada do acelerador reportada pela ECU.', tone: 'green' },
  OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1: { label: 'Tensão da sonda de oxigênio B1S1', shortLabel: 'Sonda de oxigênio', description: 'Sinal da sonda de oxigênio antes do catalisador.', tone: 'purple' },
  FUEL_LEVEL_INPUT: { label: 'Nível de combustível', shortLabel: 'Combustível', description: 'Nível de combustível quando exposto pela ECU.', tone: 'green' },
  BAROMETRIC_PRESSURE: { label: 'Pressão barométrica', shortLabel: 'Pressão ambiente', description: 'Pressão atmosférica utilizada pela ECU.', tone: 'cyan' },
  CONTROL_MODULE_VOLTAGE: { label: 'Tensão do módulo de controle', shortLabel: 'Tensão do módulo', description: 'Tensão elétrica observada pelo módulo de controle.', tone: 'purple' },
  COMMANDED_EQUIVALENCE_RATIO: { label: 'Relação de equivalência comandada', shortLabel: 'Lambda comandada', description: 'Relação ar-combustível comandada pela ECU.', tone: 'amber' },
  ENGINE_OIL_TEMPERATURE: { label: 'Temperatura do óleo do motor', shortLabel: 'Temperatura do óleo', description: 'Temperatura do óleo somente quando o PID é suportado.', tone: 'amber' },
  VEHICLE_IDENTIFICATION_NUMBER: { label: 'VIN informado pela ECU', shortLabel: 'VIN da ECU', description: 'Identificação veicular retornada pelo serviço 09, quando suportado.', tone: 'blue' },
}

export const DEFAULT_CARD_KEYS = [
  'VEHICLE_SPEED',
  'ENGINE_SPEED',
  'ENGINE_COOLANT_TEMPERATURE',
  'CONTROL_MODULE_VOLTAGE',
  'FUEL_LEVEL_INPUT',
]

const LIVE_DATA_STALE_AFTER_MS = 5_000

const TELEMETRY_FIELD_BY_PARAMETER: Partial<Record<string, keyof Pick<Telemetry,
  'speedKph' | 'rpm' | 'engineTempC' | 'engineLoadPercent' | 'throttlePositionPercent' | 'batteryVoltage' | 'fuelLevelPercent'>>> = {
  VEHICLE_SPEED: 'speedKph',
  ENGINE_SPEED: 'rpm',
  ENGINE_COOLANT_TEMPERATURE: 'engineTempC',
  CALCULATED_ENGINE_LOAD: 'engineLoadPercent',
  THROTTLE_POSITION: 'throttlePositionPercent',
  CONTROL_MODULE_VOLTAGE: 'batteryVoltage',
  FUEL_LEVEL_INPUT: 'fuelLevelPercent',
}

export function parameterMeta(key: string): ObdParameterMeta {
  return PARAMETER_META[key] ?? {
    label: key.replaceAll('_', ' ').toLocaleLowerCase('pt-BR'),
    shortLabel: key.replaceAll('_', ' '),
    description: 'Parâmetro informado pela ECU quando disponível.',
    tone: 'blue',
  }
}

export function liveValue(snapshot: SimulatedObdSnapshot, key: string): ObdLivePidValue | undefined {
  return snapshot.liveData.find((item) => item.key === key)
}

export function effectiveAvailability(snapshot: SimulatedObdSnapshot, capability: ObdCapability, now = Date.now()): ObdAvailabilityStatus {
  if (capability.status !== 'SUPPORTED') return capability.status
  if (capability.key === 'VEHICLE_IDENTIFICATION_NUMBER') {
    return snapshot.vehicleInformation ? 'SUPPORTED' : 'SUPPORTED_NO_DATA'
  }
  const value = liveValue(snapshot, capability.key)
  if (!value) return 'SUPPORTED_NO_DATA'
  if (capability.availability === 'STALE' || value.availability === 'STALE'
      || now - Date.parse(value.observedAt) > LIVE_DATA_STALE_AFTER_MS) return 'STALE'
  return 'SUPPORTED'
}

export function supportedMetricKeys(snapshot: SimulatedObdSnapshot): string[] {
  return snapshot.capabilities
    .filter((item) => item.status === 'SUPPORTED' && item.key !== 'VEHICLE_IDENTIFICATION_NUMBER')
    .map((item) => item.key)
}

export interface ParameterHistoryPoint {
  value: number
  observedAt: string
}

export function parameterHistory(history: Telemetry[], key: string): ParameterHistoryPoint[] {
  const field = TELEMETRY_FIELD_BY_PARAMETER[key]
  if (!field) return []
  return history.map((sample) => ({ value: sample[field], observedAt: sample.recordedAt }))
}

export function availabilityLabel(status: ObdAvailabilityStatus): string {
  switch (status) {
    case 'SUPPORTED': return 'Disponível'
    case 'SUPPORTED_NO_DATA': return 'Suportado · sem leitura'
    case 'STALE': return 'Último dado · antigo'
    case 'UNSUPPORTED': return 'Não suportado'
    case 'UNKNOWN': return 'Ainda não descoberto'
  }
}
