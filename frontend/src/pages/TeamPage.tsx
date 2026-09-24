import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Eye, Phone, Radio, Users } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { fmtDateTime, fmtShortDate, money } from '../lib/format'
import { todayIso } from '../lib/format'
import type { ActivityDto, TaskDto } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, ErrorBlock, Field, Loading, PageHeader, Tabs } from '../components/ui'
import { TaskRow } from '../components/TaskRow'
import { TaskDialog } from '../components/TaskDialog'

interface Where { userId: number; userName: string; businessId: number | null; businessName: string | null; since: string; lastSeen: string }
interface TeamRow {
  user: { id: number; fullName: string }
  role: string; active: boolean
  calls: number; callsReached: number; visits: number; meetings: number; samples: number
  newLeads: number; newCustomers: number
  purchases: number; sales: number; bottles: number; tasksDone: number
  lastActivityAt: string | null; lastLoginAt: string | null; nowOn: Where | null
}
interface TeamView { from: string; to: string; rows: TeamRow[] }

type Tab = 'work' | 'day' | 'history'

/**
 * The supervisor's room: what each person did over a period, when they were last at work, and who is on
 * the phone with whom right now. Pick somebody and their own day opens underneath; from there, "look
 * through their eyes" turns the whole CRM into their screens - read-only, all of it.
 */
