import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import type { ContactChannel, ContactDto } from '../lib/types'
import { useRefreshWork } from '../lib/queries'
import { useI18n } from '../i18n'
import { Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

const CHANNELS: ContactChannel[] = ['ANY', 'PHONE', 'WHATSAPP', 'VIBER', 'EMAIL', 'IN_PERSON']

export function ContactDialog({ open, onClose, businessId, contact }: {
  open: boolean; onClose: () => void; businessId: number; contact?: ContactDto | null
}) {
  const { t } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const [form, setForm] = useState({ name: '', roleTitle: '', phone: '', email: '', preferredChannel: 'ANY' as ContactChannel, decisionMaker: false, notes: '' })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setForm({
        name: contact?.name ?? '', roleTitle: contact?.roleTitle ?? '', phone: contact?.phone ?? '', email: contact?.email ?? '',
        preferredChannel: contact?.preferredChannel ?? 'ANY', decisionMaker: contact?.decisionMaker ?? false, notes: contact?.notes ?? '',
      })
    }
  }, [open, contact])

  const run = async (action: () => Promise<unknown>) => {
    setSaving(true)
    try {
      await action()
      toast.ok(t('common.saved'))
      refresh(businessId)
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  const save = () => run(() => (contact
    ? api.put(`/businesses/${businessId}/contacts/${contact.id}`, form)
    : api.post(`/businesses/${businessId}/contacts`, form)))

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={contact ? contact.name : t('business.addContact')}
      footer={
        <>
          {contact && (
            <button type="button" className="btn-ghost mr-auto text-rose-700" disabled={saving} onClick={() => void run(() => api.del(`/businesses/${businessId}/contacts/${contact.id}`))}>
              {t('common.delete')}
            </button>
          )}
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || !form.name.trim()} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={`${t('common.name')} *`}>
            <input className="input" autoFocus value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label={t('business.roleTitle')}>
            <input className="input" list="role-options" value={form.roleTitle} onChange={(e) => setForm({ ...form, roleTitle: e.target.value })} />
            <datalist id="role-options">
              {['მფლობელი', 'დირექტორი', 'მენეჯერი', 'ბარ მენეჯერი', 'ბარმენი', 'შეფი', 'Owner', 'Manager', 'Bar manager', 'Bartender'].map((r) => <option key={r} value={r} />)}
            </datalist>
          </Field>
          <Field label={t('common.phone')}>
            <input className="input" inputMode="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </Field>
          <Field label={t('common.email')}>
            <input className="input" inputMode="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </Field>
          <Field label={t('business.preferredChannel')}>
            <select className="input" value={form.preferredChannel} onChange={(e) => setForm({ ...form, preferredChannel: e.target.value as ContactChannel })}>
              {CHANNELS.map((c) => <option key={c} value={c}>{t(`channel.${c}`)}</option>)}
            </select>
          </Field>
          <label className="flex items-center gap-2 self-end pb-2 text-sm">
            <input type="checkbox" className="size-4 accent-brand-600" checked={form.decisionMaker} onChange={(e) => setForm({ ...form, decisionMaker: e.target.checked })} />
            {t('business.decisionMaker')}
          </label>
        </div>
        <Field label={t('common.notes')}>
          <textarea rows={2} className="input" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}
