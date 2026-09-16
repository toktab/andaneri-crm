import { Link } from 'react-router-dom'
import { CalendarPlus, CircleCheck, MapPin, Phone } from 'lucide-react'
import type { TaskDto } from '../lib/types'
import { daysFromToday, fmtShortDate, fmtTime, mapsHref } from '../lib/format'
import { openInCalendar } from '../lib/calendar'
import { useLookups } from '../lib/queries'
import { useI18n } from '../i18n'
import { Phones } from './Phones'
import { useToast } from './Toast'

export const TYPE_TONE: Record<string, string> = {
  CALL: 'bg-sky-100 text-sky-800',
  MEETING: 'bg-amber-100 text-amber-900',
  VISIT: 'bg-violet-100 text-violet-800',
  DELIVERY: 'bg-teal-100 text-teal-800',
  SEND_SAMPLES: 'bg-orange-100 text-orange-800',
  SEND_PRICE_LIST: 'bg-slate-100 text-slate-700',
  FOLLOW_UP: 'bg-brand-100 text-brand-800',
  CHECK_REORDER: 'bg-emerald-100 text-emerald-800',
  OTHER: 'bg-stone-100 text-stone-700',
}

/**
 * One task as a line you can act on without opening anything: the call button dials, the pin opens
 * the map, the tick completes it, and the line itself opens the task.
 */
export function TaskRow({ task, onOpen, onComplete, showDate = false, editable = true }: {
  task: TaskDto
  onOpen: (task: TaskDto) => void
  onComplete?: (task: TaskDto) => void
  showDate?: boolean
  editable?: boolean
}) {
  const { t, lang, name } = useI18n()
  const toast = useToast()
  const lookups = useLookups()
  const overdueDays = task.status === 'OPEN' ? -(daysFromToday(task.dueAt) ?? 0) : 0
  const done = task.status !== 'OPEN'
  const phone = task.contact?.phone || task.businessPhone
  // What to take along, when this is a delivery.
  const flavors = task.flavorIds
    .map((id) => lookups.data?.flavors.find((f) => f.id === id))
    .filter((f) => f !== undefined)
    .map((f) => name(f))
  const maps = task.businessAddress ? mapsHref({ mapsUrl: task.businessMapsUrl, address: task.businessAddress, name: task.businessName ?? undefined }) : null

  return (
    <div className={`flex flex-wrap items-center gap-2 rounded-xl px-2 py-2 hover:bg-canvas ${done ? 'opacity-55' : ''} ${task.imported && !done ? 'opacity-60' : ''}`}>
      {editable && onComplete && !done ? (
        <button type="button" className="grid size-8 shrink-0 place-items-center rounded-full border border-line text-muted hover:border-emerald-500 hover:text-emerald-600" onClick={() => onComplete(task)} title={t('tasks.complete')}>
          <CircleCheck className="size-4" />
        </button>
      ) : (
        <span className="grid size-8 shrink-0 place-items-center">
          {done && <CircleCheck className="size-4 text-emerald-600" />}
        </span>
      )}
      <button type="button" className="min-w-0 flex-1 text-left" onClick={() => onOpen(task)}>
        <div className="flex items-center gap-2">
          <span className="w-11 shrink-0 text-sm font-semibold tabular-nums">{task.allDay ? '-' : fmtTime(task.dueAt)}</span>
          <span className={`shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-medium ${TYPE_TONE[task.type]}`}>{t(`taskType.${task.type}`)}</span>
          <span className={`truncate text-sm font-medium ${done ? 'line-through' : ''}`}>{task.businessName ?? task.title}</span>
          {task.imported && <span className="shrink-0 rounded bg-stone-200 px-1.5 text-[11px] font-medium text-stone-600" title={t('tasks.importedHint')}>{t('tasks.imported')}</span>}
        </div>
        <div className="ml-[3.25rem] truncate text-xs text-muted">
          {showDate && <span className="mr-1.5">{fmtShortDate(task.dueAt, lang)}</span>}
          {overdueDays > 0 && <span className="mr-1.5 font-medium text-rose-600">{t('dashboard.overdueBy', { n: overdueDays })}</span>}
          {[task.businessName ? task.title : null, task.contact?.name, task.assignedTo.fullName].filter(Boolean).join(' · ')}
          {flavors.length > 0 && <span className="ml-1.5 font-medium text-teal-700">{t('activity.deliverFlavors')}: {flavors.join(', ')}</span>}
        </div>
      </button>
      {task.businessId && (task.type === 'CALL' || task.type === 'FOLLOW_UP' || task.type === 'CHECK_REORDER') && !done ? (
        <Link to={`/calls/${task.businessId}?task=${task.id}`} className="grid size-9 shrink-0 place-items-center rounded-full bg-brand-600 text-white hover:brightness-110" title={t('callMode.title')}>
          <Phone className="size-4" />
        </Link>
      ) : phone && !done ? (
        <Phones text={phone} className="order-last w-full justify-end sm:order-none sm:w-auto sm:max-w-[45%]" />
      ) : null}
      {maps && (task.type === 'MEETING' || task.type === 'VISIT') && !done && (
        <a href={maps} target="_blank" rel="noreferrer" className="grid size-9 shrink-0 place-items-center rounded-full border border-line text-muted hover:bg-canvas" title={t('business.openMaps')}>
          <MapPin className="size-4" />
        </a>
      )}
      {(task.type === 'MEETING' || task.type === 'VISIT') && !done && (
        <button type="button" onClick={() => openInCalendar(task.id, lang).catch((error) => toast.error(error))}
          className="hidden size-9 shrink-0 place-items-center rounded-full border border-line text-muted hover:bg-canvas sm:grid" title={t('tasks.addToCalendar')}>
          <CalendarPlus className="size-4" />
        </button>
      )}
    </div>
  )
}
