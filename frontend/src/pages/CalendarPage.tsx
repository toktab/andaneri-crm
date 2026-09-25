import { useMemo, useState } from 'react'
import {
  addDays, addMonths, addWeeks, endOfMonth, endOfWeek, format, isSameDay, isSameMonth, startOfDay, startOfMonth, startOfWeek,
} from 'date-fns'
import { enUS, ka as kaLocale } from 'date-fns/locale'
import { ChevronLeft, ChevronRight, Eraser, MapPin, Navigation, Plus } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { useLookups, useRefreshWork, useTasks } from '../lib/queries'
import { api } from '../lib/api'
import { fmtTime } from '../lib/format'
import type { TaskDto, TaskType } from '../lib/types'
import { useI18n } from '../i18n'
import { Choice, EmptyState, PageHeader } from '../components/ui'
import { TaskRow, TYPE_TONE } from '../components/TaskRow'
import { TaskDialog } from '../components/TaskDialog'
import { RouteBriefing } from '../components/RouteBriefing'
import { useToast } from '../components/Toast'
import { tap } from '../lib/mobile'
import { mapsHref } from '../lib/format'

type View = 'month' | 'week' | 'day' | 'agenda'
const HOURS = Array.from({ length: 15 }, (_, i) => i + 8) // 08:00 to 22:00

