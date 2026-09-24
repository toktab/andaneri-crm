import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { fromLocalInput } from '../lib/format'
import { useI18n } from '../i18n'
import { BusinessSelect, type BusinessPick } from './BusinessSelect'
import { Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

/** The notes-app replacement: type first, decide where it belongs later. */
export function NoteDialog({ open, onClose, business }: { open: boolean; onClose: () => void; business?: BusinessPick | null }) {
  const { t } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [pick, setPick] = useState<BusinessPick | null>(null)
  const [remindAt, setRemindAt] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setBody('')
      setPick(business ?? null)
      setRemindAt('')
    }
  }, [open, business])

  const save = async () => {
    if (!body.trim()) return
    setSaving(true)
    try {
      await api.post('/notes', { body, businessId: pick?.id ?? null, remindAt: fromLocalInput(remindAt) })
      queryClient.invalidateQueries({ queryKey: ['notes'] })
      toast.ok(t('common.saved'))
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('notes.title')}
      footer={
        <>
          <span className="mr-auto text-xs text-muted">{t('notes.private')}</span>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || !body.trim()} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <textarea
          autoFocus
          rows={5}
          className="input text-base"
          placeholder={t('notes.placeholder')}
          value={body}
          onChange={(event) => setBody(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) void save()
          }}
        />
        <Field label={t('notes.attach')}>
          <BusinessSelect value={pick} onChange={setPick} />
        </Field>
        <Field label={t('notes.remind')}>
          <input type="datetime-local" className="input" value={remindAt} onChange={(event) => setRemindAt(event.target.value)} />
        </Field>
      </div>
    </Modal>
  )
}
