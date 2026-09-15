import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { TriangleAlert } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import type { BusinessDetail, DuplicateDto } from '../lib/types'
import { useLookups, useRefreshWork } from '../lib/queries'
import { useI18n } from '../i18n'
import { Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

const EMPTY = { name: '', typeId: '', phone: '', address: '', district: '', city: 'თბილისი', mapsUrl: '', contactName: '', contactRole: '', contactPhone: '' }

/**
 * Find a place on Google Maps, add it in ten seconds: name, address, phone. Everything else can wait.
 * Warns while typing if the phone, name or ID code is already in the CRM.
 */
export function QuickAddDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, name: nameOf } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const refresh = useRefreshWork()
  const queryClient = useQueryClient()
  const lookups = useLookups()
  const [form, setForm] = useState(EMPTY)
  const [duplicates, setDuplicates] = useState<DuplicateDto[]>([])
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setForm(EMPTY)
      setDuplicates([])
    }
  }, [open])

  useEffect(() => {
    if (!open || (!form.name.trim() && form.phone.replace(/\D/g, '').length < 7)) {
      setDuplicates([])
      return
    }
    const handle = setTimeout(() => {
      api.get<DuplicateDto[]>('/businesses/duplicates', { name: form.name, phone: form.phone, address: form.address })
        .then(setDuplicates)
        .catch(() => setDuplicates([]))
    }, 400)
    return () => clearTimeout(handle)
  }, [open, form.name, form.phone, form.address])

  const set = (key: keyof typeof EMPTY) => (event: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: event.target.value }))

  const save = async (force: boolean) => {
    if (!form.name.trim()) return
    setSaving(true)
    try {
      const created = await api.post<BusinessDetail>('/businesses', {
        name: form.name,
        typeId: form.typeId ? Number(form.typeId) : null,
        phone: form.phone,
        address: form.address,
        district: form.district,
        city: form.city,
        mapsUrl: form.mapsUrl,
        firstContact: form.contactName.trim()
          ? { name: form.contactName, roleTitle: form.contactRole, phone: form.contactPhone }
          : null,
      }, { force })
      toast.ok(t('common.saved'))
      refresh(created.id)
      queryClient.invalidateQueries({ queryKey: ['lookups'] })
      onClose()
      navigate(`/businesses/${created.id}`)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE') {
        setDuplicates((error.body.duplicates as DuplicateDto[]) ?? [])
      } else {
        toast.error(error)
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('business.new')}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          {duplicates.length > 0 ? (
            <button type="button" className="btn-primary" disabled={saving || !form.name.trim()} onClick={() => void save(true)}>
              {saving && <Spinner className="size-4" />} {t('business.saveAnyway')}
            </button>
          ) : (
            <button type="button" className="btn-primary" disabled={saving || !form.name.trim()} onClick={() => void save(false)}>
              {saving && <Spinner className="size-4" />} {t('common.save')}
            </button>
          )}
        </>
      }
    >
      <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); void save(duplicates.length > 0) }}>
        <p className="text-xs text-muted">{t('business.quickAddHint')}</p>
        <Field label={`${t('common.name')} *`}>
          <input className="input" autoFocus value={form.name} onChange={set('name')} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('common.phone')}>
            <input className="input" inputMode="tel" value={form.phone} onChange={set('phone')} />
          </Field>
          <Field label={t('common.type')}>
            <select className="input" value={form.typeId} onChange={set('typeId')}>
              <option value="">-</option>
              {lookups.data?.businessTypes.filter((type) => type.active).map((type) => (
                <option key={type.id} value={type.id}>{nameOf(type)}</option>
              ))}
            </select>
          </Field>
        </div>
        <Field label={t('common.address')}>
          <input className="input" value={form.address} onChange={set('address')} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('common.district')}>
            <input className="input" list="district-options" value={form.district} onChange={set('district')} />
            <datalist id="district-options">
              {lookups.data?.districts.map((d) => <option key={d} value={d} />)}
            </datalist>
          </Field>
          <Field label={t('common.city')}>
            <input className="input" value={form.city} onChange={set('city')} />
          </Field>
        </div>
        <Field label={t('business.mapsUrl')}>
          <input className="input" inputMode="url" value={form.mapsUrl} onChange={set('mapsUrl')} placeholder="https://maps.app.goo.gl/..." />
        </Field>
        <div className="rounded-xl bg-canvas p-3">
          <div className="label">{t('business.firstContact')}</div>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <input className="input" placeholder={t('common.name')} value={form.contactName} onChange={set('contactName')} />
            <input className="input" placeholder={t('business.roleTitle')} value={form.contactRole} onChange={set('contactRole')} />
            <input className="input" inputMode="tel" placeholder={t('common.phone')} value={form.contactPhone} onChange={set('contactPhone')} />
          </div>
        </div>
        {duplicates.length > 0 && (
          <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm">
            <div className="mb-2 flex items-center gap-2 font-medium text-amber-900">
              <TriangleAlert className="size-4" /> {t('business.duplicateTitle')}
            </div>
            <ul className="space-y-1.5">
              {duplicates.map((d, i) => (
                <li key={`${d.id}-${i}`} className="flex items-center gap-2">
                  <span className="min-w-0 flex-1 truncate">
                    <b>{d.name}</b> <span className="text-amber-800">· {t(`business.duplicateReason.${d.reason}`)}</span>
                    {d.address && <span className="block truncate text-xs text-amber-800/80">{d.address}</span>}
                  </span>
                  {d.id && (
                    <button type="button" className="btn-secondary px-2.5 py-1 text-xs" onClick={() => { onClose(); navigate(`/businesses/${d.id}`) }}>
                      {t('business.openExisting')}
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}