/** Every task is a calendar entry: a meeting booked from a call shows up here without typing it twice. */
export function CalendarPage() {
  const { t, lang } = useI18n()
  const locale = lang === 'ka' ? kaLocale : enUS
  const { user } = useAuth()
  const { canEdit, asUserId } = useMode()
  const lookups = useLookups()
  const toast = useToast()
  const refresh = useRefreshWork()
  const [view, setView] = useState<View>(() => (window.innerWidth < 768 ? 'agenda' : 'month'))
  const [cursor, setCursor] = useState(() => startOfDay(new Date()))
  const [who, setWho] = useState('me')
  const [open, setOpen] = useState<TaskDto | null>(null)
  const [briefing, setBriefing] = useState<TaskDto | null>(null)
  // Out on the road: a stop opens as a briefing to read at the door, not as a form to fill in.
  const [route, setRoute] = useState(() => {
    try {
      return localStorage.getItem('andaneri.route') === '1'
    } catch {
      return false
    }
  })
  const [creating, setCreating] = useState<string | null>(null)
  const editable = canEdit()
  const userId = asUserId ?? (who === 'me' ? user?.id ?? null : who === 'all' ? null : Number(who))

  const range = useMemo(() => {
    if (view === 'month') return { from: startOfWeek(startOfMonth(cursor), { weekStartsOn: 1 }), to: addDays(endOfWeek(endOfMonth(cursor), { weekStartsOn: 1 }), 1) }
    if (view === 'week') return { from: startOfWeek(cursor, { weekStartsOn: 1 }), to: addDays(startOfWeek(cursor, { weekStartsOn: 1 }), 7) }
    if (view === 'day') return { from: cursor, to: addDays(cursor, 1) }
    return { from: cursor, to: addDays(cursor, 31) }
  }, [view, cursor])

  const tasks = useTasks({ from: range.from.toISOString(), to: startOfDay(range.to).toISOString(), userId, status: ['OPEN', 'DONE'] })
  const hasImported = (tasks.data ?? []).some((task) => task.imported && task.status === 'OPEN')
  const onDay = (day: Date) => (tasks.data ?? []).filter((task) => isSameDay(new Date(task.dueAt), day))

  const toggleRoute = () => {
    const next = !route
    tap()
    setRoute(next)
    if (next) {
      setView('agenda')
      setCursor(startOfDay(new Date()))
    }
    try {
      localStorage.setItem('andaneri.route', next ? '1' : '0')
    } catch {
      /* this visit only */
    }
  }

  /**
   * Anything on the calendar opens as the card you actually want to read: who was last spoken to and
   * what they said, what they pour, what they asked about, the phone and the map. Editing the event,
   * and the whole business page, are one button away from there - having to go and search for the
   * place again just to remember any of it was the whole complaint.
   */
  const openTask = (task: TaskDto) => setBriefing(task)

  const stops = (tasks.data ?? [])
    .filter((task) => task.status === 'OPEN' && isSameDay(new Date(task.dueAt), new Date()))
    .sort((a, b) => a.dueAt.localeCompare(b.dueAt))

  const step = (direction: 1 | -1) => {
    if (view === 'month') setCursor((c) => addMonths(c, direction))
    else if (view === 'week') setCursor((c) => addWeeks(c, direction))
    else if (view === 'day') setCursor((c) => addDays(c, direction))
    else setCursor((c) => addDays(c, 31 * direction))
  }

  const title = view === 'month' ? format(cursor, 'LLLL yyyy', { locale })
    : view === 'week' ? `${format(range.from, 'd MMM', { locale })} - ${format(addDays(range.from, 6), 'd MMM yyyy', { locale })}`
    : format(cursor, 'EEEE, d MMMM yyyy', { locale })

  const newAt = (day: Date, hour = 12) => {
    const at = new Date(day)
    at.setHours(hour, 0, 0, 0)
    setCreating(at.toISOString())
  }

  /** The spreadsheet's "next step" column became open tasks; they are guesses, so one press clears them. */
  const clearImported = async () => {
    if (!window.confirm(t('tasks.clearImportedConfirm'))) return
    try {
      const { cancelled } = await api.post<{ cancelled: number }>('/tasks/imported/cancel', {})
      toast.ok(t('tasks.clearedImported', { n: cancelled }))
      refresh()
    } catch (error) {
      toast.error(error)
    }
  }

  const complete = async (task: TaskDto) => {
    try {
      await api.post(`/tasks/${task.id}/complete`, {})
      refresh(task.businessId)
    } catch (error) {
      toast.error(error)
    }
  }

  const weekdays = Array.from({ length: 7 }, (_, i) => format(addDays(startOfWeek(new Date(), { weekStartsOn: 1 }), i), 'EEEEEE', { locale }))

  return (
    <div>
      <PageHeader
        title={t('calendar.title')}
        actions={
          <>
            <select className="input w-auto max-w-full" value={who} onChange={(e) => setWho(e.target.value)}>
              <option value="me">{t('calendar.mine')}</option>
              <option value="all">{t('calendar.everyone')}</option>
              {lookups.data?.users.filter((u) => u.active && u.id !== user?.id).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
            </select>
            <button
              type="button"
              className={route ? 'btn-primary' : 'btn-secondary'}
              onClick={toggleRoute}
              title={t('route.hint')}
            >
              <Navigation className="size-4" /> {t('route.mode')}
            </button>
            {editable && <button type="button" className="btn-primary" onClick={() => newAt(cursor)}><Plus className="size-4" /> {t('calendar.newEvent')}</button>}
          </>
        }
      />

      <div className="card mb-3 flex flex-wrap items-center gap-2 p-2">
        <button type="button" className="btn-secondary px-2.5" onClick={() => step(-1)} aria-label="previous"><ChevronLeft className="size-4" /></button>
        <button type="button" className="btn-secondary" onClick={() => setCursor(startOfDay(new Date()))}>{t('calendar.today')}</button>
        <button type="button" className="btn-secondary px-2.5" onClick={() => step(1)} aria-label="next"><ChevronRight className="size-4" /></button>
        <h2 className="ml-1 text-base font-semibold capitalize">{title}</h2>
        <div className="ml-auto">
          <Choice size="sm" value={view} onChange={setView} options={(['month', 'week', 'day', 'agenda'] as View[]).map((v) => ({ value: v, label: t(`calendar.${v}`) }))} />
        </div>
      </div>

      {route && (
        <section className="card mb-3 border-brand-200 p-3">
          <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold">
            <Navigation className="size-4 text-brand-600" /> {t('route.today', { n: stops.length })}
          </h2>
          {stops.length === 0 ? (
            <p className="px-1 text-sm text-muted">{t('dashboard.nothingToday')}</p>
          ) : (
            <ol className="space-y-1.5">
              {stops.map((task, i) => {
                const where = task.businessAddress
                  ? mapsHref({ mapsUrl: task.businessMapsUrl, address: task.businessAddress, name: task.businessName ?? undefined })
                  : null
                return (
                  <li key={task.id} className="flex items-center gap-2">
                    <span className="grid size-6 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">{i + 1}</span>
                    <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setBriefing(task)}>
                      <span className="block truncate text-sm font-medium">{task.businessName ?? task.title}</span>
                      <span className="block truncate text-xs text-muted">
                        {task.allDay ? '' : `${fmtTime(task.dueAt)} · `}{t(`taskType.${task.type}`)}
                        {task.businessAddress ? ` · ${task.businessAddress}` : ''}
                      </span>
                    </button>
                    {where && (
                      <a href={where} target="_blank" rel="noreferrer" className="grid size-9 shrink-0 place-items-center rounded-full border border-line text-brand-700" title={t('business.openMaps')}>
                        <MapPin className="size-4" />
                      </a>
                    )}
                  </li>
                )
              })}
            </ol>
          )}
        </section>
      )}

      <div className="mb-3 flex flex-wrap items-center gap-1.5">
        {(['CALL', 'MEETING', 'VISIT', 'DELIVERY', 'FOLLOW_UP'] as TaskType[]).map((type) => (
          <span key={type} className={`rounded-md px-1.5 py-0.5 text-[11px] font-medium ${TYPE_TONE[type]}`}>{t(`taskType.${type}`)}</span>
        ))}
        {hasImported && editable && (
          <button type="button" className="btn-ghost ml-auto text-xs" title={t('tasks.importedHint')} onClick={() => void clearImported()}>
            <Eraser className="size-3.5" /> {t('tasks.clearImported')}
          </button>
        )}
      </div>

      {view === 'month' && (
        <div className="card overflow-hidden">
          <div className="grid grid-cols-7 border-b border-line bg-canvas text-center text-xs font-medium text-muted">
            {weekdays.map((d) => <div key={d} className="py-2">{d}</div>)}
          </div>
          <div className="grid grid-cols-7">
            {eachDay(range.from, range.to).map((day) => {
              const items = onDay(day)
              const today = isSameDay(day, new Date())
              return (
                <div key={day.toISOString()} className={`group min-h-24 border-b border-r border-line p-1 sm:min-h-28 ${isSameMonth(day, cursor) ? '' : 'bg-canvas/60 text-muted'}`}>
                  <div className="flex items-center justify-between">
                    <button type="button" onClick={() => { setCursor(day); setView('day') }} className={`grid size-6 place-items-center rounded-full text-xs font-medium ${today ? 'bg-brand-600 text-white' : 'hover:bg-canvas'}`}>
                      {format(day, 'd')}
                    </button>
                    {editable && (
                      <button type="button" onClick={() => newAt(day)} className="hidden size-5 place-items-center rounded text-muted hover:bg-brand-50 hover:text-brand-700 group-hover:grid" aria-label={t('calendar.newEvent')}>
                        <Plus className="size-3.5" />
                      </button>
                    )}
                  </div>
                  <div className="mt-1 space-y-0.5">
                    {items.slice(0, 3).map((task) => (
                      <button key={task.id} type="button" onClick={() => openTask(task)} title={`${t(`taskType.${task.type}`)}: ${task.businessName ?? task.title ?? ''}`}
                        className={`block w-full truncate rounded px-1 py-0.5 text-left text-[11px] ${TYPE_TONE[task.type]} ${task.status !== 'OPEN' ? 'line-through opacity-60' : ''} ${task.imported ? 'opacity-50 italic' : ''}`}>
                        <span className="font-semibold">{task.allDay ? '' : fmtTime(task.dueAt)} {t(`taskType.${task.type}`)}</span> · {task.businessName ?? task.title}
                      </button>
                    ))}
                    {items.length > 3 && (
                      <button type="button" className="px-1 text-[11px] text-muted hover:text-ink" onClick={() => { setCursor(day); setView('day') }}>+{items.length - 3}</button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {view === 'week' && (
        <div className="grid gap-2 md:grid-cols-7">
          {eachDay(range.from, range.to).map((day) => (
            <section key={day.toISOString()} className={`card min-h-40 p-2 ${isSameDay(day, new Date()) ? 'ring-2 ring-brand-300' : ''}`}>
              <div className="mb-1 flex items-center justify-between px-1">
                <button type="button" className="text-xs font-semibold capitalize hover:text-brand-700" onClick={() => { setCursor(day); setView('day') }}>{format(day, 'EEE d', { locale })}</button>
                {editable && <button type="button" className="text-muted hover:text-brand-700" onClick={() => newAt(day)} aria-label={t('calendar.newEvent')}><Plus className="size-3.5" /></button>}
              </div>
              <div className="space-y-1">
                {onDay(day).map((task) => (
                  <button key={task.id} type="button" onClick={() => openTask(task)}
                    className={`block w-full rounded-lg px-2 py-1 text-left text-xs ${TYPE_TONE[task.type]} ${task.status !== 'OPEN' ? 'line-through opacity-60' : ''} ${task.imported ? 'opacity-50 italic' : ''}`}>
                    <span className="block font-semibold">{task.allDay ? '-' : fmtTime(task.dueAt)} · {t(`taskType.${task.type}`)}{task.imported ? ` · ${t('tasks.imported')}` : ''}</span>
                    <span className="block truncate">{task.businessName ?? task.title}</span>
                  </button>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {view === 'day' && (
        <div className="card divide-y divide-line">
          {onDay(cursor).filter((task) => task.allDay).map((task) => <TaskRow key={task.id} task={task} onOpen={openTask} onComplete={complete} editable={editable} />)}
          {HOURS.map((hour) => {
            const items = onDay(cursor).filter((task) => !task.allDay && new Date(task.dueAt).getHours() === hour)
            return (
              <div key={hour} className="group flex min-h-12 gap-2 px-2 py-1">
                <div className="w-12 shrink-0 pt-2 text-xs font-medium text-muted tabular-nums">{String(hour).padStart(2, '0')}:00</div>
                <div className="flex-1">
                  {items.map((task) => <TaskRow key={task.id} task={task} onOpen={openTask} onComplete={complete} editable={editable} />)}
                </div>
                {editable && (
                  <button type="button" className="invisible self-center rounded-lg p-1.5 text-muted hover:bg-brand-50 hover:text-brand-700 group-hover:visible" onClick={() => newAt(cursor, hour)} aria-label={t('calendar.newEvent')}>
                    <Plus className="size-4" />
                  </button>
                )}
              </div>
            )
          })}
          {onDay(cursor).filter((task) => !task.allDay && (new Date(task.dueAt).getHours() < 8 || new Date(task.dueAt).getHours() > 22)).map((task) => (
            <TaskRow key={task.id} task={task} onOpen={openTask} onComplete={complete} editable={editable} />
          ))}
        </div>
      )}

      {view === 'agenda' && (
        (tasks.data ?? []).length === 0 ? <div className="card"><EmptyState title={t('tasks.empty')} /></div> : (
          <div className="space-y-3">
            {eachDay(range.from, range.to).filter((day) => onDay(day).length > 0).map((day) => (
              <section key={day.toISOString()} className="card p-2">
                <div className="px-2 pb-1 pt-1.5 text-xs font-medium uppercase tracking-wide text-muted">{format(day, 'EEEE, d MMM', { locale })}</div>
                {onDay(day).map((task) => <TaskRow key={task.id} task={task} onOpen={openTask} onComplete={complete} editable={editable} />)}
              </section>
            ))}
          </div>
        )
      )}

      <RouteBriefing task={briefing} onClose={() => setBriefing(null)} onEdit={editable ? (task) => { setBriefing(null); setOpen(task) } : undefined} />
      <TaskDialog open={Boolean(open)} task={open} onClose={() => setOpen(null)} />
      <TaskDialog open={Boolean(creating)} defaultDueAt={creating} defaultType="MEETING" onClose={() => setCreating(null)} />
    </div>
  )
}

function eachDay(from: Date, to: Date): Date[] {
  const days: Date[] = []
  for (let day = startOfDay(from); day < to; day = addDays(day, 1)) days.push(day)
  return days
}
