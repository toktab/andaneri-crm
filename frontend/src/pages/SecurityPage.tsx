import { useEffect, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Ban, ChevronLeft, ChevronRight, ShieldCheck, X } from 'lucide-react'
import { api, type Params } from '../lib/api'
import { fmtDateTime } from '../lib/format'
import type { ChangeEntry, IpRow, IpRuleDto, LoginEvent, LogPage, RequestEntry, SecurityOverview, UserDto } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, ErrorBlock, Field, Loading, PageHeader, Stat, Tabs } from '../components/ui'
import { useToast } from '../components/Toast'

type Tab = 'overview' | 'logins' | 'ips' | 'rules' | 'requests' | 'changes' | 'users'
const PAGE = 50

/**
 * Root's view of everything: sign-ins and failed attempts with the password typed, every address that
 * reached the API, the whitelist and blocks, every request, and every row anyone added, changed or deleted.
 */
export function SecurityPage() {
  const { t } = useI18n()
  const [tab, setTab] = useState<Tab>('overview')
  const [userFilter, setUserFilter] = useState<UserDto | null>(null)

  return (
    <div className="space-y-4">
      <PageHeader title={<span className="flex items-center gap-2"><ShieldCheck className="size-6 text-brand-600" /> {t('security.title')}</span>} subtitle={t('security.subtitle')} />
      <Tabs<Tab>
        value={tab}
        onChange={(next) => { setTab(next); if (next === 'overview' || next === 'rules') setUserFilter(null) }}
        tabs={(['overview', 'logins', 'ips', 'rules', 'requests', 'changes', 'users'] as Tab[]).map((key) => ({ key, label: t(`security.tabs.${key}`) }))}
      />
      {userFilter && (
        <span className="chip border-brand-300 bg-brand-50 text-brand-800">
          {t('security.filterUser', { name: userFilter.username })}
          <button type="button" onClick={() => setUserFilter(null)} aria-label="clear"><X className="size-3.5" /></button>
        </span>
      )}
      {tab === 'overview' && <Overview />}
      {tab === 'logins' && <Logins userId={userFilter?.id} />}
      {tab === 'ips' && <Ips userId={userFilter?.id} />}
      {tab === 'rules' && <Rules />}
      {tab === 'requests' && <Requests userId={userFilter?.id} />}
      {tab === 'changes' && <Changes userId={userFilter?.id} />}
      {tab === 'users' && <Users onShow={(user, next) => { setUserFilter(user); setTab(next) }} />}
    </div>
  )
}

function useRoot<T>(path: string, params?: Params) {
  return useQuery({ queryKey: ['root', path, params], queryFn: () => api.get<T>(path, params), placeholderData: (previous) => previous, refetchInterval: 30_000 })
}

/** Block or whitelist one address; used from the sign-ins, the IP list and the overview. */
function useIpAction() {
  const { t } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  return async (ip: string, kind: 'ALLOW' | 'BLOCK') => {
    try {
      await api.post('/root/ip-rules', { pattern: ip, kind, note: null, expiresAt: null })
      toast.ok(t('security.ruleSaved'))
      client.invalidateQueries({ queryKey: ['root'] })
    } catch (error) {
      toast.error(error)
    }
  }
}

function Overview() {
  const { t, lang } = useI18n()
  const overview = useRoot<SecurityOverview>('/root/overview')
  if (overview.isLoading) return <Loading />
  if (overview.error || !overview.data) return <ErrorBlock error={overview.error} onRetry={() => overview.refetch()} />
  const o = overview.data
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        {(Object.keys(o.counts) as (keyof typeof o.counts)[]).map((key) => (
          <Stat key={key} label={t(`security.stats.${key}`)} value={o.counts[key]} tone={key === 'failedLogins24h' && o.counts[key] > 0 ? 'text-rose-600' : 'text-ink'} />
        ))}
      </div>
      <div className="flex flex-wrap gap-2 text-sm">
        <span className="chip border-line bg-surface">{t('security.yourIp', { ip: o.yourIp })}</span>
        <span className={`chip ${o.whitelistOn ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-line bg-surface text-muted'}`}>
          {o.whitelistOn ? t('security.whitelistOn') : t('security.whitelistOff')}
        </span>
        <span className={`chip ${o.liveBlocks ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-line bg-surface text-muted'}`}>{t('security.liveBlocks', { n: o.liveBlocks })}</span>
      </div>
      <section className="card p-3">
        <h2 className="mb-2 px-1 text-sm font-semibold">{t('security.recentFailures')}</h2>
        <LoginTable items={o.recentFailures} lang={lang} />
      </section>
    </div>
  )
}

