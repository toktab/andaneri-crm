import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import type {
  BusinessDetail, BusinessStatus, DrinkType, Openness, PriceSensitivity, Priority, Satisfaction, UsageAnswer,
} from '../lib/types'
import { DRINK_TYPES, STATUSES } from '../lib/types'
import { useLookups, useRefreshWork } from '../lib/queries'
import { useI18n } from '../i18n'
import { Choice, Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

type Form = {
  name: string; typeId: string; status: BusinessStatus; priority: Priority; phone: string; email: string; website: string
  mapsUrl: string; address: string; city: string; district: string; legalName: string; idCode: string; branches: string
  visitHours: string; menuChange: string; switchOpenness: Openness; priceSensitivity: PriceSensitivity; satisfaction: Satisfaction
  competitorNotes: string; reorderDays: string; assignedToId: string; notes: string; drinkTypes: DrinkType[]
  sheetId: string; custom: Record<string, string>
}

function toForm(b: BusinessDetail): Form {
  return {
    name: b.name, typeId: b.typeId ? String(b.typeId) : '', status: b.status, priority: b.priority, phone: b.phone ?? '',
    email: b.email ?? '', website: b.website ?? '', mapsUrl: b.mapsUrl ?? '', address: b.address ?? '', city: b.city ?? '',
    district: b.district ?? '', legalName: b.legalName ?? '', idCode: b.idCode ?? '', branches: b.branches?.toString() ?? '',
    visitHours: b.visitHours ?? '', menuChange: b.menuChange ?? '', switchOpenness: b.switchOpenness, priceSensitivity: b.priceSensitivity,
    satisfaction: b.satisfaction, competitorNotes: b.competitorNotes ?? '', reorderDays: b.reorderDays?.toString() ?? '',
    assignedToId: b.assignedTo ? String(b.assignedTo.id) : '', notes: b.notes ?? '', drinkTypes: b.drinkTypes,
    sheetId: b.sheetId ? String(b.sheetId) : '', custom: { ...b.customValues },
  }
}

/** Every field of a business, plus the yes / no answers per product category, in one form. */
export function BusinessEditDialog({ open, onClose, business }: { open: boolean; onClose: () => void; business: BusinessDetail }) {
  const { t, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const lookups = useLookups()
  const { isSupervisor } = useAuth()
  const [form, setForm] = useState<Form>(() => toForm(business))
  const [answers, setAnswers] = useState<Record<number, UsageAnswer>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setForm(toForm(business))
    setAnswers(Object.fromEntries(business.categoryUsages.map((u) => [u.categoryId, u.answer])))
  }, [open, business])

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }))
  const input = (key: keyof Form) => ({
    value: form[key] as string,
    onChange: (e: { target: { value: string } }) => set(key, e.target.value as never),
  })

  const save = async () => {
    setSaving(true)
    try {
      // The update replaces every field, sheet and extra fields included: leaving them out would clear them.
      const { custom, ...fields } = form
      await api.put(`/businesses/${business.id}`, {
        ...fields,
        sheetId: form.sheetId ? Number(form.sheetId) : null,
        customValues: custom,
        typeId: form.typeId ? Number(form.typeId) : null,
        branches: form.branches ? Number(form.branches) : null,
        reorderDays: form.reorderDays ? Number(form.reorderDays) : null,
        assignedToId: form.assignedToId ? Number(form.assignedToId) : null,
        latitude: business.latitude,
        longitude: business.longitude,
        version: business.version,
      })
      const previous = Object.fromEntries(business.categoryUsages.map((u) => [u.categoryId, u.answer]))
      const changed = Object.entries(answers).filter(([id, answer]) => (previous[Number(id)] ?? 'UNKNOWN') !== answer)
      if (changed.length) {
        await api.put(`/businesses/${business.id}/category-usages`, changed.map(([categoryId, answer]) => ({ categoryId: Number(categoryId), answer })))
      }
      toast.ok(t('common.saved'))
      refresh(business.id)
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  const archive = async () => {
    setSaving(true)
    try {
      await api.post(`/businesses/${business.id}/archive`, { archived: !business.archived })
      refresh(business.id)
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
      wide
      title={t('business.editDetails')}
      footer={
        <>
          {isSupervisor && (
            <button type="button" className="btn-ghost mr-auto" disabled={saving} onClick={() => void archive()}>
              {business.archived ? t('business.restore') : t('business.archive')}
            </button>
          )}
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || !form.name.trim()} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-5">
        <section className="grid gap-3 sm:grid-cols-2">
          <Field label={`${t('common.name')} *`} className="sm:col-span-2"><input className="input" {...input('name')} /></Field>
          <Field label={t('common.type')}>
            <select className="input" {...input('typeId')}>
              <option value="">-</option>
              {lookups.data?.businessTypes.map((type) => <option key={type.id} value={type.id}>{name(type)}</option>)}
            </select>
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('common.status')}>
              <select className="input" {...input('status')}>
                {STATUSES.map((s) => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
              </select>
            </Field>
            <Field label={t('common.priority')}>
              <select className="input" {...input('priority')}>
                {['LOW', 'NORMAL', 'HIGH'].map((p) => <option key={p} value={p}>{t(`priority.${p}`)}</option>)}
              </select>
            </Field>
          </div>
          <Field label={t('common.phone')}><input className="input" inputMode="tel" {...input('phone')} /></Field>
          <Field label={t('common.email')}><input className="input" inputMode="email" {...input('email')} /></Field>
          <Field label={t('common.address')}><input className="input" {...input('address')} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('common.district')}>
              <input className="input" list="edit-districts" {...input('district')} />
              <datalist id="edit-districts">{lookups.data?.districts.map((d) => <option key={d} value={d} />)}</datalist>
            </Field>
            <Field label={t('common.city')}><input className="input" {...input('city')} /></Field>
          </div>
          <Field label={t('business.mapsUrl')}><input className="input" {...input('mapsUrl')} /></Field>
          <Field label={t('business.website')}><input className="input" {...input('website')} /></Field>
          <Field label={t('business.legalName')}><input className="input" {...input('legalName')} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('business.idCode')}><input className="input" inputMode="numeric" {...input('idCode')} /></Field>
            <Field label={t('business.branches')}><input type="number" min="0" className="input" {...input('branches')} /></Field>
          </div>
          <Field label={t('business.visitHours')}><input className="input" placeholder="20:00-ის შემდეგ" {...input('visitHours')} /></Field>
          <Field label={t('business.sheet')}>
            <select className="input" {...input('sheetId')}>
              <option value="">{t('business.noSheet')}</option>
              {lookups.data?.workbooks.map((project) => (
                <optgroup key={project.id} label={project.name}>
                  {lookups.data?.sheets.filter((s) => s.workbookId === project.id).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </optgroup>
              ))}
              {lookups.data?.sheets.filter((s) => s.workbookId === null).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          {isSupervisor && (
            <Field label={t('common.assignedTo')}>
              <select className="input" {...input('assignedToId')}>
                <option value="">{t('common.unassigned')}</option>
                {lookups.data?.users.filter((u) => u.active).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
              </select>
            </Field>
          )}
        </section>

        <section>
          <div className="label">{t('business.drinkTypes')}</div>
          <div className="flex flex-wrap gap-1.5">
            {DRINK_TYPES.map((d) => {
              const on = form.drinkTypes.includes(d)
              return (
                <button key={d} type="button" className={on ? 'chip-on' : 'chip-off'} onClick={() => set('drinkTypes', on ? form.drinkTypes.filter((x) => x !== d) : [...form.drinkTypes, d])}>
                  {t(`drink.${d}`)}
                </button>
              )
            })}
          </div>
        </section>

        <section className="space-y-2">
          {lookups.data?.categories.filter((c) => c.active).map((category) => (
            <div key={category.id} className="flex flex-wrap items-center gap-2">
              <span className="w-40 text-sm">{t('business.uses', { category: name(category).toLowerCase() })}</span>
              <Choice
                size="sm"
                options={(['YES', 'SOMETIMES', 'NO', 'UNKNOWN'] as UsageAnswer[]).map((value) => ({ value, label: t(`usage.${value}`) }))}
                value={answers[category.id] ?? 'UNKNOWN'}
                onChange={(value) => setAnswers((a) => ({ ...a, [category.id]: value }))}
              />
            </div>
          ))}
        </section>

        <section className="grid gap-3 rounded-2xl bg-canvas p-3 sm:grid-cols-3">
          <Field label={t('business.switchOpenness')}>
            <select className="input" {...input('switchOpenness')}>
              {['UNKNOWN', 'YES', 'MAYBE', 'NO'].map((v) => <option key={v} value={v}>{t(`openness.${v}`)}</option>)}
            </select>
          </Field>
          <Field label={t('business.priceSensitivity')}>
            <select className="input" {...input('priceSensitivity')}>
              {['UNKNOWN', 'LOW', 'MEDIUM', 'HIGH'].map((v) => <option key={v} value={v}>{t(`priceSensitivity.${v}`)}</option>)}
            </select>
          </Field>
          <Field label={t('business.satisfaction')}>
            <select className="input" {...input('satisfaction')}>
              {['UNKNOWN', 'SATISFIED', 'NEUTRAL', 'UNSATISFIED'].map((v) => <option key={v} value={v}>{t(`satisfaction.${v}`)}</option>)}
            </select>
          </Field>
          <Field label={t('business.competitorNotes')} className="sm:col-span-3">
            <textarea rows={2} className="input" {...input('competitorNotes')} />
          </Field>
          <Field label={t('business.menuChange')} className="sm:col-span-2"><input className="input" {...input('menuChange')} /></Field>
          <Field label={t('business.reorderDays')}><input type="number" min="1" className="input" {...input('reorderDays')} /></Field>
        </section>

        {(lookups.data?.customFields ?? []).some((f) => f.active || form.custom[f.id]) && (
          <section>
            <div className="label">{t('business.customFields')}</div>
            <div className="grid gap-3 sm:grid-cols-2">
              {lookups.data?.customFields.filter((f) => f.active || form.custom[f.id]).map((f) => (
                <Field key={f.id} label={f.label}>
                  <input className="input" value={form.custom[f.id] ?? ''} onChange={(e) => set('custom', { ...form.custom, [f.id]: e.target.value })} />
                </Field>
              ))}
            </div>
          </section>
        )}

        <Field label={t('common.notes')}>
          <textarea rows={3} className="input" placeholder={t('business.notesPlaceholder')} {...input('notes')} />
        </Field>
      </div>
    </Modal>
  )
}
