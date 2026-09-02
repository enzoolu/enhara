import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from './api'
import { CapabilityPanel } from './CapabilityPanel'
import { healthTone } from './health'
import { NotesPanel } from './NotesPanel'
import { DEFAULT_CARD_KEYS, effectiveAvailability, liveValue, parameterMeta, supportedMetricKeys } from './obd'
import { VehicleProfilePage } from './VehicleProfilePage'
import type {
  Alert,
  DashboardData,
  Diagnostic,
  SimulatedObdDtc,
  SimulatedObdSnapshot,
  SimulationScenario,
  Telemetry,
  Trip,
  Vehicle,
  VehicleHealth,
  VehicleNote,
  VehicleNoteInput,
  VehiclePhoto,
  VehicleProfile,
  VehicleProfileKey,
  VehicleStatistics,
} from './types'

type Connection = 'connecting' | 'live' | 'offline'
type Page = 'dashboard' | 'statistics' | 'vehicle'

const nf = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })
const preciseNf = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 })
const timeFormat = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

function App() {
  const [page, setPage] = useState<Page>('dashboard')
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [vehicleId, setVehicleId] = useState('')
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [obd, setObd] = useState<SimulatedObdSnapshot | null>(null)
  const [statistics, setStatistics] = useState<VehicleStatistics | null>(null)
  const [notes, setNotes] = useState<VehicleNote[]>([])
  const [trips, setTrips] = useState<Trip[]>([])
  const [profile, setProfile] = useState<VehicleProfile | null>(null)
  const [photos, setPhotos] = useState<VehiclePhoto[]>([])
  const [cardKeys, setCardKeys] = useState<string[]>([])
  const [customizing, setCustomizing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [actionPending, setActionPending] = useState(false)
  const [notePending, setNotePending] = useState(false)
  const [profilePending, setProfilePending] = useState(false)
  const [error, setError] = useState('')
  const [connection, setConnection] = useState<Connection>('connecting')
  const [clock, setClock] = useState(() => Date.now())

  useEffect(() => {
    const interval = window.setInterval(() => setClock(Date.now()), 1_000)
    return () => window.clearInterval(interval)
  }, [])

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

  const refreshObd = useCallback(async () => {
    if (!vehicleId) return
    setObd(await api.simulatedObdState(vehicleId))
  }, [vehicleId])

  const refreshNotes = useCallback(async () => {
    if (!vehicleId) return
    setNotes(await api.listNotes(vehicleId, true))
  }, [vehicleId])

  const refreshVehicleProfile = useCallback(async () => {
    if (!vehicleId) return
    try {
      const [profileData, photoData, noteData] = await Promise.all([
        api.vehicleProfile(vehicleId),
        api.listVehiclePhotos(vehicleId),
        api.listNotes(vehicleId, true),
      ])
      setProfile(profileData)
      setPhotos(photoData)
      setNotes(noteData)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível carregar o perfil do veículo')
    }
  }, [vehicleId])

  const refresh = useCallback(async () => {
    if (!vehicleId) return
    try {
      setError('')
      const [dashboardData, obdData, statisticsData, noteData, tripData] = await Promise.all([
        api.dashboard(vehicleId),
        api.simulatedObdState(vehicleId),
        api.statistics(vehicleId),
        api.listNotes(vehicleId, true),
        api.listTrips(vehicleId, 100),
      ])
      setDashboard(dashboardData)
      setObd(obdData)
      setCardKeys(loadCardSelection(obdData, vehicleId))
      setStatistics(statisticsData)
      setNotes(noteData)
      setTrips(tripData)
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
      setDashboard((current) => current && ({ ...current, latestTelemetry: telemetry,
        telemetryHistory: [...current.telemetryHistory.slice(-59), telemetry],
        vehicleDataConnected: telemetry.source !== 'API' }))
      setStatistics((current) => current && ({ ...current, maxRecordedSpeedKph: Math.max(current.maxRecordedSpeedKph ?? 0, telemetry.speedKph) }))
      void refreshObd().catch(() => undefined)
    })
    events.addEventListener('alert', (event) => {
      const alert = JSON.parse(event.data) as Alert
      setDashboard((current) => current && ({ ...current, openAlerts: [alert, ...current.openAlerts] }))
    })
    events.addEventListener('diagnostic', (event) => {
      const diagnostic = JSON.parse(event.data) as Diagnostic
      setDashboard((current) => current && ({ ...current, activeDiagnostics: [diagnostic, ...current.activeDiagnostics.filter((item) => item.code !== diagnostic.code)] }))
    })
    events.addEventListener('diagnostic-resolved', (event) => {
      const diagnostic = JSON.parse(event.data) as Diagnostic
      setDashboard((current) => current && ({ ...current,
        activeDiagnostics: current.activeDiagnostics.filter((item) => item.id !== diagnostic.id && item.code !== diagnostic.code) }))
    })
    events.addEventListener('alert-acknowledged', (event) => {
      const alert = JSON.parse(event.data) as Alert
      setDashboard((current) => current && ({ ...current, openAlerts: current.openAlerts.filter((item) => item.id !== alert.id) }))
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
      setDashboard((current) => current && ({ ...current, activeTrip: null, recentTrips: [trip, ...current.recentTrips.filter((item) => item.id !== trip.id)].slice(0, 8) }))
      setTrips((current) => [trip, ...current.filter((item) => item.id !== trip.id)].slice(0, 100))
      void api.statistics(vehicleId).then(setStatistics).catch(() => undefined)
    })
    return () => events.close()
  }, [vehicleId, refresh, refreshObd])

  useEffect(() => {
    if (vehicleId) queueMicrotask(() => void refreshVehicleProfile())
  }, [vehicleId, refreshVehicleProfile])

  const currentReading = Boolean(dashboard?.vehicleDataConnected && dashboard.latestTelemetry
    && clock - Date.parse(dashboard.latestTelemetry.recordedAt) <= 5_000
    && obd?.liveData.some((value) => clock - Date.parse(value.observedAt) <= 5_000))

  function selectVehicle(nextVehicleId: string) {
    setLoading(true)
    setConnection('connecting')
    setDashboard(null)
    setObd(null)
    setStatistics(null)
    setNotes([])
    setTrips([])
    setProfile(null)
    setPhotos([])
    setVehicleId(nextVehicleId)
  }

  async function chooseScenario(nextScenario: SimulationScenario) {
    setActionPending(true)
    try {
      await api.setScenario(vehicleId, nextScenario)
      const status = await api.setSimulation(vehicleId, true)
      setDashboard((current) => current && ({ ...current, simulationRunning: status.running,
        simulationScenario: status.scenario, vehicleDataConnected: status.running }))
      await refreshObd()
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
      await api.setSimulation(vehicleId, !dashboard.simulationRunning)
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
      setDashboard((current) => current && ({ ...current, openAlerts: current.openAlerts.filter((item) => item.id !== alertId) }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Falha ao reconhecer o alerta')
    }
  }

  function toggleCard(key: string) {
    if (!obd || !supportedMetricKeys(obd).includes(key)) return
    setCardKeys((current) => {
      const next = current.includes(key) ? current.filter((item) => item !== key) : [...current, key]
      localStorage.setItem(cardStorageKey(vehicleId), JSON.stringify(next))
      return next
    })
  }

  async function noteAction(action: () => Promise<unknown>) {
    setNotePending(true)
    try {
      setError('')
      await action()
      await refreshNotes()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível salvar a nota')
      throw reason
    } finally {
      setNotePending(false)
    }
  }

  const notesProps = {
    notes,
    busy: notePending,
    onCreate: (input: VehicleNoteInput) => noteAction(() => api.createNote(vehicleId, input)),
    onUpdate: (noteId: string, input: VehicleNoteInput) => noteAction(() => api.updateNote(vehicleId, noteId, input)),
    onComplete: (noteId: string) => noteAction(() => api.completeNote(vehicleId, noteId)),
    onReopen: (noteId: string) => noteAction(() => api.reopenNote(vehicleId, noteId)),
    onDelete: (noteId: string) => noteAction(() => api.deleteNote(vehicleId, noteId)),
  }

  async function profileAction(action: () => Promise<VehicleProfile>) {
    setProfilePending(true)
    try {
      setError('')
      setProfile(await action())
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível atualizar o perfil do veículo')
      throw reason
    } finally {
      setProfilePending(false)
    }
  }

  async function photoAction(action: () => Promise<unknown>) {
    setProfilePending(true)
    try {
      setError('')
      await action()
      setPhotos(await api.listVehiclePhotos(vehicleId))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Não foi possível atualizar as fotos')
      throw reason
    } finally {
      setProfilePending(false)
    }
  }

  if (loading && !dashboard && page !== 'vehicle') return <LoadingScreen />

  if (!vehicles.length) {
    return <main className="center-state"><Brand /><h1>Nenhum veículo cadastrado</h1><p>Cadastre um veículo ou inicie a API com o perfil <code>demo</code>.</p>{error && <div className="error-banner">{error}</div>}</main>
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <nav aria-label="Navegação principal">
          <button className={`nav-item ${page === 'dashboard' ? 'active' : ''}`} onClick={() => setPage('dashboard')}><span>⌁</span><div>Painel principal<small>Estado do veículo</small></div></button>
          <button className={`nav-item ${page === 'statistics' ? 'active' : ''}`} onClick={() => setPage('statistics')}><span>↗</span><div>Minhas estatísticas<small>Histórico monitorado</small></div></button>
          <button className={`nav-item ${page === 'vehicle' ? 'active' : ''}`} onClick={() => setPage('vehicle')}><span>◇</span><div>Meu carro<small>Cadastro e fontes</small></div></button>
        </nav>
        <div className="sidebar-foot"><ConnectionLabel connection={connection} /><small>Enhara · assistência conectada</small></div>
      </aside>

      <main className="content">
        <header className="topbar">
          <div><span className="eyebrow">{page === 'dashboard' ? 'PAINEL PRINCIPAL' : page === 'statistics' ? 'HISTÓRICO ENHARA' : 'PERFIL DO VEÍCULO'}</span><h1>{page === 'dashboard' ? 'Seu carro, com clareza.' : page === 'statistics' ? 'Minhas estatísticas' : 'Meu carro'}</h1></div>
          <div className="top-actions">
            <label className="vehicle-select"><span>Veículo</span><select value={vehicleId} onChange={(event) => selectVehicle(event.target.value)}>{vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.name} · {vehicle.model}</option>)}</select></label>
            {page !== 'vehicle' && <button className={`simulation-button ${dashboard?.simulationRunning ? 'running' : ''}`} onClick={toggleSimulation} disabled={actionPending || !dashboard}><span className="pulse-dot" />{dashboard?.simulationRunning ? 'ECU conectada' : 'Conectar ECU'}</button>}
          </div>
        </header>

        {error && <div className="error-banner"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}

        {page === 'vehicle'
          ? profile
            ? <VehicleProfilePage key={vehicleId} vehicle={vehicles.find((item) => item.id === vehicleId) ?? vehicles[0]} profile={profile} photos={photos} busy={profilePending} notesPanel={<NotesPanel {...notesProps} />} photoUrl={api.vehiclePhotoUrl} onUpdate={(fields) => profileAction(() => api.updateVehicleProfile(vehicleId, fields))} onConfirm={(field: VehicleProfileKey) => profileAction(() => api.confirmVehicleProfile(vehicleId, [field]))} onEnrich={(fipeCode, selection, force) => profileAction(() => api.enrichVehicleProfile(vehicleId, fipeCode, selection, force))} onListFipeBrands={api.listFipeBrands} onListFipeModels={api.listFipeModels} onListFipeYears={api.listFipeYears} onUploadPhoto={(file, caption) => photoAction(() => api.uploadVehiclePhoto(vehicleId, file, caption))} onDeletePhoto={(photoId) => photoAction(() => api.deleteVehiclePhoto(vehicleId, photoId))} />
            : <div className="panel profile-loading"><div className="loader" /><p>Carregando dados persistidos do veículo…</p></div>
          : dashboard && obd && statistics && (page === 'dashboard'
            ? <DashboardPage dashboard={dashboard} obd={obd} connection={connection} currentReading={currentReading} cardKeys={cardKeys} customizing={customizing} setCustomizing={setCustomizing} toggleCard={toggleCard} chooseScenario={chooseScenario} actionPending={actionPending} acknowledge={acknowledge} notesPanel={<NotesPanel {...notesProps} />} />
            : <StatisticsPage dashboard={dashboard} obd={obd} statistics={statistics} trips={trips} notes={notes} currentReading={currentReading} notesPanel={<NotesPanel {...notesProps} />} />)}
      </main>

      <nav className="mobile-nav" aria-label="Navegação móvel">
        <button className={page === 'dashboard' ? 'active' : ''} onClick={() => setPage('dashboard')}><span>⌁</span>Painel</button>
        <button className={page === 'statistics' ? 'active' : ''} onClick={() => setPage('statistics')}><span>↗</span>Estatísticas</button>
        <button className={page === 'vehicle' ? 'active' : ''} onClick={() => setPage('vehicle')}><span>◇</span>Meu carro</button>
      </nav>
    </div>
  )
}

