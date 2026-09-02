import { useMemo, useState } from 'react'
import type {
  FipeOption,
  FipeSelection,
  FipeVehicleType,
  Vehicle,
  VehiclePhoto,
  VehicleProfile,
  VehicleProfileField,
  VehicleProfileKey,
} from './types'

const editableFields: VehicleProfileKey[] = [
  'VIN', 'MANUFACTURER', 'MODEL', 'MODEL_YEAR', 'VERSION', 'ENGINE', 'FUEL_TYPE', 'TRANSMISSION', 'FIPE_CODE',
]

const profileFields: VehicleProfileKey[] = [
  'MANUFACTURER', 'MODEL', 'MODEL_YEAR', 'VERSION', 'ENGINE', 'FUEL_TYPE', 'TRANSMISSION', 'VIN',
  'FIPE_CODE', 'FIPE_VALUE', 'FIPE_REFERENCE_MONTH',
]

const labels: Record<VehicleProfileKey, string> = {
  VIN: 'VIN',
  MANUFACTURER: 'Fabricante',
  MODEL: 'Modelo',
  MODEL_YEAR: 'Ano do modelo',
  VERSION: 'Versão',
  ENGINE: 'Motorização',
  FUEL_TYPE: 'Combustível',
  TRANSMISSION: 'Transmissão',
  FIPE_CODE: 'Código FIPE',
  FIPE_VALUE: 'Valor FIPE',
  FIPE_REFERENCE_MONTH: 'Referência FIPE',
}

const sourceLabels = {
  VEHICLE_REGISTRATION: 'Cadastro persistido',
  ECU_OBD: 'ECU/OBD real',
  BRASILAPI_FIPE: 'BrasilAPI / FIPE',
  NHTSA_VPIC: 'NHTSA vPIC',
  USER_PROVIDED: 'Informado pelo usuário',
} as const

interface Props {
  vehicle: Vehicle
  profile: VehicleProfile
  photos: VehiclePhoto[]
  busy: boolean
  notesPanel: React.ReactNode
  photoUrl: (photo: VehiclePhoto) => string
  onUpdate: (fields: Partial<Record<VehicleProfileKey, string>>) => Promise<void>
  onConfirm: (field: VehicleProfileKey) => Promise<void>
  onEnrich: (fipeCode: string, selection: FipeSelection | undefined, forceRefresh: boolean) => Promise<void>
  onListFipeBrands: (vehicleType: FipeVehicleType) => Promise<FipeOption[]>
  onListFipeModels: (vehicleType: FipeVehicleType, brandCode: string) => Promise<FipeOption[]>
  onListFipeYears: (vehicleType: FipeVehicleType, brandCode: string, modelCode: string) => Promise<FipeOption[]>
  onUploadPhoto: (file: File, caption: string) => Promise<void>
  onDeletePhoto: (photoId: string) => Promise<void>
}

