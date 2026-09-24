import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Clock, Pencil, Plus, Trash } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { fmtDateTime, fmtTime, fmtWeekday } from '../lib/format'
import { useLookups } from '../lib/queries'
import type { HistoryItem, HistoryPage as Page, Lookups } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, ErrorBlock, Loading, PageHeader, Spinner } from '../components/ui'
import { minutesLabel } from '../components/RemindersDialog'

type T = (key: string, vars?: Record<string, string | number>) => string

/**
 * My own recent changes, newest first: what I added, changed or deleted, on which business, and each
 * field from what to what. Supervisors can switch to a colleague's.
 */
export function HistoryPage() {
  const { t, lang } = useI18n()
  const { user, isSupervisor } = useAuth()
  const { asUserId, viewAs } = useMode()
  const lookups = useLookups()
  // While looking through someone's eyes the list is theirs and the picker follows, rather than
  // quietly showing your own day under their name.
  const [picked, setPicked] = useState<number | null>(null)
  const userId = asUserId ?? picked
  const history = useInfiniteQuery({
    queryKey: ['history', userId],
    queryFn: ({ pageParam }) => api.get<Page>('/history', { userId, page: pageParam, size: 60 }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
  })

  const days = useMemo(() => {
    const groups: { key: string; label: string; items: HistoryItem[] }[] = []
    for (const item of history.data?.pages.flatMap((p) => p.items) ?? []) {
      const date = new Date(item.at)
      const key = date.toDateString()
      let group = groups.find((g) => g.key === key)
      if (!group) {
        group = { key, label: fmtWeekday(date, lang), items: [] }
        groups.push(group)
      }
      group.items.push(item)
    }
    return groups
  }, [history.data, lang])

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <PageHeader
        title={viewAs ? t('team.theirHistory', { name: viewAs.fullName }) : t('history.title')}
        subtitle={viewAs ? undefined : t('history.subtitle')}
        actions={isSupervisor && (
          <select className="input w-auto max-w-full" value={userId ?? ''} disabled={asUserId !== null}
            onChange={(e) => setPicked(e.target.value ? Number(e.target.value) : null)}>
            <option value="">{user?.fullName} ({t('common.me')})</option>
            {lookups.data?.users.filter((u) => u.id !== user?.id).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
          </select>
        )}
      />

      {history.isLoading ? <Loading /> : history.error ? <ErrorBlock error={history.error} onRetry={() => history.refetch()} /> : days.length === 0 ? (
        <div className="card"><EmptyState icon={<Clock className="size-8" />} title={t('history.empty')} /></div>
      ) : (
        <>
          {days.map((day) => (
            <section key={day.key}>
              <h2 className="sticky top-14 z-10 mb-2 bg-canvas/95 py-1 text-xs font-semibold uppercase tracking-wide text-muted backdrop-blur">{day.label}</h2>
              <div className="card divide-y divide-line">
                {day.items.map((item) => <Entry key={`${item.id}-${item.action}`} item={item} lookups={lookups.data} />)}
              </div>
            </section>
          ))}
          {history.hasNextPage && (
            <div className="flex justify-center">
              <button type="button" className="btn-secondary" disabled={history.isFetchingNextPage} onClick={() => void history.fetchNextPage()}>
                {history.isFetchingNextPage && <Spinner className="size-4" />} {t('history.loadMore')}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

const ICON = { INSERT: Plus, UPDATE: Pencil, DELETE: Trash } as const
const TONE: Record<string, string> = {
  INSERT: 'bg-emerald-100 text-emerald-700',
  UPDATE: 'bg-sky-100 text-sky-700',
  DELETE: 'bg-rose-100 text-rose-700',
}

function Entry({ item, lookups }: { item: HistoryItem; lookups: Lookups | undefined }) {
  const { t, lang } = useI18n()
  const [expanded, setExpanded] = useState(false)
  const Icon = ICON[item.action as keyof typeof ICON] ?? Clock
  const fields = Object.entries(item.changes)
  const shown = item.action === 'UPDATE' || expanded ? fields : fields.filter(([, [before, after]]) => (after ?? before)).slice(0, 6)
  const hidden = fields.length - shown.length
  const object = item.entity === 'Business' ? item.objectName ?? item.businessName : item.objectName

  return (
    <div className="flex gap-3 px-3 py-3">
      <span className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ${TONE[item.action] ?? 'bg-canvas text-muted'}`}>
        <Icon className="size-4" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-sm">
          <span className="font-semibold">{label(t, `history.action.${item.action}`, item.action)}</span>
          <span className="text-muted">{label(t, `history.entity.${item.entity}`, item.entity)}</span>
          {object && <span className="font-medium">{object}</span>}
          {item.businessId && item.businessName && item.entity !== 'Business' && (
            <Link to={`/main?open=${item.businessId}`} className="text-brand-700 hover:underline">{item.businessName}</Link>
          )}
          {item.businessId && item.entity === 'Business' && item.action !== 'DELETE' && (
            <Link to={`/main?open=${item.businessId}`} className="text-xs text-brand-700 hover:underline">{t('history.open')}</Link>
          )}
          <span className="ml-auto text-xs tabular-nums text-muted" title={fmtDateTime(item.at, lang)}>{fmtTime(item.at)}</span>
        </div>
        {item.summary && <p className="mt-0.5 text-xs text-muted">{prettySummary(t, lang, item.summary)}</p>}
        {shown.length > 0 && (
          <dl className="mt-1.5 space-y-1 text-sm">
            {shown.map(([field, [before, after]]) => (
              <div key={field} className="flex flex-wrap items-baseline gap-x-2">
                <dt className="text-xs text-muted">{fieldLabel(t, field)}:</dt>
                <dd className="flex min-w-0 flex-wrap items-baseline gap-x-1.5">
                  {item.action !== 'INSERT' && (
                    <span className={item.action === 'UPDATE' ? 'text-rose-700 line-through decoration-rose-300' : ''}>{value(t, lang, lookups, item.entity, field, before)}</span>
                  )}
                  {item.action === 'UPDATE' && <span className="text-muted">→</span>}
                  {item.action !== 'DELETE' && (
                    <span className={item.action === 'UPDATE' ? 'font-medium text-emerald-700' : ''}>{value(t, lang, lookups, item.entity, field, after)}</span>
                  )}
                </dd>
              </div>
            ))}
          </dl>
        )}
        {hidden > 0 && (
          <button type="button" className="mt-1 text-xs text-brand-700" onClick={() => setExpanded(true)}>{t('history.more', { n: hidden })}</button>
        )}
      </div>
    </div>
  )
}

/**
 * The service-written lines ("VISIT 2026-09-21T10:00:00Z", "CUSTOMER -> REPEAT_CUSTOMER") are for machines;
 * dates and codes in them are shown the way the rest of the app shows them.
 */
function prettySummary(t: T, lang: 'ka' | 'en', text: string): string {
  const groups = ['status', 'taskType', 'activityType', 'activityResult', 'interestStatus', 'taskStatus', 'priority']
  return text
    .replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z/g, (iso) => fmtDateTime(iso, lang))
    .replace(/->/g, '→')
    .replace(/\b[A-Z][A-Z_]{2,}\b/g, (code) => {
      for (const group of groups) {
        const text = t(`${group}.${code}`)
        if (text !== `${group}.${code}`) return text
      }
      return code
    })
}

function label(t: T, key: string, fallback: string): string {
  const text = t(key)
  return text === key ? fallback : text
}

const FIELD_KEYS: Record<string, string> = {
  name: 'common.name', phone: 'common.phone', email: 'common.email', address: 'common.address', city: 'common.city',
  district: 'common.district', status: 'common.status', priority: 'common.priority', type: 'common.type',
  assignedTo: 'common.assignedTo', notes: 'common.notes', legalName: 'business.legalName', idCode: 'business.idCode',
  branches: 'business.branches', visitHours: 'business.visitHours', website: 'business.website', mapsUrl: 'business.mapsUrl',
  menuChange: 'business.menuChange', competitorNotes: 'business.competitorNotes', switchOpenness: 'business.switchOpenness',
  priceSensitivity: 'business.priceSensitivity', satisfaction: 'business.satisfaction', reorderDays: 'business.reorderDays',
  sheet: 'business.sheet', archived: 'business.archived', title: 'activity.nextTitle', dueAt: 'tasks.dueAt', endAt: 'tasks.endAt',
  allDay: 'tasks.allDay', location: 'tasks.location', roleTitle: 'business.roleTitle', decisionMaker: 'business.decisionMaker',
  preferredChannel: 'business.preferredChannel', body: 'business.comment', remindAt: 'common.reminder', done: 'common.done',
  brand: 'business.brand', flavor: 'history.flavor', result: 'activity.result', occurredAt: 'activity.when',
  remindMinutes: 'tasks.remindMe', contact: 'activity.contact', category: 'products.category', product: 'history.product',
  quantity: 'common.quantity', unitPrice: 'common.price', total: 'common.total', purchaseDate: 'common.date',
  feedback: 'business.feedback', reason: 'business.reason', answer: 'history.answer', label: 'history.label',
  value: 'history.value', field: 'history.label', completedAt: 'history.completedAt', completedBy: 'history.completedBy',
  frequency: 'business.frequency', productName: 'business.productName', drinkTypes: 'business.drinkTypes', workbook: 'business.project',
}

function fieldLabel(t: T, field: string): string {
  return FIELD_KEYS[field] ? label(t, FIELD_KEYS[field], field) : field
}

/** Enum values are translated with the group they belong to, which depends on the object. */
function enumGroup(entity: string, field: string): string | null {
  switch (field) {
    case 'status': return entity === 'Task' ? 'taskStatus' : entity === 'Interest' ? 'interestStatus' : entity === 'Business' ? 'status' : null
    case 'type': return entity === 'Task' ? 'taskType' : entity === 'Activity' ? 'activityType' : null
    case 'priority': return 'priority'
    case 'result': return 'activityResult'
    case 'switchOpenness': return 'openness'
    case 'priceSensitivity': return 'priceSensitivity'
    case 'satisfaction': return 'satisfaction'
    case 'answer': return 'usage'
    case 'preferredChannel': return 'channel'
    case 'feedback': return 'feedback'
    case 'reason': return 'interestReason'
    default: return null
  }
}

function value(t: T, lang: 'ka' | 'en', lookups: Lookups | undefined, entity: string, field: string, raw: string | null): string {
  if (raw === null || raw === '') return '-'
  if (raw === 'true') return t('common.yes')
  if (raw === 'false') return t('common.no')
  if ((field === 'remindMinutes' || field === 'reminderMinutes') && /^\d+$/.test(raw)) return minutesLabel(Number(raw), t)
  const ref = /^#(\d+)$/.exec(raw)
  if (ref) {
    const id = Number(ref[1])
    const found =
      field === 'assignedTo' || field === 'completedBy' ? lookups?.users.find((u) => u.id === id)?.fullName
        : field === 'sheet' ? lookups?.sheets.find((s) => s.id === id)?.name
          : field === 'workbook' ? lookups?.workbooks.find((w) => w.id === id)?.name
            : field === 'type' && entity === 'Business' ? pick(lang, lookups?.businessTypes.find((x) => x.id === id))
              : field === 'brand' ? lookups?.brands.find((b) => b.id === id)?.name
                : field === 'flavor' ? pick(lang, lookups?.flavors.find((f) => f.id === id))
                  : field === 'category' ? pick(lang, lookups?.categories.find((c) => c.id === id))
                    : undefined
    return found ?? raw
  }
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(raw)) return fmtDateTime(raw, lang)
  const group = enumGroup(entity, field)
  return group ? label(t, `${group}.${raw}`, raw) : raw
}

function pick(lang: 'ka' | 'en', item: { nameKa: string; nameEn: string } | undefined): string | undefined {
  return item ? (lang === 'ka' ? item.nameKa : item.nameEn) : undefined
}
