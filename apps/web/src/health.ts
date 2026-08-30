import type { Alert, Diagnostic } from './types'

export function calculateHealth(alerts: Alert[], diagnostics: Diagnostic[]): number {
  const alertPenalty = alerts.reduce(
    (total, alert) => total + (alert.severity === 'CRITICAL' ? 22 : alert.severity === 'WARNING' ? 10 : 4),
    0,
  )
  return Math.max(18, 100 - alertPenalty - diagnostics.length * 6)
}