function Logins({ userId }: { userId?: number }) {
  const { lang, t } = useI18n()
  const [success, setSuccess] = useState<'' | 'true' | 'false'>('')
  const [username, setUsername] = useState('')
  const [ip, setIp] = useState('')
  const [page, setPage] = useState(0)
  useEffect(() => setPage(0), [success, username, ip, userId])
  const logins = useRoot<LogPage<LoginEvent>>('/root/logins', { success: success || undefined, username, ip, userId, page, size: PAGE })
  return (
    <div className="space-y-3">
      <Filters>
        <select className="input w-auto" value={success} onChange={(e) => setSuccess(e.target.value as typeof success)}>
          <option value="">{t('security.all')}</option>
          <option value="true">{t('security.onlyOk')}</option>
          <option value="false">{t('security.onlyFailed')}</option>
        </select>
        <input className="input w-44" placeholder={t('security.username')} value={username} onChange={(e) => setUsername(e.target.value)} />
        <input className="input w-40" placeholder={t('security.ip')} value={ip} onChange={(e) => setIp(e.target.value)} />
      </Filters>
      <p className="text-xs text-muted">{t('security.maskedNote')}</p>
      <Paged query={logins} page={page} setPage={setPage}>
        {(items) => <LoginTable items={items} lang={lang} />}
      </Paged>
    </div>
  )
}

function LoginTable({ items, lang }: { items: LoginEvent[]; lang: string }) {
  const { t } = useI18n()
  const ipAction = useIpAction()
  if (items.length === 0) return <EmptyState title={t('security.noData')} />
  return (
    <Table head={[t('security.when'), t('security.username'), t('security.status'), t('security.attempted'), t('security.ip'), t('security.agent'), '']}>
      {items.map((e) => (
        <tr key={e.id} className="border-t border-line">
          <Td nowrap>{fmtDateTime(e.createdAt, lang as 'ka' | 'en')}</Td>
          <Td className="font-medium">{e.username}</Td>
          <Td><span className={`rounded-md px-1.5 py-0.5 text-xs font-medium ${e.success ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}`}>{t(`security.reason.${e.reason}`)}</span></Td>
          <Td className="font-mono text-xs">{e.attemptedPassword ?? ''}</Td>
          <Td className="font-mono text-xs" nowrap>{e.ip}</Td>
          <Td className="max-w-64 truncate text-xs text-muted" title={e.userAgent ?? ''}>{e.userAgent}</Td>
          <Td nowrap>{!e.success && <button type="button" className="btn-ghost px-2 py-1 text-xs text-rose-700" onClick={() => void ipAction(e.ip, 'BLOCK')}><Ban className="size-3.5" /> {t('security.block')}</button>}</Td>
        </tr>
      ))}
    </Table>
  )
}

