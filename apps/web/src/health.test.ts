import { describe, expect, it } from 'vitest'
import { calculateHealth } from './health'
import type { Alert, Diagnostic } from './types'

const diagnostic = { severity: 'WARNING' } as Diagnostic

describe('calculateHealth', () => {
  it('retorna 100 sem alertas ou diagnósticos', () => {
    expect(calculateHealth([], [])).toBe(100)
  })

  it('aplica maior penalidade para alerta crítico e respeita o piso', () => {
    const critical = { severity: 'CRITICAL' } as Alert
    expect(calculateHealth([critical], [diagnostic])).toBe(72)
    expect(calculateHealth(Array(10).fill(critical), Array(10).fill(diagnostic))).toBe(18)
  })
})