interface DashboardPageProps {
  dashboard: DashboardData
  obd: SimulatedObdSnapshot
  connection: Connection
  currentReading: boolean
  cardKeys: string[]
  customizing: boolean
  setCustomizing: (value: boolean) => void
  toggleCard: (key: string) => void
  chooseScenario: (scenario: SimulationScenario) => Promise<void>
  actionPending: boolean
  acknowledge: (alertId: string) => Promise<void>
  notesPanel: React.ReactNode
}

function DashboardPage({ dashboard, obd, connection, currentReading, cardKeys, customizing, setCustomizing, toggleCard, chooseScenario, actionPending, acknowledge, notesPanel }: DashboardPageProps) {
  const sample = dashboard.latestTelemetry
  return <>
    <section className="status-hero panel">
      <div className="vehicle-overview">
        <div className="connection-row"><span className={`state-pill ${currentReading ? 'connected' : 'paused'}`}><i />{currentReading ? 'ECU conectada' : 'ECU desconectada'}</span><span className="origin-pill">Origem: {telemetryOriginLabel(sample?.source)}</span></div>
        <h2>{dashboard.vehicle.name}</h2>
        <p>{dashboard.vehicle.manufacturer} {dashboard.vehicle.model} · {dashboard.vehicle.modelYear} · {dashboard.vehicle.licensePlate}</p>
        <div className="read-state">
          <div><span>Estado exibido</span><strong>{currentReading ? 'Leitura atual' : sample ? 'Última leitura válida' : 'Aguardando primeira leitura'}</strong></div>
          <div><span>Última leitura</span><strong>{sample ? dateTimeFormat.format(new Date(sample.recordedAt)) : 'Sem leitura registrada'}</strong></div>
          <div><span>Canal com a API</span><strong><ConnectionLabel connection={connection} /></strong></div>
        </div>
      </div>
      <div className={`health-summary ${healthTone(dashboard.health.status)}`}><span className="eyebrow">VEHICLE HEALTH</span><HealthRing value={dashboard.health.score} /><div><strong>{dashboard.health.label}</strong><p>{dashboard.health.explanation}</p></div></div>
    </section>

    <section className="section-block">
      <div className="section-heading page-section-heading"><div><span className="eyebrow">DADOS DA ECU</span><h3>{currentReading ? 'Estado atual' : 'Últimos valores válidos'}</h3></div><button className="secondary-button" onClick={() => setCustomizing(!customizing)}>⚙ Personalizar cards</button></div>
      {customizing && <CardCustomizer snapshot={obd} selected={cardKeys} onToggle={toggleCard} onClose={() => setCustomizing(false)} />}
      {!cardKeys.length ? <div className="panel empty-state"><span>＋</span><div><strong>Nenhum card selecionado</strong><p>Escolha entre os parâmetros suportados pela ECU.</p></div></div> : <div className="metric-grid">{cardKeys.map((key) => <ObdMetricCard key={key} snapshot={obd} parameterKey={key} current={currentReading} />)}</div>}
    </section>

    <section className="dashboard-grid">
      <article className="panel chart-card">
        <div className="section-heading"><div><span className="eyebrow">HISTÓRICO REGISTRADO</span><h3>Velocidade e arrefecimento</h3></div><span className="updated">{sample ? `Até ${timeFormat.format(new Date(sample.recordedAt))}` : 'Sem dados'}</span></div>
        <LineChart data={dashboard.telemetryHistory} />
        {!!dashboard.telemetryHistory.length && <div className="chart-legend"><span className="speed-key">Velocidade</span><span className="temp-key">Temperatura</span></div>}
      </article>
      <article className="panel alerts-card">
        <div className="section-heading"><div><span className="eyebrow">ALERTAS ENHARA</span><h3>Alertas abertos</h3></div><span className="counter">{dashboard.openAlerts.length}</span></div>
        {!dashboard.openAlerts.length ? <Empty icon="✓" title="Nenhum alerta aberto" text="Nenhum alerta derivado está ativo agora." /> : <div className="alert-list">{dashboard.openAlerts.slice(0, 5).map((alert) => <div className={`alert-row ${alert.severity.toLowerCase()}`} key={alert.id}><span className="alert-icon">!</span><div><strong>{alert.title}</strong><p>{alert.message}</p><small>{dateTimeFormat.format(new Date(alert.createdAt))}</small></div><button onClick={() => acknowledge(alert.id)} title="Reconhecer alerta">✓</button></div>)}</div>}
      </article>
      <article className="panel findings-card">
        <div className="section-heading"><div><span className="eyebrow">FINDINGS DO ENHARA</span><h3>Condições detectadas</h3></div><span className="counter muted">{dashboard.activeDiagnostics.length}</span></div>
        <p className="section-intro">Regras derivadas da telemetria. Não são códigos DTC gravados pela ECU.</p>
        {!dashboard.activeDiagnostics.length ? <Empty icon="◇" title="Nenhum finding ativo" text="As regras do Enhara não detectaram condição anormal." /> : <div className="diagnostic-list">{dashboard.activeDiagnostics.map((item) => <div className="diagnostic-row" key={item.id}><code>{item.code}</code><div><strong>{item.description}</strong><small>Detectado {dateTimeFormat.format(new Date(item.detectedAt))}</small></div><span className={`severity ${item.severity.toLowerCase()}`}>{item.severity}</span></div>)}</div>}
      </article>
      <article className="panel trip-card">
        <div className="section-heading"><div><span className="eyebrow">HISTÓRICO RELEVANTE</span><h3>Viagens recentes</h3></div><span className={`trip-state ${dashboard.activeTrip ? 'active' : ''}`}>{dashboard.activeTrip ? 'EM CURSO' : `${dashboard.recentTrips.filter((trip) => trip.endedAt).length} CONCLUÍDAS`}</span></div>
        {dashboard.activeTrip && <div className="active-trip"><span className="pulse-dot" /><div><strong>Viagem em andamento</strong><small>Iniciada às {timeFormat.format(new Date(dashboard.activeTrip.startedAt))}</small></div></div>}
        {!dashboard.recentTrips.some((trip) => trip.endedAt) && !dashboard.activeTrip ? <Empty icon="↗" title="Nenhuma viagem concluída" text="A primeira viagem aparecerá após encerrar uma sessão." /> : <div className="trip-list">{dashboard.recentTrips.filter((trip) => trip.endedAt).slice(0, 4).map((trip) => <TripRow key={trip.id} trip={trip} />)}</div>}
      </article>
      <div className="notes-slot">{notesPanel}</div>
      <article className="panel simulation-lab">
        <div className="section-heading"><div><span className="eyebrow">AMBIENTE DE DEMONSTRAÇÃO</span><h3>Cenário da ECU</h3></div><span className="source-badge">Somente ECU/OBD é simulado</span></div>
        <p className="section-intro">O cenário altera o estado do veículo e da ECU; a interface apenas lê o resultado.</p>
        <div className="scenario-control">{(['NORMAL', 'OVERHEAT', 'LOW_VOLTAGE', 'MISFIRE'] as SimulationScenario[]).map((scenario) => <button key={scenario} className={dashboard.simulationScenario === scenario ? 'active' : ''} onClick={() => chooseScenario(scenario)} disabled={actionPending}>{scenarioLabel(scenario)}</button>)}</div>
      </article>
    </section>
  </>
}

