import { describe, expect, it } from 'vitest'
import { healthTone } from './health'

describe('healthTone', () => {
  it('apresenta estado saudável', () => {
    expect(healthTone('GOOD')).toBe('good')
  })

  it('apresenta estado de atenção', () => {
    expect(healthTone('ATTENTION')).toBe('attention')
  })

  it('apresenta estado crítico', () => {
    expect(healthTone('CRITICAL')).toBe('critical')
  })
})
