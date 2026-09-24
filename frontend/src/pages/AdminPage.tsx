import { Fragment, useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileSpreadsheet, Plus, RotateCcw, ShieldOff, Trash, Upload } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { keys, useLookups } from '../lib/queries'
import { fmtDateTime } from '../lib/format'
import type { AuditDto, CustomField, Role, TypeDto, UserDto } from '../lib/types'
import { useI18n } from '../i18n'
import { Field, Loading, Modal, PageHeader, Spinner, Tabs } from '../components/ui'
import { useToast } from '../components/Toast'

type Tab = 'users' | 'types' | 'categories' | 'fields' | 'settings' | 'backups' | 'audit'
/** Settings that are on / off rather than a number. */
const SWITCH_SETTINGS = new Set(['backup_reminder'])

interface BackupEvent { action: string; fileName: string; username: string | null; ip: string | null; userAgent: string | null; at: string }
interface BackupInfo {
  id: number; fileName: string; sizeBytes: number; businesses: number; projects: number; createdBy: string | null
  createdAt: string; expiresAt: string; events: BackupEvent[]
}
interface BackupStatus { lastBackupAt: string | null; nextDueAt: string; due: boolean; reminder: boolean; intervalDays: number; retentionDays: number }
interface BackupOverview { status: BackupStatus; backups: BackupInfo[]; log: BackupEvent[] }
interface RestoreResult {
  dryRun: boolean; users: number; usersNeedingPassword: string[]; projects: number; sheets: number; customFields: number
  brands: number; flavors: number; products: number; businesses: number; notes: number; settings: number
}
interface IpBlock {
  id: number; pattern: string; note: string | null; automatic: boolean
  createdAt: string; expiresAt: string | null; coversYou: boolean
}
const ROLES: Role[] = ['SALES', 'SUPERVISOR', 'ADMIN']

export function AdminPage() {
  const { t } = useI18n()
  const [tab, setTab] = useState<Tab>('users')
  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title={t('admin.title')} />
      <Tabs<Tab> value={tab} onChange={setTab} tabs={[
        { key: 'users', label: t('admin.users') },
        { key: 'types', label: t('admin.types') },
        { key: 'categories', label: t('admin.categories') },
        { key: 'fields', label: t('admin.customFields') },
        { key: 'settings', label: t('admin.settings') },
        { key: 'backups', label: t('admin.backups') },
        { key: 'audit', label: t('admin.audit') },
      ]} />
      <div className="mt-4">
        {tab === 'users' && <UsersTab />}
        {tab === 'types' && <NamedList kind="business-types" />}
        {tab === 'categories' && <NamedList kind="categories" />}
        {tab === 'fields' && <CustomFieldsTab />}
        {tab === 'settings' && <SettingsTab />}
        {tab === 'backups' && <BackupsTab />}
        {tab === 'audit' && <AuditTab />}
      </div>
    </div>
  )
}