function Ips({ userId }: { userId?: number }) {
  const { t, lang } = useI18n()
  const ips = useRoot<IpRow[]>('/root/ips', { userId })
  const ipAction = useIpAction()
  if (ips.isLoading) return <Loading />
  if (ips.error || !ips.data) return <ErrorBlock error={ips.error} onRetry={() => ips.refetch()} />
  if (ips.data.length === 0) return <div className="card"><EmptyState title={t('security.noData')} /></div>
  return (
    <div className="card overflow-hidden">
      <Table head={[t('security.ip'), t('security.firstSeen'), t('security.lastSeen'), t('security.okLogins'), t('security.failedLogins'), t('security.requests'), t('security.accounts'), '']}>
        {ips.data.map(({ activity: a, rule }) => (
          <tr key={a.ip} className="border-t border-line">
            <Td className="font-mono text-xs" nowrap>
              {a.ip}
              {rule && <span className={`ml-2 rounded px-1.5 py-0.5 font-sans text-[11px] font-medium ${rule === 'BLOCK' ? 'bg-rose-100 text-rose-800' : 'bg-emerald-100 text-emerald-800'}`}>{rule === 'BLOCK' ? t('security.blocked') : t('security.allowed')}</span>}
            </Td>
            <Td nowrap>{fmtDateTime(a.firstSeen, lang)}</Td>
            <Td nowrap>{fmtDateTime(a.lastSeen, lang)}</Td>
            <Td className="tabular-nums">{a.successfulLogins}</Td>
            <Td className={`tabular-nums ${a.failedLogins ? 'font-semibold text-rose-600' : ''}`}>{a.failedLogins}</Td>
            <Td className="tabular-nums">{a.requests}</Td>
            <Td className="max-w-56 truncate">{a.usernames.join(', ')}</Td>
            <Td nowrap>
              {rule !== 'ALLOW' && <button type="button" className="btn-ghost px-2 py-1 text-xs text-emerald-700" onClick={() => void ipAction(a.ip, 'ALLOW')}>{t('security.allow')}</button>}
              {rule !== 'BLOCK' && <button type="button" className="btn-ghost px-2 py-1 text-xs text-rose-700" onClick={() => void ipAction(a.ip, 'BLOCK')}>{t('security.block')}</button>}
            </Td>
          </tr>
        ))}
      </Table>
    </div>
  )
}

function Rules() {
  const { t, lang } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  const rules = useRoot<IpRuleDto[]>('/root/ip-rules')
  const settings = useRoot<Record<string, number>>('/root/settings')
  const [values, setValues] = useState<Record<string, number>>({})
  const [form, setForm] = useState({ pattern: '', kind: 'ALLOW' as 'ALLOW' | 'BLOCK', note: '', expiresAt: '' })

  useEffect(() => {
    if (settings.data) setValues(settings.data)
  }, [settings.data])

  const saveSettings = async (next: Record<string, number>) => {
    try {
      setValues(await api.put<Record<string, number>>('/root/settings', next))
      toast.ok(t('common.saved'))
      client.invalidateQueries({ queryKey: ['root'] })
    } catch (error) {
      toast.error(error)
    }
  }
  const addRule = async () => {
    try {
      await api.post('/root/ip-rules', { pattern: form.pattern, kind: form.kind, note: form.note || null, expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null })
      setForm({ pattern: '', kind: 'ALLOW', note: '', expiresAt: '' })
      toast.ok(t('security.ruleSaved'))
      client.invalidateQueries({ queryKey: ['root'] })
    } catch (error) {
      toast.error(error)
    }
  }
  const remove = async (id: number) => {
    try {
      await api.del(`/root/ip-rules/${id}`)
      client.invalidateQueries({ queryKey: ['root'] })
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[22rem_minmax(0,1fr)]">
      <div className="space-y-4">
        <section className="card space-y-3 p-4">
          <label className="flex items-start gap-3">
            <input type="checkbox" className="mt-1 size-4 accent-brand-600" checked={values.ip_whitelist_enabled === 1}
              onChange={(e) => void saveSettings({ ...values, ip_whitelist_enabled: e.target.checked ? 1 : 0 })} />
            <span>
              <span className="block text-sm font-semibold">{t('security.whitelistToggle')}</span>
              <span className="block text-xs text-muted">{t('security.whitelistHint')}</span>
            </span>
          </label>
          {(['max_failed_logins', 'lock_minutes', 'log_retention_days'] as const).map((key) => (
            <Field key={key} label={t(`security.settings.${key}`)}>
              <input type="number" min={1} className="input" value={values[key] ?? ''} onChange={(e) => setValues({ ...values, [key]: Number(e.target.value) })} />
            </Field>
          ))}
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="size-4 accent-brand-600" checked={values.request_log_enabled === 1} onChange={(e) => setValues({ ...values, request_log_enabled: e.target.checked ? 1 : 0 })} />
            {t('security.settings.request_log_enabled')}
          </label>
          <button type="button" className="btn-primary w-full" onClick={() => void saveSettings(values)}>{t('common.save')}</button>
        </section>

        <section className="card space-y-3 p-4">
          <h2 className="text-sm font-semibold">{t('security.addRule')}</h2>
          <Field label={t('security.pattern')} hint={t('security.patternHint')}>
            <input className="input font-mono" value={form.pattern} onChange={(e) => setForm({ ...form, pattern: e.target.value })} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('security.kind')}>
              <select className="input" value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value as 'ALLOW' | 'BLOCK' })}>
                <option value="ALLOW">{t('security.allow')}</option>
                <option value="BLOCK">{t('security.block')}</option>
              </select>
            </Field>
            <Field label={t('security.expires')}>
              <input type="datetime-local" className="input" value={form.expiresAt} onChange={(e) => setForm({ ...form, expiresAt: e.target.value })} />
            </Field>
          </div>
          <Field label={t('security.note')}><input className="input" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field>
          <button type="button" className="btn-secondary w-full" disabled={!form.pattern.trim()} onClick={() => void addRule()}>{t('security.addRule')}</button>
        </section>
      </div>

      <div className="card overflow-hidden">
        {rules.isLoading ? <Loading /> : !rules.data?.length ? <EmptyState title={t('security.noData')} /> : (
          <Table head={[t('security.pattern'), t('security.kind'), t('security.note'), t('security.expires'), t('security.user'), '']}>
            {rules.data.map((r) => (
              <tr key={r.id} className={`border-t border-line ${r.live ? '' : 'opacity-50'}`}>
                <Td className="font-mono text-xs" nowrap>{r.pattern}</Td>
                <Td nowrap>
                  <span className={`rounded-md px-1.5 py-0.5 text-xs font-medium ${r.kind === 'BLOCK' ? 'bg-rose-100 text-rose-800' : 'bg-emerald-100 text-emerald-800'}`}>{r.kind === 'BLOCK' ? t('security.blocked') : t('security.allowed')}</span>
                  {r.automatic && <span className="ml-1 text-xs text-muted">{t('security.automatic')}</span>}
                </Td>
                <Td className="max-w-64 truncate">{r.note}</Td>
                <Td nowrap>{r.expiresAt ? fmtDateTime(r.expiresAt, lang) : t('security.never')}{!r.live && ` · ${t('security.expired')}`}</Td>
                <Td nowrap className="text-xs text-muted">{r.createdBy ?? ''} · {fmtDateTime(r.createdAt, lang)}</Td>
                <Td><button type="button" className="btn-ghost px-2 py-1 text-xs text-rose-700" onClick={() => void remove(r.id)}>{t('security.remove')}</button></Td>
              </tr>
            ))}
          </Table>
        )}
      </div>
    </div>
  )
}