function StatisticsPage({ dashboard, obd, statistics, trips, notes, currentReading, notesPanel }: { dashboard: DashboardData; obd: SimulatedObdSnapshot; statistics: VehicleStatistics; trips: Trip[]; notes: VehicleNote[]; currentReading: boolean; notesPanel: React.ReactNode }) {
  const completedTrips = trips.filter((trip) => trip.endedAt)
  const activity = useMemo(() => buildRecentActivity(completedTrips, notes, obd.dtcs), [completedTrips, notes, obd.dtcs])
  return <>
    <section className="stats-summary">
      <StatCard eyebrow="MONITORADO PELO ENHARA" label="Distância registrada" value={`${preciseNf.format(statistics.distanceTrackedKm)} km`} helper={`${statistics.completedTrips} ${statistics.completedTrips === 1 ? 'viagem concluída' : 'viagens concluídas'} · não é o odômetro total`} />
      <StatCard eyebrow="TELEMETRIA REGISTRADA" label="Velocidade máxima" value={statistics.maxRecordedSpeedKph == null ? 'Indisponível' : `${nf.format(statistics.maxRecordedSpeedKph)} km/h`} helper={statistics.maxRecordedSpeedKph == null ? 'Nenhuma leitura válida' : 'Maior valor persistido'} />
      <StatCard eyebrow="DADOS SUFICIENTES" label="Consumo médio" value={statistics.averageConsumptionKmPerLiter == null ? 'Indisponível' : `${nf.format(statistics.averageConsumptionKmPerLiter)} km/l`} helper={statistics.averageConsumptionKmPerLiter == null ? 'Falta combustível consumido confiável' : 'Calculado pelo Enhara'} muted={statistics.averageConsumptionKmPerLiter == null} />
      <StatCard eyebrow="MEMÓRIA DA ECU" label="DTCs registrados" value={String(obd.dtcs.length)} helper={obd.milOn ? 'MIL acesa pela ECU' : 'MIL apagada'} tone={obd.milOn ? 'warning' : 'normal'} />
    </section>

    <section className="statistics-grid">
      <article className="panel activity-chart-card"><div className="section-heading"><div><span className="eyebrow">SOMENTE VIAGENS REAIS</span><h3>Distância por viagem</h3></div><span className="source-badge">{completedTrips.length} registros</span></div><TripBars trips={completedTrips.slice(0, 10).reverse()} /></article>
      <article className="panel dtc-card">
        <div className="section-heading"><div><span className="eyebrow">CÓDIGOS DA ECU</span><h3>DTCs da ECU simulada</h3></div><span className={`mil-badge ${obd.milOn ? 'on' : ''}`}>MIL {obd.milOn ? 'ACESA' : 'APAGADA'}</span></div>
        <p className="section-intro">Memória própria da ECU. Estes códigos não são findings nem alertas do Enhara.</p>
        {!obd.dtcs.length ? <Empty icon="◇" title="Nenhum DTC registrado" text="A ECU simulada não possui códigos visíveis na memória." /> : <div className="dtc-list">{obd.dtcs.map((dtc) => <DtcRow key={dtc.code} dtc={dtc} milOn={obd.milOn} />)}</div>}
      </article>
      <article className="panel recent-activity-card"><div className="section-heading"><div><span className="eyebrow">EVENTOS REGISTRADOS</span><h3>Atividade recente</h3></div></div>{!activity.length ? <Empty icon="↻" title="Sem atividade recente" text="Viagens, notas e DTCs aparecerão aqui quando existirem." /> : <div className="timeline">{activity.map((item) => <div className={`timeline-row ${item.tone}`} key={item.id}><i /><div><span>{item.kind}</span><strong>{item.title}</strong><p>{item.detail}</p><small>{dateTimeFormat.format(new Date(item.at))}</small></div></div>)}</div>}</article>
      <div className="notes-slot">{notesPanel}</div>
      <div className="capabilities-slot"><CapabilityPanel snapshot={obd} current={currentReading}
        origin={telemetryOriginLabel(dashboard.latestTelemetry?.source)} telemetryHistory={dashboard.telemetryHistory} /></div>
      <article className="panel readiness-card"><div className="section-heading"><div><span className="eyebrow">MONITORES DA ECU</span><h3>Readiness</h3></div></div><div className="readiness-list">{obd.readiness.map((item) => <div key={item.monitor}><span>{readinessLabel(item.monitor)}</span><strong className={item.status.toLowerCase().replaceAll('_', '-')}>{readinessStatusLabel(item.status)}</strong></div>)}</div><small className="footnote">Status informado pela ECU simulada; não representa inspeção mecânica.</small></article>
      <article className={`panel health-detail ${healthTone(dashboard.health.status)}`}><span className="eyebrow">VEHICLE HEALTH DERIVADO</span><h3>{dashboard.health.label}</h3><p>{dashboard.health.explanation}</p><ul>{dashboard.health.observations.map((item) => <li key={item}>{item}</li>)}</ul><strong>{dashboard.health.recommendation}</strong></article>
    </section>
  </>
}

