import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, ArrowRight, Layers, CalendarClock, CircleCheck, CirclePlus, Clock, Download, FileSpreadsheet, FileText, Gift, GripVertical,
  Hash, Mail, MapPin, MessageSquare, Navigation, Package, Phone, Sparkles, Store, Tag, Undo2, Upload, Users, X, Globe, Map as MapIcon,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { keys, useLookups, useRefreshWork } from '../lib/queries'
import type { CustomField, ImportField, ImportPreview, ImportResult, ImportTarget, JsonImportResult, ParsedFile, SheetDto } from '../lib/types'
import { MULTI_COLUMN_FIELDS } from '../lib/types'
import { useI18n } from '../i18n'
import { Field, Modal, PageHeader, Spinner, StatusBadge } from '../components/ui'
import { useToast } from '../components/Toast'

/** Import from the old spreadsheet (or CSV, or this CRM's own JSON), and export everything. */
export function ImportPage() {
  const { t, lang } = useI18n()
  const toast = useToast()
  const { canEdit } = useMode()

  const download = async (format: 'xlsx' | 'json') => {
    try {
      await api.download('/export/businesses', { format, lang }, `businesses.${format}`)
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <div className="mx-auto max-w-7xl space-y-4">
      <PageHeader title={t('import.title')} />
      {canEdit() && <ExcelImport />}
      <section className="card p-4">
        <h2 className="text-base font-semibold">{t('import.exportTitle')}</h2>
        <p className="mb-3 text-sm text-muted">{t('import.exportHint')} {t('import.reportsHint')}</p>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-secondary" onClick={() => void download('xlsx')}><FileSpreadsheet className="size-4" /> {t('import.exportXlsx')}</button>
          <button type="button" className="btn-secondary" onClick={() => void download('json')}><Download className="size-4" /> {t('import.exportJson')}</button>
        </div>
      </section>
      {canEdit() && <JsonImport />}
    </div>
  )
}

// ----------------------------------------------------------------------------- Excel: file, where, columns, import

type Step = 'file' | 'where' | 'columns' | 'check'
interface SheetPlan { include: boolean; target: string; mapping: Record<string, ImportTarget> }
type Mapping = Record<string, ImportTarget>

const GROUPS: [string, ImportField[]][] = [
  ['basic', ['NAME', 'LEGAL_NAME', 'TYPE', 'ID_CODE', 'BRANCHES']],
  ['contact', ['PHONE', 'EMAIL', 'WEBSITE', 'CONTACTS']],
  ['location', ['ADDRESS', 'CITY', 'DISTRICT']],
  ['product', ['SYRUP_USAGE', 'FLAVORS', 'SAMPLES', 'CUSTOMER']],
  ['history', ['HISTORY', 'COMMENT', 'NEXT_STEP']],
]
const ICONS: Record<string, LucideIcon> = {
  NAME: Store, LEGAL_NAME: FileText, TYPE: Tag, ID_CODE: Hash, BRANCHES: Layers, PHONE: Phone, EMAIL: Mail, WEBSITE: Globe,
  CONTACTS: Users, ADDRESS: MapPin, CITY: MapIcon, DISTRICT: Navigation, SYRUP_USAGE: Package, FLAVORS: Sparkles, SAMPLES: Gift,
  CUSTOMER: CircleCheck, HISTORY: Clock, COMMENT: MessageSquare, NEXT_STEP: CalendarClock,
}

/** The guessed mapping minus its "do not import" guesses: those columns wait for a decision instead. */
const initialMapping = (sheet: SheetDto): Mapping =>
  Object.fromEntries(Object.entries(sheet.mapping).filter(([, target]) => target !== 'IGNORE'))

function ExcelImport() {
  const { t, name } = useI18n()
  const toast = useToast()
  const lookups = useLookups()
  const refresh = useRefreshWork()
  const client = useQueryClient()
  const { isSupervisor } = useAuth()
  const [step, setStep] = useState<Step>('file')
  const [file, setFile] = useState<ParsedFile | null>(null)
  const [plans, setPlans] = useState<SheetPlan[]>([])
  const [project, setProject] = useState<{ mode: 'new' | 'existing' | 'none'; name: string; id: string }>({ mode: 'new', name: '', id: '' })
  const [current, setCurrent] = useState(0)
  const [options, setOptions] = useState({ assignedToId: '', typeId: '', city: 'თბილისი', district: '', skipDuplicates: true, createFollowUps: true })
  const [previews, setPreviews] = useState<Record<number, ImportPreview>>({})
  const [results, setResults] = useState<{ sheet: string; result: ImportResult }[]>([])
  const [busy, setBusy] = useState<string | null>(null)

  const included = plans.map((plan, i) => (plan.include ? i : -1)).filter((i) => i >= 0)
  const sheet = file?.sheets[current]
  const plan = plans[current]

  const choose = async (picked: File | undefined) => {
    if (!picked) return
    setBusy('parse')
    try {
      const parsed = await api.upload<ParsedFile>('/import/parse', picked)
      setFile(parsed)
      setPlans(parsed.sheets.map((s) => ({ include: s.rows.length > 0, target: s.name, mapping: initialMapping(s) })))
      setProject({ mode: 'new', name: parsed.fileName.replace(/\.[^.]+$/, ''), id: '' })
      setCurrent(parsed.sheets.findIndex((s) => s.rows.length > 0) === -1 ? 0 : parsed.sheets.findIndex((s) => s.rows.length > 0))
      setPreviews({})
      setResults([])
      setStep('where')
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(null)
    }
  }

  const setPlan = (index: number, change: Partial<SheetPlan>) => {
    setPlans((all) => all.map((p, i) => (i === index ? { ...p, ...change } : p)))
    if (change.mapping) setPreviews((all) => { const next = { ...all }; delete next[index]; return next })
  }

  const body = (index: number) => {
    const s = file!.sheets[index]
    const p = plans[index]
    return {
      headers: s.headers,
      rows: s.rows,
      mapping: Object.fromEntries(Object.entries(p.mapping).filter(([, target]) => target !== 'IGNORE')),
      source: `${file!.fileName} / ${s.name}`,
      workbookId: project.mode === 'existing' && project.id ? Number(project.id) : null,
      workbookName: project.mode === 'new' ? project.name.trim() || file!.fileName : null,
      sheetName: p.target.trim() || s.name,
      options: {
        assignedToId: options.assignedToId ? Number(options.assignedToId) : null,
        typeId: options.typeId ? Number(options.typeId) : null,
        city: options.city, district: options.district,
        skipDuplicates: options.skipDuplicates, createFollowUps: options.createFollowUps,
      },
    }
  }

  const preview = async (index: number) => {
    setBusy('preview')
    try {
      const result = await api.post<ImportPreview>('/import/preview', body(index))
      setPreviews((all) => ({ ...all, [index]: result }))
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(null)
    }
  }

  // One sheet after another, so a problem in one leaves the others already imported and reported.
  const commitAll = async () => {
    const done: { sheet: string; result: ImportResult }[] = []
    for (const index of included) {
      setBusy(`commit-${index}`)
      try {
        done.push({ sheet: plans[index].target || file!.sheets[index].name, result: await api.post<ImportResult>('/import/commit', body(index)) })
      } catch (error) {
        toast.error(error)
        break
      }
    }
    setBusy(null)
    setResults(done)
    refresh()
    client.invalidateQueries({ queryKey: keys.lookups })
    if (done.length) toast.ok(t('import.allDone'))
  }

  const reset = () => {
    setFile(null)
    setPlans([])
    setResults([])
    setStep('file')
  }

  const nameMapped = (p?: SheetPlan) => Boolean(p && Object.values(p.mapping).includes('NAME'))

  return (
    <section className="card p-4">
      <div className="mb-4 flex flex-wrap items-center gap-x-4 gap-y-2">
        <h2 className="mr-auto text-base font-semibold">{t('import.excelTitle')}</h2>
        <Stepper step={step} />
      </div>

      {step === 'file' && (
        <div className="grid place-items-center gap-3 rounded-2xl border-2 border-dashed border-line px-4 py-10 text-center">
          <FileSpreadsheet className="size-10 text-emerald-600" />
          <p className="max-w-lg text-sm text-muted">{t('import.excelHint')} {t('import.guessNote')}</p>
          <label className="btn-primary cursor-pointer">
            {busy === 'parse' ? <Spinner className="size-4" /> : <Upload className="size-4" />} {t('import.chooseFile')}
            <input type="file" accept=".xlsx,.xls,.csv" className="hidden" onChange={(e) => { void choose(e.target.files?.[0]); e.target.value = '' }} />
          </label>
        </div>
      )}

      {file && step === 'where' && (
        <div className="space-y-4">
          <p className="text-sm text-muted"><FileSpreadsheet className="mr-1 inline size-4 text-emerald-600" />{file.fileName} · {t('import.projectHint')}</p>
          <div className="grid gap-3 md:grid-cols-3">
            {(['new', 'existing', 'none'] as const).map((mode) => (
              <label key={mode} className={`cursor-pointer rounded-2xl border p-3 ${project.mode === mode ? 'border-brand-500 bg-brand-50' : 'border-line hover:border-brand-300'}`}>
                <span className="flex items-center gap-2 text-sm font-semibold">
                  <input type="radio" className="accent-brand-600" checked={project.mode === mode} onChange={() => setProject({ ...project, mode })} />
                  {t(`import.project${mode === 'new' ? 'New' : mode === 'existing' ? 'Existing' : 'None'}`)}
                </span>
                {mode === 'new' && project.mode === 'new' && (
                  <input className="input mt-2" maxLength={120} value={project.name} onChange={(e) => setProject({ ...project, name: e.target.value })} />
                )}
                {mode === 'existing' && project.mode === 'existing' && (
                  <select className="input mt-2" value={project.id} onChange={(e) => setProject({ ...project, id: e.target.value })}>
                    <option value="">{t('common.select')}</option>
                    {lookups.data?.workbooks.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
                  </select>
                )}
              </label>
            ))}
          </div>

          <div>
            <div className="label">{t('import.sheetsToImport')}</div>
            <div className="divide-y divide-line rounded-2xl border border-line">
              {file.sheets.map((s, i) => (
                <div key={s.name} className="flex flex-wrap items-center gap-3 px-3 py-2">
                  <label className="flex min-w-48 flex-1 items-center gap-2 text-sm">
                    <input type="checkbox" className="size-4 accent-brand-600" checked={plans[i].include} disabled={s.rows.length === 0} onChange={(e) => setPlan(i, { include: e.target.checked })} />
                    <FileSpreadsheet className="size-4 text-emerald-600" />
                    <span className="font-medium">{s.name}</span>
                    <span className="text-xs text-muted">{s.rows.length ? t('import.rows', { n: s.rows.length }) : t('import.emptySheet')}</span>
                  </label>
                  {plans[i].include && (
                    <label className="flex items-center gap-2 text-xs text-muted">
                      {t('import.sheetTarget')}
                      <input className="input w-48 py-1" maxLength={80} value={plans[i].target} onChange={(e) => setPlan(i, { target: e.target.value })} />
                    </label>
                  )}
                </div>
              ))}
            </div>
          </div>

          <StepButtons onBack={reset} onNext={() => { setCurrent(included[0] ?? 0); setStep('columns') }}
            nextDisabled={!included.length || (project.mode === 'existing' && !project.id) || (project.mode === 'new' && !project.name.trim())} />
        </div>
      )}

      {file && sheet && plan && step === 'columns' && (
        <div className="space-y-3">
          <SheetChips file={file} plans={plans} included={included} current={current} onPick={setCurrent} ok={(i) => nameMapped(plans[i])} />
          <ColumnMapper key={current} sheet={sheet} mapping={plan.mapping} onChange={(mapping) => setPlan(current, { mapping })} fields={lookups.data?.customFields ?? []} />
          {!nameMapped(plan) && <p className="rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-700">{t('import.nameMissing')}</p>}
          <StepButtons onBack={() => setStep('where')} onNext={() => setStep('check')} nextDisabled={!included.every((i) => nameMapped(plans[i]))} />
        </div>
      )}

      {file && sheet && step === 'check' && (
        <div className="space-y-4">
          {results.length > 0 ? (
            <div className="space-y-3 rounded-2xl border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900">
              <b className="flex items-center gap-2 text-base"><CircleCheck className="size-5" /> {t('import.allDone')}</b>
              {results.map(({ sheet: sheetName, result }) => (
                <div key={sheetName}>
                  <div className="font-medium">{t('import.sheetResult', { sheet: sheetName, created: result.created, skipped: result.skippedDuplicates + result.skippedInvalid })}</div>
                  <div className="text-xs">{t('import.doneDetail', { contacts: result.contacts, activities: result.activities, comments: result.comments, tasks: result.tasks, skipped: result.skippedDuplicates })}</div>
                </div>
              ))}
              <div className="flex flex-wrap gap-2">
                <Link to="/main" className="btn-primary">{t('import.openMain')}</Link>
                <button type="button" className="btn-secondary" onClick={reset}>{t('import.another')}</button>
              </div>
            </div>
          ) : (
            <>
              <div className="grid gap-3 rounded-xl bg-canvas p-3 sm:grid-cols-2 lg:grid-cols-4">
                {isSupervisor && (
                  <Field label={t('import.assignTo')}>
                    <select className="input" value={options.assignedToId} onChange={(e) => setOptions((o) => ({ ...o, assignedToId: e.target.value }))}>
                      <option value="">{t('common.unassigned')}</option>
                      {lookups.data?.users.filter((u) => u.active).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
                    </select>
                  </Field>
                )}
                <Field label={t('import.defaultType')}>
                  <select className="input" value={options.typeId} onChange={(e) => setOptions((o) => ({ ...o, typeId: e.target.value }))}>
                    <option value="">-</option>
                    {lookups.data?.businessTypes.map((type) => <option key={type.id} value={type.id}>{name(type)}</option>)}
                  </select>
                </Field>
                <Field label={t('import.city')}><input className="input" value={options.city} onChange={(e) => setOptions((o) => ({ ...o, city: e.target.value }))} /></Field>
                <Field label={t('import.district')}><input className="input" value={options.district} onChange={(e) => setOptions((o) => ({ ...o, district: e.target.value }))} /></Field>
                <label className="flex items-center gap-2 text-sm sm:col-span-2">
                  <input type="checkbox" className="size-4 accent-brand-600" checked={options.skipDuplicates} onChange={(e) => { setOptions((o) => ({ ...o, skipDuplicates: e.target.checked })); setPreviews({}) }} /> {t('import.skipDuplicates')}
                </label>
                <label className="flex items-center gap-2 text-sm sm:col-span-2">
                  <input type="checkbox" className="size-4 accent-brand-600" checked={options.createFollowUps} onChange={(e) => { setOptions((o) => ({ ...o, createFollowUps: e.target.checked })); setPreviews({}) }} /> {t('import.createFollowUps')}
                </label>
              </div>

              <SheetChips file={file} plans={plans} included={included} current={current} onPick={setCurrent} ok={(i) => Boolean(previews[i])} />
              <div className="flex flex-wrap gap-2">
                <button type="button" className="btn-secondary" disabled={busy !== null} onClick={() => void preview(current)}>
                  {busy === 'preview' && <Spinner className="size-4" />} {busy === 'preview' ? t('import.previewing') : `${t('import.preview')}: ${plans[current].target}`}
                </button>
              </div>
              {previews[current] && <PreviewTable preview={previews[current]} />}

              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3">
                <button type="button" className="btn-ghost" onClick={() => setStep('columns')}><ArrowLeft className="size-4" /> {t('import.back')}</button>
                <button type="button" className="btn-primary" disabled={busy !== null} onClick={() => void commitAll()}>
                  {busy?.startsWith('commit') ? <><Spinner className="size-4" /> {t('import.committing')} {file.sheets[Number(busy.split('-')[1])]?.name}</> : t('import.importAll', { n: included.length })}
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </section>
  )
}

function Stepper({ step }: { step: Step }) {
  const { t } = useI18n()
  const order: Step[] = ['file', 'where', 'columns', 'check']
  const at = order.indexOf(step)
  return (
    <ol className="flex flex-wrap items-center gap-1 text-xs">
      {order.map((s, i) => (
        <li key={s} className="flex items-center gap-1">
          <span className={`grid size-5 place-items-center rounded-full text-[11px] font-semibold ${i <= at ? 'bg-brand-600 text-white' : 'bg-canvas text-muted'}`}>{i + 1}</span>
          <span className={i === at ? 'font-semibold text-ink' : 'text-muted'}>{t(`import.steps.${s}`)}</span>
          {i < order.length - 1 && <span className="mx-1 h-px w-4 bg-line" />}
        </li>
      ))}
    </ol>
  )
}

function StepButtons({ onBack, onNext, nextDisabled }: { onBack: () => void; onNext: () => void; nextDisabled?: boolean }) {
  const { t } = useI18n()
  return (
    <div className="flex items-center justify-between gap-2 border-t border-line pt-3">
      <button type="button" className="btn-ghost" onClick={onBack}><ArrowLeft className="size-4" /> {t('import.back')}</button>
      <button type="button" className="btn-primary" disabled={nextDisabled} onClick={onNext}>{t('import.continue')} <ArrowRight className="size-4" /></button>
    </div>
  )
}

function SheetChips({ file, plans, included, current, onPick, ok }: {
  file: ParsedFile; plans: SheetPlan[]; included: number[]; current: number; onPick: (index: number) => void; ok: (index: number) => boolean
}) {
  if (included.length < 2) return null
  return (
    <div className="flex flex-wrap gap-1.5">
      {included.map((i) => (
        <button key={i} type="button" onClick={() => onPick(i)} className={i === current ? 'chip-on' : 'chip-off'}>
          <FileSpreadsheet className="size-3.5" /> {plans[i].target || file.sheets[i].name}
          {ok(i) && <CircleCheck className="size-3.5" />}
        </button>
      ))}
    </div>
  )
}

/**
 * Split screen: the CRM's fields on the left, the file's columns on the right. A column is dragged onto a
 * field (or clicked, then the field clicked). A column that fits nowhere is either skipped, after seeing
 * exactly what would be left out, or becomes a new field of its own.
 */
function ColumnMapper({ sheet, mapping, onChange, fields }: { sheet: SheetDto; mapping: Mapping; onChange: (mapping: Mapping) => void; fields: CustomField[] }) {
  const { t } = useI18n()
  const [picked, setPicked] = useState<number | null>(null)
  const [skipAsk, setSkipAsk] = useState<number | null>(null)
  const [newFieldFor, setNewFieldFor] = useState<number | null>(null)

  const values = useMemo(() => sheet.headers.map((_, col) => {
    const all = sheet.rows.map((row) => (row[col] ?? '').trim()).filter(Boolean)
    return { count: all.length, distinct: [...new Set(all)] }
  }), [sheet])

  const header = (col: number) => sheet.headers[col]?.split('\n')[0] || `#${col + 1}`
  const columnsFor = (target: ImportTarget) => Object.entries(mapping).filter(([, v]) => v === target).map(([k]) => Number(k))
  const targetLabel = (target: ImportTarget) => (target.startsWith('CUSTOM:') ? fields.find((f) => `CUSTOM:${f.id}` === target)?.label ?? target : t(`import.field.${target}`))

  const assign = (col: number, target: ImportTarget) => {
    const next: Mapping = { ...mapping }
    // Built-in fields take one column, except the free-text ones; custom fields join several with "; ".
    if (!target.startsWith('CUSTOM:') && !MULTI_COLUMN_FIELDS.includes(target as ImportField)) {
      for (const other of columnsFor(target)) delete next[other]
    }
    next[col] = target
    onChange(next)
    setPicked(null)
  }
  const detach = (col: number) => {
    const next = { ...mapping }
    delete next[col]
    onChange(next)
  }

  const undecided = sheet.headers.filter((_, col) => !mapping[col] && values[col].count > 0).length

  const card = (target: ImportTarget, label: string, hint: string, Icon: LucideIcon) => (
    <FieldCard key={target} target={target} label={label} hint={hint} Icon={Icon} required={target === 'NAME'}
      multiple={MULTI_COLUMN_FIELDS.includes(target as ImportField)} picked={picked !== null}
      attached={columnsFor(target).map((col) => ({ col, name: header(col) }))}
      onDrop={(col) => assign(col, target)} onClick={() => picked !== null && assign(picked, target)} onDetach={detach} />
  )

  return (
    <>
      <p className="text-sm text-muted">
        {picked !== null ? <b className="text-brand-700">{t('import.picked', { name: header(picked) })}</b> : t('import.dragHint')}
      </p>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3 rounded-2xl bg-canvas p-3 lg:max-h-[70vh] lg:overflow-y-auto">
          <h3 className="text-sm font-semibold">{t('import.systemFields')}</h3>
          {GROUPS.map(([group, list]) => (
            <div key={group}>
              <div className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted">{t(`import.groups.${group}`)}</div>
              <div className="grid gap-2 sm:grid-cols-2">{list.map((f) => card(f, t(`import.field.${f}`), t(`import.hint.${f}`), ICONS[f]))}</div>
            </div>
          ))}
          {fields.some((f) => f.active) && (
            <div>
              <div className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted">{t('import.groups.custom')}</div>
              <div className="grid gap-2 sm:grid-cols-2">{fields.filter((f) => f.active).map((f) => card(`CUSTOM:${f.id}`, f.label, '', CirclePlus))}</div>
            </div>
          )}
        </div>

        <div className="space-y-2 rounded-2xl border border-line p-3 lg:max-h-[70vh] lg:overflow-y-auto">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold">{t('import.fileColumns')}</h3>
            {undecided > 0 && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">{t('import.unmapped', { n: undecided })}</span>}
          </div>
          {sheet.headers.map((_, col) => {
            const target = mapping[col]
            const skipped = target === 'IGNORE'
            const empty = values[col].count === 0
            return (
              <div
                key={col}
                draggable={!skipped}
                onDragStart={(e) => { e.dataTransfer.setData('text/plain', String(col)); e.dataTransfer.effectAllowed = 'link' }}
                onClick={() => !skipped && setPicked(picked === col ? null : col)}
                className={`rounded-xl border p-2.5 transition ${picked === col ? 'border-brand-500 ring-2 ring-brand-200' : skipped || empty ? 'border-line opacity-60' : target ? 'border-emerald-300 bg-emerald-50/50' : 'border-amber-300 bg-amber-50/40'} ${skipped ? '' : 'cursor-grab active:cursor-grabbing'}`}
              >
                <div className="flex items-center gap-2">
                  {!skipped && <GripVertical className="size-4 shrink-0 text-muted" />}
                  <span className={`min-w-0 flex-1 truncate text-sm font-semibold ${skipped ? 'line-through' : ''}`} title={sheet.headers[col]}>{header(col)}</span>
                  {target && !skipped && <span className="truncate rounded-md bg-emerald-100 px-1.5 py-0.5 text-xs font-medium text-emerald-800">→ {targetLabel(target)}</span>}
                  {skipped && <span className="text-xs text-muted">{t('import.skipped')}</span>}
                </div>
                <div className="mt-1 truncate text-xs text-muted">
                  {empty ? t('import.noValues') : `${t('import.values', { n: values[col].count })} · ${values[col].distinct.slice(0, 3).join(' | ')}`}
                </div>
                <div className="mt-1.5 flex flex-wrap gap-1.5" onClick={(e) => e.stopPropagation()}>
                  {skipped ? (
                    <button type="button" className="btn-ghost px-2 py-0.5 text-xs" onClick={() => detach(col)}><Undo2 className="size-3.5" /> {t('import.undoSkip')}</button>
                  ) : (
                    <>
                      {target && <button type="button" className="btn-ghost px-2 py-0.5 text-xs" onClick={() => detach(col)}><X className="size-3.5" /> {t('import.detach')}</button>}
                      {!target && !empty && <button type="button" className="btn-ghost px-2 py-0.5 text-xs text-brand-700" onClick={() => setNewFieldFor(col)}><CirclePlus className="size-3.5" /> {t('import.newField')}</button>}
                      <button type="button" className="btn-ghost px-2 py-0.5 text-xs" onClick={() => (empty ? onChange({ ...mapping, [col]: 'IGNORE' }) : setSkipAsk(col))}>{t('import.skip')}</button>
                    </>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {skipAsk !== null && (
        <Modal open onClose={() => setSkipAsk(null)} title={t('import.skipTitle', { name: header(skipAsk) })} footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setSkipAsk(null)}>{t('common.cancel')}</button>
            <button type="button" className="btn-danger" onClick={() => { onChange({ ...mapping, [skipAsk]: 'IGNORE' }); setSkipAsk(null) }}>{t('import.skipConfirm')}</button>
          </>
        }>
          <p className="mb-2 text-sm">{t('import.skipBody', { n: values[skipAsk].count })}</p>
          <ul className="max-h-80 space-y-1 overflow-y-auto rounded-xl bg-canvas p-2 text-sm">
            {values[skipAsk].distinct.slice(0, 60).map((value) => <li key={value} className="whitespace-pre-line border-b border-line/60 pb-1 last:border-0">{value}</li>)}
          </ul>
          {values[skipAsk].distinct.length > 60 && <p className="mt-1 text-xs text-muted">{t('import.moreValues', { n: values[skipAsk].distinct.length - 60 })}</p>}
        </Modal>
      )}
      {newFieldFor !== null && (
        <NewFieldDialog initial={header(newFieldFor)} sample={values[newFieldFor].distinct.slice(0, 5)} onClose={() => setNewFieldFor(null)}
          onCreated={(field) => { assign(newFieldFor, `CUSTOM:${field.id}`); setNewFieldFor(null) }} />
      )}
    </>
  )
}

function FieldCard({ target, label, hint, Icon, required, multiple, picked, attached, onDrop, onClick, onDetach }: {
  target: ImportTarget; label: string; hint: string; Icon: LucideIcon; required: boolean; multiple: boolean; picked: boolean
  attached: { col: number; name: string }[]; onDrop: (col: number) => void; onClick: () => void; onDetach: (col: number) => void
}) {
  const { t } = useI18n()
  const [over, setOver] = useState(false)
  const filled = attached.length > 0
  return (
    <div
      data-target={target}
      onDragOver={(e) => { e.preventDefault(); setOver(true) }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => { e.preventDefault(); setOver(false); const col = Number(e.dataTransfer.getData('text/plain')); if (!Number.isNaN(col)) onDrop(col) }}
      onClick={onClick}
      className={`rounded-xl border bg-surface p-2.5 transition ${filled ? 'border-brand-300' : required ? 'border-dashed border-rose-300' : 'border-dashed border-line'} ${over ? 'scale-[1.02] border-brand-500 ring-2 ring-brand-300' : picked ? 'cursor-pointer hover:border-brand-400 hover:ring-2 hover:ring-brand-100' : ''}`}
    >
      <div className="flex items-center gap-2">
        <span className={`grid size-7 shrink-0 place-items-center rounded-lg ${filled ? 'bg-brand-600 text-white' : 'bg-brand-50 text-brand-700'}`}><Icon className="size-4" /></span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1 text-sm font-semibold leading-tight">
            {label}
            {required && <span className="rounded bg-rose-100 px-1 text-[10px] font-medium text-rose-700">{t('import.required')}</span>}
            {multiple && <span className="rounded bg-sky-100 px-1 text-[10px] font-medium text-sky-800">{t('import.multiple')}</span>}
          </div>
          {hint && <div className="truncate text-[11px] text-muted" title={hint}>{hint}</div>}
        </div>
      </div>
      <div className="mt-1.5 flex min-h-6 flex-wrap gap-1">
        {attached.map(({ col, name }) => (
          <span key={col} className="inline-flex max-w-full items-center gap-1 rounded-md bg-brand-100 px-1.5 py-0.5 text-xs font-medium text-brand-800">
            <span className="truncate">{name}</span>
            <button type="button" onClick={(e) => { e.stopPropagation(); onDetach(col) }} aria-label="detach"><X className="size-3" /></button>
          </span>
        ))}
        {!filled && <span className="text-[11px] text-muted/80">{t('import.dropHere')}</span>}
      </div>
    </div>
  )
}

function NewFieldDialog({ initial, sample, onClose, onCreated }: { initial: string; sample: string[]; onClose: () => void; onCreated: (field: CustomField) => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  const [label, setLabel] = useState(initial.slice(0, 80))
  const [busy, setBusy] = useState(false)
  const create = async () => {
    setBusy(true)
    try {
      const field = await api.post<CustomField>('/custom-fields', { label })
      await client.invalidateQueries({ queryKey: keys.lookups })
      onCreated(field)
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal open onClose={onClose} title={t('import.newFieldTitle')} footer={
      <>
        <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
        <button type="button" className="btn-primary" disabled={busy || !label.trim()} onClick={() => void create()}>{busy && <Spinner className="size-4" />} {t('common.create')}</button>
      </>
    }>
      <div className="space-y-3">
        <p className="text-sm text-muted">{t('import.newFieldHint')}</p>
        <Field label={t('import.newFieldLabel')}><input className="input" autoFocus maxLength={80} value={label} onChange={(e) => setLabel(e.target.value)} /></Field>
        {sample.length > 0 && <ul className="rounded-xl bg-canvas p-2 text-xs text-muted">{sample.map((v) => <li key={v} className="truncate">{v}</li>)}</ul>}
      </div>
    </Modal>
  )
}

function PreviewTable({ preview }: { preview: ImportPreview }) {
  const { t } = useI18n()
  return (
    <div>
      <div className="mb-2 flex flex-wrap gap-2 text-sm">
        <Pill tone="bg-emerald-100 text-emerald-800">{t('import.ready', { n: preview.ready })}</Pill>
        <Pill tone="bg-amber-100 text-amber-800">{t('import.duplicates', { n: preview.duplicates })}</Pill>
        {preview.possibleDuplicates > 0 && <Pill tone="bg-sky-100 text-sky-800">{t('import.possible', { n: preview.possibleDuplicates })}</Pill>}
        <Pill tone="bg-rose-100 text-rose-800">{t('import.invalid', { n: preview.invalid })}</Pill>
        <Pill tone="bg-violet-100 text-violet-800">{t('import.customers', { n: preview.customers })}</Pill>
      </div>
      <div className="max-h-[28rem] overflow-auto rounded-xl border border-line">
        <table className="w-full text-xs">
          <thead className="sticky top-0 bg-canvas text-left text-muted">
            <tr>
              <th className="px-2 py-2">#</th><th className="px-2 py-2">{t('common.name')}</th><th className="px-2 py-2">{t('common.status')}</th>
              <th className="px-2 py-2">{t('business.brand')}</th><th className="px-2 py-2">{t('business.flavors')}</th><th className="px-2 py-2">{t('business.contacts')}</th>
              <th className="px-2 py-2">{t('common.history')}</th><th className="px-2 py-2">{t('business.nextStep')}</th><th className="px-2 py-2" />
            </tr>
          </thead>
          <tbody>
            {preview.rows.map((row) => (
              <tr key={row.index} className={`border-t border-line ${row.error ? 'bg-rose-50' : row.duplicate?.strong ? 'bg-amber-50' : row.duplicate ? 'bg-sky-50' : ''}`}>
                <td className="px-2 py-1.5 text-muted">{row.index + 1}</td>
                <td className="max-w-48 px-2 py-1.5"><div className="truncate font-medium">{row.name ?? '-'}</div><div className="truncate text-muted">{[row.address, row.phone].filter(Boolean).join(' · ')}</div></td>
                <td className="px-2 py-1.5"><StatusBadge status={row.status} /></td>
                <td className="px-2 py-1.5">{row.brands.join(', ') || (row.usesSyrup !== 'UNKNOWN' ? t(`usage.${row.usesSyrup}`) : '-')}</td>
                <td className="max-w-40 truncate px-2 py-1.5">{row.flavors.join(', ') || '-'}</td>
                <td className="max-w-40 truncate px-2 py-1.5">{row.contacts.map((c) => c.name).join(', ') || '-'}</td>
                <td className="px-2 py-1.5 tabular-nums">{row.historyCount}</td>
                <td className="max-w-40 truncate px-2 py-1.5">{row.nextStep ?? '-'}</td>
                <td className="whitespace-nowrap px-2 py-1.5">
                  {row.error && <span className="text-rose-700">{row.error}</span>}
                  {row.duplicate?.strong && <span className="text-amber-800">{row.duplicate.id ? t('import.duplicateOf', { name: row.duplicate.name }) : t('import.inThisFile')}</span>}
                  {row.duplicate && !row.duplicate.strong && (
                    <span className="text-sky-800">{t('import.possibleDuplicate', { reason: t(`business.duplicateReason.${row.duplicate.reason}`), name: row.duplicate.name })}</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Pill({ tone, children }: { tone: string; children: ReactNode }) {
  return <span className={`rounded-full px-2.5 py-0.5 ${tone}`}>{children}</span>
}

// ----------------------------------------------------------------------------- JSON

function JsonImport() {
  const { t } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const [picked, setPicked] = useState<File | null>(null)
  const [dryRun, setDryRun] = useState<JsonImportResult | null>(null)
  const [done, setDone] = useState<JsonImportResult | null>(null)
  const [busy, setBusy] = useState(false)

  const send = async (file: File, dry: boolean) => {
    setBusy(true)
    try {
      const result = await api.upload<JsonImportResult>('/import/json', file, { dryRun: dry ? 'true' : 'false', skipDuplicates: 'true' })
      if (dry) { setDryRun(result); setDone(null) } else { setDone(result); setDryRun(null); refresh(); toast.ok(t('import.jsonDone', { created: result.created })) }
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card p-4">
      <h2 className="text-base font-semibold">{t('import.jsonTitle')}</h2>
      <p className="mb-3 text-sm text-muted">{t('import.jsonHint')}</p>
      <div className="flex flex-wrap items-center gap-2">
        <label className="btn-secondary cursor-pointer">
          <Upload className="size-4" /> {t('import.chooseFile')}
          <input type="file" accept=".json,application/json" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) { setPicked(f); void send(f, true) } e.target.value = '' }} />
        </label>
        {picked && <span className="text-sm text-muted">{picked.name}</span>}
        {busy && <Spinner className="size-4 text-muted" />}
      </div>
      {dryRun && picked && (
        <div className="mt-3 flex flex-wrap items-center gap-3 rounded-xl bg-canvas p-3 text-sm">
          <span>{t('import.jsonDryRun', { total: dryRun.total, created: dryRun.created, skipped: dryRun.skippedDuplicates })}</span>
          <button type="button" className="btn-primary" disabled={busy || dryRun.created === 0} onClick={() => void send(picked, false)}>{t('import.commit')}</button>
        </div>
      )}
      {done && <div className="mt-3 rounded-xl border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900">{t('import.jsonDone', { created: done.created })}</div>}
    </section>
  )
}
