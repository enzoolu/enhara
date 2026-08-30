import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import { healthTone } from './health'
import type { Alert, DashboardData, Diagnostic, SimulationScenario, Telemetry, Trip, Vehicle, VehicleHealth } from './types'

type Connection = 'connecting' | 'live' | 'offline'

const nf = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })
const timeFormat = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

function App() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [vehicleId, setVehicleId] = useState('')
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionPending, setActionPending] = useState(false)
  const [error, setError] = useState('')
  const [connection, setConnection] = useState<Connection>('connecting')

  useEffect(() => {
    api.listVehicles()
      .then((items) => {
        setVehicles(items)
        if (items.length) setVehicleId(items[0].id)
        else setLoading(false)
      })
      .catch((reason: Error) => {
        setError(reason.message)
        setLoading(false)
      })
  }, [])

  const refresh = useCallback(async () => {
    if (!vehicleId) return
    try {
      setError('')
      const data = await api.dashboard(vehicleId)
      setDashboard(data)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível carregar os dados')
    } finally {
      setLoading(false)
    }
  }, [vehicleId])

  useEffect(() => {
    if (!vehicleId) return
    queueMicrotask(() => void refresh())
    const events = new EventSource(api.streamUrl(vehicleId))
    events.onopen = () => setConnection('live')
    events.onerror = () => setConnection('offline')
    events.addEventListener('telemetry', (event) => {
      const telemetry = JSON.parse(event.data) as Telemetry
      setDashboard((current) => current && ({
        ...current,
        latestTelemetry: telemetry,
        telemetryHistory: [...current.telemetryHistory.slice(-59), telemetry],
      }))
    })
    events.addEventListener('alert', (event) => {
      const alert = JSON.parse(event.data) as Alert
      setDashboard((current) => current && ({ ...current, openAlerts: [alert, ...current.openAlerts] }))
    })
    events.addEventListener('diagnostic', (event) => {
      const diagnostic = JSON.parse(event.data) as Diagnostic
      setDashboard((current) => current && ({
        ...current,
        activeDiagnostics: [diagnostic, ...current.activeDiagnostics.filter((item) => item.code !== diagnostic.code)],
      }))
    })
    events.addEventListener('alert-acknowledged', (event) => {
      const alert = JSON.parse(event.data) as Alert
      setDashboard((current) => current && ({
        ...current,
        openAlerts: current.openAlerts.filter((item) => item.id !== alert.id),
      }))
    })
    events.addEventListener('health', (event) => {
      const health = JSON.parse(event.data) as VehicleHealth
      setDashboard((current) => current && ({ ...current, health }))
    })
    events.addEventListener('trip-started', (event) => {
      const trip = JSON.parse(event.data) as Trip
      setDashboard((current) => current && ({ ...current, activeTrip: trip }))
    })
    events.addEventListener('trip-finished', (event) => {
      const trip = JSON.parse(event.data) as Trip
      setDashboard((current) => current && ({
        ...current,
        activeTrip: null,
        recentTrips: [trip, ...current.recentTrips.filter((item) => item.id !== trip.id)].slice(0, 8),
      }))
    })
    return () => events.close()
  }, [vehicleId, refresh])

  function selectVehicle(nextVehicleId: string) {
    setLoading(true)
    setConnection('connecting')
    setVehicleId(nextVehicleId)
  }

  async function chooseScenario(nextScenario: SimulationScenario) {
    setActionPending(true)
    try {
      await api.setScenario(vehicleId, nextScenario)
      const status = await api.setSimulation(vehicleId, true)
      setDashboard((current) => current && ({
        ...current,
        simulationRunning: status.running,
        simulationScenario: status.scenario,
      }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Falha ao selecionar o cenário')
    } finally {
      setActionPending(false)
    }
  }

  async function toggleSimulation() {
    if (!dashboard) return
    setActionPending(true)
    try {
      const status = await api.setSimulation(vehicleId, !dashboard.simulationRunning)
      setDashboard((current) => current && ({ ...current, simulationRunning: status.running }))
      await refresh()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Falha ao controlar o simulador')
    } finally {
      setActionPending(false)
    }
  }

  async function acknowledge(alertId: string) {
    try {
      await api.acknowledge(vehicleId, alertId)
      setDashboard((current) => current && ({
        ...current,
        openAlerts: current.openAlerts.filter((item) => item.id !== alertId),
      }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Falha ao reconhecer o alerta')
    }
  }

  if (loading && !dashboard) return <LoadingScreen />

  if (!vehicles.length) {
    return (
      <main className="center-state">
        <Brand />
        <h1>Nenhum veículo cadastrado</h1>
        <p>Inicie a API com o perfil <code>demo</code> ou crie um veículo pela API para abrir a central.</p>
        {error && <div className="error-banner">{error}</div>}
      </main>
    )
  }

  const sample = dashboard?.latestTelemetry

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <nav aria-label="Navegação principal">
          <a className="nav-item active" href="#overview"><span>⌁</span> Visão geral</a>
          <a className="nav-item" href="#telemetry"><span>⌁</span> Telemetria</a>
          <a className="nav-item" href="#diagnostics"><span>◇</span> Diagnósticos</a>
          <a className="nav-item" href="#alerts"><span>!</span> Alertas</a>
          <a className="nav-item" href="#trips"><span>↗</span> Viagens</a>
        </nav>
        <div className="sidebar-foot">
          <div className="mini-status"><i className={connection} /> API {connection === 'live' ? 'conectada' : 'reconectando'}</div>
          <small>Enhara CP1 · 2026</small>
        </div>
      </aside>

      <main className="content" id="overview">
        <header className="topbar">
          <div>
            <span className="eyebrow">CENTRAL VEICULAR</span>
            <h1>Olá, acompanhe seu carro.</h1>
          </div>
          <div className="top-actions">
            <label className="vehicle-select">
              <span>Veículo</span>
              <select value={vehicleId} onChange={(event) => selectVehicle(event.target.value)}>
                {vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.name} · {vehicle.model}</option>)}
              </select>
            </label>
            <div className="scenario-control" aria-label="Cenário da simulação">
              {(['NORMAL', 'OVERHEAT', 'LOW_BATTERY'] as SimulationScenario[]).map((item) => (
                <button key={item} className={dashboard?.simulationScenario === item ? 'active' : ''} onClick={() => chooseScenario(item)} disabled={actionPending}>
                  {item === 'NORMAL' ? 'Normal' : item === 'OVERHEAT' ? 'Superaquecimento' : 'Bateria baixa'}
                </button>
              ))}
            </div>
            <button className={`simulation-button ${dashboard?.simulationRunning ? 'running' : ''}`}
                    onClick={toggleSimulation} disabled={actionPending || !dashboard}>
              <span className="pulse-dot" />
              {dashboard?.simulationRunning ? 'Simulação ativa' : 'Iniciar simulação'}
            </button>
          </div>
        </header>

        {error && <div className="error-banner"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}

        {dashboard && (
          <>
            <section className="hero-grid">
              <article className="vehicle-hero panel">
                <div className="vehicle-copy">
                  <span className="live-pill"><i className={connection} /> {connection === 'live' ? 'AO VIVO' : 'RECONECTANDO'}</span>
                  <h2>{dashboard.vehicle.name}</h2>
                  <p>{dashboard.vehicle.manufacturer} {dashboard.vehicle.model} · {dashboard.vehicle.modelYear}</p>
                  <dl>
                    <div><dt>VIN</dt><dd>{dashboard.vehicle.vin.slice(-8)}</dd></div>
                    <div><dt>Placa</dt><dd>{dashboard.vehicle.licensePlate}</dd></div>
                    <div><dt>Odômetro</dt><dd>{nf.format(dashboard.vehicle.odometerKm)} km</dd></div>
                    <div><dt>Fonte</dt><dd>{sample?.source ?? '—'}</dd></div>
                  </dl>
                </div>
                <CarIllustration />
              </article>
              <article className={`health-card panel ${healthTone(dashboard.health.status)}`}>
                <span className="eyebrow">SAÚDE DO VEÍCULO</span>
                <HealthRing value={dashboard.health.score} />
                <strong>{dashboard.health.label}</strong>
                <span>{dashboard.health.explanation}</span>
                <p>{dashboard.health.observations[0]}</p>
              </article>
            </section>

            <section className="metrics" aria-label="Métricas em tempo real">
              <Metric label="Velocidade" value={sample ? nf.format(sample.speedKph) : '—'} unit="km/h" tone="green" level={(sample?.speedKph ?? 0) / 1.8} />
              <Metric label="Rotação" value={sample ? nf.format(sample.rpm) : '—'} unit="rpm" tone="blue" level={(sample?.rpm ?? 0) / 60} />
              <Metric label="Temperatura" value={sample ? nf.format(sample.engineTempC) : '—'} unit="°C" tone={sample && sample.engineTempC >= 105 ? 'red' : 'amber'} level={(sample?.engineTempC ?? 0) / 1.25} />
              <Metric label="Bateria" value={sample ? nf.format(sample.batteryVoltage) : '—'} unit="V" tone={sample && sample.batteryVoltage < 12 ? 'red' : 'purple'} level={(sample?.batteryVoltage ?? 0) / 0.15} />
              <Metric label="Combustível" value={sample ? nf.format(sample.fuelLevelPercent) : '—'} unit="%" tone={sample && sample.fuelLevelPercent <= 15 ? 'red' : 'green'} level={sample?.fuelLevelPercent ?? 0} />
            </section>

            <section className="lower-grid">
              <article className="chart-card panel" id="telemetry">
                <div className="section-heading">
                  <div><span className="eyebrow">ÚLTIMOS 60 REGISTROS</span><h3>Ritmo da viagem</h3></div>
                  <span className="updated">Atualizado {sample ? timeFormat.format(new Date(sample.recordedAt)) : '—'}</span>
                </div>
                <LineChart data={dashboard.telemetryHistory} />
                <div className="chart-legend"><span className="speed-key">Velocidade</span><span className="temp-key">Temperatura</span></div>
              </article>

              <article className="alerts-card panel" id="alerts">
                <div className="section-heading">
                  <div><span className="eyebrow">ATENÇÃO</span><h3>Alertas abertos</h3></div>
                  <span className="counter">{dashboard.openAlerts.length}</span>
                </div>
                <div className="alert-list">
                  {!dashboard.openAlerts.length && <Empty icon="✓" title="Nenhum alerta aberto" text="O veículo está operando dentro dos limites." />}
                  {dashboard.openAlerts.slice(0, 5).map((alert) => (
                    <div className={`alert-row ${alert.severity.toLowerCase()}`} key={alert.id}>
                      <span className="alert-icon">!</span>
                      <div><strong>{alert.title}</strong><p>{alert.message}</p><small>{timeFormat.format(new Date(alert.createdAt))}</small></div>
                      <button onClick={() => acknowledge(alert.id)} title="Reconhecer alerta">✓</button>
                    </div>
                  ))}
                </div>
              </article>

              <article className="diagnostics-card panel" id="diagnostics">
                <div className="section-heading">
                  <div><span className="eyebrow">LEITURA OBD</span><h3>Diagnósticos ativos</h3></div>
                  <span className="counter muted">{dashboard.activeDiagnostics.length}</span>
                </div>
                {!dashboard.activeDiagnostics.length
                  ? <Empty icon="◇" title="Nenhum código ativo" text="A leitura contínua não encontrou falhas." />
                  : <div className="diagnostic-list">{dashboard.activeDiagnostics.map((diagnostic) => (
                    <div className="diagnostic-row" key={diagnostic.id}>
                      <code>{diagnostic.code}</code>
                      <div><strong>{diagnostic.description}</strong><small>Detectado {timeFormat.format(new Date(diagnostic.detectedAt))}</small></div>
                      <span className={`severity ${diagnostic.severity.toLowerCase()}`}>{diagnostic.severity}</span>
                    </div>
                  ))}</div>}
              </article>

              <article className="trips-card panel" id="trips">
                <div className="section-heading">
                  <div><span className="eyebrow">HISTÓRICO RECENTE</span><h3>Viagens</h3></div>
                  <span className={`trip-state ${dashboard.activeTrip ? 'active' : ''}`}>
                    {dashboard.activeTrip ? 'EM CURSO' : `${dashboard.recentTrips.filter((trip) => trip.endedAt).length} CONCLUÍDAS`}
                  </span>
                </div>
                {dashboard.activeTrip && (
                  <div className="active-trip">
                    <span className="pulse-dot" />
                    <div><strong>Viagem em andamento</strong><small>Iniciada às {timeFormat.format(new Date(dashboard.activeTrip.startedAt))}</small></div>
                  </div>
                )}
                {!dashboard.recentTrips.some((trip) => trip.endedAt) && !dashboard.activeTrip
                  ? <Empty icon="↗" title="Nenhuma viagem concluída" text="Inicie e pare a simulação para gerar o primeiro resumo." />
                  : <div className="trip-list">{dashboard.recentTrips.filter((trip) => trip.endedAt).slice(0, 4).map((trip) => (
                    <div className="trip-row" key={trip.id}>
                      <div><strong>{nf.format(trip.distanceKm)} km</strong><small>{new Date(trip.startedAt).toLocaleDateString('pt-BR')} · {timeFormat.format(new Date(trip.startedAt))}</small></div>
                      <div><span>Média</span><strong>{nf.format(trip.averageSpeedKph)} km/h</strong></div>
                      <div><span>Máxima</span><strong>{nf.format(trip.maxSpeedKph)} km/h</strong></div>
                      <div className="driving-score"><span>Score experimental</span><strong>{trip.drivingScore}</strong></div>
                    </div>
                  ))}</div>}
              </article>

              <article className="location-card panel">
                <div className="map-grid" />
                <div className="location-pin"><span>●</span></div>
                <div className="location-copy">
                  <span className="eyebrow">LOCALIZAÇÃO ATUAL</span>
                  <h3>{sample?.latitude ? 'São Paulo, SP' : 'Aguardando posição'}</h3>
                  <p>{sample?.latitude ? `${sample.latitude.toFixed(5)}, ${sample.longitude?.toFixed(5)}` : 'O próximo pacote de telemetria atualizará o mapa.'}</p>
                </div>
              </article>
            </section>
          </>
        )}
      </main>
    </div>
  )
}