export function TeamPage() {
  const { t, lang } = useI18n()
  const navigate = useNavigate()
  const { isSupervisor } = useAuth()
  const { setViewAs } = useMode()
  const [from, setFrom] = useState(() => todayIso().slice(0, 8) + '01')
  const [to, setTo] = useState(todayIso)
  const [picked, setPicked] = useState<TeamRow | null>(null)

  const team = useQuery({
    queryKey: ['team', from, to],
    queryFn: () => api.get<TeamView>('/team', { from, to }),
    enabled: isSupervisor,
    refetchInterval: 60_000,
  })
  // Who is working somewhere at this moment; refreshed often enough to be true.
  const now = useQuery({
    queryKey: ['team-now'],
    queryFn: () => api.get<Where[]>('/team/now'),
    enabled: isSupervisor,
    refetchInterval: 20_000,
  })

  if (!isSupervisor) return <div className="card"><EmptyState title={t('errors.FORBIDDEN')} /></div>

  const rows = team.data?.rows ?? []
  const working = now.data ?? []

  return (
    <div>
      <PageHeader
        title={<span className="flex items-center gap-2"><Users className="size-5 text-brand-600" /> {t('team.title')}</span>}
        subtitle={t('team.subtitle')}
        actions={
          <>
            <Field label={t('common.from')}><input type="date" className="input" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
            <Field label={t('common.to')}><input type="date" className="input" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
          </>
        }
      />

      {/* Right now */}
      <section className="card mb-4 p-3">
        <h2 className="mb-2 flex items-center gap-2 px-1 text-sm font-semibold">
          <Radio className="size-4 text-emerald-600" /> {t('team.rightNow')}
        </h2>
        {working.length === 0 ? (
          <p className="px-1 text-sm text-muted">{t('team.nobodyNow')}</p>
        ) : (
          <ul className="space-y-1 px-1">
            {working.map((where) => (
              <li key={where.userId} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="size-2 shrink-0 animate-pulse rounded-full bg-emerald-500" />
                <b>{where.userName}</b>
                {where.businessId ? (
                  <>
                    <span className="text-muted">{t('team.isOn')}</span>
                    <Link to={`/businesses/${where.businessId}`} className="font-medium text-brand-700 hover:underline">{where.businessName}</Link>
                  </>
                ) : <span className="text-muted">{t('team.isInApp')}</span>}
                <span className="text-xs text-muted">{t('team.since', { at: fmtDateTime(where.since, lang) })}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {team.isLoading ? <Loading /> : team.error ? <ErrorBlock error={team.error} onRetry={() => team.refetch()} /> : (
        <section className="card overflow-x-auto p-2">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted">
              <tr>
                <th className="px-2 py-2">{t('common.name')}</th>
                <th className="px-2 py-2">{t('reports.calls')}</th>
                <th className="px-2 py-2">{t('reports.visits')}</th>
                <th className="px-2 py-2">{t('reports.meetings')}</th>
                <th className="px-2 py-2">{t('reports.samples')}</th>
                <th className="px-2 py-2">{t('reports.newLeads')}</th>
                <th className="px-2 py-2">{t('reports.newCustomers')}</th>
                <th className="px-2 py-2">{t('reports.sales')}</th>
                <th className="px-2 py-2">{t('team.lastSeen')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.user.id} className={`border-t border-line ${picked?.user.id === row.user.id ? 'bg-brand-50/60' : ''}`}>
                  <td className="px-2 py-2">
                    <button type="button" className="text-left font-medium hover:text-brand-700" onClick={() => setPicked(row)}>
                      {row.user.fullName}
                    </button>
                    <div className="text-xs text-muted">
                      {t(`role.${row.role}`)}{row.active ? '' : ` · ${t('common.inactive')}`}
                      {row.nowOn && <span className="ml-1.5 text-emerald-700">● {row.nowOn.businessName ?? t('team.isInApp')}</span>}
                    </div>
                  </td>
                  <td className="px-2 py-2 tabular-nums">{row.calls}<span className="text-xs text-muted"> / {row.callsReached}</span></td>
                  <td className="px-2 py-2 tabular-nums">{row.visits}</td>
                  <td className="px-2 py-2 tabular-nums">{row.meetings}</td>
                  <td className="px-2 py-2 tabular-nums">{row.samples}</td>
                  <td className="px-2 py-2 tabular-nums">{row.newLeads}</td>
                  <td className="px-2 py-2 tabular-nums">{row.newCustomers}</td>
                  <td className="whitespace-nowrap px-2 py-2 tabular-nums">{money(row.sales)}</td>
                  <td className="whitespace-nowrap px-2 py-2 text-xs text-muted">
                    {row.lastActivityAt ? fmtDateTime(row.lastActivityAt, lang) : '-'}
                  </td>
                  <td className="whitespace-nowrap px-2 py-2 text-right">
                    <button
                      type="button"
                      className="btn-secondary px-2 py-1 text-xs"
                      onClick={() => { setViewAs({ id: row.user.id, fullName: row.user.fullName }); navigate('/') }}
                    >
                      <Eye className="size-3.5" /> {t('team.viewAs')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {picked && <PersonDetail row={picked} onClose={() => setPicked(null)} />}
    </div>
  )
}

/** One person opened up: their day, and everything they wrote down. */
function PersonDetail({ row, onClose }: { row: TeamRow; onClose: () => void }) {
  const { t, lang } = useI18n()
  const [tab, setTab] = useState<Tab>('work')
  const [openTask, setOpenTask] = useState<TaskDto | null>(null)
  const userId = row.user.id

  const activities = useQuery({
    queryKey: ['team-activities', userId],
    queryFn: () => api.get<ActivityDto[]>('/activities', { userId, from: new Date(Date.now() - 30 * 864e5).toISOString() }),
  })
  const tasks = useQuery({
    queryKey: ['team-tasks', userId],
    queryFn: () => api.get<TaskDto[]>('/tasks', {
      from: new Date(new Date().setHours(0, 0, 0, 0)).toISOString(),
      to: new Date(Date.now() + 14 * 864e5).toISOString(),
      status: ['OPEN', 'DONE'],
      userId,
    }),
  })
  const history = useQuery({
    queryKey: ['team-history', userId],
    queryFn: () => api.get<{ items: { id: number; at: string; action: string; entity: string; summary: string | null; businessName: string | null }[] }>('/history', { userId, page: 0 }),
  })

  return (
    <section className="card mt-4 p-3">
      <div className="mb-2 flex flex-wrap items-center gap-2 px-1">
        <h2 className="text-base font-semibold">{row.user.fullName}</h2>
        {row.nowOn && (
          <span className="chip border-emerald-300 bg-emerald-50 text-emerald-800">
            <Phone className="size-3" /> {row.nowOn.businessName ?? t('team.isInApp')}
          </span>
        )}
        <span className="text-xs text-muted">
          {t('team.lastSeen')}: {row.lastActivityAt ? fmtDateTime(row.lastActivityAt, lang) : '-'}
          {row.lastLoginAt && ` · ${t('team.lastLogin')}: ${fmtDateTime(row.lastLoginAt, lang)}`}
        </span>
        <button type="button" className="btn-ghost ml-auto text-xs" onClick={onClose}>{t('common.close')}</button>
      </div>

      <div className="mb-3 grid grid-cols-2 gap-2 px-1 sm:grid-cols-4 lg:grid-cols-7">
        {[
          [t('reports.calls'), `${row.calls} / ${row.callsReached}`],
          [t('reports.visits'), String(row.visits)],
          [t('reports.meetings'), String(row.meetings)],
          [t('reports.samples'), String(row.samples)],
          [t('reports.newLeads'), String(row.newLeads)],
          [t('reports.newCustomers'), String(row.newCustomers)],
          [t('reports.tasksDone'), String(row.tasksDone)],
          [t('reports.sales'), money(row.sales)],
        ].map(([label, value]) => (
          <div key={label} className="rounded-xl border border-line px-2.5 py-2">
            <div className="text-[11px] text-muted">{label}</div>
            <div className="text-base font-semibold tabular-nums">{value}</div>
          </div>
        ))}
      </div>

      <Tabs<Tab>
        value={tab}
        onChange={setTab}
        tabs={[
          { key: 'work', label: t('team.whatTheyDid'), count: activities.data?.length },
          { key: 'day', label: t('team.theirPlan'), count: tasks.data?.length },
          { key: 'history', label: t('nav.history') },
        ]}
      />

      <div className="mt-3">
        {tab === 'work' && (
          activities.isLoading ? <Loading /> : (activities.data ?? []).length === 0 ? <EmptyState title={t('business.noHistory')} /> : (
            <ul className="divide-y divide-line">
              {(activities.data ?? []).map((a) => (
                <li key={a.id} className="flex flex-wrap items-center gap-x-2 gap-y-0.5 py-2 text-sm">
                  <span className="w-28 shrink-0 text-xs text-muted">{fmtDateTime(a.occurredAt, lang)}</span>
                  <b>{t(`activityType.${a.type}`)}</b>
                  <Link to={`/businesses/${a.businessId}`} className="font-medium hover:text-brand-700">{a.businessName}</Link>
                  <span className="text-xs text-muted">{a.results.map((r) => t(`activityResult.${r}`)).join(', ')}</span>
                  {a.resultNote && <span className="w-full text-xs italic text-muted">{a.resultNote}</span>}
                  {a.notes && <span className="w-full text-xs text-muted">{a.notes}</span>}
                </li>
              ))}
            </ul>
          )
        )}

        {tab === 'day' && (
          tasks.isLoading ? <Loading /> : (tasks.data ?? []).length === 0 ? <EmptyState title={t('tasks.empty')} /> : (
            <div>{(tasks.data ?? []).map((task) => (
              <TaskRow key={task.id} task={task} showDate editable={false} onOpen={setOpenTask} />
            ))}</div>
          )
        )}

        {tab === 'history' && (
          history.isLoading ? <Loading /> : (history.data?.items ?? []).length === 0 ? <EmptyState title={t('history.empty')} /> : (
            <ul className="divide-y divide-line">
              {(history.data?.items ?? []).map((item) => (
                <li key={item.id} className="flex flex-wrap items-center gap-x-2 py-1.5 text-sm">
                  <span className="w-28 shrink-0 text-xs text-muted">{fmtShortDate(item.at, lang)}</span>
                  <span className="text-xs">{t(`history.action.${item.action}`, { entity: item.entity })}</span>
                  {item.businessName && <b className="font-medium">{item.businessName}</b>}
                  {item.summary && <span className="text-xs text-muted">{item.summary}</span>}
                </li>
              ))}
            </ul>
          )
        )}
      </div>

      <TaskDialog open={Boolean(openTask)} task={openTask} onClose={() => setOpenTask(null)} />
    </section>
  )
}
