import type { VehicleHealthStatus } from './types'

export function healthTone(status: VehicleHealthStatus): 'good' | 'attention' | 'critical' {
  return status === 'GOOD' ? 'good' : status === 'ATTENTION' ? 'attention' : 'critical'
}
