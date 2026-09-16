import { useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown } from 'lucide-react'
import { api } from '../lib/api'
import type {
  ActivityDto, ActivityResult, ActivityType, BrandDto, BusinessDetail, BusinessStatus, FlavorDto, InterestStatus,
  TaskDto, TaskType, TastingFeedback,
} from '../lib/types'
import { ACTIVITY_TYPES, TASK_TYPES } from '../lib/types'
import { useLookups, useRefreshWork, keys } from '../lib/queries'
import { atDaysFromNow, fromLocalInput, inHours, toLocalInput } from '../lib/format'
import { useI18n } from '../i18n'
import { ChipPicker } from './ChipPicker'
import { Choice, Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

const RESULTS: Record<ActivityType, ActivityResult[]> = {
  CALL: ['NO_ANSWER', 'TALKED', 'INTERESTED', 'CALL_BACK', 'MEETING_SET', 'SAMPLES_REQUESTED', 'ORDERED', 'NOT_INTERESTED', 'WRONG_NUMBER'],
  VISIT: ['SPONTANEOUS_VISIT', 'TALKED', 'INTERESTED', 'SAMPLES_LEFT', 'SAMPLES_LEFT_MORE', 'SAMPLES_REQUESTED', 'MEETING_SET', 'ORDERED', 'CALL_BACK', 'NOT_INTERESTED', 'OTHER'],
  MEETING: ['TALKED', 'INTERESTED', 'SAMPLES_LEFT', 'SAMPLES_LEFT_MORE', 'SAMPLES_REQUESTED', 'ORDERED', 'CALL_BACK', 'NOT_INTERESTED', 'OTHER'],
  SAMPLES: ['SAMPLES_LEFT', 'SAMPLES_LEFT_MORE', 'OTHER', 'INTERESTED', 'ORDERED', 'NOT_INTERESTED'],
  MESSAGE: ['TALKED', 'INTERESTED', 'NO_ANSWER', 'CALL_BACK', 'NOT_INTERESTED', 'OTHER'],
  OTHER: ['OTHER', 'TALKED', 'INTERESTED', 'NOT_INTERESTED'],
}

export const RESULT_TONE: Partial<Record<ActivityResult, string>> = {
  NO_ANSWER: 'border-slate-500 bg-slate-500 text-white',
  WRONG_NUMBER: 'border-slate-500 bg-slate-500 text-white',
  NOT_INTERESTED: 'border-rose-600 bg-rose-600 text-white',
  INTERESTED: 'border-emerald-600 bg-emerald-600 text-white',
  MEETING_SET: 'border-emerald-600 bg-emerald-600 text-white',
  SAMPLES_REQUESTED: 'border-emerald-600 bg-emerald-600 text-white',
  SAMPLES_LEFT: 'border-emerald-600 bg-emerald-600 text-white',
  SAMPLES_LEFT_MORE: 'border-emerald-600 bg-emerald-600 text-white',
  SPONTANEOUS_VISIT: 'border-violet-600 bg-violet-600 text-white',
  ORDERED: 'border-emerald-700 bg-emerald-700 text-white',
}

// How far along each stage is, to suggest moving forward but never backwards.
const RANK: Record<BusinessStatus, number> = {
  NEW: 0, CONTACTED: 1, INTERESTED: 2, MEETING: 3, TESTING: 4, NEGOTIATION: 5, CUSTOMER: 6, REPEAT_CUSTOMER: 7,
  FOLLOW_UP_LATER: -1, NOT_INTERESTED: -1, LOST: -1,
}

function suggestedStatus(current: BusinessStatus, result: ActivityResult | null): BusinessStatus | '' {
  if (!result) return ''
  const forward = (target: BusinessStatus) => (RANK[current] < RANK[target] ? target : '')
  switch (result) {
    case 'TALKED':
    case 'CALL_BACK':
      return forward('CONTACTED')
    case 'INTERESTED':
      return forward('INTERESTED')
    case 'MEETING_SET':
      return forward('MEETING')
    case 'SAMPLES_REQUESTED':
    case 'SAMPLES_LEFT':
    case 'SAMPLES_LEFT_MORE':
      return forward('TESTING')
    case 'NOT_INTERESTED':
      return current === 'NOT_INTERESTED' ? '' : 'NOT_INTERESTED'
    default:
      return ''
  }
}

function suggestedNext(result: ActivityResult | null, reorderDays: number): { type: TaskType; dueAt: string; preset: string } | null {
  switch (result) {
    case 'NO_ANSWER':
    case 'WRONG_NUMBER':
    case 'CALL_BACK':
      return { type: 'CALL', dueAt: atDaysFromNow(1, 12), preset: 'tomorrow' }
    case 'MEETING_SET':
      return { type: 'MEETING', dueAt: atDaysFromNow(1, 15), preset: '' }
    case 'SAMPLES_REQUESTED':
      return { type: 'DELIVERY', dueAt: atDaysFromNow(1, 12), preset: 'tomorrow' }
    case 'SAMPLES_LEFT':
    case 'SAMPLES_LEFT_MORE':
      return { type: 'FOLLOW_UP', dueAt: atDaysFromNow(3, 12), preset: '' }
    case 'TALKED':
    case 'INTERESTED':
      return { type: 'FOLLOW_UP', dueAt: atDaysFromNow(2, 12), preset: 'in2days' }
    case 'ORDERED':
      return { type: 'CHECK_REORDER', dueAt: atDaysFromNow(reorderDays, 12), preset: '' }
    default:
      return null
  }
}

export const NEXT_PRESETS: { key: string; at: () => string }[] = [
  { key: 'asap', at: () => inHours(0) },
  { key: 'in1h', at: () => inHours(1) },
  { key: 'in3h', at: () => inHours(3) },
  { key: 'tonight', at: () => atDaysFromNow(0, 19) },
  { key: 'tomorrow', at: () => atDaysFromNow(1, 12) },
  { key: 'in2days', at: () => atDaysFromNow(2, 12) },
  { key: 'nextWeek', at: () => atDaysFromNow(7, 12) },
  { key: 'in2weeks', at: () => atDaysFromNow(14, 12) },
]

/**
 * One call, visit or meeting, recorded once: what happened, with whom, what they said, the new
 * stage, what they use and want, and the next step, which lands on the calendar by itself.
 */
export function LogActivityDialog({
  open, onClose, business, initialType = 'CALL', initialResult = null, initialNotes = '', completeTask, activity, onSaved,
}: {
  open: boolean
  onClose: () => void
  business: BusinessDetail
  initialType?: ActivityType
  initialResult?: ActivityResult | null
  /** Set to change a call or visit already written down, instead of adding a new one. */
  activity?: ActivityDto | null
  /** What was already typed, e.g. in call mode's quick note. */
  initialNotes?: string
  completeTask?: TaskDto | null
  onSaved?: (activity: ActivityDto, result: ActivityResult) => void
}) {
  const { t, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const queryClient = useQueryClient()
  const lookups = useLookups()

  const [type, setType] = useState<ActivityType>(initialType)
  // One visit can be several things at once: we dropped in, left samples, and they asked about other flavors.
  const [results, setResults] = useState<ActivityResult[]>(initialResult ? [initialResult] : [])
  const [resultNote, setResultNote] = useState('')
  const result = results[0] ?? null
  const [contactId, setContactId] = useState<string>('')
  const [when, setWhen] = useState(toLocalInput(new Date()))
  const [notes, setNotes] = useState('')
  const [status, setStatus] = useState<BusinessStatus | ''>('')
  const [statusTouched, setStatusTouched] = useState(false)
  const [learnOpen, setLearnOpen] = useState(false)
  const [brandIds, setBrandIds] = useState<number[]>([])
  const [flavorIds, setFlavorIds] = useState<number[]>([])
  const [wantIds, setWantIds] = useState<number[]>([])
  const [wantStatus, setWantStatus] = useState<InterestStatus>('INTERESTED')
  const [feedback, setFeedback] = useState<TastingFeedback>('UNKNOWN')
  const [nextOn, setNextOn] = useState(false)
  const [nextTouched, setNextTouched] = useState(false)
  const [nextType, setNextType] = useState<TaskType>('CALL')
  const [nextAt, setNextAt] = useState(atDaysFromNow(1, 12))
  const [preset, setPreset] = useState('tomorrow')
  const [nextTitle, setNextTitle] = useState('')
  const [nextFlavors, setNextFlavors] = useState<number[]>([])
  const [saving, setSaving] = useState(false)

  const reorderDays = lookups.data?.settings.reorder_days ?? 21
  const syrupCategory = lookups.data?.categories.find((c) => c.nameEn === 'Syrup')

  useEffect(() => {
    if (!open) return
    setType(activity ? activity.type : completeTask ? taskToActivity(completeTask.type) : initialType)
    setResults(activity ? activity.results : initialResult ? [initialResult] : [])
    setResultNote(activity?.resultNote ?? '')
    setContactId(activity?.contact ? String(activity.contact.id)
      : completeTask?.contact ? String(completeTask.contact.id)
        : business.contacts.find((c) => c.decisionMaker) ? String(business.contacts.find((c) => c.decisionMaker)!.id) : '')
    setWhen(toLocalInput(activity ? activity.occurredAt : new Date()))
    setNotes(activity?.notes ?? initialNotes)
    setStatus('')
    setStatusTouched(false)
    setLearnOpen(initialType === 'VISIT' || initialType === 'MEETING')
    setBrandIds([])
    setFlavorIds([])
    setWantIds([])
    setWantStatus('INTERESTED')
    setFeedback('UNKNOWN')
    setNextOn(false)
    setNextTouched(false)
    setNextTitle('')
    setNextFlavors([])
    // Reset only when the dialog opens, not when the business refreshes underneath it.
  }, [open])

  // Choosing a result suggests the next stage and the next step; anything touched by hand stays.
  useEffect(() => {
    if (activity) return
    if (!statusTouched) setStatus(suggestedStatus(business.status, result))
    if (!nextTouched) {
      const next = suggestedNext(result, reorderDays)
      setNextOn(Boolean(next))
      if (next) {
        setNextType(next.type)
        setNextAt(next.dueAt)
        setPreset(next.preset)
      }
    }
  }, [result, business.status, statusTouched, nextTouched, reorderDays, activity])

  useEffect(() => {
    setResults((chosen) => chosen.filter((r) => RESULTS[type].includes(r)))
    if (type === 'SAMPLES') setWantStatus('TESTING')
  }, [type])

  const brandOptions = useMemo(
    () => (lookups.data?.brands ?? []).filter((b) => b.active && !b.own).map((b) => ({ id: b.id, label: b.name })),
    [lookups.data],
  )
  const flavorOptions = useMemo(
    () => (lookups.data?.flavors ?? []).filter((f) => f.active).map((f) => ({ id: f.id, label: name(f), hint: f.nameEn })),
    [lookups.data, name],
  )

  const createBrand = async (text: string) => {
    const brand = await api.post<BrandDto>('/brands', { name: text })
    queryClient.invalidateQueries({ queryKey: keys.lookups })
    return { id: brand.id, label: brand.name }
  }
  const createFlavor = async (text: string) => {
    const flavor = await api.post<FlavorDto>('/flavors', { nameKa: text, nameEn: '' })
    queryClient.invalidateQueries({ queryKey: keys.lookups })
    return { id: flavor.id, label: name(flavor) }
  }

  const save = async () => {
    if (!result) return
    setSaving(true)
    try {
      const usages = syrupCategory && (brandIds.length || flavorIds.length)
        ? brandIds.length > 1
          ? [...brandIds.map((brandId) => ({ categoryId: syrupCategory.id, brandId, flavorIds: [] })), { categoryId: syrupCategory.id, brandId: null, flavorIds }]
          : [{ categoryId: syrupCategory.id, brandId: brandIds[0] ?? null, flavorIds }]
        : null
      const dueAt = nextAt
      const body = {
        type,
        result,
        results,
        resultNote: resultNote || null,
        contactId: contactId ? Number(contactId) : null,
        occurredAt: fromLocalInput(when),
        notes,
        newStatus: status || null,
        completeTaskId: completeTask?.id ?? null,
        usages,
        interests: wantIds.length ? { flavorIds: wantIds, status: wantStatus, feedback: feedback === 'UNKNOWN' ? null : feedback } : null,
        nextTask: nextOn
          ? {
              type: nextType,
              dueAt,
              endAt: nextType === 'MEETING' ? new Date(new Date(dueAt).getTime() + 3_600_000).toISOString() : null,
              title: nextTitle || null,
              contactId: contactId ? Number(contactId) : null,
              flavorIds: nextFlavors,
            }
          : null,
      }
      const saved = activity
        ? await api.put<ActivityDto>(`/businesses/${business.id}/activities/${activity.id}`, body)
        : await api.post<ActivityDto>(`/businesses/${business.id}/activities`, body)
      toast.ok(t('activity.saved'))
      refresh(business.id)
      onSaved?.(saved, result)
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
      title={activity ? t('activity.editTitle', { name: business.name }) : t('activity.title', { name: business.name })}
      footer={
        <>
          {completeTask && <span className="mr-auto text-xs text-emerald-700">{t('activity.completeTask')}</span>}
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary min-w-28" disabled={saving || !result} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label={t('activity.type')} hint={t(`activityHint.${type}`)}>
          <Choice options={ACTIVITY_TYPES.map((value) => ({ value, label: t(`activityType.${value}`) }))} value={type} onChange={setType} />
        </Field>

        <Field label={t('activity.result')} hint={t('activity.resultHint')}>
          <div className="flex flex-wrap gap-1.5">
            {RESULTS[type].map((value) => {
              const on = results.includes(value)
              return (
                <button
                  key={value}
                  type="button"
                  className={`rounded-xl border px-3 py-2 text-sm font-medium transition ${on ? RESULT_TONE[value] ?? 'border-brand-600 bg-brand-600 text-white' : 'border-line bg-surface text-ink hover:border-brand-300'}`}
                  onClick={() => setResults((chosen) => (on ? chosen.filter((r) => r !== value) : [...chosen, value]))}
                >
                  {t(`activityResult.${value}`)}
                </button>
              )
            })}
          </div>
          <input className="input mt-2" placeholder={t('activity.resultNotePlaceholder')} maxLength={200} value={resultNote} onChange={(e) => setResultNote(e.target.value)} />
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t('activity.contact')}>
            <select className="input" value={contactId} onChange={(e) => setContactId(e.target.value)}>
              <option value="">-</option>
              {business.contacts.map((c) => (
                <option key={c.id} value={c.id}>{c.name}{c.roleTitle ? ` (${c.roleTitle})` : ''}</option>
              ))}
            </select>
          </Field>
          <Field label={t('activity.when')}>
            <input type="datetime-local" className="input" value={when} onChange={(e) => setWhen(e.target.value)} />
          </Field>
        </div>

        <Field label={t('activity.notes')}>
          <textarea rows={3} className="input" placeholder={t('activity.notesPlaceholder')} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>

        <Field label={t('activity.newStatus')}>
          <select
            className={`input ${status ? 'border-brand-400 bg-brand-50' : ''}`}
            value={status}
            onChange={(e) => { setStatus(e.target.value as BusinessStatus | ''); setStatusTouched(true) }}
          >
            <option value="">{t('activity.keepStatus')} ({t(`status.${business.status}`)})</option>
            {(Object.keys(RANK) as BusinessStatus[]).filter((s) => s !== business.status).map((s) => (
              <option key={s} value={s}>{t(`status.${s}`)}</option>
            ))}
          </select>
        </Field>

        {/* What they use and want */}
        <div className="rounded-2xl border border-line">
          <button type="button" className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium" onClick={() => setLearnOpen(!learnOpen)}>
            {t('activity.learned')}
            {(brandIds.length + flavorIds.length + wantIds.length > 0) && (
              <span className="ml-2 rounded-full bg-brand-100 px-2 text-xs text-brand-700">{brandIds.length + flavorIds.length + wantIds.length}</span>
            )}
            <ChevronDown className={`ml-auto size-4 transition ${learnOpen ? 'rotate-180' : ''}`} />
          </button>
          {learnOpen && (
            <div className="space-y-4 border-t border-line px-4 py-3">
              <div>
                <div className="label">{t('activity.uses')}: {t('business.brand')}</div>
                <ChipPicker options={brandOptions} selected={brandIds} onChange={setBrandIds} onCreate={createBrand} placeholder="Monin, 1883..." initialCount={12} />
              </div>
              <div>
                <div className="label">{t('activity.uses')}: {t('business.flavors')}</div>
                <ChipPicker options={flavorOptions} selected={flavorIds} onChange={setFlavorIds} onCreate={createFlavor} placeholder={t('common.search')} />
              </div>
              <div>
                <div className="label">{t('activity.wants')}</div>
                <ChipPicker options={flavorOptions} selected={wantIds} onChange={setWantIds} onCreate={createFlavor} placeholder={t('common.search')} tone="green" />
                {wantIds.length > 0 && (
                  <div className="mt-2 space-y-2">
                    <Choice
                      size="sm"
                      options={(['INTERESTED', 'VERY_INTERESTED', 'SAMPLE_REQUESTED', 'TESTING', 'NOT_INTERESTED'] as InterestStatus[]).map((value) => ({ value, label: t(`interestStatus.${value}`) }))}
                      value={wantStatus}
                      onChange={setWantStatus}
                    />
                    <Choice
                      size="sm"
                      options={(['LIKED', 'OK', 'DISLIKED'] as TastingFeedback[]).map((value) => ({
                        value, label: t(`feedback.${value}`),
                        tone: value === 'LIKED' ? 'border-emerald-600 bg-emerald-600 text-white' : value === 'DISLIKED' ? 'border-rose-600 bg-rose-600 text-white' : undefined,
                      }))}
                      value={feedback === 'UNKNOWN' ? null : feedback}
                      onChange={(value) => setFeedback(value === feedback ? 'UNKNOWN' : value)}
                    />
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Next step */}
        <div className={`rounded-2xl border ${nextOn ? 'border-brand-300 bg-brand-50/40' : 'border-line'}`}>
          <label className="flex cursor-pointer items-center gap-3 px-4 py-3 text-sm font-medium">
            <input type="checkbox" className="size-4 accent-brand-600" checked={nextOn} onChange={(e) => { setNextOn(e.target.checked); setNextTouched(true) }} />
            {t('activity.nextStep')}
          </label>
          {nextOn && (
            <div className="space-y-3 border-t border-line px-4 py-3">
              <Choice
                size="sm"
                options={TASK_TYPES.map((value) => ({ value, label: t(`taskType.${value}`) }))}
                value={nextType}
                onChange={(value) => { setNextType(value); setNextTouched(true) }}
              />
              <div className="flex flex-wrap gap-1.5">
                {NEXT_PRESETS.map((p) => (
                  <button
                    key={p.key}
                    type="button"
                    className={preset === p.key ? 'chip-on' : 'chip-off'}
                    onClick={() => { setNextAt(p.at()); setPreset(p.key); setNextTouched(true) }}
                  >
                    {t(`activity.presets.${p.key}`)}
                  </button>
                ))}
                <button
                  type="button"
                  className="chip-off"
                  onClick={() => { setNextType('FOLLOW_UP'); setNextTitle(t('activity.theyWillText')); setNextAt(atDaysFromNow(1, 12)); setPreset('tomorrow'); setNextTouched(true) }}
                >
                  {t('activity.theyWillText')}
                </button>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label={t('activity.customTime')}>
                  <input
                    type="datetime-local"
                    className="input"
                    value={toLocalInput(nextAt)}
                    onChange={(e) => { const iso = fromLocalInput(e.target.value); if (iso) { setNextAt(iso); setPreset(''); setNextTouched(true) } }}
                  />
                </Field>
                <Field label={t('activity.nextTitle')}>
                  <input className="input" value={nextTitle} onChange={(e) => setNextTitle(e.target.value)} />
                </Field>
              </div>
              {(nextType === 'DELIVERY' || nextType === 'SEND_SAMPLES') && (
                <Field label={t('activity.deliverFlavors')} hint={t('activity.deliverFlavorsHint')}>
                  <ChipPicker options={flavorOptions} selected={nextFlavors} onChange={setNextFlavors} onCreate={createFlavor} placeholder={t('common.search')} tone="green" />
                </Field>
              )}
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}

function taskToActivity(type: TaskType): ActivityType {
  if (type === 'MEETING') return 'MEETING'
  if (type === 'VISIT') return 'VISIT'
  if (type === 'SEND_SAMPLES') return 'SAMPLES'
  if (type === 'SEND_PRICE_LIST') return 'MESSAGE'
  return 'CALL'
}