function Brand() {
  return <div className="brand"><span className="brand-mark">E</span><span>enhara<small>DRIVE WITH CLARITY</small></span></div>
}

function LoadingScreen() {
  return <main className="loading-screen"><Brand /><div className="loader" /><p>Conectando à central veicular…</p></main>
}

function Metric({ label, value, unit, tone, level }: { label: string; value: string; unit: string; tone: string; level: number }) {
  const safeLevel = Math.max(0, Math.min(100, level))
  return <article className={`metric panel ${tone}`}>
    <span>{label}</span><strong>{value}<small>{unit}</small></strong>
    <div className="metric-track"><i style={{ width: `${safeLevel}%` }} /></div>
  </article>
}

function HealthRing({ value }: { value: number }) {
  return (
    <div className="health-ring" style={{ '--health': `${value * 3.6}deg` } as React.CSSProperties}>
      <div><strong>{value}</strong><span>/ 100</span></div>
    </div>
  )
}

function CarIllustration() {
  return (
    <svg className="car" viewBox="0 0 520 230" role="img" aria-label="Silhueta de automóvel">
      <defs><linearGradient id="carPaint" x1="0" x2="1"><stop stopColor="#172b24"/><stop offset=".55" stopColor="#3af59b"/><stop offset="1" stopColor="#16372b"/></linearGradient></defs>
      <ellipse cx="270" cy="193" rx="205" ry="21" fill="#020706" opacity=".7" />
      <path d="M52 153c9-31 29-50 69-59l45-45c14-14 30-20 51-20h104c24 0 45 8 62 27l39 44c31 8 48 25 52 53l-8 22h-42c-6-30-25-47-52-47-28 0-48 18-53 48H188c-6-30-26-48-54-48-27 0-46 17-52 47H48l-9-15 13-7z" fill="url(#carPaint)" />
      <path d="M180 57c10-10 20-14 37-14h96c20 0 35 5 49 21l26 30H145l35-37z" fill="#091512" stroke="#6bffbd" strokeOpacity=".45" />
      <path d="M244 46v46M315 46l45 47" stroke="#48d995" strokeOpacity=".35" />
      <circle cx="134" cy="174" r="35" fill="#08100e" stroke="#345c4d" strokeWidth="8"/><circle cx="134" cy="174" r="14" fill="#8afac7" opacity=".6"/>
      <circle cx="371" cy="174" r="35" fill="#08100e" stroke="#345c4d" strokeWidth="8"/><circle cx="371" cy="174" r="14" fill="#8afac7" opacity=".6"/>
      <path d="M57 142h55M408 111h39" stroke="#c0ffe2" strokeWidth="5" strokeLinecap="round" opacity=".8"/>
    </svg>
  )
}