export function VehicleProfilePage({ vehicle, profile, photos, busy, notesPanel, photoUrl, onUpdate, onConfirm,
  onEnrich, onListFipeBrands, onListFipeModels, onListFipeYears, onUploadPhoto, onDeletePhoto }: Props) {
  const byKey = useMemo(() => new Map(profile.fields.map((field) => [field.key, field])), [profile.fields])
  const lastExternalUpdate = useMemo(() => profile.providers
    .map((provider) => provider.dataFetchedAt)
    .filter((value): value is string => Boolean(value))
    .sort((first, second) => Date.parse(second) - Date.parse(first))[0], [profile.providers])
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<Partial<Record<VehicleProfileKey, string>>>(() =>
    Object.fromEntries(profile.fields.map((field) => [field.key, field.value])))
  const [dirty, setDirty] = useState<VehicleProfileKey[]>([])
  const [fipeCode, setFipeCode] = useState(() =>
    profile.fields.find((field) => field.key === 'FIPE_CODE')?.value ?? '')
  const [guidedFipeOpen, setGuidedFipeOpen] = useState(false)
  const [fipeVehicleType, setFipeVehicleType] = useState<FipeVehicleType>('CAR')
  const [brands, setBrands] = useState<FipeOption[]>([])
  const [models, setModels] = useState<FipeOption[]>([])
  const [years, setYears] = useState<FipeOption[]>([])
  const [brandCode, setBrandCode] = useState('')
  const [modelCode, setModelCode] = useState('')
  const [yearCode, setYearCode] = useState('')
  const [catalogBusy, setCatalogBusy] = useState(false)
  const [catalogError, setCatalogError] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)
  const [caption, setCaption] = useState('')

  function beginEditing() {
    setDraft(Object.fromEntries(profile.fields.map((field) => [field.key, field.value])))
    setDirty([])
    setEditing(true)
  }

  function change(key: VehicleProfileKey, value: string) {
    setDraft((current) => ({ ...current, [key]: value }))
    setDirty((current) => current.includes(key) ? current : [...current, key])
  }

  async function save(event: React.FormEvent) {
    event.preventDefault()
    const fields = Object.fromEntries(dirty
      .map((key) => [key, draft[key]?.trim()])
      .filter((entry): entry is [VehicleProfileKey, string] => Boolean(entry[1])))
    if (!Object.keys(fields).length) return
    await onUpdate(fields)
    setEditing(false)
    setDirty([])
  }

  async function submitPhoto(event: React.FormEvent) {
    event.preventDefault()
    if (!photo) return
    const form = event.currentTarget
    await onUploadPhoto(photo, caption)
    setPhoto(null)
    setCaption('')
    const input = form.querySelector<HTMLInputElement>('input[type=file]')
    if (input) input.value = ''
  }

  async function loadBrands(vehicleType = fipeVehicleType) {
    setCatalogBusy(true)
    setCatalogError('')
    try {
      setBrands(await onListFipeBrands(vehicleType))
      setModels([])
      setYears([])
      setBrandCode('')
      setModelCode('')
      setYearCode('')
      setGuidedFipeOpen(true)
    } catch (reason) {
      setCatalogError(errorMessage(reason))
    } finally {
      setCatalogBusy(false)
    }
  }

  async function chooseBrand(code: string) {
    setBrandCode(code)
    setModelCode('')
    setYearCode('')
    setModels([])
    setYears([])
    if (!code) return
    setCatalogBusy(true)
    setCatalogError('')
    try {
      setModels(await onListFipeModels(fipeVehicleType, code))
    } catch (reason) {
      setCatalogError(errorMessage(reason))
    } finally {
      setCatalogBusy(false)
    }
  }

  async function chooseModel(code: string) {
    setModelCode(code)
    setYearCode('')
    setYears([])
    if (!code) return
    setCatalogBusy(true)
    setCatalogError('')
    try {
      setYears(await onListFipeYears(fipeVehicleType, brandCode, code))
    } catch (reason) {
      setCatalogError(errorMessage(reason))
    } finally {
      setCatalogBusy(false)
    }
  }

  async function enrichGuided(forceRefresh: boolean) {
    if (!brandCode || !modelCode || !yearCode) return
    await onEnrich('', { vehicleType: fipeVehicleType, brandCode, modelCode, yearCode }, forceRefresh)
  }

  return <>
    <section className="vehicle-profile-hero panel">
      <div>
        <div className="connection-row"><span className="state-pill connected"><i />Perfil persistido</span><span className="origin-pill safe">Sem dados do simulador</span></div>
        <span className="eyebrow">MEU CARRO</span>
        <h2>{value(byKey, 'MANUFACTURER')} {value(byKey, 'MODEL')}</h2>
        <p>{[value(byKey, 'VERSION'), value(byKey, 'MODEL_YEAR')].filter(Boolean).join(' · ') || vehicle.name}</p>
        <small>Perfil atualizado em {formatDate(profile.updatedAt)} · Última atualização externa: {lastExternalUpdate ? formatDate(lastExternalUpdate) : 'não realizada'}.</small>
      </div>
      <button className="secondary-button" onClick={() => editing ? setEditing(false) : beginEditing()}>{editing ? 'Fechar edição' : 'Corrigir dados'}</button>
    </section>

    <section className="vehicle-profile-grid">
      <article className="panel vehicle-data-card">
        <div className="section-heading"><div><span className="eyebrow">DADOS COM PROVENANCE</span><h3>Identificação e especificações</h3></div></div>
        <p className="section-intro">Campos ausentes permanecem ausentes. O Enhara não estima potência, torque, marchas, tanque, peso ou redline.</p>
        <div className="profile-field-grid">
          {profileFields.map((key) => <ProfileFieldRow key={key} field={byKey.get(key)} fieldKey={key} busy={busy} onConfirm={onConfirm} />)}
        </div>
      </article>

      <article className="panel enrichment-card">
        <div className="section-heading"><div><span className="eyebrow">FONTES EXTERNAS REAIS</span><h3>Consultar dados</h3></div></div>
        <p className="section-intro">O VIN válido consulta o NHTSA vPIC. Para a FIPE, escolha o veículo pelo catálogo oficial ou informe um código exato.</p>
        <button className="secondary-button guided-fipe-trigger" disabled={catalogBusy}
          onClick={() => guidedFipeOpen ? setGuidedFipeOpen(false) : void loadBrands()}>
          {catalogBusy ? 'Consultando BrasilAPI…' : guidedFipeOpen ? 'Fechar identificação guiada' : 'Identificar pela FIPE'}
        </button>
        {guidedFipeOpen && <div className="guided-fipe" aria-label="Identificação guiada pela FIPE">
          <label>Tipo de veículo<select value={fipeVehicleType} onChange={(event) => {
            const next = event.target.value as FipeVehicleType
            setFipeVehicleType(next)
            void loadBrands(next)
          }}><option value="CAR">Carro</option><option value="MOTORCYCLE">Moto</option><option value="TRUCK">Caminhão</option></select></label>
          <label>Marca<select value={brandCode} disabled={catalogBusy || !brands.length}
            onChange={(event) => void chooseBrand(event.target.value)}><option value="">Selecione a marca</option>{brands.map((item) => <option key={item.code} value={item.code}>{item.label}</option>)}</select></label>
          <label>Modelo / versão<select value={modelCode} disabled={catalogBusy || !models.length}
            onChange={(event) => void chooseModel(event.target.value)}><option value="">Selecione o modelo</option>{models.map((item) => <option key={item.code} value={item.code}>{item.label}</option>)}</select></label>
          <label>Ano / combustível<select value={yearCode} disabled={catalogBusy || !years.length}
            onChange={(event) => setYearCode(event.target.value)}><option value="">Selecione o ano e combustível</option>{years.map((item) => <option key={item.code} value={item.code}>{item.label}</option>)}</select></label>
          <button className="primary-button" disabled={busy || catalogBusy || !yearCode}
            onClick={() => void enrichGuided(false)}>Usar identificação FIPE</button>
        </div>}
        {catalogError && <div className="catalog-error"><strong>Catálogo FIPE indisponível.</strong><span>{catalogError} Você ainda pode preencher os dados manualmente.</span></div>}
        <div className="fipe-code-fallback"><span>ou use o código conhecido</span><label className="profile-input">Código FIPE (opcional)<input placeholder="000000-0" pattern="[0-9]{6}-[0-9]" value={fipeCode} onChange={(event) => setFipeCode(event.target.value)} /></label></div>
        <div className="enrichment-actions">
          <button className="primary-button" disabled={busy || Boolean(fipeCode && !/^[0-9]{6}-[0-9]$/.test(fipeCode))} onClick={() => onEnrich(fipeCode, undefined, false)}>Consultar VIN / código</button>
          <button className="text-button" disabled={busy || Boolean(fipeCode && !/^[0-9]{6}-[0-9]$/.test(fipeCode))} onClick={() => onEnrich(fipeCode, undefined, true)}>Atualizar agora</button>
        </div>
        <div className="provider-list">
          {!profile.providers.length && <div className="provider-empty">Nenhuma consulta externa realizada.</div>}
          {profile.providers.map((provider) => <div className={`provider-row ${provider.state.toLowerCase()}`} key={provider.provider}>
            <div><strong>{providerName(provider.provider)}</strong><span>{providerState(provider.state)}</span></div>
            <p>{provider.message}</p>
            <small>Verificado em {formatDate(provider.checkedAt)}{provider.dataFetchedAt ? ` · dados de ${formatDate(provider.dataFetchedAt)}` : ''}</small>
          </div>)}
        </div>
      </article>

      {editing && <article className="panel profile-editor">
        <div className="section-heading"><div><span className="eyebrow">CORREÇÃO DO USUÁRIO</span><h3>Editar cadastro</h3></div></div>
        <p className="section-intro">Ao salvar, apenas os campos alterados passam a ter origem “Informado pelo usuário”.</p>
        <form onSubmit={save}>
          {editableFields.map((key) => <label key={key}>{labels[key]}<input maxLength={512} pattern={key === 'VIN' ? '[A-HJ-NPR-Za-hj-npr-z0-9]{17}' : key === 'FIPE_CODE' ? '[0-9]{6}-[0-9]' : undefined} value={draft[key] ?? ''} onChange={(event) => change(key, event.target.value)} /></label>)}
          <div className="profile-editor-actions"><button type="button" className="text-button" onClick={() => setEditing(false)}>Cancelar</button><button className="primary-button" disabled={busy || !dirty.length}>Salvar alterações</button></div>
        </form>
      </article>}

      <article className="panel photo-card">
        <div className="section-heading"><div><span className="eyebrow">DADOS DO USUÁRIO</span><h3>Fotos do veículo</h3></div><span className="source-badge">Persistidas</span></div>
        {!photos.length ? <div className="vehicle-photo-empty"><span>▧</span><strong>Nenhuma foto cadastrada</strong><p>O Enhara não usa imagens fictícias para preencher esta área.</p></div> : <div className="vehicle-photo-grid">{photos.map((item) => <figure key={item.id}><img src={photoUrl(item)} alt={item.caption || `Foto de ${vehicle.name}`} /><figcaption><div><strong>{item.caption || item.originalFilename}</strong><small>Usuário · {formatDate(item.createdAt)}</small></div><button className="danger-text" onClick={() => window.confirm('Excluir esta foto?') && onDeletePhoto(item.id)} disabled={busy}>Excluir</button></figcaption></figure>)}</div>}
        <form className="photo-form" onSubmit={submitPhoto}>
          <label>Foto JPEG ou PNG<input required type="file" accept="image/jpeg,image/png" onChange={(event) => setPhoto(event.target.files?.[0] ?? null)} /></label>
          <label>Legenda<input maxLength={240} value={caption} onChange={(event) => setCaption(event.target.value)} placeholder="Opcional" /></label>
          <button className="secondary-button" disabled={busy || !photo}>Adicionar foto</button>
        </form>
      </article>

      <div className="vehicle-notes-slot">{notesPanel}</div>
    </section>
  </>
}