function Requests({ userId }: { userId?: number }) {
  const { t, lang } = useI18n()
  const [method, setMethod] = useState('')
  const [path, setPath] = useState('')
  const [ip, setIp] = useState('')
  const [page, setPage] = useState(0)
  useEffect(() => setPage(0), [method, path, ip, userId])
  const requests = useRoot<LogPage<RequestEntry>>('/root/requests', { method, path, ip, userId, page, size: PAGE })
  return (
    <div className="space-y-3">
      <Filters>
        <select className="input w-auto" value={method} onChange={(e) => setMethod(e.target.value)}>
          <option value="">{t('security.method')}</option>
          {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
        <input className="input w-56" placeholder={t('security.path')} value={path} onChange={(e) => setPath(e.target.value)} />
        <input className="input w-40" placeholder={t('security.ip')} value={ip} onChange={(e) => setIp(e.target.value)} />
      </Filters>
      <Paged query={requests} page={page} setPage={setPage}>
        {(items) => (
          <Table head={[t('security.when'), t('security.user'), t('security.method'), t('security.path'), t('security.status'), t('security.duration'), t('security.ip')]}>
            {items.map((r) => (
              <tr key={r.id} className="border-t border-line">
                <Td nowrap>{fmtDateTime(r.createdAt, lang)}</Td>
                <Td>{r.username ?? '-'}</Td>
                <Td className="font-mono text-xs">{r.method}</Td>
                <Td className="max-w-md truncate font-mono text-xs" title={r.query ? `${r.path}?${r.query}` : r.path}>{r.path}{r.query ? `?${r.query}` : ''}</Td>
                <Td className={`tabular-nums ${r.status >= 400 ? 'font-semibold text-rose-600' : ''}`}>{r.status}</Td>
                <Td className="tabular-nums text-muted">{r.durationMs}</Td>
                <Td className="font-mono text-xs" nowrap>{r.ip}</Td>
              </tr>
            ))}
          </Table>
        )}
      </Paged>
    </div>
  )
}

function Changes({ userId }: { userId?: number }) {
  const { t, lang } = useI18n()
  const [entity, setEntity] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<number | null>(null)
  useEffect(() => setPage(0), [entity, action, userId])
  const changes = useRoot<LogPage<ChangeEntry>>('/root/changes', { entity, action, userId, page, size: PAGE })
  return (
    <div className="space-y-3">
      <Filters>
        <select className="input w-auto" value={entity} onChange={(e) => setEntity(e.target.value)}>
          <option value="">{t('security.entity')}</option>
          {['Business', 'Contact', 'Activity', 'Task', 'Comment', 'Purchase', 'ProductUsage', 'Interest', 'User', 'Workbook', 'BusinessSheet', 'CustomField', 'BusinessFieldValue', 'IpRule', 'Setting', 'Product']
            .map((name) => <option key={name} value={name}>{name}</option>)}
        </select>
        <select className="input w-auto" value={action} onChange={(e) => setAction(e.target.value)}>
          <option value="">{t('security.action')}</option>
          {['INSERT', 'UPDATE', 'DELETE'].map((a) => <option key={a} value={a}>{t(`security.actionName.${a}`)}</option>)}
        </select>
      </Filters>
      <Paged query={changes} page={page} setPage={setPage}>
        {(items) => (
          <Table head={[t('security.when'), t('security.user'), t('security.action'), t('security.entity'), t('security.field'), t('security.ip')]}>
            {items.map((c) => {
              const diff = parseDiff(c.changes)
              const expanded = open === c.id
              return (
                <tr key={c.id} className="border-t border-line align-top">
                  <Td nowrap>{fmtDateTime(c.createdAt, lang)}</Td>
                  <Td>{c.username ?? '-'}</Td>
                  <Td nowrap>
                    <span className={`rounded-md px-1.5 py-0.5 text-xs font-medium ${c.action === 'DELETE' ? 'bg-rose-100 text-rose-800' : c.action === 'INSERT' ? 'bg-emerald-100 text-emerald-800' : c.action === 'UPDATE' ? 'bg-sky-100 text-sky-800' : 'bg-canvas'}`}>
                      {t(`security.actionName.${c.action}`).startsWith('security.') ? c.action : t(`security.actionName.${c.action}`)}
                    </span>
                  </Td>
                  <Td nowrap>{c.entity} #{c.entityId}{c.businessId && c.entity !== 'Business' ? <span className="text-xs text-muted"> · Business #{c.businessId}</span> : null}</Td>
                  <Td className="min-w-72">
                    {c.summary && <div className="text-xs">{c.summary}</div>}
                    {diff.length > 0 && (
                      <button type="button" className="text-left text-xs text-brand-700" onClick={() => setOpen(expanded ? null : c.id)}>
                        {diff.map(([field]) => field).slice(0, expanded ? undefined : 4).join(', ')}{!expanded && diff.length > 4 ? ` +${diff.length - 4}` : ''}
                      </button>
                    )}
                    {expanded && (
                      <table className="mt-1 w-full text-xs">
                        <thead><tr className="text-muted"><th className="text-left font-medium">{t('security.field')}</th><th className="text-left font-medium">{t('security.before')}</th><th className="text-left font-medium">{t('security.after')}</th></tr></thead>
                        <tbody>
                          {diff.map(([field, before, after]) => (
                            <tr key={field} className="border-t border-line/60">
                              <td className="py-0.5 pr-2 font-mono">{field}</td>
                              <td className="py-0.5 pr-2 text-rose-700 line-through decoration-rose-300">{before ?? ''}</td>
                              <td className="py-0.5 text-emerald-700">{after ?? ''}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </Td>
                  <Td className="font-mono text-xs" nowrap>{c.ip ?? ''}</Td>
                </tr>
              )
            })}
          </Table>
        )}
      </Paged>
    </div>
  )
}

function parseDiff(text: string | null): [string, string | null, string | null][] {
  if (!text) return []
  try {
    return Object.entries(JSON.parse(text) as Record<string, [string | null, string | null]>).map(([field, [before, after]]) => [field, before, after])
  } catch {
    return []
  }
}

function Users({ onShow }: { onShow: (user: UserDto, tab: Tab) => void }) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const users = useRoot<UserDto[]>('/root/users')
  const revoke = async (user: UserDto) => {
    if (!window.confirm(t('security.revokeConfirm', { name: user.fullName }))) return
    try {
      await api.post(`/root/users/${user.id}/revoke-sessions`)
      toast.ok(t('security.revoked'))
    } catch (error) {
      toast.error(error)
    }
  }
  if (users.isLoading) return <Loading />
  if (users.error || !users.data) return <ErrorBlock error={users.error} onRetry={() => users.refetch()} />
  return (
    <div className="card overflow-hidden">
      <Table head={[t('security.username'), t('admin.fullName'), t('admin.role'), t('common.status'), t('security.lastLogin'), '']}>
        {users.data.map((u) => (
          <tr key={u.id} className="border-t border-line">
            <Td className="font-medium">{u.username}</Td>
            <Td>{u.fullName}</Td>
            <Td nowrap>{t(`role.${u.role}`)}</Td>
            <Td>{u.active ? t('common.active') : <span className="text-rose-600">{t('common.inactive')}</span>}</Td>
            <Td nowrap>{u.lastLoginAt ? `${fmtDateTime(u.lastLoginAt, lang)} · ${u.lastLoginIp ?? ''}` : '-'}</Td>
            <Td nowrap>
              <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => onShow(u, 'logins')}>{t('security.tabs.logins')}</button>
              <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => onShow(u, 'ips')}>{t('security.ipHistory')}</button>
              <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => onShow(u, 'changes')}>{t('security.tabs.changes')}</button>
              {u.role !== 'ROOT' && <button type="button" className="btn-ghost px-2 py-1 text-xs text-rose-700" onClick={() => void revoke(u)}>{t('security.revoke')}</button>}
            </Td>
          </tr>
        ))}
      </Table>
    </div>
  )
}

// ----------------------------------------------------------------------------- building blocks

function Filters({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center gap-2">{children}</div>
}

function Paged<T>({ query, page, setPage, children }: {
  query: { data?: LogPage<T>; isLoading: boolean; error: unknown; refetch: () => unknown }; page: number; setPage: (page: number) => void
  children: (items: T[]) => ReactNode
}) {
  const { t } = useI18n()
  if (query.isLoading) return <Loading />
  if (query.error || !query.data) return <ErrorBlock error={query.error} onRetry={() => query.refetch()} />
  const { items, total, size } = query.data
  if (total === 0) return <div className="card"><EmptyState title={t('security.noData')} /></div>
  return (
    <div className="card overflow-hidden">
      {children(items)}
      <div className="flex items-center justify-end gap-2 border-t border-line px-3 py-2 text-xs text-muted">
        {t('common.page', { from: page * size + 1, to: page * size + items.length, total })}
        <button type="button" className="btn-ghost p-1" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="previous"><ChevronLeft className="size-4" /></button>
        <button type="button" className="btn-ghost p-1" disabled={(page + 1) * size >= total} onClick={() => setPage(page + 1)} aria-label="next"><ChevronRight className="size-4" /></button>
      </div>
    </div>
  )
}

function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-canvas text-left text-xs text-muted">
          <tr>{head.map((h, i) => <th key={i} className="whitespace-nowrap px-3 py-2 font-medium">{h}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

function Td({ children, className = '', nowrap, title }: { children?: ReactNode; className?: string; nowrap?: boolean; title?: string }) {
  return <td className={`px-3 py-1.5 ${nowrap ? 'whitespace-nowrap' : ''} ${className}`} title={title}>{children}</td>
}
