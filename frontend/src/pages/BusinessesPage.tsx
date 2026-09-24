import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Download, Funnel, Phone, Plus, Search, X } from 'lucide-react'
import { api, type Params } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { useBusinesses, useLookups } from '../lib/queries'
import { daysFromToday, fmtDateTime, fmtShortDate, telHref } from '../lib/format'
import type { BusinessStatus } from '../lib/types'
import { STATUSES } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, ErrorBlock, Field, Loading, PageHeader, PriorityMark, STATUS_STYLE, StatusBadge } from '../components/ui'
import { QuickAddDialog } from '../components/QuickAddDialog'
import { useToast } from '../components/Toast'

const PAGE_SIZE = 50
// Filters live in the address bar, so a filtered list can be bookmarked or linked from the dashboard.
const FILTER_KEYS = ['q', 'status', 'typeId', 'district', 'assignedToId', 'unassigned', 'brandId', 'flavorId', 'interestFlavorId', 'customer', 'notContactedDays', 'purchasedDaysAgo', 'priority', 'followUpDue', 'archived', 'sort'] as const

export function BusinessesPage() {
  const { t, lang, name } = useI18n()
  const toast = useToast()
  const { user } = useAuth()
  const { canEdit, asUserId, viewAs } = useMode()
  const lookups = useLookups()
  const [search, setSearch] = useSearchParams()
  const [text, setText] = useState(search.get('q') ?? '')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [exporting, setExporting] = useState(false)
  const page = Number(search.get('page') ?? '0')

  const set = (key: string, value: string | null) => {
    const next = new URLSearchParams(search)
    if (value === null || value === '') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearch(next, { replace: true })
  }

  useEffect(() => {
    const handle = setTimeout(() => {
      if ((search.get('q') ?? '') !== text) set('q', text.trim() || null)
    }, 300)
    return () => clearTimeout(handle)
  }, [text])

  const filters = useMemo<Params>(() => {
    const params: Params = {}
    for (const key of FILTER_KEYS) {
      const value = search.get(key)
      if (value !== null && value !== '') params[key] = value
    }
    return params
  }, [search])
  const statuses = (search.get('status') ?? '').split(',').filter(Boolean) as BusinessStatus[]
  const activeFilterCount = FILTER_KEYS.filter((key) => key !== 'q' && key !== 'sort' && search.get(key)).length

  const list = useBusinesses({ ...filters, page, size: PAGE_SIZE })

  const toggleStatus = (status: BusinessStatus) => {
    const next = statuses.includes(status) ? statuses.filter((s) => s !== status) : [...statuses, status]
    set('status', next.join(',') || null)
  }

  const exportList = async (format: 'xlsx' | 'json') => {
    setExporting(true)
    try {
      await api.download('/export/businesses', { ...filters, format, lang }, `businesses.${format}`)
    } catch (error) {
      toast.error(error)
    } finally {
      setExporting(false)
    }
  }

  const typeName = (id: number | null) => name(lookups.data?.businessTypes.find((type) => type.id === id))
  const brands = lookups.data?.brands ?? []
  const flavors = [...(lookups.data?.flavors ?? [])].sort((a, b) => name(a).localeCompare(name(b)))

  return (
    <div>
      <PageHeader
        title={t('businesses.title')}
        subtitle={list.data ? t('businesses.count', { n: list.data.total }) : undefined}
        actions={
          <>
            <button type="button" className="btn-secondary hidden md:inline-flex" disabled={exporting} onClick={() => void exportList('xlsx')}>
              <Download className="size-4" /> {t('businesses.exportXlsx')}
            </button>
            <button type="button" className="btn-secondary hidden md:inline-flex" disabled={exporting} onClick={() => void exportList('json')}>
              <Download className="size-4" /> {t('businesses.exportJson')}
            </button>
            {canEdit() && (
              <button type="button" className="btn-primary" onClick={() => setAddOpen(true)}>
                <Plus className="size-4" /> {t('business.new')}
              </button>
            )}
          </>
        }
      />

      <div className="card mb-3 p-3">
        <div className="flex flex-wrap gap-2">
          <div className="relative min-w-0 flex-1 basis-60">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
            <input className="input pl-9" placeholder={t('business.searchPlaceholder')} value={text} onChange={(e) => setText(e.target.value)} />
          </div>
          <select className="input w-auto max-w-full" value={search.get('sort') ?? ''} onChange={(e) => set('sort', e.target.value || null)}>
            <option value="">{t('businesses.sortUpdated')}</option>
            <option value="name">{t('businesses.sortName')}</option>
            <option value="lastContact">{t('businesses.sortLastContact')}</option>
            <option value="created">{t('businesses.sortCreated')}</option>
            <option value="lastPurchase">{t('businesses.sortLastPurchase')}</option>
          </select>
          <button type="button" className={activeFilterCount ? 'btn-primary' : 'btn-secondary'} onClick={() => setFiltersOpen(!filtersOpen)}>
            <Funnel className="size-4" /> {t('common.filters')} {activeFilterCount > 0 && `(${activeFilterCount})`}
          </button>
          {activeFilterCount > 0 && (
            <button type="button" className="btn-ghost" onClick={() => { setText(''); setSearch(new URLSearchParams(), { replace: true }) }}>
              <X className="size-4" /> {t('common.clear')}
            </button>
          )}
        </div>

        <div className={`mt-3 flex-wrap gap-1.5 ${filtersOpen ? 'flex' : 'hidden md:flex'}`}>
          {STATUSES.map((status) => (
            <button key={status} type="button" onClick={() => toggleStatus(status)} className={`chip ${statuses.includes(status) ? `${STATUS_STYLE[status].badge} border-transparent ring-2 ring-brand-400` : 'border-line bg-surface text-muted hover:text-ink'}`}>
              <span className={`size-1.5 rounded-full ${STATUS_STYLE[status].dot}`} /> {t(`status.${status}`)}
            </button>
          ))}
        </div>

        {filtersOpen && (
          <div className="mt-3 grid gap-3 border-t border-line pt-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label={t('common.type')}>
              <select className="input" value={search.get('typeId') ?? ''} onChange={(e) => set('typeId', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {lookups.data?.businessTypes.map((type) => <option key={type.id} value={type.id}>{name(type)}</option>)}
              </select>
            </Field>
            <Field label={t('common.district')}>
              <select className="input" value={search.get('district') ?? ''} onChange={(e) => set('district', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {lookups.data?.districts.map((d) => <option key={d} value={d}>{d}</option>)}
              </select>
            </Field>
            <Field label={t('common.assignedTo')}>
              <select
                className="input"
                value={search.get('unassigned') ? 'none' : search.get('assignedToId') ?? ''}
                onChange={(e) => {
                  const next = new URLSearchParams(search)
                  next.delete('assignedToId'); next.delete('unassigned'); next.delete('page')
                  if (e.target.value === 'none') next.set('unassigned', 'true')
                  else if (e.target.value) next.set('assignedToId', e.target.value)
                  setSearch(next, { replace: true })
                }}
              >
                <option value="">{t('common.all')}</option>
                {/* "Me" is whoever's eyes these are. */}
                {user && <option value={asUserId ?? user.id}>{viewAs ? viewAs.fullName : t('common.me')}</option>}
                <option value="none">{t('common.unassigned')}</option>
                {lookups.data?.users.filter((u) => u.id !== (asUserId ?? user?.id)).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
              </select>
            </Field>
            <Field label={t('common.priority')}>
              <select className="input" value={search.get('priority') ?? ''} onChange={(e) => set('priority', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {['HIGH', 'NORMAL', 'LOW'].map((p) => <option key={p} value={p}>{t(`priority.${p}`)}</option>)}
              </select>
            </Field>
            <Field label={t('businesses.usesBrand')}>
              <select className="input" value={search.get('brandId') ?? ''} onChange={(e) => set('brandId', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
              </select>
            </Field>
            <Field label={t('businesses.usesFlavor')}>
              <select className="input" value={search.get('flavorId') ?? ''} onChange={(e) => set('flavorId', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {flavors.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
              </select>
            </Field>
            <Field label={t('businesses.wantsFlavor')}>
              <select className="input" value={search.get('interestFlavorId') ?? ''} onChange={(e) => set('interestFlavorId', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {flavors.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
              </select>
            </Field>
            <Field label={t('businesses.customers')}>
              <select className="input" value={search.get('customer') ?? ''} onChange={(e) => set('customer', e.target.value)}>
                <option value="">{t('common.all')}</option>
                <option value="true">{t('businesses.customersOnly')}</option>
                <option value="false">{t('businesses.notCustomers')}</option>
              </select>
            </Field>
            <Field label={t('businesses.notContacted')}>
              <input type="number" min="1" className="input" value={search.get('notContactedDays') ?? ''} onChange={(e) => set('notContactedDays', e.target.value)} />
            </Field>
            <Field label={t('businesses.purchasedAgo')}>
              <input type="number" min="1" className="input" value={search.get('purchasedDaysAgo') ?? ''} onChange={(e) => set('purchasedDaysAgo', e.target.value)} />
            </Field>
            <label className="flex items-center gap-2 self-end pb-2 text-sm">
              <input type="checkbox" className="size-4 accent-brand-600" checked={Boolean(search.get('followUpDue'))} onChange={(e) => set('followUpDue', e.target.checked ? 'true' : null)} />
              {t('businesses.followUpDue')}
            </label>
            <label className="flex items-center gap-2 self-end pb-2 text-sm">
              <input type="checkbox" className="size-4 accent-brand-600" checked={Boolean(search.get('archived'))} onChange={(e) => set('archived', e.target.checked ? 'true' : null)} />
              {t('businesses.archived')}
            </label>
          </div>
        )}
      </div>

      {list.isLoading ? <Loading /> : list.error ? <ErrorBlock error={list.error} onRetry={() => list.refetch()} /> : !list.data || list.data.items.length === 0 ? (
        <div className="card"><EmptyState title={t('common.noResults')} /></div>
      ) : (
        <>
          {/* Table on wide screens */}
          <div className="card hidden overflow-x-auto md:block">
            <table className="w-full text-sm">
              <thead className="border-b border-line text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">{t('common.name')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('common.status')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('business.brand')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('business.lastContact')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('business.nextStep')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('common.assignedTo')}</th>
                  <th className="w-10" />
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((b) => {
                  const nextDays = daysFromToday(b.nextTask?.dueAt)
                  const phone = telHref(b.phone)
                  return (
                    <tr key={b.id} className="border-b border-line/70 last:border-0 hover:bg-canvas/60">
                      <td className="max-w-72 px-4 py-2.5">
                        <Link to={`/businesses/${b.id}`} className="flex items-center gap-1.5 font-medium hover:text-brand-700">
                          <PriorityMark priority={b.priority} /> <span className="truncate">{b.name}</span>
                        </Link>
                        <div className="truncate text-xs text-muted">{[typeName(b.typeId), b.district, b.address].filter(Boolean).join(' · ')}</div>
                      </td>
                      <td className="px-3 py-2.5"><StatusBadge status={b.status} /></td>
                      <td className="max-w-40 truncate px-3 py-2.5 text-xs">{b.brands.join(', ') || <span className="text-muted">-</span>}</td>
                      <td className="whitespace-nowrap px-3 py-2.5 text-xs text-muted">{b.lastContactAt ? fmtShortDate(b.lastContactAt, lang) : '-'}</td>
                      <td className="whitespace-nowrap px-3 py-2.5 text-xs">
                        {b.nextTask ? (
                          <span className={nextDays !== null && nextDays < 0 ? 'font-medium text-rose-600' : ''}>
                            {t(`taskType.${b.nextTask.type}`)} · {fmtDateTime(b.nextTask.dueAt, lang)}
                          </span>
                        ) : <span className="text-muted">-</span>}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2.5 text-xs text-muted">{b.assignedTo?.fullName ?? '-'}</td>
                      <td className="px-2">
                        {phone && (
                          <Link to={`/calls/${b.id}`} className="grid size-8 place-items-center rounded-full text-brand-700 hover:bg-brand-50" title={t('callMode.title')}>
                            <Phone className="size-4" />
                          </Link>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Cards on phones */}
          <div className="space-y-2 md:hidden">
            {list.data.items.map((b) => (
              <div key={b.id} className="card flex items-center gap-3 p-3">
                <Link to={`/businesses/${b.id}`} className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5 font-medium"><PriorityMark priority={b.priority} /><span className="truncate">{b.name}</span></div>
                  <div className="truncate text-xs text-muted">{[b.district, b.address].filter(Boolean).join(' · ')}</div>
                  <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                    <StatusBadge status={b.status} />
                    {b.brands.length > 0 && <span className="text-xs text-muted">{b.brands.join(', ')}</span>}
                  </div>
                  {b.nextTask && <div className="mt-1 text-xs">{t(`taskType.${b.nextTask.type}`)} · {fmtDateTime(b.nextTask.dueAt, lang)}</div>}
                </Link>
                {b.phone && (
                  <Link to={`/calls/${b.id}`} className="grid size-11 shrink-0 place-items-center rounded-full bg-brand-600 text-white">
                    <Phone className="size-5" />
                  </Link>
                )}
              </div>
            ))}
          </div>

          <div className="mt-3 flex items-center justify-between text-sm text-muted">
            <span>{t('businesses.page', { from: page * PAGE_SIZE + 1, to: Math.min((page + 1) * PAGE_SIZE, list.data.total), total: list.data.total })}</span>
            <div className="flex gap-2">
              <button type="button" className="btn-secondary" disabled={page === 0} onClick={() => { const n = new URLSearchParams(search); n.set('page', String(page - 1)); setSearch(n) }}>
                <ChevronLeft className="size-4" />
              </button>
              <button type="button" className="btn-secondary" disabled={(page + 1) * PAGE_SIZE >= list.data.total} onClick={() => { const n = new URLSearchParams(search); n.set('page', String(page + 1)); setSearch(n) }}>
                <ChevronRight className="size-4" />
              </button>
            </div>
          </div>
        </>
      )}

      <QuickAddDialog open={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  )
}