function ProfileFieldRow({ field, fieldKey, busy, onConfirm }: { field?: VehicleProfileField; fieldKey: VehicleProfileKey; busy: boolean; onConfirm: (key: VehicleProfileKey) => Promise<void> }) {
  if (!field) return <div className="profile-field missing"><span>{labels[fieldKey]}</span><strong>Não informado</strong><small>Nenhuma fonte confiável retornou este dado.</small></div>
  const canConfirm = !field.provenance.confirmedAt && ['ECU_OBD', 'BRASILAPI_FIPE', 'NHTSA_VPIC'].includes(field.provenance.source)
  return <div className={`profile-field ${field.provenance.stale ? 'stale' : ''}`}>
    <span>{labels[fieldKey]}</span><strong>{field.value}</strong>
    <small>{sourceLabels[field.provenance.source]} · {formatDate(field.provenance.observedAt ?? field.provenance.retrievedAt)}{field.provenance.cached ? field.provenance.stale ? ' · cache expirado' : ' · cache' : ''}</small>
    {field.provenance.confirmedAt && <em>Confirmado pelo usuário em {formatDate(field.provenance.confirmedAt)}</em>}
    {canConfirm && <button className="field-confirm" disabled={busy} onClick={() => onConfirm(fieldKey)}>Confirmar dado</button>}
  </div>
}

function value(fields: Map<VehicleProfileKey, VehicleProfileField>, key: VehicleProfileKey) {
  return fields.get(key)?.value ?? ''
}

function providerName(provider: string) {
  return provider === 'NHTSA_VPIC' ? 'NHTSA vPIC' : provider === 'BRASILAPI_FIPE' ? 'BrasilAPI / FIPE' : provider
}

function providerState(state: string) {
  const states: Record<string, string> = { LIVE: 'Resposta atual', CACHE_FRESH: 'Cache válido', CACHE_STALE: 'Último dado armazenado', UNAVAILABLE: 'Indisponível', CONFLICT: 'Revisão necessária', NOT_REQUESTED: 'Não consultado' }
  return states[state] ?? state
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function errorMessage(reason: unknown) {
  return reason instanceof Error ? reason.message : 'Não foi possível consultar o provider.'
}
