import { useMemo, useState } from 'react'
import { Eraser, Plus } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { useLookups, useOverdue, useRefreshWork, useTasks } from '../lib/queries'
import { fmtWeekday } from '../lib/format'
import type { TaskDto } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, Loading, PageHeader, Tabs } from '../components/ui'
import { TaskRow } from '../components/TaskRow'
import { TaskDialog } from '../components/TaskDialog'
import { useToast } from '../components/Toast'

type Tab = 'overdue' | 'today' | 'upcoming' | 'done'

function startOfDay(offset: number): Date {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() + offset)
  return date
}

export function TasksPage() {
  const { t, lang } = useI18n()
  const { user } = useAuth()
  const { canEdit } = useMode()
  const toast = useToast()
  const refresh = useRefreshWork()
  const lookups = useLookups()
  const [tab, setTab] = useState<Tab>('today')
  const [who, setWho] = useState<string>('me')
  const [open, setOpen] = useState<TaskDto | 'new' | null>(null)
  const userId = who === 'me' ? user?.id ?? null : who === 'all' ? null : Number(who)
  const editable = canEdit()

  const overdue = useOverdue(userId)
  const range = useMemo(() => {
    if (tab === 'today') return { from: startOfDay(0).toISOString(), to: startOfDay(1).toISOString(), status: ['OPEN', 'DONE'] }
    if (tab === 'upcoming') return { from: startOfDay(1).toISOString(), to: startOfDay(31).toISOString(), status: ['OPEN'] }
    return { from: startOfDay(-14).toISOString(), to: startOfDay(1).toISOString(), status: ['DONE', 'CANCELLED'] }
  }, [tab])
  const tasks = useTasks({ ...range, userId }, tab !== 'overdue')
  const today = useTasks({ from: startOfDay(0).toISOString(), to: startOfDay(1).toISOString(), status: ['OPEN'], userId })

  const shown = tab === 'overdue' ? overdue.data ?? [] : tab === 'done' ? [...(tasks.data ?? [])].reverse() : tasks.data ?? []
  const byDay = useMemo(() => {
    const groups = new Map<string, TaskDto[]>()
    for (const task of shown) {
      const key = new Date(task.dueAt).toDateString()
      groups.set(key, [...(groups.get(key) ?? []), task])
    }
    return [...groups.values()]
  }, [shown])

  const complete = async (task: TaskDto) => {
    try {
      await api.post(`/tasks/${task.id}/complete`, {})
      refresh(task.businessId)
    } catch (error) {
      toast.error(error)
    }
  }

  // The spreadsheet's "next step" column became open tasks. They are guesses, and one press clears them.
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

  const loading = tab === 'overdue' ? overdue.isLoading : tasks.isLoading
  const hasImported = shown.some((task) => task.imported && task.status === 'OPEN')

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={t('tasks.title')}
        actions={
          <>
            <select className="input w-auto max-w-full" value={who} onChange={(e) => setWho(e.target.value)}>
              <option value="me">{t('common.me')}</option>
              <option value="all">{t('common.team')}</option>
              {lookups.data?.users.filter((u) => u.active && u.id !== user?.id).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
            </select>
            {editable && hasImported && (
              <button type="button" className="btn-secondary" onClick={() => void clearImported()} title={t('tasks.importedHint')}>
                <Eraser className="size-4" /> {t('tasks.clearImported')}
              </button>
            )}
            {editable && <button type="button" className="btn-primary" onClick={() => setOpen('new')}><Plus className="size-4" /> {t('tasks.newTask')}</button>}
          </>
        }
      />
      <Tabs<Tab>
        value={tab}
        onChange={setTab}
        tabs={[
          { key: 'overdue', label: t('tasks.overdue'), count: overdue.data?.length },
          { key: 'today', label: t('tasks.today'), count: today.data?.length },
          { key: 'upcoming', label: t('tasks.upcoming') },
          { key: 'done', label: t('tasks.done') },
        ]}
      />
      <div className="mt-4">
        {loading ? <Loading /> : shown.length === 0 ? <div className="card"><EmptyState title={t('tasks.empty')} /></div> : (
          <div className="space-y-3">
            {byDay.map((group) => (
              <section key={group[0].id} className="card p-2">
                <div className="px-2 pb-1 pt-1.5 text-xs font-medium uppercase tracking-wide text-muted">{fmtWeekday(group[0].dueAt, lang, 'EEEE, d MMM')}</div>
                {group.map((task) => <TaskRow key={task.id} task={task} onOpen={setOpen} onComplete={complete} editable={editable} />)}
              </section>
            ))}
          </div>
        )}
      </div>
      <TaskDialog open={open !== null} onClose={() => setOpen(null)} task={open === 'new' ? null : open} />
    </div>
  )
}
