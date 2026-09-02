import { describe, expect, it } from 'vitest'
import type { SimulatedObdSnapshot } from './types'
import { effectiveAvailability, parameterHistory, supportedMetricKeys } from './obd'

const snapshot = {
  capabilities: [
    { key: 'VEHICLE_SPEED', service: '01', pid: '0D', unit: 'km/h', status: 'SUPPORTED', availability: 'SUPPORTED' },
    { key: 'ENGINE_OIL_TEMPERATURE', service: '01', pid: '5C', unit: '°C', status: 'UNSUPPORTED', availability: 'UNSUPPORTED' },
    { key: 'VEHICLE_IDENTIFICATION_NUMBER', service: '09', pid: '02', unit: null, status: 'SUPPORTED', availability: 'SUPPORTED' },
  ],
  liveData: [],
  vehicleInformation: null,
} as unknown as SimulatedObdSnapshot

describe('capabilities OBD na interface', () => {
  it('não cria um valor quando o PID suportado ainda não tem leitura', () => {
    expect(effectiveAvailability(snapshot, snapshot.capabilities[0])).toBe('SUPPORTED_NO_DATA')
  })

  it('permite cards apenas para parâmetros numéricos suportados', () => {
    expect(supportedMetricKeys(snapshot)).toEqual(['VEHICLE_SPEED'])
  })

  it('mantém o último valor e o classifica como stale após a janela atual', () => {
    const withReading = {
      ...snapshot,
      liveData: [{ key: 'VEHICLE_SPEED', service: '01', pid: '0D', unit: 'km/h', value: 42,
        availability: 'SUPPORTED', observedAt: '2026-08-31T12:00:00Z' }],
    } as SimulatedObdSnapshot

    expect(effectiveAvailability(withReading, withReading.capabilities[0], Date.parse('2026-08-31T12:00:06Z')))
      .toBe('STALE')
  })

  it('expõe histórico somente para parâmetros realmente persistidos', () => {
    const history = [{ speedKph: 42, recordedAt: '2026-08-31T12:00:00Z' }] as never[]
    expect(parameterHistory(history, 'VEHICLE_SPEED')).toEqual([{ value: 42, observedAt: '2026-08-31T12:00:00Z' }])
    expect(parameterHistory(history, 'MAF_AIR_FLOW_RATE')).toEqual([])
  })
})
