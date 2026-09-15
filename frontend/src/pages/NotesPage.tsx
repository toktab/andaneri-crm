import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Bell, Check, MessageSquare, RotateCcw, Store, X } from 'lucide-react'
import { api } from '../lib/api'
import { useMode } from '../lib/mode'
import { keys, useNotes, useRefreshWork } from '../lib/queries'
import { fmtDateTime } from '../lib/format'
import type { NoteDto } from '../lib/types'
import { useI18n } from '../i18n'
import { BusinessSelect } from '../components/BusinessSelect'
import { EmptyState, Loading, Modal, PageHeader } from '../components/ui'
import { useToast } from '../components/Toast'

/** Everything jotted down in a hurry. Type here, attach to a business later, save it to the history when it matters. */
export function NotesPage() {
  const { t, lang } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const refresh = useRefreshWork()
  const { canEdit } = useMode()
  const notes = useNotes()
  const [body, setBody] = useState('')
  const [showDone, setShowDone] = useState(false)
  const [attaching, setAttaching] = useState<NoteDto | null>(null)
  const editable = canEdit()

  const reload = () => queryClient.invalidateQueries({ queryKey: keys.notes })
  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      reload()
    } catch (error) {
      toast.error(error)
    }
  }
  const update = (note: NoteDto, patch: Partial<NoteDto>) => run(() => api.put(`/notes/${note.id}`, {
    body: patch.body ?? note.body,
    businessId: patch.businessId !== undefined ? patch.businessId : note.businessId,
    remindAt: note.remindAt,
    done: patch.done ?? note.done,
  }))

  const add = async () => {
    if (!body.trim()) return
    await run(() => api.post('/notes', { body }))
    setBody('')
  }

  const list = (notes.data ?? []).filter((n) => showDone || !n.done)

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={t('notes.title')}
        subtitle={t('notes.private')}
        actions={
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="size-4 accent-brand-600" checked={showDone} onChange={(e) => setShowDone(e.target.checked)} /> {t('notes.showDone')}
          </label>
        }
      />
      {editable && (
        <div className="card mb-4 p-3">
          <textarea
            rows={3}
            className="input text-base"
            placeholder={t('notes.placeholder')}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) void add() }}
          />
          <div className="mt-2 flex justify-end">
            <button type="button" className="btn-primary" disabled={!body.trim()} onClick={() => void add()}>{t('common.save')}</button>
          </div>
        </div>
      )}
      {notes.isLoading ? <Loading /> : list.length === 0 ? <div className="card"><EmptyState title={t('notes.empty')} /></div> : (
        <ul className="space-y-2">
          {list.map((note) => (
            <li key={note.id} className={`card p-3 ${note.done ? 'opacity-60' : ''}`}>
              <p className={`whitespace-pre-line text-sm ${note.done ? 'line-through' : ''}`}>{note.body}</p>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
                <span>{fmtDateTime(note.createdAt, lang)}</span>
                {note.remindAt && <span className="inline-flex items-center gap-1 text-amber-700"><Bell className="size-3" /> {fmtDateTime(note.remindAt, lang)}</span>}
                {note.businessId && <Link to={`/businesses/${note.businessId}`} className="inline-flex items-center gap-1 text-brand-700"><Store className="size-3" /> {note.businessName}</Link>}
                {editable && (
                  <div className="ml-auto flex gap-1">
                    {!note.done && (
                      <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => setAttaching(note)}>
                        <Store className="size-3.5" /> {t('notes.attach')}
                      </button>
                    )}
                    {note.businessId && !note.done && (
                      <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => void run(async () => { await api.post(`/notes/${note.id}/to-comment`, {}); refresh(note.businessId) })}>
                        <MessageSquare className="size-3.5" /> {t('notes.toComment')}
                      </button>
                    )}
                    <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => void update(note, { done: !note.done })}>
                      {note.done ? <RotateCcw className="size-3.5" /> : <Check className="size-3.5" />} {note.done ? t('common.reopen') : t('notes.done')}
                    </button>
                    <button type="button" className="btn-ghost px-2 py-1 text-xs text-rose-700" onClick={() => void run(() => api.del(`/notes/${note.id}`))} aria-label={t('common.delete')}>
                      <X className="size-3.5" />
                    </button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Modal open={Boolean(attaching)} onClose={() => setAttaching(null)} title={t('notes.attach')}>
        <BusinessSelect autoFocus value={null} onChange={(pick) => {
          if (pick && attaching) void update(attaching, { businessId: pick.id })
          setAttaching(null)
        }} />
      </Modal>
    </div>
  )
}
