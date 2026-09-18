import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { CircleCheck, Clock, ExternalLink, FlaskConical, MapPin, Star } from 'lucide-react'
import { api } from '../lib/api'
import { useBusiness, useLookups, useRefreshWork, useTimeline } from '../lib/queries'
import { fmtDateTime, fmtShortDate, fmtTime, mapsHref, money } from '../lib/format'
import { useMode } from '../lib/mode'
import { tap } from '../lib/mobile'
import type { TaskDto } from '../lib/types'
import { useI18n } from '../i18n'
import { Loading, Modal, StatusBadge } from './ui'
import { Phones } from './Phones'
import { TYPE_TONE } from './TaskRow'
import { useToast } from './Toast'

/**
 * Standing outside the door with a phone in one hand: everything needed to walk in and nothing else.
 * Where it is, who to ask for and on which number, what they pour now, what they asked about, what was
 * said last time, and what to hand over. Opened from the calendar while route mode is on.
 */
export function RouteBriefing({ task, onClose }: { task: TaskDto | null; onClose: () => void }) {
  const { t, lang, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const lookups = useLookups()
  const { canEdit } = useMode()
  const business = useBusiness(task?.businessId ?? null)
  const timeline = useTimeline(task?.businessId ?? null)

  const b = business.data
  const maps = b ? mapsHref(b) : task?.businessAddress ? mapsHref({ mapsUrl: task.businessMapsUrl, address: task.businessAddress, name: task.businessName ?? undefined }) : null
  const deliver = (task?.flavorIds ?? [])
    .map((id) => lookups.data?.flavors.find((f) => f.id === id))
    .filter((f) => f !== undefined)
    .map((f) => name(f))
  // What was said the last few times, shortest first: the things worth remembering at the door.
  const recent = (timeline.data ?? []).filter((item) => item.kind === 'ACTIVITY').slice(0, 3)

  const complete = async () => {
    if (!task) return
    try {
      await api.post(`/tasks/${task.id}/complete`, {})
      tap(18)
      toast.ok(t('common.saved'))
      refresh(task.businessId)
      onClose()
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <Modal
      open={Boolean(task)}
      onClose={onClose}
      title={task?.businessName ?? task?.title ?? ''}
      footer={task && (
        <>
          {task.businessId && (
            <Link to={`/flavors/${task.businessId}`} className="btn-secondary" onClick={onClose}>
              <FlaskConical className="size-4" /> {t('flavors.title')}
            </Link>
          )}
          {task.businessId && canEdit(b?.canEdit) && (
            <Link to={`/businesses/${task.businessId}?task=${task.id}`} className="btn-secondary" onClick={onClose}>
              <ExternalLink className="size-4" /> {t('business.logActivity')}
            </Link>
          )}
          {task.status === 'OPEN' && canEdit(b?.canEdit) && (
            <button type="button" className="btn-primary" onClick={() => void complete()}>
              <CircleCheck className="size-4" /> {t('tasks.complete')}
            </button>
          )}
        </>
      )}
    >
      {!task ? null : (
        <div className="space-y-4">
          {/* When, and what kind of stop this is */}
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xl font-semibold tabular-nums">{task.allDay ? '-' : fmtTime(task.dueAt)}</span>
            <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${TYPE_TONE[task.type]}`}>{t(`taskType.${task.type}`)}</span>
            {b && <StatusBadge status={b.status} />}
          </div>

          {/* Where */}
          <div className="space-y-2">
            {(task.businessAddress || b?.address) && (
              <p className="text-sm">{[b?.address ?? task.businessAddress, b?.district, task.location].filter(Boolean).join(', ')}</p>
            )}
            {maps && (
              <a href={maps} target="_blank" rel="noreferrer" className="btn-primary w-full py-3">
                <MapPin className="size-5" /> {t('business.openMaps')}
              </a>
            )}
            {b?.visitHours && (
              <p className="inline-flex items-center gap-1.5 rounded-lg bg-amber-50 px-2 py-1 text-sm text-amber-900">
                <Clock className="size-4" /> {b.visitHours}
              </p>
            )}
          </div>

          {business.isLoading ? <Loading /> : b && (
            <>
              {/* Who to ask for */}
              <Section title={t('business.contacts')}>
                {task.contact && !b.contacts.some((c) => c.id === task.contact?.id) && (
                  <p className="text-sm font-medium">{task.contact.name}</p>
                )}
                {b.contacts.length === 0 && !b.phone && <p className="text-sm text-muted">{t('business.none')}</p>}
                {b.phone && <Phones text={b.phone} className="mb-1" />}
                {b.contacts.map((c) => (
                  <div key={c.id} className="mb-1.5">
                    <div className="flex items-center gap-1 text-sm font-medium">
                      {c.decisionMaker && <Star className="size-3.5 fill-amber-400 text-amber-400" />}
                      {c.name}
                      {c.roleTitle && <span className="font-normal text-muted">· {c.roleTitle}</span>}
                    </div>
                    {c.phone && <Phones text={c.phone} />}
                  </div>
                ))}
              </Section>

              {deliver.length > 0 && (
                <Section title={t('activity.deliverFlavors')}>
                  <div className="flex flex-wrap gap-1.5">
                    {deliver.map((flavor) => <span key={flavor} className="chip border-teal-300 bg-teal-50 text-teal-800">{flavor}</span>)}
                  </div>
                </Section>
              )}

              {/* What they pour now, and what they asked about */}
              {b.usages.length > 0 && (
                <Section title={t('business.whatTheyUse')}>
                  {[...new Set(b.usages.map((u) => u.brandName ?? t('business.noBrand')))].map((brand) => (
                    <div key={brand} className="text-sm">
                      <b className={b.usages.find((u) => (u.brandName ?? t('business.noBrand')) === brand)?.ownBrand ? 'text-brand-700' : ''}>{brand}</b>
                      <span className="text-muted">
                        : {b.usages.filter((u) => (u.brandName ?? t('business.noBrand')) === brand).map((u) => (u.flavor ? name(u.flavor) : u.productName)).filter(Boolean).join(', ') || '-'}
                      </span>
                    </div>
                  ))}
                </Section>
              )}

              {b.interests.length > 0 && (
                <Section title={t('business.whatTheyWant')}>
                  <div className="flex flex-wrap gap-1.5">
                    {b.interests.map((i) => (
                      <span key={i.id} className="chip border-emerald-300 bg-emerald-50 text-emerald-800">
                        {i.flavor ? name(i.flavor) : i.product ? name(i.product) : '-'}
                      </span>
                    ))}
                  </div>
                </Section>
              )}

              {/* What was said last time */}
              {recent.length > 0 && (
                <Section title={t('business.lastTime')}>
                  {recent.map((item) => (
                    <div key={`${item.kind}-${item.refId}`} className="mb-2 text-sm">
                      <div className="flex flex-wrap items-center gap-x-2 text-xs text-muted">
                        <b className="text-ink">{t(`activityType.${item.type}`)}</b>
                        {item.results.map((r) => <span key={r}>{t(`activityResult.${r}`)}</span>)}
                        <span>{fmtDateTime(item.at, lang)}</span>
                      </div>
                      {item.resultNote && <p className="italic">{item.resultNote}</p>}
                      {item.notes && <p className="whitespace-pre-line">{item.notes}</p>}
                    </div>
                  ))}
                </Section>
              )}

              {b.purchases.count > 0 && (
                <Section title={t('business.purchases')}>
                  <p className="text-sm">
                    {t('business.ordersCount', { n: b.purchases.count })} · {money(b.purchases.total)}
                    {b.purchases.lastDate && ` · ${t('business.lastContact')}: ${fmtShortDate(b.purchases.lastDate, lang)}`}
                  </p>
                </Section>
              )}
            </>
          )}

          {(task.title || task.notes) && (
            <Section title={t('activity.nextTitle')}>
              {task.title && <p className="text-sm font-medium">{task.title}</p>}
              {task.notes && <p className="whitespace-pre-line text-sm">{task.notes}</p>}
            </Section>
          )}
        </div>
      )}
    </Modal>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line p-3">
      <h3 className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted">{title}</h3>
      {children}
    </section>
  )
}
