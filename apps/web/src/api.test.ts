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
})
