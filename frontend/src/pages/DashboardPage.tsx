import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarPlus, Flame, Phone, Plus, ShoppingCart, Sparkles, TriangleAlert } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { useDashboard, useRefreshWork } from '../lib/queries'
import { fmtShortDate, fmtWeekday, money } from '../lib/format'
import type { Alert, BusinessStatus, TaskDto } from '../lib/types'
import { STATUSES } from '../lib/types'
import { useI18n } from '../i18n'
import { Choice, EmptyState, ErrorBlock, Loading, PageHeader, STATUS_STYLE, Stat } from '../components/ui'
import { TaskRow } from '../components/TaskRow'
import { TaskDialog } from '../components/TaskDialog'
import { QuickAddDialog } from '../components/QuickAddDialog'
import { useToast } from '../components/Toast'

const ACTIVE_PIPELINE: BusinessStatus[] = ['NEW', 'CONTACTED', 'INTERESTED', 'MEETING', 'TESTING', 'NEGOTIATION', 'CUSTOMER', 'REPEAT_CUSTOMER']

/** 12:00, arriving at work: who to call, who to visit, what meetings, what is late, what needs attention. */
export function DashboardPage() {
  const { t, lang } = useI18n()
  const { user, isSupervisor } = useAuth()
  const { canEdit } = useMode()
  const toast = useToast()
  const refresh = useRefreshWork()
  const [scope, setScope] = useState<'mine' | 'team'>('mine')
  const dashboard = useDashboard(scope)
  const [openTask, setOpenTask] = useState<TaskDto | null>(null)
  const [newTask, setNewTask] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const editable = canEdit()

  const hour = new Date().getHours()
  const greeting = t(hour < 12 ? 'dashboard.morning' : hour < 18 ? 'dashboard.afternoon' : 'dashboard.evening', { name: user?.fullName.split(' ')[0] ?? '' })

  const upcomingByDay = useMemo(() => {
    const groups = new Map<string, TaskDto[]>()
    for (const task of dashboard.data?.upcoming ?? []) {
      // Grouped by local calendar day, not by the UTC date in the ISO string.
      const key = new Date(task.dueAt).toDateString()
      groups.set(key, [...(groups.get(key) ?? []), task])
    }
    return [...groups.entries()]
  }, [dashboard.data])

  const complete = async (task: TaskDto) => {
    try {
      await api.post(`/tasks/${task.id}/complete`, {})
      toast.ok(t('common.saved'))
      refresh(task.businessId)
    } catch (error) {
      toast.error(error)
    }
  }

  if (dashboard.isLoading) return <Loading />
  if (dashboard.error || !dashboard.data) return <ErrorBlock error={dashboard.error} onRetry={() => dashboard.refetch()} />
  const d = dashboard.data
  const maxPipeline = Math.max(1, ...ACTIVE_PIPELINE.map((s) => d.pipeline[s] ?? 0))
  const callsWaiting = [...d.overdue, ...d.today].filter((task) => task.status === 'OPEN' && task.businessId).length

  return (
    <div>
      <PageHeader
        title={greeting}
        subtitle={fmtWeekday(new Date(), lang)}
        actions={
          <>
            {isSupervisor && (
              <Choice size="sm" options={[{ value: 'mine', label: t('dashboard.scopeMine') }, { value: 'team', label: t('dashboard.scopeTeam') }]} value={scope} onChange={setScope} />
            )}
            {editable && (
              <>
                <button type="button" className="btn-secondary" onClick={() => setNewTask(true)}>
                  <CalendarPlus className="size-4" /> <span className="hidden sm:inline">{t('tasks.newTask')}</span>
                </button>
                <button type="button" className="btn-secondary" onClick={() => setAddOpen(true)}>
                  <Plus className="size-4" /> <span className="hidden sm:inline">{t('dashboard.quickAdd')}</span>
                </button>
              </>
            )}
            <Link to="/calls" className="btn-primary">
              <Phone className="size-4" /> {t('dashboard.startCalling')}
              {callsWaiting > 0 && <span className="rounded-full bg-surface/25 px-1.5 text-xs">{callsWaiting}</span>}
            </Link>
          </>
        }
      />

      <div className="mb-5 grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-6">
        <Stat label={t('dashboard.callsToday')} value={d.stats.callsToday} />
        <Stat label={t('dashboard.visitsToday')} value={d.stats.visitsToday} />
        <Stat label={t('dashboard.meetingsToday')} value={d.stats.meetingsToday} />
        <Stat label={t('dashboard.weekActivity')} value={d.stats.activitiesThisWeek} />
        <Stat label={t('dashboard.newLeadsWeek')} value={d.stats.newLeadsThisWeek} />
        <Stat label={t('dashboard.salesMonth')} value={money(d.stats.salesThisMonth)} sub={`${d.stats.purchasesThisMonth} ${t('reports.purchases').toLowerCase()}`} tone="text-emerald-700" />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {d.overdue.length > 0 && (
            <section className="card border-rose-200 p-3">
              <h2 className="mb-1 flex items-center gap-2 px-2 text-sm font-semibold text-rose-700">
                <TriangleAlert className="size-4" /> {t('dashboard.overdue')} <span className="rounded-full bg-rose-100 px-2 text-xs">{d.overdue.length}</span>
              </h2>
              {d.overdue.map((task) => <TaskRow key={task.id} task={task} showDate onOpen={setOpenTask} onComplete={complete} editable={editable} />)}
            </section>
          )}

          <section className="card p-3">
            <h2 className="mb-1 px-2 text-sm font-semibold">{t('dashboard.today')}</h2>
            {d.today.length === 0 ? (
              <EmptyState title={t('dashboard.nothingToday')} />
            ) : (
              d.today.map((task) => <TaskRow key={task.id} task={task} onOpen={setOpenTask} onComplete={complete} editable={editable} />)
            )}
          </section>

          <section className="card p-3">
            <h2 className="mb-1 px-2 text-sm font-semibold">{t('dashboard.upcoming', { n: d.upcomingDays })}</h2>
            {upcomingByDay.length === 0 && <EmptyState title="-" />}
            {upcomingByDay.map(([day, tasks]) => (
              <div key={day} className="mb-2">
                <div className="px-2 pt-2 text-xs font-medium uppercase tracking-wide text-muted">{fmtWeekday(tasks[0].dueAt, lang, 'EEEE, d MMM')}</div>
                {tasks.map((task) => <TaskRow key={task.id} task={task} onOpen={setOpenTask} onComplete={complete} editable={editable} />)}
              </div>
            ))}
          </section>
        </div>

        <div className="space-y-4">
          <section className="card p-3">
            <h2 className="mb-2 flex items-center gap-2 px-1 text-sm font-semibold">
              <Flame className="size-4 text-raspberry" /> {t('dashboard.alerts')}
            </h2>
            {d.alerts.length === 0 && <EmptyState title={t('dashboard.noAlerts')} />}
            <ul className="space-y-1">
              {d.alerts.map((alert, index) => <AlertRow key={`${alert.kind}-${alert.businessId}-${index}`} alert={alert} />)}
            </ul>
          </section>

          <section className="card p-3">
            <h2 className="mb-2 px-1 text-sm font-semibold">{t('dashboard.pipeline')}</h2>
            <div className="space-y-1.5">
              {ACTIVE_PIPELINE.map((status) => (
                <Link key={status} to={`/businesses?status=${status}`} className="group flex items-center gap-2 rounded-lg px-1 py-0.5 hover:bg-canvas">
                  <span className="w-32 truncate text-xs text-muted group-hover:text-ink">{t(`status.${status}`)}</span>
                  <span className="h-2.5 flex-1 overflow-hidden rounded-full bg-canvas">
                    <span className={`block h-full rounded-full ${STATUS_STYLE[status].dot}`} style={{ width: `${((d.pipeline[status] ?? 0) / maxPipeline) * 100}%` }} />
                  </span>
                  <span className="w-8 text-right text-xs font-semibold tabular-nums">{d.pipeline[status] ?? 0}</span>
                </Link>
              ))}
              <div className="flex gap-3 px-1 pt-1 text-xs text-muted">
                {STATUSES.filter((s) => !ACTIVE_PIPELINE.includes(s)).map((s) => (
                  <Link key={s} to={`/businesses?status=${s}`} className="hover:text-ink">{t(`status.${s}`)}: {d.pipeline[s] ?? 0}</Link>
                ))}
              </div>
            </div>
          </section>

          <section className="card p-3">
            <h2 className="mb-2 flex items-center gap-2 px-1 text-sm font-semibold">
              <ShoppingCart className="size-4 text-emerald-600" /> {t('dashboard.recentPurchases')}
            </h2>
            {d.recentPurchases.length === 0 && <EmptyState title={t('business.noPurchases')} />}
            {d.recentPurchases.map((p) => (
              <Link key={p.id} to={`/businesses/${p.businessId}`} className="flex items-center gap-2 rounded-lg px-1 py-1.5 text-sm hover:bg-canvas">
                <span className="w-14 shrink-0 text-xs text-muted">{fmtShortDate(p.purchaseDate, lang)}</span>
                <span className="min-w-0 flex-1 truncate">{p.businessName}</span>
                <span className="font-medium tabular-nums">{money(p.total)}</span>
              </Link>
            ))}
          </section>
        </div>
      </div>

      <TaskDialog open={Boolean(openTask)} task={openTask} onClose={() => setOpenTask(null)} />
      <TaskDialog open={newTask} onClose={() => setNewTask(false)} />
      <QuickAddDialog open={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  )
}

function AlertRow({ alert }: { alert: Alert }) {
  const { t, lang } = useI18n()
  const detail = lang === 'ka' ? alert.detailKa : alert.detailEn
  const icon = alert.kind === 'REORDER' ? <ShoppingCart className="size-4 text-emerald-600" />
    : alert.kind === 'SAMPLES' ? <Sparkles className="size-4 text-orange-500" />
    : <Flame className="size-4 text-slate-400" />
  return (
    <li>
      <Link to={alert.kind === 'STALE' || alert.kind === 'NEVER_CONTACTED' ? `/calls/${alert.businessId}` : `/businesses/${alert.businessId}`} className="flex gap-2.5 rounded-xl px-2 py-2 hover:bg-canvas">
        <span className="mt-0.5">{icon}</span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium">{alert.businessName}</span>
          <span className="block text-xs text-muted">
            {t(`alert.${alert.kind}`, { days: alert.days ?? '', detail: detail ?? '' })}
            {alert.kind === 'REORDER' && detail ? ` (${detail})` : ''}
          </span>
        </span>
      </Link>
    </li>
  )
}
