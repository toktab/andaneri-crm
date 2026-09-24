import { useMemo, useState, type ReactNode } from 'react'
import { endOfMonth, format, startOfMonth, startOfWeek, startOfYear, subDays, subMonths } from 'date-fns'
import { Download, Printer } from 'lucide-react'
import { api } from '../lib/api'
import { useLookups, useReport } from '../lib/queries'
import { money, number } from '../lib/format'
import type { FlavorRow, Report } from '../lib/types'
import { STATUSES } from '../lib/types'
import { MarketSummary } from '../components/MarketSummary'
import { useI18n } from '../i18n'
import { Choice, EmptyState, ErrorBlock, Field, Loading, PageHeader, STATUS_STYLE, Stat } from '../components/ui'
import { useToast } from '../components/Toast'

type Preset = 'thisWeek' | 'thisMonth' | 'lastMonth' | 'last30' | 'last90' | 'thisYear' | 'custom'
const SECTIONS = ['summary', 'funnel', 'users', 'products', 'flavorsSold', 'flavorsWanted', 'flavorsLiked', 'flavorsDisliked', 'marketBrands', 'types', 'districts', 'pipeline'] as const
type Section = (typeof SECTIONS)[number]
const STORAGE_KEY = 'andaneri.reportSections'

const day = (date: Date) => format(date, 'yyyy-MM-dd')

function periodOf(preset: Preset): { from: string; to: string } {
  const now = new Date()
  switch (preset) {
    case 'thisWeek': return { from: day(startOfWeek(now, { weekStartsOn: 1 })), to: day(now) }
    case 'lastMonth': { const last = subMonths(now, 1); return { from: day(startOfMonth(last)), to: day(endOfMonth(last)) } }
    case 'last30': return { from: day(subDays(now, 29)), to: day(now) }
    case 'last90': return { from: day(subDays(now, 89)), to: day(now) }
    case 'thisYear': return { from: day(startOfYear(now)), to: day(now) }
    default: return { from: day(startOfMonth(now)), to: day(now) }
  }
}

function readSections(): Section[] {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Section[] | null
    return stored?.length ? stored.filter((s) => SECTIONS.includes(s)) : [...SECTIONS]
  } catch {
    return [...SECTIONS]
  }
}