function CardCustomizer({ snapshot, selected, onToggle, onClose }: { snapshot: SimulatedObdSnapshot; selected: string[]; onToggle: (key: string) => void; onClose: () => void }) {
  return <div className="panel card-customizer"><div><strong>Escolha os cards do painel</strong><p>Apenas PIDs declarados como suportados por este perfil podem ser selecionados.</p></div><button className="close-button" onClick={onClose}>×</button><div className="customizer-options">{supportedMetricKeys(snapshot).map((key) => <label key={key}><input type="checkbox" checked={selected.includes(key)} onChange={() => onToggle(key)} /><span>{parameterMeta(key).shortLabel}</span></label>)}</div></div>
}

function ObdMetricCard({ snapshot, parameterKey, current }: { snapshot: SimulatedObdSnapshot; parameterKey: string; current: boolean }) {
  const capability = snapshot.capabilities.find((item) => item.key === parameterKey)
  if (!capability || capability.status !== 'SUPPORTED') return null
  const item = liveValue(snapshot, parameterKey)
  const availability = effectiveAvailability(snapshot, capability)
  const meta = parameterMeta(parameterKey)
  return <article className={`panel obd-metric ${meta.tone}`}><div className="metric-top"><span>{meta.shortLabel}</span><i /></div>{item ? <strong>{preciseNf.format(item.value)} <small>{item.unit}</small></strong> : <strong className="empty-value">—</strong>}<p>{availability === 'SUPPORTED_NO_DATA' ? 'Suportado · aguardando leitura válida' : availability === 'STALE' ? 'Último valor · dado antigo' : current ? 'Estado atual da ECU' : 'Último valor válido'}</p>{item && <small>Serviço {item.service} · PID {item.pid} · {timeFormat.format(new Date(item.observedAt))}</small>}</article>
}

