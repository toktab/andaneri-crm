import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import {
  ArrowLeft, CalendarPlus, Clock, FileSpreadsheet, Globe, Mail, MapPin, MessageSquare, Pencil, Phone, Plus, ShoppingCart, Sparkles, Star, StickyNote,
  ThumbsDown, ThumbsUp, User, X,
} from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { useBusiness, useLookups, usePurchases, useRefreshWork, useTimeline } from '../lib/queries'
import { fmtDate, fmtDateTime, mapsHref, money, number, splitPhones } from '../lib/format'
import type {
  ActivityDto, ActivityType, BusinessDetail, BusinessStatus, ContactDto, InterestDto, InterestStatus, MissingCode,
  TaskDto, TastingFeedback, TimelineItem, UsageDto,
} from '../lib/types'
import { STATUSES } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, ErrorBlock, Loading, PriorityMark, StatusBadge, Tabs } from '../components/ui'
import { LogActivityDialog, RESULT_TONE } from '../components/LogActivityDialog'
import { TaskDialog } from '../components/TaskDialog'
import { PurchaseDialog } from '../components/PurchaseDialog'
import { ContactDialog } from '../components/ContactDialog'
import { BusinessEditDialog } from '../components/BusinessEditDialog'
import { InterestDialog, UsageDialog } from '../components/ProductInfoDialogs'
import { Phones } from '../components/Phones'
import { NoteDialog } from '../components/NoteDialog'
import { TaskRow } from '../components/TaskRow'
import { useToast } from '../components/Toast'

type Tab = 'summary' | 'calls' | 'visits' | 'answers' | 'history' | 'purchases' | 'details'

export function BusinessPage() {
  const { t } = useI18n()
  const params = useParams()
  const id = Number(params.id)
  const business = useBusiness(id)
  if (business.isLoading) return <Loading />
  if (business.error || !business.data) return <ErrorBlock error={business.error} onRetry={() => business.refetch()} />
  return <Profile key={id} b={business.data} backLabel={t('businesses.title')} />
}

