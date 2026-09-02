import { useState } from 'react'
import type { VehicleNote, VehicleNoteCategory, VehicleNoteInput } from './types'

interface NotesPanelProps {
  notes: VehicleNote[]
  busy: boolean
  onCreate: (input: VehicleNoteInput) => Promise<void>
  onUpdate: (noteId: string, input: VehicleNoteInput) => Promise<void>
  onComplete: (noteId: string) => Promise<void>
  onReopen: (noteId: string) => Promise<void>
  onDelete: (noteId: string) => Promise<void>
}

const categoryLabels: Record<VehicleNoteCategory, string> = {
  MAINTENANCE: 'Manutenção',
  DOCUMENTATION: 'Documentação',
  GENERAL: 'Geral',
}

const emptyInput: VehicleNoteInput = { title: '', description: '', category: 'GENERAL', dueAt: null }

export function NotesPanel({ notes, busy, onCreate, onUpdate, onComplete, onReopen, onDelete }: NotesPanelProps) {
  const [editing, setEditing] = useState<VehicleNote | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [input, setInput] = useState<VehicleNoteInput>(emptyInput)
  const [dueAtLocal, setDueAtLocal] = useState('')

  function closeForm() {
    setEditing(null)
    setInput(emptyInput)
    setDueAtLocal('')
    setFormOpen(false)
  }

  function beginEdit(note: VehicleNote) {
    setEditing(note)
    setInput({ title: note.title, description: note.description, category: note.category, dueAt: note.dueAt })
    setDueAtLocal(toLocalDateTime(note.dueAt))
    setFormOpen(true)
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const payload = { ...input, dueAt: dueAtLocal ? new Date(dueAtLocal).toISOString() : null }
    if (editing) await onUpdate(editing.id, payload)
    else await onCreate(payload)
    closeForm()
  }

  async function deleteNote(noteId: string) {
    if (window.confirm('Excluir esta nota? Essa ação não pode ser desfeita.')) await onDelete(noteId)
  }

  return (
    <article className="panel notes-panel">
      <div className="section-heading">
        <div><span className="eyebrow">DADOS DO USUÁRIO</span><h3>Notas e lembretes</h3></div>
        <button className="secondary-button compact" onClick={() => setFormOpen((open) => !open)}>
          {formOpen ? 'Fechar' : '+ Nova nota'}
        </button>
      </div>

      {formOpen && (
        <form className="note-form" onSubmit={submit}>
          <label>Título<input required maxLength={120} value={input.title} onChange={(event) => setInput({ ...input, title: event.target.value })} /></label>
          <label className="wide">Descrição<textarea required maxLength={1000} rows={3} value={input.description} onChange={(event) => setInput({ ...input, description: event.target.value })} /></label>
          <label>Categoria<select value={input.category} onChange={(event) => setInput({ ...input, category: event.target.value as VehicleNoteCategory })}>
            {Object.entries(categoryLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select></label>
          <label>Lembrar em<input type="datetime-local" value={dueAtLocal} onChange={(event) => setDueAtLocal(event.target.value)} /></label>
          <div className="note-form-actions"><button type="button" className="text-button" onClick={closeForm}>Cancelar</button><button className="primary-button" disabled={busy}>{editing ? 'Salvar alterações' : 'Adicionar nota'}</button></div>
        </form>
      )}

      {!notes.length && !formOpen && <EmptyNotes />}
      {!!notes.length && <div className="note-list">{notes.map((note) => (
        <div className={`note-row ${note.status === 'COMPLETED' ? 'completed' : ''}`} key={note.id}>
          <button className="note-check" onClick={() => note.status === 'OPEN' ? onComplete(note.id) : onReopen(note.id)} aria-label={note.status === 'OPEN' ? 'Concluir nota' : 'Reabrir nota'} disabled={busy}>{note.status === 'COMPLETED' ? '✓' : ''}</button>
          <div className="note-copy">
            <div className="note-meta"><span>{categoryLabels[note.category]}</span><span>Informado pelo usuário</span><strong className={`note-status ${noteState(note).className}`}>{noteState(note).label}</strong></div>
            <h4>{note.title}</h4><p>{note.description}</p>
            <small>{note.dueAt ? `Lembrete: ${formatDateTime(note.dueAt)}` : `Atualizada em ${formatDateTime(note.updatedAt)}`}</small>
          </div>
          <div className="row-actions"><button onClick={() => beginEdit(note)} disabled={busy}>Editar</button><button className="danger-text" onClick={() => deleteNote(note.id)} disabled={busy}>Excluir</button></div>
        </div>
      ))}</div>}
    </article>
  )
}

function EmptyNotes() {
  return <div className="empty-state small"><span>✎</span><div><strong>Nenhuma nota cadastrada</strong><p>Adicione lembretes reais para este veículo.</p></div></div>
}

function noteState(note: VehicleNote) {
  if (note.status === 'COMPLETED') return { label: 'CONCLUÍDA', className: 'done' }
  if (note.overdue) return { label: 'ATRASADA', className: 'late' }
  return { label: 'PENDENTE', className: 'pending' }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function toLocalDateTime(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}