function StatCard({ eyebrow, label, value, helper, muted, tone = 'normal' }: { eyebrow: string; label: string; value: string; helper: string; muted?: boolean; tone?: 'normal' | 'warning' }) {
  return <article className={`panel stat-card ${muted ? 'muted' : ''} ${tone}`}><span className="eyebrow">{eyebrow}</span><h3>{label}</h3><strong>{value}</strong><p>{helper}</p></article>
}

function DtcRow({ dtc, milOn }: { dtc: SimulatedObdDtc; milOn: boolean }) {
  return <div className={`dtc-row ${dtc.active ? 'active' : 'memory'}`}>
    <div className="dtc-technical">
      <div className="dtc-code-line"><code>{dtc.code}</code><span className={`dtc-presence ${dtc.active ? 'present' : ''}`}>{dtc.active ? 'Presente' : 'Memória'}</span></div>
      <div className="dtc-statuses">{dtc.statuses.map((status) => <span key={status}>{dtcStatusLabel(status)}</span>)}</div>
      <small className="technical-evidence">Evidência técnica da ECU: {dtc.description}</small>
      <dl className="dtc-metadata">
        <div><dt>Primeira ocorrência</dt><dd>{dateTimeFormat.format(new Date(dtc.firstDetectedAt))}</dd></div>
        <div><dt>Última ocorrência</dt><dd>{dateTimeFormat.format(new Date(dtc.lastDetectedAt))}</dd></div>
        <div><dt>MIL</dt><dd>{milOn ? 'Acesa' : 'Apagada'}</dd></div>
        <div><dt>Freeze frame</dt><dd>{dtc.freezeFrame ? dateTimeFormat.format(new Date(dtc.freezeFrame.capturedAt)) : 'Não disponível'}</dd></div>
      </dl>
    </div>
    <div className="dtc-explanation"><span>Explicação amigável</span><p>{dtcSimpleExplanation(dtc.code)}</p><small>Esta explicação auxilia a leitura; não substitui a evidência técnica nem afirma uma causa mecânica.</small></div>
  </div>
}