function UsersTab() {
  const { t } = useI18n()
  const users = useQuery({ queryKey: ['admin-users'], queryFn: () => api.get<UserDto[]>('/admin/users') })
  const [editing, setEditing] = useState<UserDto | 'new' | null>(null)
  if (users.isLoading) return <Loading />
  return (
    <div className="card overflow-x-auto p-3">
      <div className="mb-2 flex justify-end">
        <button type="button" className="btn-primary" onClick={() => setEditing('new')}><Plus className="size-4" /> {t('admin.addUser')}</button>
      </div>
      <table className="w-full text-sm">
        <thead className="text-left text-xs text-muted"><tr><th className="px-2 py-2">{t('admin.fullName')}</th><th className="px-2 py-2">{t('auth.username')}</th><th className="px-2 py-2">{t('admin.role')}</th><th className="px-2 py-2">{t('common.phone')}</th><th className="px-2 py-2">{t('common.status')}</th></tr></thead>
        <tbody>
          {users.data?.map((u) => (
            <tr key={u.id} className="cursor-pointer border-t border-line hover:bg-canvas" onClick={() => setEditing(u)}>
              <td className="px-2 py-2 font-medium">{u.fullName}</td>
              <td className="px-2 py-2">{u.username}</td>
              <td className="px-2 py-2">{t(`role.${u.role}`)}</td>
              <td className="px-2 py-2">{u.phone ?? '-'}</td>
              <td className="px-2 py-2">{u.active ? t('common.active') : <span className="text-rose-700">{t('common.inactive')}</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {editing && <UserDialog user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function UserDialog({ user, onClose }: { user: UserDto | null; onClose: () => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ username: user?.username ?? '', fullName: user?.fullName ?? '', phone: user?.phone ?? '', role: user?.role ?? ('SALES' as Role), active: user?.active ?? true, password: '' })
  const [saving, setSaving] = useState(false)
  // Root's name, role, password and active flag come from the environment; only its display name and phone are editable here.
  const root = user?.role === 'ROOT'

  const save = async () => {
    setSaving(true)
    try {
      if (user) await api.put(`/admin/users/${user.id}`, { fullName: form.fullName, phone: form.phone, role: form.role, active: form.active, password: form.password })
      else await api.post('/admin/users', form)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      queryClient.invalidateQueries({ queryKey: keys.lookups })
      toast.ok(t('common.saved'))
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={user ? user.fullName : t('admin.addUser')} footer={
      <>
        <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
        <button type="button" className="btn-primary" disabled={saving || !form.fullName.trim() || (!user && (!form.username.trim() || form.password.length < 8))} onClick={() => void save()}>
          {saving && <Spinner className="size-4" />} {t('common.save')}
        </button>
      </>
    }>
      <div className="space-y-3">
        {!user && <Field label={t('auth.username')} hint="a-z, 0-9, . _ -"><input className="input" autoCapitalize="none" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></Field>}
        <Field label={t('admin.fullName')}><input className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
        <Field label={t('common.phone')}><input className="input" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
        {root && <p className="rounded-xl bg-amber-50 p-2.5 text-xs text-amber-900">{t('admin.rootLocked')}</p>}
        <Field label={t('admin.role')}>
          <select className="input" value={form.role} disabled={root} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
            {(root ? (['ROOT'] as Role[]) : ROLES).map((r) => <option key={r} value={r}>{t(`role.${r}`)}</option>)}
          </select>
        </Field>
        {!root && (
          <Field label={user ? t('admin.newPassword') : t('auth.newPassword')}>
            <input type="password" className="input" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </Field>
        )}
        {user && !root && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="size-4 accent-brand-600" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> {t('common.active')}
          </label>
        )}
      </div>
    </Modal>
  )
}

function NamedList({ kind }: { kind: 'business-types' | 'categories' }) {
  const { t } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const lookups = useLookups()
  const [draft, setDraft] = useState({ nameKa: '', nameEn: '' })
  const items: TypeDto[] = (kind === 'business-types' ? lookups.data?.businessTypes : lookups.data?.categories) ?? []

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      queryClient.invalidateQueries({ queryKey: keys.lookups })
    } catch (error) {
      toast.error(error)
    }
  }
  const update = (item: TypeDto, patch: Partial<TypeDto>) => run(() => api.put(`/admin/${kind}/${item.id}`, { ...item, ...patch }))

  return (
    <div className="card p-3">
      <form className="mb-3 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); void run(() => api.post(`/admin/${kind}`, draft)).then(() => setDraft({ nameKa: '', nameEn: '' })) }}>
        <input className="input flex-1" placeholder={t('admin.nameKa')} value={draft.nameKa} onChange={(e) => setDraft({ ...draft, nameKa: e.target.value })} />
        <input className="input flex-1" placeholder={t('admin.nameEn')} value={draft.nameEn} onChange={(e) => setDraft({ ...draft, nameEn: e.target.value })} />
        <button type="submit" className="btn-primary" disabled={!draft.nameKa.trim() || !draft.nameEn.trim()}><Plus className="size-4" /> {kind === 'business-types' ? t('admin.addType') : t('admin.addCategory')}</button>
      </form>
      <ul className="divide-y divide-line">
        {items.map((item) => (
          <li key={item.id} className={`flex flex-wrap items-center gap-2 py-2 ${item.active ? '' : 'opacity-50'}`}>
            <input className="input w-16 py-1" type="number" defaultValue={item.sortOrder} title={t('admin.order')} onBlur={(e) => { if (Number(e.target.value) !== item.sortOrder) void update(item, { sortOrder: Number(e.target.value) }) }} />
            <input className="input flex-1 py-1" defaultValue={item.nameKa} onBlur={(e) => { if (e.target.value.trim() && e.target.value !== item.nameKa) void update(item, { nameKa: e.target.value }) }} />
            <input className="input flex-1 py-1" defaultValue={item.nameEn} onBlur={(e) => { if (e.target.value.trim() && e.target.value !== item.nameEn) void update(item, { nameEn: e.target.value }) }} />
            <label className="flex items-center gap-1 text-xs text-muted">
              <input type="checkbox" className="accent-brand-600" checked={item.active} onChange={(e) => void update(item, { active: e.target.checked })} /> {t('common.active')}
            </label>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Extra fields (usually born from a spreadsheet column on import): rename, reorder, retire. Values are kept when retired. */
function CustomFieldsTab() {
  const { t } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const lookups = useLookups()
  const [label, setLabel] = useState('')
  const fields = lookups.data?.customFields ?? []

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      await Promise.all([queryClient.invalidateQueries({ queryKey: keys.lookups }), queryClient.invalidateQueries({ queryKey: ['grid'] })])
    } catch (error) {
      toast.error(error)
    }
  }
  const update = (field: CustomField, patch: Partial<CustomField>) =>
    run(() => api.put(`/admin/custom-fields/${field.id}`, { label: field.label, sortOrder: field.sortOrder, active: field.active, ...patch }))

  return (
    <div className="card p-3">
      <form className="mb-3 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); void run(() => api.post('/custom-fields', { label })).then(() => setLabel('')) }}>
        <input className="input flex-1" maxLength={80} placeholder={t('admin.fieldName')} value={label} onChange={(e) => setLabel(e.target.value)} />
        <button type="submit" className="btn-primary" disabled={!label.trim()}><Plus className="size-4" /> {t('admin.addField')}</button>
      </form>
      {fields.length === 0 ? <p className="px-1 py-4 text-sm text-muted">{t('admin.noCustomFields')}</p> : (
        <ul className="divide-y divide-line">
          {fields.map((field) => (
            <li key={field.id} className={`flex flex-wrap items-center gap-2 py-2 ${field.active ? '' : 'opacity-50'}`}>
              <input className="input w-16 py-1" type="number" defaultValue={field.sortOrder} title={t('admin.order')} onBlur={(e) => { if (Number(e.target.value) !== field.sortOrder) void update(field, { sortOrder: Number(e.target.value) }) }} />
              <input className="input flex-1 py-1" maxLength={80} defaultValue={field.label} onBlur={(e) => { if (e.target.value.trim() && e.target.value !== field.label) void update(field, { label: e.target.value }) }} />
              <label className="flex items-center gap-1 text-xs text-muted">
                <input type="checkbox" className="accent-brand-600" checked={field.active} onChange={(e) => void update(field, { active: e.target.checked })} /> {t('common.active')}
              </label>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function SettingsTab() {
  const { t } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const lookups = useLookups()
  const [values, setValues] = useState<Record<string, number>>({})
  useEffect(() => {
    if (lookups.data) setValues(lookups.data.settings)
  }, [lookups.data])

  const save = async () => {
    try {
      await api.put('/admin/settings', values)
      queryClient.invalidateQueries({ queryKey: keys.lookups })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.invalidateQueries({ queryKey: ['backup-status'] })
      queryClient.invalidateQueries({ queryKey: ['backups'] })
      toast.ok(t('common.saved'))
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <div className="max-w-xl space-y-4">
      <IpBlocksCard />
      <div className="card space-y-3 p-4">
      {Object.keys(values).map((key) => SWITCH_SETTINGS.has(key) ? (
        <label key={key} className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-brand-600" checked={values[key] === 1} onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.checked ? 1 : 0 }))} />
          {t(`admin.setting.${key}`)}
        </label>
      ) : (
        <Field key={key} label={t(`admin.setting.${key}`)}>
          <input type="number" min="1" max="365" className="input max-w-32" value={values[key]} onChange={(e) => setValues((v) => ({ ...v, [key]: Number(e.target.value) }))} />
        </Field>
      ))}
      <button type="button" className="btn-primary" onClick={() => void save()}>{t('common.save')}</button>
      </div>
    </div>
  )
}

/**
 * The addresses the guard is currently turning away. Ten wrong passwords from one office used to lock
 * everyone in it out with nobody able to undo it; now an admin sees the block here and lifts it in a click,
 * and the line that covers this very computer is marked, because that is the one doing the damage.
 */
function IpBlocksCard() {
  const { t, lang } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const blocks = useQuery({ queryKey: ['ip-blocks'], queryFn: () => api.get<IpBlock[]>('/admin/ip-blocks') })

  const lift = async (id: number) => {
    try {
      await api.del(`/admin/ip-blocks/${id}`)
      queryClient.invalidateQueries({ queryKey: ['ip-blocks'] })
      toast.ok(t('admin.blockLifted'))
    } catch (error) {
      toast.error(error)
    }
  }

  if (blocks.isLoading || (blocks.data ?? []).length === 0) return null
  return (
    <div className="card space-y-2 p-4">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-rose-700">
        <ShieldOff className="size-4" /> {t('admin.blockedIps')}
      </h3>
      <p className="text-xs text-muted">{t('admin.blockedIpsHint')}</p>
      <ul className="divide-y divide-line">
        {(blocks.data ?? []).map((b) => (
          <li key={b.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
            <span className="font-mono text-xs">{b.pattern}</span>
            {b.coversYou && <span className="chip border-rose-300 bg-rose-50 text-xs text-rose-800">{t('admin.blockCoversYou')}</span>}
            {b.automatic && <span className="chip border-line text-xs text-muted">{t('admin.blockAutomatic')}</span>}
            <span className="text-xs text-muted">{fmtDateTime(b.createdAt, lang)}</span>
            {b.note && <span className="truncate text-xs text-muted">{b.note}</span>}
            <button type="button" className="btn-secondary ml-auto px-2 py-1 text-xs" onClick={() => void lift(b.id)}>
              {t('admin.liftBlock')}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Backups: make one (it downloads straight away), download one someone else made while the server keeps it,
 * see who downloaded which from where, and turn any backup file into Excel.
 */
function BackupsTab() {
  const { t, lang } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const overview = useQuery({ queryKey: ['backups'], queryFn: () => api.get<BackupOverview>('/admin/backups') })
  const [restored, setRestored] = useState<RestoreResult | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [open, setOpen] = useState<number | null>(null)

  const run = async (key: string, action: () => Promise<unknown>) => {
    setBusy(key)
    try {
      await action()
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(null)
      await queryClient.invalidateQueries({ queryKey: ['backups'] })
      await queryClient.invalidateQueries({ queryKey: ['backup-status'] })
    }
  }
  const backupNow = () => run('create', async () => {
    const backup = await api.post<BackupInfo>('/admin/backups')
    await api.download(`/admin/backups/${backup.id}/download`, undefined, backup.fileName)
    toast.ok(t('admin.backupDone'))
  })
  const downloadOne = (backup: BackupInfo) => run(`get-${backup.id}`, () => api.download(`/admin/backups/${backup.id}/download`, undefined, backup.fileName))
  const remove = (backup: BackupInfo) => {
    if (!window.confirm(t('admin.deleteConfirm'))) return
    void run(`del-${backup.id}`, () => api.del(`/admin/backups/${backup.id}`))
  }
  const convert = (file: File | undefined) => {
    if (!file) return
    void run('convert', () => api.convert('/admin/backups/convert', file, { lang }, 'andaneri-backup.xlsx'))
  }

  // Putting a backup back. The server refuses a database that still holds businesses unless it is told
  // twice, so the usual path - a fresh server - needs no warning, and the other one is asked about.
  const restore = (file: File | undefined, force = false) => {
    if (!file) return
    void run('restore', async () => {
      try {
        const result = await api.upload<RestoreResult>('/admin/backups/restore', file, force ? { force: true } : undefined)
        setRestored(result)
        toast.ok(t('admin.restoreDone', { n: result.businesses }))
      } catch (error) {
        if (!force && error instanceof ApiError && error.code === 'NOT_EMPTY') {
          if (window.confirm(t('admin.restoreNotEmpty'))) {
            restore(file, true)
            return
          }
          return
        }
        throw error
      }
    })
  }

  if (overview.isLoading) return <Loading />
  const data = overview.data
  const status = data?.status

  return (
    <div className="space-y-4">
      <section className="card flex flex-wrap items-center gap-4 p-4">
        <div className="min-w-0 flex-1 basis-72 space-y-1 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-muted">{t('admin.lastBackup')}:</span>
            <b>{status?.lastBackupAt ? fmtDateTime(status.lastBackupAt, lang) : t('admin.never')}</b>
            {status?.due && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">{t('admin.due')}</span>}
          </div>
          {status && !status.due && <div><span className="text-muted">{t('admin.nextDue')}:</span> {fmtDateTime(status.nextDueAt, lang)}</div>}
          {status && <p className="text-xs text-muted">{t('admin.keptDays', { n: status.retentionDays })} {t('admin.settingsHint')}</p>}
        </div>
        <button type="button" className="btn-primary" disabled={busy !== null} onClick={() => void backupNow()}>
          {busy === 'create' ? <Spinner className="size-4" /> : <Download className="size-4" />} {t('admin.backupNow')}
        </button>
      </section>

      <section className="card overflow-x-auto p-3">
        <h3 className="mb-2 px-1 text-sm font-semibold">{t('admin.storedTitle')}</h3>
        {!data?.backups.length ? <p className="px-1 py-3 text-sm text-muted">{t('admin.noBackups')}</p> : (
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted">
              <tr>
                <th className="px-2 py-2">{t('admin.made')}</th><th className="px-2 py-2">{t('admin.by')}</th><th className="px-2 py-2">{t('admin.contents')}</th>
                <th className="px-2 py-2">{t('admin.size')}</th><th className="px-2 py-2">{t('admin.keptUntil')}</th><th className="px-2 py-2">{t('admin.downloads')}</th><th />
              </tr>
            </thead>
            <tbody>
              {data.backups.map((b) => {
                const downloads = b.events.filter((e) => e.action === 'DOWNLOADED')
                return (
                  <Fragment key={b.id}>
                    <tr className="border-t border-line">
                      <td className="whitespace-nowrap px-2 py-2">{fmtDateTime(b.createdAt, lang)}</td>
                      <td className="px-2 py-2">{b.createdBy ?? '-'}</td>
                      <td className="px-2 py-2 text-xs">{t('admin.contains', { n: b.businesses, p: b.projects })}</td>
                      <td className="whitespace-nowrap px-2 py-2 tabular-nums">{fileSize(b.sizeBytes)}</td>
                      <td className="whitespace-nowrap px-2 py-2">{fmtDateTime(b.expiresAt, lang)}</td>
                      <td className="px-2 py-2">
                        <button type="button" className="text-brand-700 hover:underline" onClick={() => setOpen(open === b.id ? null : b.id)}>{downloads.length}</button>
                      </td>
                      <td className="whitespace-nowrap px-2 py-2 text-right">
                        <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={busy !== null} onClick={() => void downloadOne(b)}>
                          {busy === `get-${b.id}` ? <Spinner className="size-3.5" /> : <Download className="size-3.5" />} {t('admin.download')}
                        </button>
                        <button type="button" className="btn-ghost ml-1 px-2 py-1 text-xs text-rose-700" disabled={busy !== null} onClick={() => remove(b)}>
                          <Trash className="size-3.5" />
                        </button>
                      </td>
                    </tr>
                    {open === b.id && (
                      <tr className="bg-canvas">
                        <td colSpan={7} className="px-2 py-2"><EventList events={b.events} /></td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        )}
      </section>

      <section className="card p-4">
        <h3 className="flex items-center gap-2 text-sm font-semibold"><FileSpreadsheet className="size-4 text-emerald-600" /> {t('admin.convertTitle')}</h3>
        <p className="mb-3 mt-1 text-sm text-muted">{t('admin.convertHint')}</p>
        <label className="btn-secondary cursor-pointer">
          {busy === 'convert' ? <Spinner className="size-4" /> : <Upload className="size-4" />} {t('admin.convertButton')}
          <input type="file" accept=".zip,.json,application/zip,application/json" className="hidden" onChange={(e) => { convert(e.target.files?.[0]); e.target.value = '' }} />
        </label>
      </section>

      <section className="card border-amber-200 p-4">
        <h3 className="flex items-center gap-2 text-sm font-semibold"><RotateCcw className="size-4 text-amber-600" /> {t('admin.restoreTitle')}</h3>
        <p className="mb-3 mt-1 text-sm text-muted">{t('admin.restoreHint')}</p>
        <label className="btn-secondary cursor-pointer">
          {busy === 'restore' ? <Spinner className="size-4" /> : <Upload className="size-4" />} {t('admin.restoreButton')}
          <input type="file" accept=".zip,.json,application/zip,application/json" className="hidden" onChange={(e) => { restore(e.target.files?.[0]); e.target.value = '' }} />
        </label>
        {restored && (
          <div className="mt-3 rounded-xl border border-line bg-canvas p-3 text-sm">
            <p>{t('admin.restoreResult', { n: restored.businesses, p: restored.projects, f: restored.customFields, o: restored.notes })}</p>
            {restored.usersNeedingPassword.length > 0 && (
              <p className="mt-1 text-amber-800">
                {t('admin.restoreUsers', { names: restored.usersNeedingPassword.join(', ') })}
              </p>
            )}
          </div>
        )}
      </section>

      <section className="card p-3">
        <h3 className="mb-2 px-1 text-sm font-semibold">{t('admin.logTitle')}</h3>
        {data?.log.length ? <EventList events={data.log} withFile /> : <p className="px-1 text-sm text-muted">-</p>}
      </section>
    </div>
  )
}

function EventList({ events, withFile }: { events: BackupEvent[]; withFile?: boolean }) {
  const { t, lang } = useI18n()
  const tone: Record<string, string> = {
    CREATED: 'bg-emerald-100 text-emerald-800', DOWNLOADED: 'bg-sky-100 text-sky-800', DELETED: 'bg-rose-100 text-rose-800', EXPIRED: 'bg-stone-100 text-stone-700',
  }
  return (
    <ul className="space-y-1 text-xs">
      {events.map((e, i) => (
        <li key={`${e.at}-${i}`} className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
          <span className="w-28 shrink-0 tabular-nums text-muted">{fmtDateTime(e.at, lang)}</span>
          <span className={`rounded px-1.5 py-0.5 font-medium ${tone[e.action] ?? 'bg-canvas'}`}>{t(`admin.event.${e.action}`)}</span>
          <span className="font-medium">{e.username ?? '-'}</span>
          {e.ip && <span className="font-mono text-muted">{e.ip}</span>}
          {withFile && <span className="truncate text-muted">{e.fileName}</span>}
          {e.userAgent && <span className="hidden max-w-md truncate text-muted lg:inline" title={e.userAgent}>{e.userAgent}</span>}
        </li>
      ))}
    </ul>
  )
}

function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function AuditTab() {
  const { t, lang } = useI18n()
  const audit = useQuery({ queryKey: ['audit'], queryFn: () => api.get<AuditDto[]>('/admin/audit', { limit: 300 }) })
  if (audit.isLoading) return <Loading />
  return (
    <div className="card overflow-x-auto p-3">
      <table className="w-full text-xs">
        <thead className="text-left text-muted"><tr><th className="px-2 py-2">{t('common.date')}</th><th className="px-2 py-2">{t('admin.fullName')}</th><th className="px-2 py-2">{t('admin.auditEntity')}</th><th className="px-2 py-2">{t('admin.auditAction')}</th><th className="px-2 py-2">{t('common.details')}</th></tr></thead>
        <tbody>
          {audit.data?.map((a) => (
            <tr key={a.id} className="border-t border-line">
              <td className="whitespace-nowrap px-2 py-1.5">{fmtDateTime(a.createdAt, lang)}</td>
              <td className="whitespace-nowrap px-2 py-1.5">{a.user?.fullName ?? '-'}</td>
              <td className="px-2 py-1.5">{a.businessId ? <a className="text-brand-700" href={`/businesses/${a.businessId}`}>{a.entity}</a> : a.entity}</td>
              <td className="px-2 py-1.5">{a.action}</td>
              <td className="max-w-md truncate px-2 py-1.5">{a.summary}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
