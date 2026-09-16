import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarPlus, CircleCheck, RotateCcw, X } from 'lucide-react'
import { api } from '../lib/api'
import { googleCalendarUrl, openInCalendar } from '../lib/calendar'
import { minutesLabel } from './RemindersDialog'
import { useAuth } from '../lib/auth'
import type { FlavorDto, Priority, TaskDto, TaskType } from '../lib/types'
import { TASK_TYPES } from '../lib/types'
import { useLookups, useRefreshWork, keys } from '../lib/queries'
import { useQueryClient } from '@tanstack/react-query'
import { atDaysFromNow, fromLocalInput, toLocalInput } from '../lib/format'
import { useMode } from '../lib/mode'
import { useI18n } from '../i18n'
import { BusinessSelect, type BusinessPick } from './BusinessSelect'
import { ChipPicker } from './ChipPicker'
import { NEXT_PRESETS } from './LogActivityDialog'
import { Choice, Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

/** Plan a call, meeting or visit; or open one to move it, tick it off, or cancel it. */
export function TaskDialog({ open, onClose, task, business, defaultDueAt, defaultType = 'CALL' }: {
  open: boolean
  onClose: () => void
  task?: TaskDto | null
  business?: BusinessPick | null
  defaultDueAt?: string | null
  defaultType?: TaskType
}) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const refresh = useRefreshWork()
  const lookups = useLookups()
  const queryClient = useQueryClient()
  const { user, isSupervisor } = useAuth()
  const { canEdit } = useMode()
  const editable = canEdit()

  const [type, setType] = useState<TaskType>(defaultType)
  const [pick, setPick] = useState<BusinessPick | null>(null)
  const [title, setTitle] = useState('')
  const [dueAt, setDueAt] = useState(atDaysFromNow(1, 12))
  const [endAt, setEndAt] = useState('')
  const [allDay, setAllDay] = useState(false)
  const [location, setLocation] = useState('')
  const [priority, setPriority] = useState<Priority>('NORMAL')
  const [assignedToId, setAssignedToId] = useState<string>('')
  const [notes, setNotes] = useState('')
  // What to take along when this is a delivery.
  const [flavorIds, setFlavorIds] = useState<number[]>([])
  // '' = my default reminder time, '0' = none, otherwise minutes before.
  const [remind, setRemind] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setType(task?.type ?? defaultType)
    setPick(task?.businessId ? { id: task.businessId, name: task.businessName ?? '' } : business ?? null)
    setTitle(task?.title ?? '')
    setDueAt(task?.dueAt ?? defaultDueAt ?? atDaysFromNow(1, 12))
    setEndAt(task?.endAt ?? '')
    setAllDay(task?.allDay ?? false)
    setLocation(task?.location ?? '')
    setPriority(task?.priority ?? 'NORMAL')
    setAssignedToId(String(task?.assignedTo.id ?? user?.id ?? ''))
    setNotes(task?.notes ?? '')
    setFlavorIds(task?.flavorIds ?? [])
    setRemind(task?.remindMinutes === null || task?.remindMinutes === undefined ? '' : String(task.remindMinutes))
  }, [open, task, business, defaultDueAt, defaultType, user])

  const run = async (action: () => Promise<unknown>, message = t('common.saved')) => {
    setSaving(true)
    try {
      await action()
      toast.ok(message)
      refresh(task?.businessId ?? pick?.id)
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  const body = () => ({
    businessId: pick?.id ?? null,
    type,
    title,
    dueAt,
    endAt: endAt || (type === 'MEETING' ? new Date(new Date(dueAt).getTime() + 3_600_000).toISOString() : null),
    allDay,
    location,
    priority,
    assignedToId: assignedToId ? Number(assignedToId) : null,
    notes,
    contactId: task?.contact?.id ?? null,
    flavorIds,
    remindMinutes: remind === '' ? null : Number(remind),
  })

  const save = () => run(() => (task ? api.put(`/tasks/${task.id}`, body()) : api.post('/tasks', body())))

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={task ? t('tasks.editTask') : t('tasks.newTask')}
      footer={editable && (
        <>
          {task && task.status === 'OPEN' && (
            <div className="mr-auto flex flex-wrap gap-2">
              <button type="button" className="btn-secondary text-emerald-700" disabled={saving} onClick={() => void run(() => api.post(`/tasks/${task.id}/complete`, {}))}>
                <CircleCheck className="size-4" /> {t('tasks.complete')}
              </button>
              {task.businessId && (
                <button type="button" className="btn-secondary" onClick={() => { onClose(); navigate(`/businesses/${task.businessId}?task=${task.id}`) }}>
                  {t('tasks.completeWithLog')}
                </button>
              )}
              <button type="button" className="btn-ghost text-rose-700" disabled={saving} onClick={() => void run(() => api.post(`/tasks/${task.id}/cancel`, {}))}>
                <X className="size-4" /> {t('tasks.cancelTask')}
              </button>
            </div>
          )}
          {task && task.status !== 'OPEN' && (
            <button type="button" className="btn-secondary mr-auto" disabled={saving} onClick={() => void run(() => api.post(`/tasks/${task.id}/reopen`, {}))}>
              <RotateCcw className="size-4" /> {t('common.reopen')}
            </button>
          )}
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      )}
    >
      <fieldset disabled={!editable} className="space-y-3">
        <Choice size="sm" options={TASK_TYPES.map((value) => ({ value, label: t(`taskType.${value}`) }))} value={type} onChange={setType} />
        <Field label={t('tasks.business')}>
          <BusinessSelect value={pick} onChange={setPick} />
        </Field>
        <Field label={t('activity.nextTitle')}>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <div className="flex flex-wrap gap-1.5">
          {NEXT_PRESETS.map((p) => (
            <button key={p.key} type="button" className="chip-off" onClick={() => setDueAt(p.at())}>
              {t(`activity.presets.${p.key}`)}
            </button>
          ))}
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t('tasks.dueAt')}>
            <input type="datetime-local" className="input" value={toLocalInput(dueAt)} onChange={(e) => { const iso = fromLocalInput(e.target.value); if (iso) setDueAt(iso) }} />
          </Field>
          {type === 'MEETING' && (
            <Field label={t('tasks.endAt')}>
              <input type="datetime-local" className="input" value={toLocalInput(endAt)} onChange={(e) => setEndAt(fromLocalInput(e.target.value) ?? '')} />
            </Field>
          )}
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-brand-600" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} /> {t('tasks.allDay')}
        </label>
        {(type === 'MEETING' || type === 'VISIT') && (
          <Field label={t('tasks.location')}>
            <input className="input" value={location} onChange={(e) => setLocation(e.target.value)} />
          </Field>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t('common.priority')}>
            <select className="input" value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {(['LOW', 'NORMAL', 'HIGH'] as Priority[]).map((p) => <option key={p} value={p}>{t(`priority.${p}`)}</option>)}
            </select>
          </Field>
          {isSupervisor && (
            <Field label={t('common.assignedTo')}>
              <select className="input" value={assignedToId} onChange={(e) => setAssignedToId(e.target.value)}>
                {lookups.data?.users.filter((u) => u.active).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
              </select>
            </Field>
          )}
        </div>
        <Field label={t('tasks.remindMe')}>
          <select className="input" value={remind} onChange={(e) => setRemind(e.target.value)}>
            <option value="">{t('tasks.remindDefault')}</option>
            {[0, 15, 30, 60, 120, 1440].map((m) => <option key={m} value={m}>{minutesLabel(m, t)}</option>)}
          </select>
        </Field>
        {(type === 'DELIVERY' || type === 'SEND_SAMPLES') && (
          <Field label={t('activity.deliverFlavors')} hint={t('activity.deliverFlavorsHint')}>
            <ChipPicker
              options={(lookups.data?.flavors ?? []).filter((f) => f.active).map((f) => ({ id: f.id, label: lang === 'ka' ? f.nameKa : f.nameEn || f.nameKa, hint: f.nameEn }))}
              selected={flavorIds}
              onChange={setFlavorIds}
              onCreate={async (text) => {
                const flavor = await api.post<FlavorDto>('/flavors', { nameKa: text, nameEn: '' })
                queryClient.invalidateQueries({ queryKey: keys.lookups })
                return { id: flavor.id, label: flavor.nameKa }
              }}
              placeholder={t('common.search')}
              tone="green"
            />
          </Field>
        )}
        <Field label={t('common.notes')}>
          <textarea rows={3} className="input" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </fieldset>
      {task && task.status === 'OPEN' && (
        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-line pt-3">
          <button type="button" className="btn-secondary" onClick={() => openInCalendar(task.id, lang).catch((error) => toast.error(error))}>
            <CalendarPlus className="size-4" /> {t('tasks.addToCalendar')}
          </button>
          <a className="btn-ghost text-sm" href={googleCalendarUrl(task, t(`taskType.${task.type}`))} target="_blank" rel="noreferrer">
            Google Calendar
          </a>
        </div>
      )}
    </Modal>
  )
}