function TripBars({ trips }: { trips: Trip[] }) {
  if (!trips.length) return <Empty icon="▥" title="Sem dados para o gráfico" text="Nenhuma viagem concluída foi registrada pelo Enhara." />
  const max = Math.max(...trips.map((trip) => trip.distanceKm), 0.01)
  return <div className="trip-bars" aria-label="Distância registrada por viagem">{trips.map((trip) => <div className="trip-bar-column" key={trip.id}><div className="bar-value">{preciseNf.format(trip.distanceKm)} km</div><div className="bar-track"><i style={{ height: `${trip.distanceKm / max * 100}%` }} /></div><small>{new Date(trip.startedAt).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}</small></div>)}</div>
}

function TripRow({ trip }: { trip: Trip }) {
  return <div className="trip-row"><div><strong>{preciseNf.format(trip.distanceKm)} km</strong><small>{dateTimeFormat.format(new Date(trip.startedAt))}</small></div><div><span>Média</span><strong>{nf.format(trip.averageSpeedKph)} km/h</strong></div><div><span>Máxima registrada</span><strong>{nf.format(trip.maxSpeedKph)} km/h</strong></div></div>
}

function LineChart({ data }: { data: Telemetry[] }) {
  const width = 720
  const height = 230
  const plot = (values: number[], min: number, max: number) => values.map((value, index) => {
    const x = 18 + (index / Math.max(values.length - 1, 1)) * (width - 36)
    const y = 18 + (1 - (value - min) / Math.max(max - min, 1)) * (height - 42)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
  if (data.length < 2) return <div className="chart-empty">São necessárias pelo menos duas leituras válidas para desenhar o histórico.</div>
  const speedMax = Math.max(10, ...data.map((item) => item.speedKph))
  const tempMin = Math.min(...data.map((item) => item.engineTempC)) - 5
  const tempMax = Math.max(tempMin + 10, ...data.map((item) => item.engineTempC)) + 5
  return <svg className="line-chart" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="Histórico real de velocidade e temperatura do líquido de arrefecimento"><defs><linearGradient id="speedFill" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#c78cff" stopOpacity=".24" /><stop offset="1" stopColor="#c78cff" stopOpacity="0" /></linearGradient></defs>{[45, 90, 135, 180].map((y) => <line key={y} x1="18" x2={width - 18} y1={y} y2={y} />)}<polygon points={`18,${height - 24} ${plot(data.map((item) => item.speedKph), 0, speedMax)} ${width - 18},${height - 24}`} fill="url(#speedFill)" /><polyline points={plot(data.map((item) => item.speedKph), 0, speedMax)} className="speed-line" /><polyline points={plot(data.map((item) => item.engineTempC), tempMin, tempMax)} className="temp-line" /></svg>
}

function HealthRing({ value }: { value: number }) {
  return <div className="health-ring" style={{ '--health': `${value * 3.6}deg` } as React.CSSProperties}><div><strong>{value}</strong><span>/ 100</span></div></div>
}

function ConnectionLabel({ connection }: { connection: Connection }) {
  const label = connection === 'live' ? 'API conectada' : connection === 'connecting' ? 'Conectando à API' : 'API reconectando'
  return <span className={`connection-label ${connection}`}><i />{label}</span>
}

function telemetryOriginLabel(source: Telemetry['source'] | undefined) {
  if (source === 'SIMULATED_OBD' || source === 'SIMULATOR') return 'ECU/OBD simulada'
  if (source === 'MOBILE') return 'ECU/OBD via mobile'
  if (source === 'API') return 'integração via API'
  return 'aguardando telemetria'
}

function Empty({ icon, title, text }: { icon: string; title: string; text: string }) {
  return <div className="empty-state"><span>{icon}</span><div><strong>{title}</strong><p>{text}</p></div></div>
}

function Brand() {
  return <div className="brand"><span className="brand-mark">E</span><span>enhara<small>DRIVE WITH CLARITY</small></span></div>
}

function LoadingScreen() {
  return <main className="loading-screen"><Brand /><div className="loader" /><p>Conectando à central veicular…</p></main>
}

function readStoredCards(vehicleId: string): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(cardStorageKey(vehicleId)) ?? '[]')
    return Array.isArray(parsed) && parsed.every((item) => typeof item === 'string') ? parsed : []
  } catch {
    return []
  }
}

