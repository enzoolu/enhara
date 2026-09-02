import { useState } from 'react'
import type { ObdCapability, SimulatedObdSnapshot, Telemetry } from './types'
import { availabilityLabel, effectiveAvailability, liveValue, parameterHistory, parameterMeta } from './obd'

interface CapabilityPanelProps {
  snapshot: SimulatedObdSnapshot
  current: boolean
  origin: string
  telemetryHistory: Telemetry[]
}

export function CapabilityPanel({ snapshot, current, origin, telemetryHistory }: CapabilityPanelProps) {
  const [selected, setSelected] = useState<ObdCapability | null>(null)

  return (
    <article className="panel capability-panel">
      <div className="section-heading">
        <div><span className="eyebrow">CAPABILITY-AWARE</span><h3>Parâmetros disponíveis</h3></div>
        <span className="source-badge">ECU simulada · {snapshot.profile === 'COMPACT_GASOLINE_LIMITED' ? 'perfil limitado' : 'perfil completo'}</span>
      </div>
      <p className="section-intro">Cada estado vem da descoberta de capabilities do perfil. Itens indisponíveis não abrem detalhes nem alimentam cards.</p>
      <div className="capability-legend" aria-label="Legenda de disponibilidade">
        <span className="supported">Disponível</span><span className="no-data">Sem leitura</span><span className="stale">Dado antigo</span><span className="unknown">Não descoberto</span><span className="unsupported">Não suportado</span>
      </div>
      <div className="capability-grid">{snapshot.capabilities.map((capability) => {
        const availability = effectiveAvailability(snapshot, capability)
        const clickable = availability === 'SUPPORTED' || availability === 'SUPPORTED_NO_DATA' || availability === 'STALE'
        return (
          <button key={capability.key} className={`capability-chip ${availability.toLowerCase().replaceAll('_', '-')}`}
                  onClick={() => clickable && setSelected(capability)} disabled={!clickable}>
            <span>{parameterMeta(capability.key).shortLabel}</span>
            <small>{availabilityLabel(availability)}</small>
          </button>
        )
      })}</div>
      {selected && <ParameterDetail snapshot={snapshot} capability={selected} current={current} origin={origin}
        telemetryHistory={telemetryHistory} onClose={() => setSelected(null)} />}
    </article>
  )
}

function ParameterDetail({ snapshot, capability, current, origin, telemetryHistory, onClose }: {
  snapshot: SimulatedObdSnapshot
  capability: ObdCapability
  current: boolean
  origin: string
  telemetryHistory: Telemetry[]
  onClose: () => void
}) {
  const value = liveValue(snapshot, capability.key)
  const vin = capability.key === 'VEHICLE_IDENTIFICATION_NUMBER' ? snapshot.vehicleInformation?.vin : null
  const availability = effectiveAvailability(snapshot, capability)
  const history = parameterHistory(telemetryHistory, capability.key).slice(-24)
  return (
    <div className="parameter-detail">
      <button className="close-button" onClick={onClose} aria-label="Fechar detalhes">×</button>
      <span className="eyebrow">SERVIÇO {capability.service} · PID {capability.pid}</span>
      <h4>{parameterMeta(capability.key).label}</h4>
      <p>{parameterMeta(capability.key).description}</p>
      {value && <strong>{formatValue(value.value)} <small>{value.unit}</small></strong>}
      {vin && <strong className="vin-value">{vin}</strong>}
      {availability === 'SUPPORTED_NO_DATA' && <div className="no-reading">A ECU declara suporte, mas ainda não há leitura válida nesta sessão.</div>}
      {availability === 'STALE' && <div className="stale-reading">Último valor conhecido; a janela de leitura atual já expirou.</div>}
      {(value || vin) && <small>Origem: {origin} · {current ? 'estado atual da ECU' : 'último valor válido'}{value ? ` · ${formatTime(value.observedAt)}` : ''}</small>}
      {!!history.length && <ParameterHistory points={history} unit={value?.unit ?? capability.unit ?? ''} />}
      {!history.length && capability.key !== 'VEHICLE_IDENTIFICATION_NUMBER' && <div className="parameter-history-empty">Histórico persistido ainda indisponível para este parâmetro.</div>}
    </div>
  )
}

function ParameterHistory({ points, unit }: { points: Array<{ value: number; observedAt: string }>; unit: string }) {
  const width = 420
  const height = 76
  const values = points.map((point) => point.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const coordinates = points.map((point, index) => {
    const x = 4 + index / Math.max(points.length - 1, 1) * (width - 8)
    const y = 5 + (1 - (point.value - min) / Math.max(max - min, 1)) * (height - 10)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
  return <div className="parameter-history">
    <div><strong>Histórico registrado</strong><span>{points.length} leituras · {formatTime(points.at(-1)!.observedAt)}</span></div>
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="Histórico persistido do parâmetro"><polyline points={coordinates} /></svg>
    <div className="parameter-history-range"><span>Mín. {formatValue(min)} {unit}</span><span>Máx. {formatValue(max)} {unit}</span></div>
  </div>
}

function formatValue(value: number) {
  return new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(value)
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value))
}
