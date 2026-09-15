import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  ArrowLeft, ArrowRight, CalendarDays, Clock, ExternalLink, MapPin, MessageSquare, Phone, PhoneOff, ShoppingCart, Sparkles, Star, ThumbsDown, ThumbsUp,
} from 'lucide-react'
import { api } from '../lib/api'
import { useMode } from '../lib/mode'
import { useBusiness, useDashboard, useLookups, useRefreshWork } from '../lib/queries'
import { atDaysFromNow, daysFromToday, fmtDateTime, mapsHref, money, splitPhones } from '../lib/format'
import type { ActivityResult, BusinessDetail, MissingCode, TaskDto } from '../lib/types'
import { useI18n } from '../i18n'
import { BusinessSelect } from '../components/BusinessSelect'
import { EmptyState, ErrorBlock, Loading, PageHeader, StatusBadge } from '../components/ui'
import { LogActivityDialog, RESULT_TONE } from '../components/LogActivityDialog'
import { ContactDialog } from '../components/ContactDialog'
import { BusinessEditDialog } from '../components/BusinessEditDialog'
import { PurchaseDialog } from '../components/PurchaseDialog'
import { TaskRow } from '../components/TaskRow'
import { useToast } from '../components/Toast'

const QUICK_RESULTS: ActivityResult[] = ['TALKED', 'CALL_BACK', 'INTERESTED', 'MEETING_SET', 'SAMPLES_REQUESTED', 'NOT_INTERESTED']
const CONTACT_QUESTIONS: MissingCode[] = ['PHONE', 'CONTACT_PERSON', 'DECISION_MAKER']
const PRODUCT_QUESTIONS: MissingCode[] = ['USES_SYRUP', 'SYRUP_BRAND', 'SYRUP_FLAVORS', 'NEXT_STEP']

/**
 * Call mode: one place at a time, everything needed during the call on one screen. Numbers big
 * enough to tap, what happened last time and what is planned, the questions still open in red,
 * a note box, and one tap for the result. Works through today's calls one after another.
 */
export function CallModePage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const params = useParams()
  const [search] = useSearchParams()
  const businessId = params.id ? Number(params.id) : null
  const taskId = search.get('task') ? Number(search.get('task')) : null
  const dashboard = useDashboard('mine')

  // Today's queue: overdue first, then today's, one entry per business.
  const queue = useMemo(() => {
    const seen = new Set<number>()
    const list: TaskDto[] = []
    for (const task of [...(dashboard.data?.overdue ?? []), ...(dashboard.data?.today ?? [])]) {
      if (task.status !== 'OPEN' || !task.businessId || task.type === 'MEETING' || task.type === 'VISIT' || seen.has(task.businessId)) continue
      seen.add(task.businessId)
      list.push(task)
    }
    return list
  }, [dashboard.data])

  const go = (task: TaskDto | undefined) => {
    if (task?.businessId) navigate(`/calls/${task.businessId}?task=${task.id}`)
    else navigate('/calls')
  }

  if (!businessId) {
    return (
      <div className="mx-auto max-w-2xl">
        <PageHeader title={t('callMode.title')} subtitle={t('callMode.queueHint')} />
        <div className="card mb-4 p-3">
          <div className="label px-1">{t('callMode.pickPlace')}</div>
          <BusinessSelect value={null} onChange={(pick) => pick && navigate(`/calls/${pick.id}`)} />
        </div>
        <section className="card p-3">
          <div className="mb-1 flex items-center justify-between px-2">
            <h2 className="text-sm font-semibold">{t('callMode.queue')} ({queue.length})</h2>
            {queue.length > 0 && (
              <button type="button" className="btn-primary" onClick={() => go(queue[0])}>
                <Phone className="size-4" /> {t('common.next')}
              </button>
            )}
          </div>
          {dashboard.isLoading ? <Loading /> : queue.length === 0 ? <EmptyState icon={<Phone className="size-8" />} title={t('callMode.empty')} /> : (
            queue.map((task) => <TaskRow key={task.id} task={task} showDate onOpen={() => go(task)} editable={false} />)
          )}
        </section>
      </div>
    )
  }

  const index = queue.findIndex((task) => task.businessId === businessId)
  const current = taskId ? [...(dashboard.data?.overdue ?? []), ...(dashboard.data?.today ?? [])].find((task) => task.id === taskId) ?? null : null
  return (
    <CallCard
      key={businessId}
      businessId={businessId}
      task={current}
      position={index >= 0 ? { index, total: queue.length } : null}
      onPrev={index > 0 ? () => go(queue[index - 1]) : undefined}
      onNext={() => go(queue[index + 1] ?? queue.find((task) => task.businessId !== businessId))}
    />
  )
}