/** The whole file of one business. {@code embedded}: shown inside the Main page's drawer, without the back link. */
export function Profile({ b, backLabel, embedded }: { b: BusinessDetail; backLabel: string; embedded?: boolean }) {
  const { t, lang, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const { user, isSupervisor } = useAuth()
  const { canEdit } = useMode()
  const lookups = useLookups()
  const timeline = useTimeline(b.id)
  const [search, setSearch] = useSearchParams()
  const [tab, setTab] = useState<Tab>('summary')
  const [log, setLog] = useState<{ type: ActivityType; task: TaskDto | null } | null>(null)
  const [taskOpen, setTaskOpen] = useState<TaskDto | 'new' | null>(null)
  const [purchaseOpen, setPurchaseOpen] = useState(false)
  const [contact, setContact] = useState<ContactDto | 'new' | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [usageOpen, setUsageOpen] = useState(false)
  const [usage, setUsage] = useState<UsageDto | null>(null)
  const [editing, setEditing] = useState<ActivityDto | null>(null)
  const [interestOpen, setInterestOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const editable = canEdit(b.canEdit)

  // Arriving from "done + log" on a task: open the log for it straight away.
  useEffect(() => {
    const taskId = search.get('task')
    if (!taskId) return
    const task = b.openTasks.find((x) => x.id === Number(taskId)) ?? null
    if (task && editable) setLog({ type: task.type === 'MEETING' ? 'MEETING' : task.type === 'VISIT' ? 'VISIT' : 'CALL', task })
    const next = new URLSearchParams(search)
    next.delete('task')
    setSearch(next, { replace: true })
  }, [search, b.openTasks, editable, setSearch])

  const activities = (timeline.data ?? []).filter((item) => item.kind === 'ACTIVITY')
  const calls = activities.filter((item) => item.type === 'CALL' || item.type === 'MESSAGE')
  const visits = activities.filter((item) => item.type === 'VISIT' || item.type === 'MEETING' || item.type === 'SAMPLES')

  const changeStatus = async (status: BusinessStatus) => {
    try {
      await api.post(`/businesses/${b.id}/status`, { status })
      refresh(b.id)
    } catch (error) {
      toast.error(error)
    }
  }
  const claim = async () => {
    try {
      await api.post(`/businesses/${b.id}/assign`, { userId: user?.id })
      refresh(b.id)
    } catch (error) {
      toast.error(error)
    }
  }
  const completeTask = async (task: TaskDto) => {
    try {
      await api.post(`/tasks/${task.id}/complete`, {})
      refresh(b.id)
    } catch (error) {
      toast.error(error)
    }
  }

  const maps = mapsHref(b)
  const typeName = name(lookups.data?.businessTypes.find((x) => x.id === b.typeId))

  return (
    // Room at the bottom for the action bar that floats over the page on a phone.
    <div className={embedded ? '' : 'pb-16 md:pb-0'}>
      {!embedded && <Link to="/businesses" className="btn-ghost -ml-2 mb-2 px-2 text-sm"><ArrowLeft className="size-4" /> {backLabel}</Link>}

      <div className="card mb-4 p-4">
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1">
            <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
              <PriorityMark priority={b.priority} /> {b.name}
              {b.archived && <span className="rounded-full bg-stone-200 px-2 py-0.5 text-xs font-medium text-stone-700">{t('business.archived')}</span>}
            </h1>
            <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-muted">
              {editable ? (
                <select
                  value={b.status}
                  onChange={(e) => void changeStatus(e.target.value as BusinessStatus)}
                  className="rounded-full border border-line bg-surface px-2 py-0.5 text-xs font-medium text-ink"
                >
                  {STATUSES.map((s) => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
                </select>
              ) : <StatusBadge status={b.status} />}
              {typeName && <span>{typeName}</span>}
              {b.sheetName && (
                <Link to={`/main?sheet=${b.sheetId}`} className="inline-flex items-center gap-1 rounded-full bg-canvas px-2 py-0.5 text-xs text-ink/80 hover:text-brand-700">
                  <FileSpreadsheet className="size-3.5" /> {[b.workbookName, b.sheetName].filter(Boolean).join(' / ')}
                </Link>
              )}
              {(b.address || b.district) && <span>{[b.address, b.district, b.city].filter(Boolean).join(', ')}</span>}
              <span className="inline-flex items-center gap-1"><User className="size-3.5" /> {b.assignedTo?.fullName ?? t('common.unassigned')}</span>
              {!b.assignedTo && !isSupervisor && editable && (
                <button type="button" className="text-xs font-medium text-brand-700 underline" onClick={() => void claim()}>{t('business.claim')}</button>
              )}
            </div>
          </div>
          <div className="no-print hidden flex-wrap gap-2 md:flex">
            <Link to={`/calls/${b.id}`} className="btn-primary"><Phone className="size-4" /> {t('dashboard.startCalling')}</Link>
            {editable && (
              <>
                <button type="button" className="btn-secondary" onClick={() => setLog({ type: 'VISIT', task: null })}><MessageSquare className="size-4" /> {t('business.logActivity')}</button>
                <button type="button" className="btn-secondary" onClick={() => setTaskOpen('new')}><CalendarPlus className="size-4" /> {t('business.schedule')}</button>
                <button type="button" className="btn-secondary" onClick={() => setPurchaseOpen(true)}><ShoppingCart className="size-4" /> {t('business.addPurchase')}</button>
                <button type="button" className="btn-secondary" onClick={() => setNoteOpen(true)} title={t('notes.title')}><StickyNote className="size-4" /></button>
                <button type="button" className="btn-secondary" onClick={() => setEditOpen(true)} title={t('business.editDetails')}><Pencil className="size-4" /></button>
              </>
            )}
          </div>
        </div>
      </div>

      {/* The bar a thumb reaches without moving the hand. It sits above the navigation, out of the way of the file. */}
      {!embedded && (
        <div className="no-print fixed inset-x-0 bottom-[calc(3.9rem+env(safe-area-inset-bottom))] z-30 flex gap-2 border-t border-line bg-surface/95 py-2 pl-3 pr-[4.75rem] backdrop-blur md:hidden">
          <Link to={`/calls/${b.id}`} className="btn-primary flex-1 py-3"><Phone className="size-5" /> {t('dashboard.startCalling')}</Link>
          {editable && (
            <>
              <button type="button" className="btn-secondary px-4 py-3" onClick={() => setLog({ type: 'VISIT', task: null })} title={t('business.logActivity')}>
                <MessageSquare className="size-5" />
              </button>
              <button type="button" className="btn-secondary px-4 py-3" onClick={() => setTaskOpen('new')} title={t('business.schedule')}>
                <CalendarPlus className="size-5" />
              </button>
              <button type="button" className="btn-secondary px-4 py-3" onClick={() => setEditOpen(true)} title={t('business.editDetails')}>
                <Pencil className="size-5" />
              </button>
            </>
          )}
        </div>
      )}

      <Tabs<Tab>
        value={tab}
        onChange={setTab}
        tabs={[
          { key: 'summary', label: t('business.tabs.summary') },
          { key: 'calls', label: t('business.tabs.calls'), count: calls.length },
          { key: 'visits', label: t('business.tabs.visits'), count: visits.length },
          { key: 'answers', label: t('business.tabs.answers'), count: b.usages.length + b.interests.length },
          { key: 'history', label: t('business.tabs.history'), count: timeline.data?.length },
          { key: 'purchases', label: t('business.tabs.purchases'), count: b.purchases.count },
          { key: 'details', label: t('business.tabs.details') },
        ]}
      />

      <div className="mt-4">
        {tab === 'summary' && (
          <div className="grid gap-4 lg:grid-cols-3">
            <div className="min-w-0 space-y-4 lg:col-span-2">
              <Card title={t('business.nextStep')} action={editable && <IconButton onClick={() => setTaskOpen('new')}><Plus className="size-4" /></IconButton>}>
                {b.openTasks.length === 0 ? <p className="px-1 text-sm text-rose-600">{t('business.noNextStep')}</p> : (
                  b.openTasks.map((task) => <TaskRow key={task.id} task={task} showDate editable={editable} onOpen={setTaskOpen} onComplete={completeTask} />)
                )}
              </Card>

              <Card title={t('business.lastTime')}>
                {b.lastActivity ? (
                  <div className="px-1">
                    <div className="flex flex-wrap items-center gap-2 text-sm">
                      <b>{t(`activityType.${b.lastActivity.type}`)}</b>
                      <ResultBadge result={b.lastActivity.result} />
                      <span className="text-xs text-muted">{fmtDateTime(b.lastActivity.occurredAt, lang)} · {b.lastActivity.user.fullName}</span>
                      {editable && (
                        <button type="button" className="btn-ghost ml-auto p-1.5" title={t('activity.edit')} onClick={() => void openActivity(b.lastActivity!.id)}>
                          <Pencil className="size-3.5" />
                        </button>
                      )}
                    </div>
                    {b.lastActivity.notes && <p className="mt-2 whitespace-pre-line text-sm">{b.lastActivity.notes}</p>}
                  </div>
                ) : <p className="px-1 text-sm text-muted">{t('callMode.neverContacted')}</p>}
              </Card>

              {b.missing.length > 0 && (
                <Card title={t('business.askThem')}>
                  <div className="flex flex-wrap gap-2 px-1">
                    {b.missing.map((code) => (
                      <button key={code} type="button" disabled={!editable} onClick={() => openFor(code)} className="rounded-xl border border-rose-300 bg-rose-50 px-2.5 py-1.5 text-sm text-rose-700 hover:bg-rose-100 disabled:cursor-default">
                        {t(`missing.${code}`)}
                      </button>
                    ))}
                  </div>
                </Card>
              )}

              <Card title={t('business.whatTheyUse')} action={editable && <IconButton onClick={() => setUsageOpen(true)}><Plus className="size-4" /></IconButton>}>
                <UsageSummary b={b} />
              </Card>

              <CommentBox businessId={b.id} />
            </div>

            <div className="space-y-4">
              <Card title={t('business.contacts')} action={editable && <IconButton onClick={() => setContact('new')}><Plus className="size-4" /></IconButton>}>
                <ContactList b={b} onEdit={editable ? setContact : undefined} />
              </Card>

              <Card title={t('business.suggestions')} action={editable && <IconButton onClick={() => setInterestOpen(true)}><Plus className="size-4" /></IconButton>}>
                {b.suggestions.length === 0 ? <p className="px-1 text-sm text-muted">{t('business.none')}</p> : (
                  <ul className="space-y-2 px-1">
                    {b.suggestions.map((s) => (
                      <li key={`${s.kind}-${s.product.id}`} className="flex items-start gap-2 text-sm">
                        <Sparkles className="mt-0.5 size-4 shrink-0 text-brand-500" />
                        <span className="min-w-0 flex-1">
                          <span className="block font-medium">{name(s.product)}</span>
                          <span className="block text-xs text-muted">
                            {t(`suggestion.${s.kind}`, { match: s.kind === 'TREND' ? s.matchEn.split(',').map((d) => t(`drink.${d}`)).join(', ') : lang === 'ka' ? s.matchKa : s.matchEn })}
                          </span>
                        </span>
                        <span className="font-semibold tabular-nums">{money(s.product.price)}</span>
                      </li>
                    ))}
                  </ul>
                )}
                {b.interests.length > 0 && (
                  <div className="mt-2 border-t border-line px-1 pt-2">
                    <div className="text-xs font-semibold uppercase tracking-wide text-muted">{t('business.offered')}</div>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {b.interests.map((i) => (
                        <span key={i.id} className="chip border-brand-200 bg-brand-50 text-brand-800">
                          {i.flavor ? name(i.flavor) : i.product ? name(i.product) : '-'}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </Card>

              <Card title={t('business.purchases')}>
                <PurchaseFacts b={b} />
              </Card>
            </div>
          </div>
        )}

        {tab === 'calls' && (
          <ActivityList items={calls} loading={timeline.isLoading} empty={t('business.callsEmpty')} businessId={b.id} onEdit={editable ? openActivity : undefined} action={editable && (
            <button type="button" className="btn-primary" onClick={() => setLog({ type: 'CALL', task: null })}><Phone className="size-4" /> {t('activityType.CALL')}</button>
          )} />
        )}

        {tab === 'visits' && (
          <ActivityList items={visits} loading={timeline.isLoading} empty={t('business.visitsEmpty')} businessId={b.id} onEdit={editable ? openActivity : undefined} action={editable && (
            <div className="flex gap-2">
              <button type="button" className="btn-primary" onClick={() => setLog({ type: 'VISIT', task: null })}>{t('activityType.VISIT')}</button>
              <button type="button" className="btn-secondary" onClick={() => setLog({ type: 'MEETING', task: null })}>{t('activityType.MEETING')}</button>
            </div>
          )} />
        )}

        {tab === 'answers' && (
          <AnswersTab b={b} editable={editable} onAddUsage={() => setUsageOpen(true)} onEditUsage={setUsage} onAddInterest={() => setInterestOpen(true)} onEdit={() => setEditOpen(true)} />
        )}

        {tab === 'history' && (
          <div className="space-y-4">
            <CommentBox businessId={b.id} />
            {timeline.isLoading ? <Loading /> : (timeline.data ?? []).length === 0 ? <div className="card"><EmptyState title={t('business.noHistory')} /></div> : (
              <div className="card p-4"><Timeline items={timeline.data ?? []} businessId={b.id} onEdit={editable ? openActivity : undefined} /></div>
            )}
          </div>
        )}

        {tab === 'purchases' && <PurchasesTab b={b} editable={editable} onAdd={() => setPurchaseOpen(true)} />}

        {tab === 'details' && <DetailsTab b={b} typeName={typeName} maps={maps} editable={editable} onEdit={() => setEditOpen(true)} />}
      </div>

      {log && (
        <LogActivityDialog open onClose={() => setLog(null)} business={b} initialType={log.type} completeTask={log.task}
          onSaved={(_, result) => { if (result === 'ORDERED') setPurchaseOpen(true) }} />
      )}
      {editing && <LogActivityDialog open onClose={() => setEditing(null)} business={b} activity={editing} />}
      <TaskDialog open={taskOpen !== null} onClose={() => setTaskOpen(null)} task={taskOpen === 'new' ? null : taskOpen} business={{ id: b.id, name: b.name }} />
      <PurchaseDialog open={purchaseOpen} onClose={() => setPurchaseOpen(false)} business={b} />
      <ContactDialog open={contact !== null} onClose={() => setContact(null)} businessId={b.id} contact={contact === 'new' ? null : contact} />
      <BusinessEditDialog open={editOpen} onClose={() => setEditOpen(false)} business={b} />
      <UsageDialog open={usageOpen} onClose={() => setUsageOpen(false)} businessId={b.id} />
      <UsageDialog open={usage !== null} onClose={() => setUsage(null)} businessId={b.id} usage={usage} />
      <InterestDialog open={interestOpen} onClose={() => setInterestOpen(false)} businessId={b.id} />
      <NoteDialog open={noteOpen} onClose={() => setNoteOpen(false)} business={{ id: b.id, name: b.name }} />
    </div>
  )

  /** The timeline only carries a summary, so the entry itself is fetched before it is opened. */
  async function openActivity(id: number) {
    try {
      setEditing(await api.get<ActivityDto>(`/businesses/${b.id}/activities/${id}`))
    } catch (error) {
      toast.error(error)
    }
  }

  function openFor(code: MissingCode) {
    if (code === 'PHONE' || code === 'CONTACT_PERSON' || code === 'DECISION_MAKER') setContact(code === 'DECISION_MAKER' && b.contacts[0] ? b.contacts[0] : 'new')
    else if (code === 'SYRUP_BRAND' || code === 'SYRUP_FLAVORS') setUsageOpen(true)
    else if (code === 'NEXT_STEP') setTaskOpen('new')
    else setEditOpen(true)
  }
}

// ----------------------------------------------------------------------------- building blocks

function Card({ title, action, children }: { title: ReactNode; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="card p-3">
      <div className="mb-2 flex items-center justify-between px-1">
        <h2 className="text-sm font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

function IconButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return <button type="button" onClick={onClick} className="grid size-7 place-items-center rounded-full text-brand-700 hover:bg-brand-50">{children}</button>
}

function ResultBadge({ result }: { result: string }) {
  const { t } = useI18n()
  return <span className={`rounded-md px-1.5 py-0.5 text-xs font-medium ${RESULT_TONE[result as keyof typeof RESULT_TONE] ?? 'bg-canvas text-ink'}`}>{t(`activityResult.${result}`)}</span>
}

function ContactList({ b, onEdit }: { b: BusinessDetail; onEdit?: (contact: ContactDto) => void }) {
  const { t } = useI18n()
  const phones = splitPhones(b.phone)
  return (
    <div className="space-y-2 px-1">
      {phones.length > 0 && <Phones text={b.phone} />}
      {b.contacts.length === 0 && <p className="text-sm text-muted">{t('business.none')}</p>}
      {b.contacts.map((c) => (
        <div key={c.id} className="flex items-start gap-2 rounded-xl p-1.5 hover:bg-canvas">
          <div className="grid size-8 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700">{c.name.slice(0, 1)}</div>
          <div className="min-w-0 flex-1">
            <button type="button" className="w-full text-left" onClick={() => onEdit?.(c)} disabled={!onEdit}>
              <div className="flex items-center gap-1 text-sm font-medium">
                {c.name} {c.decisionMaker && <Star className="size-3.5 fill-amber-400 text-amber-400" />}
              </div>
              <div className="text-xs text-muted">{[c.roleTitle, c.preferredChannel !== 'ANY' ? t(`channel.${c.preferredChannel}`) : null].filter(Boolean).join(' · ')}</div>
              {c.notes && <div className="text-xs text-muted">{c.notes}</div>}
            </button>
            {c.phone && <Phones text={c.phone} className="mt-1" />}
          </div>
          {onEdit && (
            <button type="button" className="btn-ghost shrink-0 p-1.5" title={t('common.edit')} onClick={() => onEdit(c)}>
              <Pencil className="size-3.5" />
            </button>
          )}
        </div>
      ))}
    </div>
  )
}

function UsageSummary({ b }: { b: BusinessDetail }) {
  const { t, name } = useI18n()
  const lookups = useLookups()
  if (b.usages.length === 0 && b.categoryUsages.length === 0) return <p className="px-1 text-sm text-muted">{t('business.none')}</p>
  const byCategory = new Map<number, typeof b.usages>()
  for (const usage of b.usages) byCategory.set(usage.categoryId, [...(byCategory.get(usage.categoryId) ?? []), usage])
  for (const answer of b.categoryUsages) if (!byCategory.has(answer.categoryId)) byCategory.set(answer.categoryId, [])
  return (
    <div className="space-y-2 px-1">
      {[...byCategory.entries()].map(([categoryId, rows]) => {
        const category = lookups.data?.categories.find((c) => c.id === categoryId)
        const answer = b.categoryUsages.find((a) => a.categoryId === categoryId)?.answer
        const brands = [...new Set(rows.map((r) => r.brandName ?? t('business.noBrand')))]
        return (
          <div key={categoryId} className="text-sm">
            <div className="text-xs font-semibold uppercase tracking-wide text-muted">
              {name(category)} {answer && answer !== 'YES' && `· ${t(`usage.${answer}`)}`}
            </div>
            {brands.map((brand) => (
              <div key={brand}>
                <b className={rows.find((r) => (r.brandName ?? t('business.noBrand')) === brand)?.ownBrand ? 'text-brand-700' : ''}>{brand}</b>
                <span className="text-muted">: {rows.filter((r) => (r.brandName ?? t('business.noBrand')) === brand).map((r) => r.flavor ? name(r.flavor) : r.productName).filter(Boolean).join(', ') || '-'}</span>
              </div>
            ))}
          </div>
        )
      })}
    </div>
  )
}

function PurchaseFacts({ b }: { b: BusinessDetail }) {
  const { t, lang } = useI18n()
  const p = b.purchases
  if (p.count === 0) return <p className="px-1 text-sm text-muted">{t('business.noPurchases')}</p>
  return (
    <dl className="space-y-1 px-1 text-sm">
      <div className="flex justify-between"><dt className="text-muted">{t('business.ordersCount', { n: p.count })}</dt><dd className="font-semibold">{money(p.total)}</dd></div>
      <div className="flex justify-between"><dt className="text-muted">{t('business.lastContact')}</dt><dd>{fmtDate(p.lastDate, lang)}</dd></div>
      <div className="flex justify-between"><dt className="text-muted">{t('business.nextReorder')}</dt><dd>{fmtDate(p.nextReorderDate, lang)}</dd></div>
      <div className="text-xs text-muted">{t('business.sinceLast', { n: p.daysSinceLast ?? 0 })} · {t('business.every', { n: p.expectedDays })}</div>
    </dl>
  )
}

function CommentBox({ businessId }: { businessId: number }) {
  const { t } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const [body, setBody] = useState('')
  const [saving, setSaving] = useState(false)
  const save = async () => {
    setSaving(true)
    try {
      await api.post(`/businesses/${businessId}/comments`, { body })
      setBody('')
      refresh(businessId)
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }
  return (
    <div className="card flex items-end gap-2 p-3">
      <textarea rows={2} className="input flex-1" placeholder={t('business.commentPlaceholder')} value={body} onChange={(e) => setBody(e.target.value)} />
      <button type="button" className="btn-primary" disabled={saving || !body.trim()} onClick={() => void save()}>{t('common.add')}</button>
    </div>
  )
}

function ActivityList({ items, loading, empty, action, businessId, onEdit }: {
  items: TimelineItem[]; loading: boolean; empty: string; action?: ReactNode; businessId: number
  onEdit?: (id: number) => void
}) {
  return (
    <div className="space-y-3">
      {action && <div className="flex justify-end">{action}</div>}
      {loading ? <Loading /> : items.length === 0 ? <div className="card"><EmptyState title={empty} /></div> : (
        <div className="card p-4"><Timeline items={items} businessId={businessId} onEdit={onEdit} /></div>
      )}
    </div>
  )
}

function Timeline({ items, businessId, onEdit }: { items: TimelineItem[]; businessId: number; onEdit?: (id: number) => void }) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()

  const remove = async (item: TimelineItem) => {
    if (!window.confirm(t('activity.deleteConfirm'))) return
    try {
      await api.del(`/businesses/${businessId}/activities/${item.refId}`)
      toast.ok(t('activity.deleted'))
      refresh(businessId)
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <ol className="relative space-y-4 border-l border-line pl-5">
      {items.map((item) => (
        <li key={`${item.kind}-${item.refId}`} className="relative">
          <span className={`absolute -left-[1.6rem] top-1 size-3 rounded-full border-2 border-surface ${dotFor(item)}`} />
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
            <span className="font-semibold">{headline(item, t)}</span>
            {item.kind === 'ACTIVITY' && item.result && <ResultBadge result={item.result} />}
            {item.kind === 'PURCHASE' && <span className="font-semibold text-emerald-700">{money(item.amount)}</span>}
            {item.imported && <span className="rounded bg-lime-brand/40 px-1.5 text-[11px] font-medium text-emerald-900">{t('timeline.imported')}</span>}
            {onEdit && item.kind === 'ACTIVITY' && (
              <span className="ml-auto flex shrink-0 gap-1">
                <button type="button" className="btn-ghost p-1" title={t('activity.edit')} onClick={() => onEdit(item.refId)}><Pencil className="size-3.5" /></button>
                <button type="button" className="btn-ghost p-1 text-rose-700" title={t('activity.delete')} onClick={() => void remove(item)}><X className="size-3.5" /></button>
              </span>
            )}
          </div>
          {item.results && item.results.length > 1 && (
            <div className="mt-1 flex flex-wrap gap-1">
              {item.results.slice(1).map((r) => <ResultBadge key={r} result={r} />)}
            </div>
          )}
          {item.resultNote && <p className="mt-1 text-sm italic">{item.resultNote}</p>}
          <div className="text-xs text-muted">
            {fmtDateTime(item.at, lang)}{item.user && ` · ${item.user.fullName}`}{item.contact && ` · ${item.contact.name}`}
          </div>
          {item.title && item.kind !== 'ACTIVITY' && <p className="mt-1 text-sm">{item.title}</p>}
          {item.notes && <p className="mt-1 whitespace-pre-line text-sm">{item.notes}</p>}
          {item.comments.length > 0 && (
            <div className="mt-2 space-y-1.5 border-l-2 border-brand-100 pl-3">
              {item.comments.map((c) => (
                <div key={c.id} className="text-sm"><b>{c.author.fullName}:</b> {c.body} <span className="text-xs text-muted">{fmtDateTime(c.createdAt, lang)}</span></div>
              ))}
            </div>
          )}
        </li>
      ))}
    </ol>
  )
}

function headline(item: TimelineItem, t: (key: string, vars?: Record<string, string | number>) => string): string {
  switch (item.kind) {
    case 'ACTIVITY': return t(`activityType.${item.type}`)
    case 'STATUS': return `${t(`status.${item.fromStatus}`)} → ${t(`status.${item.toStatus}`)}`
    case 'CREATED': return `${t('timeline.CREATED')} · ${t(`status.${item.toStatus}`)}`
    case 'TASK_DONE':
    case 'TASK_CANCELLED': return `${t(`timeline.${item.kind}`)}: ${t(`taskType.${item.type}`)}`
    default: return t(`timeline.${item.kind}`)
  }
}

function dotFor(item: TimelineItem): string {
  if (item.kind === 'PURCHASE') return 'bg-emerald-500'
  if (item.kind === 'COMMENT') return 'bg-sky-400'
  if (item.kind === 'STATUS' || item.kind === 'CREATED') return 'bg-violet-400'
  if (item.result === 'NOT_INTERESTED') return 'bg-rose-500'
  if (item.result === 'NO_ANSWER') return 'bg-slate-400'
  return 'bg-brand-500'
}

// ----------------------------------------------------------------------------- tabs

function AnswersTab({ b, editable, onAddUsage, onEditUsage, onAddInterest, onEdit }: {
  b: BusinessDetail; editable: boolean; onAddUsage: () => void; onEditUsage: (usage: UsageDto) => void
  onAddInterest: () => void; onEdit: () => void
}) {
  const { t, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const lookups = useLookups()

  const liked = b.interests.filter((i) => i.feedback === 'LIKED')
  const disliked = b.interests.filter((i) => i.feedback === 'DISLIKED' || i.status === 'NOT_INTERESTED')

  const updateInterest = async (interest: InterestDto, patch: { status?: InterestStatus; feedback?: TastingFeedback }) => {
    try {
      await api.put(`/businesses/${b.id}/interests/${interest.id}`, {
        status: patch.status ?? interest.status, reason: interest.reason, feedback: patch.feedback ?? interest.feedback, notes: interest.notes,
      })
      refresh(b.id)
    } catch (error) {
      toast.error(error)
    }
  }
  const remove = async (path: string) => {
    try {
      await api.del(path)
      refresh(b.id)
    } catch (error) {
      toast.error(error)
    }
  }
  const label = (i: InterestDto) => (i.flavor ? name(i.flavor) : i.product ? name(i.product) : '-')

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">{t('business.answersIntro')}</p>
      <div className="grid gap-4 md:grid-cols-2">
        <Card title={<span className="flex items-center gap-1.5 text-emerald-700"><ThumbsUp className="size-4" /> {t('feedback.LIKED')}</span>}>
          {liked.length === 0 ? <p className="px-1 text-sm text-muted">-</p> : (
            <div className="flex flex-wrap gap-1.5 px-1">{liked.map((i) => <span key={i.id} className="chip border-emerald-300 bg-emerald-50 text-emerald-800">{label(i)}</span>)}</div>
          )}
        </Card>
        <Card title={<span className="flex items-center gap-1.5 text-rose-700"><ThumbsDown className="size-4" /> {t('feedback.DISLIKED')}</span>}>
          {disliked.length === 0 ? <p className="px-1 text-sm text-muted">-</p> : (
            <div className="flex flex-wrap gap-1.5 px-1">{disliked.map((i) => <span key={i.id} className="chip border-rose-300 bg-rose-50 text-rose-800">{label(i)}</span>)}</div>
          )}
        </Card>
      </div>

      <Card title={t('business.whatTheyWant')} action={editable && <IconButton onClick={onAddInterest}><Plus className="size-4" /></IconButton>}>
        {b.interests.length === 0 ? <p className="px-1 text-sm text-muted">{t('business.none')}</p> : (
          <div className="divide-y divide-line">
            {b.interests.map((i) => (
              <div key={i.id} className="flex flex-wrap items-center gap-2 px-1 py-2">
                <span className="min-w-32 flex-1 text-sm font-medium">
                  {label(i)}
                  <span className="block text-xs font-normal text-muted">{t(`interestReason.${i.reason}`)}{i.notes ? ` · ${i.notes}` : ''}</span>
                </span>
                {editable ? (
                  <>
                    <select className="input w-auto max-w-full py-1 text-xs" value={i.status} onChange={(e) => void updateInterest(i, { status: e.target.value as InterestStatus })}>
                      {(['INTERESTED', 'VERY_INTERESTED', 'SAMPLE_REQUESTED', 'TESTING', 'NOT_INTERESTED', 'PURCHASED'] as InterestStatus[]).map((s) => <option key={s} value={s}>{t(`interestStatus.${s}`)}</option>)}
                    </select>
                    <div className="flex gap-1">
                      {(['LIKED', 'OK', 'DISLIKED'] as TastingFeedback[]).map((f) => (
                        <button key={f} type="button" onClick={() => void updateInterest(i, { feedback: i.feedback === f ? 'UNKNOWN' : f })}
                          className={`rounded-lg border px-2 py-1 text-xs ${i.feedback === f ? (f === 'LIKED' ? 'border-emerald-600 bg-emerald-600 text-white' : f === 'DISLIKED' ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-500 bg-slate-500 text-white') : 'border-line'}`}>
                          {t(`feedback.${f}`)}
                        </button>
                      ))}
                    </div>
                    <button type="button" className="btn-ghost p-1.5" onClick={() => void remove(`/businesses/${b.id}/interests/${i.id}`)} aria-label="remove"><X className="size-4" /></button>
                  </>
                ) : (
                  <span className="text-xs text-muted">{t(`interestStatus.${i.status}`)} · {t(`feedback.${i.feedback}`)}</span>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card title={t('business.whatTheyUse')} action={editable && <IconButton onClick={onAddUsage}><Plus className="size-4" /></IconButton>}>
        {b.usages.length === 0 ? <p className="px-1 text-sm text-muted">{t('business.none')}</p> : (
          <div className="divide-y divide-line">
            {b.usages.map((u) => (
              <div key={u.id} className="flex items-center gap-2 px-1 py-2 text-sm">
                <span className="w-28 shrink-0 text-xs text-muted">{name(lookups.data?.categories.find((c) => c.id === u.categoryId))}</span>
                <span className={`w-32 shrink-0 font-medium ${u.ownBrand ? 'text-brand-700' : ''}`}>{u.brandName ?? t('business.noBrand')}</span>
                <span className="min-w-0 flex-1 truncate">{u.flavor ? name(u.flavor) : u.productName ?? '-'}{u.quantity ? ` · ${u.quantity}` : ''}{u.frequency ? ` / ${u.frequency}` : ''}</span>
                {editable && (
                  <>
                    <button type="button" className="btn-ghost p-1.5" title={t('common.edit')} onClick={() => onEditUsage(u)}><Pencil className="size-4" /></button>
                    <button type="button" className="btn-ghost p-1.5" onClick={() => void remove(`/businesses/${b.id}/usages/${u.id}`)} aria-label="remove"><X className="size-4" /></button>
                  </>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card title={t('business.competitor')} action={editable && <IconButton onClick={onEdit}><Pencil className="size-4" /></IconButton>}>
        <dl className="grid gap-2 px-1 text-sm sm:grid-cols-2">
          {lookups.data?.categories.filter((c) => c.active).map((c) => (
            <Fact key={c.id} label={t('business.uses', { category: name(c).toLowerCase() })} value={t(`usage.${b.categoryUsages.find((a) => a.categoryId === c.id)?.answer ?? 'UNKNOWN'}`)} />
          ))}
          <Fact label={t('business.switchOpenness')} value={t(`openness.${b.switchOpenness}`)} />
          <Fact label={t('business.priceSensitivity')} value={t(`priceSensitivity.${b.priceSensitivity}`)} />
          <Fact label={t('business.satisfaction')} value={t(`satisfaction.${b.satisfaction}`)} />
          <Fact label={t('business.menuChange')} value={b.menuChange} />
          <Fact label={t('business.drinkTypes')} value={b.drinkTypes.map((d) => t(`drink.${d}`)).join(', ')} wide />
          <Fact label={t('business.competitorNotes')} value={b.competitorNotes} wide />
        </dl>
      </Card>
    </div>
  )
}

function PurchasesTab({ b, editable, onAdd }: { b: BusinessDetail; editable: boolean; onAdd: () => void }) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const { isSupervisor } = useAuth()
  const purchases = usePurchases(b.id)
  const totals = useMemo(() => (purchases.data ?? []).reduce((sum, p) => sum + p.items.reduce((q, i) => q + i.quantity, 0), 0), [purchases.data])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="card flex-1 p-3"><PurchaseFacts b={b} /></div>
        <div className="card p-3 text-sm"><div className="text-xs text-muted">{t('reports.bottlesSold')}</div><div className="text-2xl font-semibold">{number(totals)}</div></div>
        {editable && <button type="button" className="btn-primary" onClick={onAdd}><ShoppingCart className="size-4" /> {t('business.addPurchase')}</button>}
      </div>
      {purchases.isLoading ? <Loading /> : (purchases.data ?? []).length === 0 ? <div className="card"><EmptyState title={t('business.noPurchases')} /></div> : (
        (purchases.data ?? []).map((p) => (
          <div key={p.id} className="card p-4">
            <div className="flex items-center gap-2">
              <span className="font-semibold">{fmtDate(p.purchaseDate, lang)}</span>
              <span className="text-xs text-muted">{p.user.fullName}</span>
              <span className="ml-auto font-semibold text-emerald-700">{money(p.total)}</span>
              {isSupervisor && editable && (
                <button type="button" className="btn-ghost p-1.5" onClick={async () => {
                  if (!window.confirm(t('common.delete') + '?')) return
                  try { await api.del(`/businesses/${b.id}/purchases/${p.id}`); refresh(b.id) } catch (error) { toast.error(error) }
                }} aria-label="delete"><X className="size-4" /></button>
              )}
            </div>
            <table className="mt-2 w-full text-sm">
              <tbody>
                {p.items.map((i) => (
                  <tr key={i.id} className="border-t border-line/60">
                    <td className="py-1.5">{i.description}</td>
                    <td className="py-1.5 text-right tabular-nums">{number(i.quantity)} × {money(i.unitPrice)}</td>
                    <td className="w-24 py-1.5 text-right font-medium tabular-nums">{money(i.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {p.notes && <p className="mt-2 text-sm text-muted">{p.notes}</p>}
          </div>
        ))
      )}
    </div>
  )
}

function DetailsTab({ b, typeName, maps, editable, onEdit }: { b: BusinessDetail; typeName: string; maps: string | null; editable: boolean; onEdit: () => void }) {
  const { t, lang } = useI18n()
  const lookups = useLookups()
  // Active fields always show (a blank one is a question to ask); retired ones only where a value was kept.
  const fields = (lookups.data?.customFields ?? []).filter((f) => f.active || b.customValues[f.id])
  return (
    <div className="card p-4">
      <div className="mb-3 flex justify-end">{editable && <button type="button" className="btn-secondary" onClick={onEdit}><Pencil className="size-4" /> {t('business.editDetails')}</button>}</div>
      <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
        <Fact label={t('common.name')} value={b.name} />
        <Fact label={t('business.legalName')} value={b.legalName} />
        <Fact label={t('business.idCode')} value={b.idCode} />
        <Fact label={t('common.type')} value={typeName} />
        <Fact label={t('business.branches')} value={b.branches?.toString()} />
        <Fact label={t('common.priority')} value={t(`priority.${b.priority}`)} />
        <Fact label={t('common.phone')} value={b.phone} />
        <Fact label={t('common.email')} value={b.email && <a className="inline-flex items-center gap-1 text-brand-700" href={`mailto:${b.email}`}><Mail className="size-3.5" />{b.email}</a>} />
        <Fact label={t('business.website')} value={b.website && <a className="inline-flex items-center gap-1 break-all text-brand-700" href={b.website.startsWith('http') ? b.website : `https://${b.website}`} target="_blank" rel="noreferrer"><Globe className="size-3.5" />{b.website}</a>} />
        <Fact label={t('common.address')} value={b.address && (maps ? <a className="inline-flex items-center gap-1 text-brand-700" href={maps} target="_blank" rel="noreferrer"><MapPin className="size-3.5" />{b.address}</a> : b.address)} />
        <Fact label={t('common.district')} value={b.district} />
        <Fact label={t('common.city')} value={b.city} />
        <Fact label={t('business.visitHours')} value={b.visitHours && <span className="inline-flex items-center gap-1"><Clock className="size-3.5" />{b.visitHours}</span>} />
        <Fact label={t('common.assignedTo')} value={b.assignedTo?.fullName} />
        <Fact label={t('business.created')} value={`${fmtDate(b.createdAt, lang)} · ${b.createdBy?.fullName ?? ''}`} />
        <Fact label={t('business.project')} value={b.workbookName} />
        <Fact label={t('business.sheet')} value={b.sheetName} />
        <Fact label={t('common.notes')} value={b.notes} wide />
      </dl>
      {fields.length > 0 && (
        <>
          <h3 className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-muted">{t('business.customFields')}</h3>
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
            {fields.map((f) => <Fact key={f.id} label={f.label} value={b.customValues[f.id]} />)}
          </dl>
        </>
      )}
    </div>
  )
}

function Fact({ label, value, wide }: { label: string; value: ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'sm:col-span-2 lg:col-span-3' : ''}>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 whitespace-pre-line">{value || <span className="text-muted">-</span>}</dd>
    </div>
  )
}