function loadCardSelection(snapshot: SimulatedObdSnapshot, vehicleId: string) {
  const supported = supportedMetricKeys(snapshot)
  const stored = readStoredCards(vehicleId).filter((key) => supported.includes(key))
  const defaults = DEFAULT_CARD_KEYS.filter((key) => supported.includes(key))
  return stored.length ? stored : defaults.length ? defaults : supported.slice(0, 5)
}

function cardStorageKey(vehicleId: string) {
  return `enhara.dashboard.cards.${vehicleId}`
}

function scenarioLabel(scenario: SimulationScenario) {
  switch (scenario) {
    case 'NORMAL': return 'Normal'
    case 'OVERHEAT': return 'Superaquecimento'
    case 'LOW_VOLTAGE': return 'Tensão baixa'
    case 'MISFIRE': return 'Falha de combustão'
    case 'LOW_BATTERY': return 'Tensão baixa (legado)'
  }
}

function dtcSimpleExplanation(code: string) {
  const explanations: Record<string, string> = {
    P0217: 'A ECU registrou temperatura do motor acima da faixa esperada.',
    P0562: 'A ECU registrou tensão elétrica do sistema abaixo da faixa esperada.',
    P0300: 'A ECU identificou falhas de combustão distribuídas entre os cilindros.',
  }
  return explanations[code] ?? 'A ECU registrou um código técnico que requer interpretação conforme o veículo.'
}