function CallCard({ businessId, task, position, onPrev, onNext }: {
  businessId: number
  task: TaskDto | null
  position: { index: number; total: number } | null
  onPrev?: () => void
  onNext: () => void
}) {
  const { t, lang, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const { canEdit } = useMode()
  const lookups = useLookups()
  const business = useBusiness(businessId)
  const [note, setNote] = useState('')
  const [logResult, setLogResult] = useState<ActivityResult | null>(null)
  const [logOpen, setLogOpen] = useState(false)
  const [contactOpen, setContactOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [purchaseOpen, setPurchaseOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  if (business.isLoading) return <Loading />
  if (business.error || !business.data) return <ErrorBlock error={business.error} onRetry={() => business.refetch()} />
  const b: BusinessDetail = business.data
  const editable = canEdit(b.canEdit)

  const phones = [
    ...splitPhones(b.phone).map((p) => ({ ...p, who: b.name, role: null as string | null, decides: false })),
    ...b.contacts.flatMap((c) => splitPhones(c.phone).map((p) => ({ ...p, who: c.name, role: c.roleTitle, decides: c.decisionMaker }))),
  ]
  const maps = mapsHref(b)
  const syrup = lookups.data?.categories.find((c) => c.nameEn === 'Syrup')
  const categoryName = (id: number) => name(lookups.data?.categories.find((c) => c.id === id))

  // "No answer" is the most common result by far: one tap saves it and plans the retry for tomorrow.
  const noAnswer = async () => {
    setBusy(true)
    try {
      await api.post(`/businesses/${b.id}/activities`, {
        type: 'CALL', result: 'NO_ANSWER', notes: note || null, completeTaskId: task?.id ?? null,
        nextTask: { type: 'CALL', dueAt: atDaysFromNow(1, 12), title: task?.title ?? null },
      })
      toast.ok(`${t('activityResult.NO_ANSWER')} · ${t('activity.presets.tomorrow')}`)
      setNote('')
      refresh(b.id)
      if (position) onNext()
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }

  const saveNote = async () => {
    if (!note.trim()) return
    setBusy(true)
    try {
      await api.post(`/businesses/${b.id}/comments`, { body: note })
      toast.ok(t('common.saved'))
      setNote('')
      refresh(b.id)
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }

  const askAbout = (code: MissingCode) => {
    if (!editable) return
    if (CONTACT_QUESTIONS.includes(code)) setContactOpen(true)
    else if (PRODUCT_QUESTIONS.includes(code)) { setLogResult('TALKED'); setLogOpen(true) }
    else setEditOpen(true)
  }

  const lastDays = daysFromToday(b.lastActivity?.occurredAt)
  const used = b.usages.filter((u) => !u.ownBrand)
  const brands = [...new Set(used.map((u) => u.brandName).filter(Boolean))]

  return (
    <div className="mx-auto max-w-5xl">
      {/* Header with queue navigation */}
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <Link to="/calls" className="btn-ghost -ml-2 px-2"><ArrowLeft className="size-4" /> {t('callMode.title')}</Link>
        {position && <span className="text-xs text-muted">{t('callMode.of', { i: position.index + 1, n: position.total })}</span>}
        <div className="ml-auto flex gap-2">
          {onPrev && <button type="button" className="btn-secondary" onClick={onPrev}><ArrowLeft className="size-4" /> {t('callMode.prev')}</button>}
          <button type="button" className="btn-secondary" onClick={onNext}>{t('callMode.next')} <ArrowRight className="size-4" /></button>
        </div>
      </div>

      <div className="card mb-4 p-4">
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1">
            <h1 className="text-2xl font-semibold tracking-tight">{b.name}</h1>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
              <StatusBadge status={b.status} />
              {b.typeId && <span>{name(lookups.data?.businessTypes.find((x) => x.id === b.typeId))}</span>}
              {(b.address || b.district) && <span>{[b.address, b.district].filter(Boolean).join(', ')}</span>}
            </div>
            {b.visitHours && (
              <div className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-amber-50 px-2 py-1 text-sm text-amber-900">
                <Clock className="size-4" /> {b.visitHours}
              </div>
            )}
          </div>
          <div className="flex gap-2">
            {maps && <a href={maps} target="_blank" rel="noreferrer" className="btn-secondary"><MapPin className="size-4" /> {t('business.openMaps')}</a>}
            <Link to={`/businesses/${b.id}`} className="btn-secondary"><ExternalLink className="size-4" /> {t('callMode.open')}</Link>
          </div>
        </div>

        {/* Big numbers */}
        <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {phones.length === 0 && <div className="rounded-2xl border-2 border-dashed border-rose-300 bg-rose-50 p-4 text-sm font-medium text-rose-700">{t('missing.PHONE')}</div>}
          {phones.map((phone, i) => (
            <a key={`${phone.href}-${i}`} href={phone.href} className="flex items-center gap-3 rounded-2xl bg-brand-600 p-3.5 text-white shadow-sm transition hover:brightness-110 active:scale-[0.99]">
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-surface/20"><Phone className="size-5" /></span>
              <span className="min-w-0">
                <span className="block text-xl font-semibold tracking-wide tabular-nums">{phone.display}</span>
                <span className="flex items-center gap-1 truncate text-xs opacity-85">
                  {phone.decides && <Star className="size-3 fill-current" />}
                  {phone.who}{phone.role ? ` · ${phone.role}` : ''}
                </span>
              </span>
            </a>
          ))}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-3">
          {/* Result buttons */}
          {editable && (
            <section className="card p-4">
              <h2 className="mb-3 text-sm font-semibold">{t('callMode.logResult')}</h2>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <button type="button" disabled={busy} onClick={() => void noAnswer()} className="flex flex-col items-center gap-1 rounded-2xl border-2 border-slate-300 bg-slate-50 p-3 text-sm font-semibold text-slate-700 hover:bg-slate-100">
                  <PhoneOff className="size-5" /> {t('activityResult.NO_ANSWER')}
                </button>
                {QUICK_RESULTS.map((result) => (
                  <button
                    key={result}
                    type="button"
                    onClick={() => { setLogResult(result); setLogOpen(true) }}
                    className={`rounded-2xl border-2 p-3 text-sm font-semibold ${
                      RESULT_TONE[result] ? `${RESULT_TONE[result]} hover:opacity-90` : 'border-line bg-surface hover:border-brand-300'
                    }`}
                  >
                    {t(`activityResult.${result}`)}
                  </button>
                ))}
                <button type="button" onClick={() => setPurchaseOpen(true)} className="flex items-center justify-center gap-1.5 rounded-2xl border-2 border-emerald-700 bg-emerald-50 p-3 text-sm font-semibold text-emerald-800 hover:bg-emerald-100">
                  <ShoppingCart className="size-4" /> {t('activityResult.ORDERED')}
                </button>
              </div>
              <div className="mt-3">
                <div className="label flex items-center gap-1.5"><MessageSquare className="size-3.5" /> {t('callMode.quickNote')}</div>
                <textarea rows={3} className="input text-base" placeholder={t('callMode.notePlaceholder')} value={note} onChange={(e) => setNote(e.target.value)} />
                <div className="mt-2 flex flex-wrap justify-end gap-2">
                  <button type="button" className="btn-secondary" disabled={busy || !note.trim()} onClick={() => void saveNote()}>{t('callMode.saveNote')}</button>
                  <button type="button" className="btn-secondary" onClick={() => { setLogResult(null); setLogOpen(true) }}>{t('callMode.more')}</button>
                </div>
              </div>
            </section>
          )}

          {/* Ask them */}
          <section className="card p-4">
            <h2 className="mb-2 text-sm font-semibold">{t('callMode.askThem')}</h2>
            {b.missing.length === 0 ? (
              <p className="text-sm text-emerald-700">{t('callMode.allKnown')}</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {b.missing.map((code) => (
                  <button
                    key={code}
                    type="button"
                    onClick={() => askAbout(code)}
                    className="rounded-xl border border-rose-300 bg-rose-50 px-3 py-2 text-left text-sm font-medium text-rose-700 hover:bg-rose-100"
                  >
                    {t(`missing.${code}`)}
                  </button>
                ))}
              </div>
            )}
          </section>

          {/* What they use and want */}
          <section className="card p-4">
            <h2 className="mb-2 text-sm font-semibold">{t('business.whatTheyUse')}</h2>
            {b.usages.length === 0 && <p className="text-sm text-muted">{t('business.none')}</p>}
            <div className="space-y-1.5">
              {brands.map((brand) => (
                <div key={brand} className="text-sm">
                  <span className="font-semibold">{brand}</span>
                  <span className="text-muted">: {used.filter((u) => u.brandName === brand && u.flavor).map((u) => name(u.flavor)).join(', ') || '-'}</span>
                </div>
              ))}
              {used.some((u) => !u.brandName && u.flavor) && (
                <div className="text-sm">
                  <span className="font-semibold text-muted">{t('business.noBrand')}</span>
                  <span className="text-muted">: {used.filter((u) => !u.brandName && u.flavor).map((u) => `${name(u.flavor)}${u.categoryId !== syrup?.id ? ` (${categoryName(u.categoryId)})` : ''}`).join(', ')}</span>
                </div>
              )}
              {b.usages.some((u) => u.ownBrand) && (
                <div className="text-sm">
                  <span className="font-semibold text-brand-700">Andaneri</span>
                  <span className="text-muted">: {b.usages.filter((u) => u.ownBrand).map((u) => name(u.flavor)).filter(Boolean).join(', ')}</span>
                </div>
              )}
            </div>
            {b.interests.length > 0 && (
              <>
                <h3 className="mb-1.5 mt-3 text-xs font-semibold uppercase tracking-wide text-muted">{t('business.whatTheyWant')}</h3>
                <div className="flex flex-wrap gap-1.5">
                  {b.interests.map((i) => (
                    <span key={i.id} className={`chip ${i.feedback === 'LIKED' ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : i.feedback === 'DISLIKED' || i.status === 'NOT_INTERESTED' ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-line bg-surface'}`}>
                      {i.feedback === 'LIKED' && <ThumbsUp className="size-3" />}
                      {i.feedback === 'DISLIKED' && <ThumbsDown className="size-3" />}
                      {i.flavor ? name(i.flavor) : i.product ? name(i.product) : ''} · {t(`interestStatus.${i.status}`)}
                    </span>
                  ))}
                </div>
              </>
            )}
          </section>
        </div>

        <div className="space-y-4 lg:col-span-2">
          <section className="card p-4">
            <h2 className="mb-2 text-sm font-semibold">{t('business.lastTime')}</h2>
            {b.lastActivity ? (
              <div>
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-medium">{t(`activityType.${b.lastActivity.type}`)}</span>
                  <span className={`rounded-md px-1.5 py-0.5 text-xs ${RESULT_TONE[b.lastActivity.result] ?? 'bg-canvas'}`}>{t(`activityResult.${b.lastActivity.result}`)}</span>
                </div>
                <div className="mt-0.5 text-xs text-muted">
                  {fmtDateTime(b.lastActivity.occurredAt, lang)}
                  {lastDays !== null && lastDays < 0 && ` · ${t('common.daysAgo', { n: -lastDays })}`}
                  {` · ${b.lastActivity.user.fullName}`}
                  {b.lastActivity.contact && ` · ${b.lastActivity.contact.name}`}
                </div>
                {b.lastActivity.notes && <p className="mt-2 whitespace-pre-line rounded-xl bg-canvas p-2.5 text-sm">{b.lastActivity.notes}</p>}
              </div>
            ) : (
              <p className="text-sm text-muted">{t('callMode.neverContacted')}</p>
            )}
          </section>

          <section className="card p-4">
            <h2 className="mb-2 flex items-center gap-1.5 text-sm font-semibold"><CalendarDays className="size-4" /> {t('business.planned')}</h2>
            {b.openTasks.length === 0 ? (
              <p className="text-sm text-muted">{t('callMode.nothingPlanned')}</p>
            ) : (
              b.openTasks.slice(0, 4).map((open) => (
                <div key={open.id} className={`rounded-xl px-2 py-1.5 text-sm ${open.id === task?.id ? 'bg-brand-50' : ''}`}>
                  <span className="font-medium">{fmtDateTime(open.dueAt, lang)}</span> · {t(`taskType.${open.type}`)}
                  {open.title && <span className="block truncate text-xs text-muted">{open.title}</span>}
                </div>
              ))
            )}
          </section>

          {b.suggestions.length > 0 && (
            <section className="card p-4">
              <h2 className="mb-2 flex items-center gap-1.5 text-sm font-semibold"><Sparkles className="size-4 text-brand-500" /> {t('business.suggestions')}</h2>
              <ul className="space-y-1.5">
                {b.suggestions.slice(0, 6).map((s) => (
                  <li key={`${s.kind}-${s.product.id}`} className="flex items-center gap-2 text-sm">
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{name(s.product)}</span>
                      <span className="block truncate text-xs text-muted">
                        {t(`suggestion.${s.kind}`, { match: s.kind === 'TREND' ? s.matchEn.split(',').map((d) => t(`drink.${d}`)).join(', ') : lang === 'ka' ? s.matchKa : s.matchEn })}
                      </span>
                    </span>
                    <span className="font-semibold tabular-nums">{money(s.product.price)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {b.purchases.count > 0 && (
            <section className="card p-4 text-sm">
              <div className="font-semibold">{t('business.ordersCount', { n: b.purchases.count })} · {money(b.purchases.total)}</div>
              {b.purchases.daysSinceLast !== null && <div className="text-muted">{t('business.sinceLast', { n: b.purchases.daysSinceLast })} · {t('business.every', { n: b.purchases.expectedDays })}</div>}
            </section>
          )}
        </div>
      </div>

      <LogActivityDialog
        open={logOpen}
        onClose={() => setLogOpen(false)}
        business={b}
        initialType="CALL"
        initialResult={logResult}
        initialNotes={note}
        completeTask={task}
        onSaved={(_, result) => {
          setNote('')
          if (result === 'ORDERED') setPurchaseOpen(true)
        }}
      />
      <ContactDialog open={contactOpen} onClose={() => setContactOpen(false)} businessId={b.id} />
      <BusinessEditDialog open={editOpen} onClose={() => setEditOpen(false)} business={b} />
      <PurchaseDialog open={purchaseOpen} onClose={() => setPurchaseOpen(false)} business={b} />
    </div>
  )
}