/** Everything the work turned into, for any period and person, with the sections you choose; as Excel, JSON or print. */
export function ReportsPage() {
  const { t, lang } = useI18n()
  const toast = useToast()
  const lookups = useLookups()
  const [preset, setPreset] = useState<Preset>('thisMonth')
  const [custom, setCustom] = useState(periodOf('thisMonth'))
  const [userId, setUserId] = useState('')
  const [sections, setSections] = useState<Section[]>(readSections)
  const period = preset === 'custom' ? custom : periodOf(preset)
  const params = { ...period, userId: userId || null }
  const report = useReport(params)
  const show = (s: Section) => sections.includes(s)

  const toggle = (s: Section) => {
    const next = show(s) ? sections.filter((x) => x !== s) : SECTIONS.filter((x) => x === s || sections.includes(x))
    setSections(next)
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)) } catch { /* this visit only */ }
  }

  const download = async (formatName: 'xlsx' | 'json') => {
    try {
      await api.download('/reports/export', { ...params, sections: [...sections], format: formatName, lang }, `report.${formatName}`)
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <div>
      <PageHeader
        title={t('reports.title')}
        subtitle={`${period.from} - ${period.to}`}
        actions={
          <>
            <button type="button" className="btn-secondary" onClick={() => void download('xlsx')}><Download className="size-4" /> {t('reports.exportXlsx')}</button>
            <button type="button" className="btn-secondary" onClick={() => void download('json')}><Download className="size-4" /> {t('reports.exportJson')}</button>
            <button type="button" className="btn-secondary" onClick={() => window.print()}><Printer className="size-4" /> {t('reports.print')}</button>
          </>
        }
      />

      <div className="card no-print mb-4 space-y-3 p-3">
        <div className="flex flex-wrap items-end gap-3">
          <Choice size="sm" value={preset} onChange={setPreset} options={(['thisWeek', 'thisMonth', 'lastMonth', 'last30', 'last90', 'thisYear', 'custom'] as Preset[]).map((p) => ({ value: p, label: p === 'custom' ? '...' : t(`reports.${p}`) }))} />
          {preset === 'custom' && (
            <>
              <Field label={t('common.from')}><input type="date" className="input" value={custom.from} onChange={(e) => setCustom((c) => ({ ...c, from: e.target.value }))} /></Field>
              <Field label={t('common.to')}><input type="date" className="input" value={custom.to} onChange={(e) => setCustom((c) => ({ ...c, to: e.target.value }))} /></Field>
            </>
          )}
          <Field label={t('reports.person')} className="ml-auto">
            <select className="input" value={userId} onChange={(e) => setUserId(e.target.value)}>
              <option value="">{t('common.team')}</option>
              {lookups.data?.users.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
            </select>
          </Field>
        </div>
        <div>
          <div className="label">{t('reports.sections')}</div>
          <div className="flex flex-wrap gap-1.5">
            {SECTIONS.map((s) => <button key={s} type="button" className={show(s) ? 'chip-on' : 'chip-off'} onClick={() => toggle(s)}>{t(`reports.section.${s}`)}</button>)}
          </div>
        </div>
      </div>

      {report.isLoading ? <Loading /> : report.error || !report.data ? <ErrorBlock error={report.error} onRetry={() => report.refetch()} /> : (
        <ReportBody r={report.data} show={show} />
      )}
    </div>
  )
}

function ReportBody({ r, show }: { r: Report; show: (s: Section) => boolean }) {
  const { t, lang, name } = useI18n()
  const lookups = useLookups()
  const flavorLabel = (f: FlavorRow) => (lang === 'ka' ? f.nameKa : f.nameEn)

  return (
    <div className="space-y-4">
      {show('summary') && (
        <Section title={t('reports.section.summary')}>
          <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4 lg:grid-cols-7">
            <Stat label={t('reports.newLeads')} value={r.newLeads} />
            <Stat label={t('reports.calls')} value={r.calls} />
            <Stat label={t('reports.visits')} value={r.visits} />
            <Stat label={t('reports.meetings')} value={r.meetings} />
            <Stat label={t('reports.samples')} value={r.samples} />
            <Stat label={t('reports.newCustomers')} value={r.newCustomers} tone="text-emerald-700" />
            <Stat label={t('reports.lost')} value={r.lost} tone="text-rose-700" />
            <Stat label={t('reports.purchases')} value={r.purchases} />
            <Stat label={t('reports.bottlesSold')} value={number(r.bottlesSold)} tone="text-emerald-700" />
            <Stat label={t('reports.sales')} value={money(r.salesTotal)} tone="text-emerald-700" />
            <Stat label={t('reports.conversion')} value={r.conversionPercent === null ? '-' : `${r.conversionPercent}%`} sub={`${r.contactedBusinesses} ${t('reports.contacted').toLowerCase()}`} />
            <Stat label={t('reports.tasksDone')} value={r.tasksDone} />
            <Stat label={t('reports.overdueNow')} value={r.overdueNow} tone={r.overdueNow ? 'text-rose-700' : 'text-ink'} />
          </div>
        </Section>
      )}

      {show('funnel') && (
        <Section title={t('reports.section.funnel')}>
          <div className="grid gap-4 lg:grid-cols-3">
            <SplitBar title={`${t('reports.calls')}: ${r.funnel.calls}`} parts={[
              { label: t('reports.funnel.callsNoAnswer'), value: r.funnel.callsNoAnswer, tone: 'bg-slate-400' },
              { label: t('reports.funnel.callsTalked'), value: r.funnel.callsTalked, tone: 'bg-sky-400' },
              { label: t('reports.funnel.callsSaidYes'), value: r.funnel.callsSaidYes, tone: 'bg-emerald-500' },
              { label: t('reports.funnel.callsSaidNo'), value: r.funnel.callsSaidNo, tone: 'bg-rose-500' },
            ]} />
            <SplitBar title={`${t('reports.funnel.meetings')}: ${r.funnel.meetings}`} parts={[
              { label: t('reports.funnel.meetingsSaidYes'), value: r.funnel.meetingsSaidYes, tone: 'bg-emerald-500' },
              { label: t('reports.funnel.meetingsSaidNo'), value: r.funnel.meetingsSaidNo, tone: 'bg-rose-500' },
              { label: t('activityResult.TALKED'), value: Math.max(0, r.funnel.meetings - r.funnel.meetingsSaidYes - r.funnel.meetingsSaidNo), tone: 'bg-sky-400' },
            ]} />
            <SplitBar title={`${t('reports.funnel.visits')}: ${r.funnel.visits}`} parts={[
              { label: t('reports.funnel.visitsSaidYes'), value: r.funnel.visitsSaidYes, tone: 'bg-emerald-500' },
              { label: t('reports.funnel.visitsSaidNo'), value: r.funnel.visitsSaidNo, tone: 'bg-rose-500' },
              { label: t('activityResult.TALKED'), value: Math.max(0, r.funnel.visits - r.funnel.visitsSaidYes - r.funnel.visitsSaidNo), tone: 'bg-sky-400' },
            ]} />
          </div>
          <div className="mt-5">
            <BarList rows={[
              { label: t('reports.funnel.businessesCalled'), value: r.funnel.businessesCalled },
              { label: t('reports.funnel.businessesReached'), value: r.funnel.businessesReached },
              { label: t('reports.funnel.businessesMet'), value: r.funnel.businessesMet },
              { label: t('reports.samples'), value: r.funnel.samplesSent },
              { label: t('reports.funnel.becameClients'), value: r.funnel.becameClients, highlight: true },
              { label: t('reports.bottlesSold'), value: r.funnel.bottlesSold, highlight: true },
            ]} />
          </div>
        </Section>
      )}

      {show('users') && (
        <Section title={t('reports.section.users')}>
          <Table
            head={[t('reports.col.person'), t('reports.calls'), t('reports.col.reached'), t('reports.visits'), t('reports.meetings'), t('reports.newLeads'), t('reports.newCustomers'), t('reports.col.orders'), t('reports.col.bottles'), t('reports.sales'), t('reports.tasksDone')]}
            rows={r.byUser.map((u) => [u.name, u.calls, u.callsReached, u.visits, u.meetings, u.newLeads, u.newCustomers, u.purchases, number(u.bottles), money(u.sales), u.tasksDone])}
          />
        </Section>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {show('products') && (
          <Section title={t('reports.section.products')}>
            <BarList rows={r.byProduct.slice(0, 20).map((p) => ({ label: lang === 'ka' ? p.nameKa : p.nameEn, value: p.quantity, sub: money(p.total) }))} />
          </Section>
        )}
        {show('flavorsSold') && (
          <Section title={t('reports.section.flavorsSold')}>
            <BarList rows={r.flavorsSold.slice(0, 20).map((f) => ({ label: flavorLabel(f), value: f.quantity ?? 0, sub: `${f.count} ${t('reports.col.businesses').toLowerCase()}` }))} />
          </Section>
        )}
        {show('flavorsWanted') && (
          <Section title={t('reports.section.flavorsWanted')}>
            <BarList rows={r.flavorsWanted.slice(0, 20).map((f) => ({ label: flavorLabel(f), value: f.count }))} />
          </Section>
        )}
        {show('flavorsLiked') && (
          <Section title={t('reports.section.flavorsLiked')}>
            <BarList tone="bg-emerald-500" rows={r.flavorsLiked.slice(0, 20).map((f) => ({ label: flavorLabel(f), value: f.count }))} />
          </Section>
        )}
        {show('flavorsDisliked') && (
          <Section title={t('reports.section.flavorsDisliked')}>
            <BarList tone="bg-rose-500" rows={r.flavorsDisliked.slice(0, 20).map((f) => ({ label: flavorLabel(f), value: f.count }))} />
          </Section>
        )}
        {show('marketBrands') && (
          <Section title={t('reports.section.marketBrands')}>
            <MarketSummary />
          </Section>
        )}
        {show('types') && (
          <Section title={t('reports.section.types')}>
            <Table head={[t('common.type'), t('reports.col.orders'), t('reports.sales')]} rows={r.byType.map((g) => [
              g.key ? name(lookups.data?.businessTypes.find((x) => String(x.id) === g.key)) || g.key : t('reports.noType'), g.purchases, money(g.total),
            ])} />
          </Section>
        )}
        {show('districts') && (
          <Section title={t('reports.section.districts')}>
            <Table head={[t('common.district'), t('reports.col.orders'), t('reports.sales')]} rows={r.byDistrict.map((g) => [g.key || t('reports.noDistrict'), g.purchases, money(g.total)])} />
          </Section>
        )}
        {show('pipeline') && (
          <Section title={t('reports.section.pipeline')}>
            <div className="space-y-1.5">
              {STATUSES.map((s) => {
                const max = Math.max(1, ...Object.values(r.pipeline))
                return (
                  <div key={s} className="flex items-center gap-2 text-sm">
                    <span className="w-36 truncate text-xs text-muted">{t(`status.${s}`)}</span>
                    <span className="h-3 flex-1 overflow-hidden rounded-full bg-canvas"><span className={`block h-full ${STATUS_STYLE[s].dot}`} style={{ width: `${((r.pipeline[s] ?? 0) / max) * 100}%` }} /></span>
                    <span className="w-8 text-right text-xs font-semibold tabular-nums">{r.pipeline[s] ?? 0}</span>
                  </div>
                )
              })}
            </div>
          </Section>
        )}
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="card p-4">
      <h2 className="mb-3 text-sm font-semibold">{title}</h2>
      {children}
    </section>
  )
}

function BarList({ rows, tone = 'bg-brand-500' }: { rows: { label: string; value: number; sub?: string; highlight?: boolean }[]; tone?: string }) {
  const { t } = useI18n()
  const max = useMemo(() => Math.max(1, ...rows.map((r) => r.value)), [rows])
  if (rows.length === 0) return <EmptyState title={t('reports.empty')} />
  return (
    <div className="space-y-2">
      {rows.map((row, i) => (
        <div key={`${row.label}-${i}`} className="text-sm">
          <div className="flex items-baseline justify-between gap-2">
            <span className={`truncate ${row.highlight ? 'font-semibold text-brand-700' : ''}`}>{row.label}</span>
            <span className="shrink-0 font-semibold tabular-nums">{number(row.value)} {row.sub && <span className="font-normal text-muted">· {row.sub}</span>}</span>
          </div>
          <div className="mt-1 h-2 overflow-hidden rounded-full bg-canvas">
            <div className={`h-full rounded-full ${row.highlight ? 'bg-emerald-500' : tone}`} style={{ width: `${(row.value / max) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  )
}

function SplitBar({ title, parts }: { title: string; parts: { label: string; value: number; tone: string }[] }) {
  const total = parts.reduce((sum, p) => sum + p.value, 0)
  return (
    <div>
      <div className="mb-2 text-sm font-semibold">{title}</div>
      <div className="flex h-4 overflow-hidden rounded-full bg-canvas">
        {total > 0 && parts.map((p) => <div key={p.label} className={p.tone} style={{ width: `${(p.value / total) * 100}%` }} title={`${p.label}: ${p.value}`} />)}
      </div>
      <ul className="mt-2 space-y-1 text-sm">
        {parts.map((p) => (
          <li key={p.label} className="flex items-center gap-2">
            <span className={`size-2.5 rounded-full ${p.tone}`} />
            <span className="flex-1">{p.label}</span>
            <span className="font-semibold tabular-nums">{p.value}</span>
            <span className="w-10 text-right text-xs text-muted">{total ? `${Math.round((p.value / total) * 100)}%` : ''}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Table({ head, rows }: { head: string[]; rows: (string | number)[][] }) {
  const { t } = useI18n()
  if (rows.length === 0) return <EmptyState title={t('reports.empty')} />
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead><tr className="border-b border-line text-left text-xs text-muted">{head.map((h) => <th key={h} className="whitespace-nowrap px-2 py-2 font-medium">{h}</th>)}</tr></thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="border-b border-line/60 last:border-0">
              {row.map((cell, j) => <td key={j} className={`whitespace-nowrap px-2 py-2 ${j === 0 ? 'font-medium' : 'tabular-nums'}`}>{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