function dtcStatusLabel(status: SimulatedObdDtc['statuses'][number]) {
  const labels = { PENDING: 'Pendente', CONFIRMED: 'Confirmado', PERMANENT: 'Permanente' }
  return labels[status]
}

function readinessLabel(monitor: string) {
  const labels: Record<string, string> = { MISFIRE: 'Falha de combustão', FUEL_SYSTEM: 'Sistema de combustível', COMPREHENSIVE_COMPONENT: 'Componentes abrangentes', CATALYST: 'Catalisador', OXYGEN_SENSOR: 'Sensor de oxigênio' }
  return labels[monitor] ?? monitor
}

function readinessStatusLabel(status: string) {
  const labels: Record<string, string> = { READY: 'Pronto', NOT_READY: 'Ainda não concluído', NOT_SUPPORTED: 'Não suportado' }
  return labels[status] ?? status
}

interface ActivityItem { id: string; at: string; kind: string; title: string; detail: string; tone: 'green' | 'amber' | 'blue' }

function buildRecentActivity(trips: Trip[], notes: VehicleNote[], dtcs: SimulatedObdDtc[]): ActivityItem[] {
  return [
    ...trips.filter((trip) => trip.endedAt).map((trip): ActivityItem => ({ id: `trip-${trip.id}`, at: trip.endedAt!, kind: 'Viagem concluída', title: `${preciseNf.format(trip.distanceKm)} km monitorados`, detail: `Máxima registrada de ${nf.format(trip.maxSpeedKph)} km/h.`, tone: 'green' })),
    ...notes.map((note): ActivityItem => ({ id: `note-${note.id}`, at: note.updatedAt, kind: 'Nota do usuário', title: note.title, detail: note.status === 'COMPLETED' ? 'Marcada como concluída.' : note.overdue ? 'Lembrete em atraso.' : 'Nota atualizada.', tone: 'blue' })),
    ...dtcs.map((dtc): ActivityItem => ({ id: `dtc-${dtc.code}-${dtc.lastDetectedAt}`, at: dtc.lastDetectedAt, kind: 'DTC da ECU', title: dtc.code, detail: dtcSimpleExplanation(dtc.code), tone: 'amber' })),
  ].sort((first, second) => new Date(second.at).getTime() - new Date(first.at).getTime()).slice(0, 8)
}

export default App