function LineChart({ data }: { data: Telemetry[] }) {
  const width = 640
  const height = 210
  const plot = (values: number[], min: number, max: number) => values.map((value, index) => {
    const x = 18 + (index / Math.max(values.length - 1, 1)) * (width - 36)
    const y = 18 + (1 - (value - min) / Math.max(max - min, 1)) * (height - 42)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
  if (data.length < 2) return <div className="chart-empty">Aguardando a série de telemetria…</div>
  return (
    <svg className="line-chart" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-label="Histórico de velocidade e temperatura">
      <defs>
        <linearGradient id="speedFill" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#4df5a5" stopOpacity=".3"/><stop offset="1" stopColor="#4df5a5" stopOpacity="0"/></linearGradient>
      </defs>
      {[40, 80, 120, 160].map((y) => <line key={y} x1="18" x2="622" y1={y} y2={y} stroke="#ffffff" strokeOpacity=".06" />)}
      <polygon points={`18,190 ${plot(data.map((item) => item.speedKph), 0, 130)} 622,190`} fill="url(#speedFill)" />
      <polyline points={plot(data.map((item) => item.speedKph), 0, 130)} fill="none" stroke="#4df5a5" strokeWidth="3" vectorEffect="non-scaling-stroke" />
      <polyline points={plot(data.map((item) => item.engineTempC), 60, 125)} fill="none" stroke="#ffb65c" strokeWidth="2" strokeDasharray="6 5" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

function Empty({ icon, title, text }: { icon: string; title: string; text: string }) {
  return <div className="empty"><span>{icon}</span><strong>{title}</strong><p>{text}</p></div>
}

export default App
