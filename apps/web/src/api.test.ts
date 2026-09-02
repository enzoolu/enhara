import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

describe('api client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('usa o endpoint SSE canônico', () => {
    expect(api.streamUrl('vehicle-1')).toContain('/api/vehicles/vehicle-1/events')
  })

  it('envia a seleção de cenário por POST', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ scenario: 'OVERHEAT' }) })
    vi.stubGlobal('fetch', fetchMock)
    await api.setScenario('vehicle-1', 'OVERHEAT')
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/simulation/scenario/OVERHEAT'), expect.objectContaining({ method: 'POST' }))
  })

  it('consulta estatísticas derivadas do veículo', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve({ distanceTrackedKm: 0 }) })
    vi.stubGlobal('fetch', fetchMock)
    await api.statistics('vehicle-1')
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/vehicles/vehicle-1/statistics'), expect.any(Object))
  })

  it('aceita o 204 retornado ao excluir nota', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 })
    vi.stubGlobal('fetch', fetchMock)
    await expect(api.deleteNote('vehicle-1', 'note-1')).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/notes/note-1'), expect.objectContaining({ method: 'DELETE' }))
  })

  it('envia a identificação FIPE guiada sem misturar código avulso', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve({ fields: [] }) })
    vi.stubGlobal('fetch', fetchMock)
    await api.enrichVehicleProfile('vehicle-1', '', {
      vehicleType: 'CAR', brandCode: '21', modelCode: '4828', yearCode: '1998-1',
    }, false)

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(init.body as string)).toEqual({
      fipeCode: null,
      fipeSelection: { vehicleType: 'CAR', brandCode: '21', modelCode: '4828', yearCode: '1998-1' },
      forceRefresh: false,
    })
  })

  it('consulta o catálogo FIPE somente quando solicitado', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve([]) })
    vi.stubGlobal('fetch', fetchMock)
    await api.listFipeYears('CAR', '21', '4828')
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(
      '/api/vehicle-data/fipe/brands/21/models/4828/years?vehicleType=CAR'), expect.any(Object))
  })
})
