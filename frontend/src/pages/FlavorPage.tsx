import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Check, ClipboardCopy, FlaskConical, Truck } from 'lucide-react'
import { api } from '../lib/api'
import { useBusiness, useLookups, useRefreshWork, useTimeline } from '../lib/queries'
import { atDaysFromNow, fmtShortDate } from '../lib/format'
import { useMode } from '../lib/mode'
import { tap } from '../lib/mobile'
import type { BusinessDetail, FlavorDto, TaskDto, UsageDto } from '../lib/types'
import { useI18n } from '../i18n'
import { BusinessSelect } from '../components/BusinessSelect'
import { EmptyState, ErrorBlock, Loading, PageHeader, StatusBadge } from '../components/ui'
import { TaskDialog } from '../components/TaskDialog'
import { useToast } from '../components/Toast'

/**
 * Flavor mode: one place, everything that is about taste, on one screen - what they pour from a bought
 * bottle, what they squeeze fresh, what they cook themselves, what they asked about, what we already
 * left with them - and from all of that, the list of 100 ml testers to pack for the next visit.
 */
export function FlavorPage() {
  const { t } = useI18n()
  const params = useParams()
  const navigate = useNavigate()
  const id = params.id ? Number(params.id) : null
  const business = useBusiness(id)

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        title={<span className="flex items-center gap-2"><FlaskConical className="size-5 text-brand-600" /> {t('flavors.title')}</span>}
        subtitle={t('flavors.subtitle')}
      />

      <div className="card mb-4 p-3">
        <div className="label px-1">{t('flavors.pick')}</div>
        <BusinessSelect
          value={business.data ? { id: business.data.id, name: business.data.name } : null}
          onChange={(pick) => navigate(pick ? `/flavors/${pick.id}` : '/flavors')}
        />
      </div>

      {!id ? (
        <div className="card"><EmptyState icon={<FlaskConical className="size-8" />} title={t('flavors.pickHint')} /></div>
      ) : business.isLoading ? <Loading />
        : business.error || !business.data ? <ErrorBlock error={business.error} onRetry={() => business.refetch()} />
          : <FlavorBoard b={business.data} />}
    </div>
  )
}

/** Everything about one place's flavors, and what to put in the bag. */
function FlavorBoard({ b }: { b: BusinessDetail }) {
  const { t, lang, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const lookups = useLookups()
  const { canEdit } = useMode()
  const timeline = useTimeline(b.id)
  const [picked, setPicked] = useState<number[] | null>(null)
  const [planning, setPlanning] = useState<TaskDto | 'new' | null>(null)
  const editable = canEdit(b.canEdit)

  const syrupCategory = lookups.data?.categories.find((c) => c.nameEn === 'Syrup')
  const bought = b.usages.filter((u) => u.source === 'BRAND')
  const fresh = b.usages.filter((u) => u.source === 'FRESH')
  const houseMade = b.usages.filter((u) => u.source === 'HOUSE_MADE')
  const syrups = bought.filter((u) => u.categoryId === syrupCategory?.id)
  const otherBought = bought.filter((u) => u.categoryId !== syrupCategory?.id)

  // What they asked about, and what already went to them, so nothing is packed twice by mistake.
  const wanted = b.interests.filter((i) => i.status !== 'NOT_INTERESTED' && i.status !== 'PURCHASED')
  const delivered = useMemo(() => {
    const ids = new Set<number>()
    for (const task of b.openTasks) task.flavorIds.forEach((f) => ids.add(f))
    return ids
  }, [b.openTasks])
  const samples = (timeline.data ?? []).filter((item) => item.kind === 'ACTIVITY'
    && (item.type === 'SAMPLES' || (item.results ?? []).some((r) => r.startsWith('SAMPLES'))))

  // The bag: what they asked for and has not been planned yet, until it is changed by hand.
  const suggested = wanted.map((i) => i.flavor?.id).filter((x) => x !== undefined).filter((x) => !delivered.has(x))
  const bag = picked ?? suggested
  const toggle = (flavorId: number) => {
    tap()
    setPicked(bag.includes(flavorId) ? bag.filter((x) => x !== flavorId) : [...bag, flavorId])
  }
  const flavorName = (id: number) => name(lookups.data?.flavors.find((f) => f.id === id))

  const copyBag = async () => {
    const text = `${b.name} · ${t('flavors.testers')}\n` + bag.map((id) => `- ${flavorName(id)} 100ml`).join('\n')
    try {
      await navigator.clipboard.writeText(text)
      toast.ok(t('common.copied'))
    } catch {
      window.prompt(t('common.copyManually'), text)
    }
  }

  const planDelivery = async () => {
    try {
      const task = await api.post<TaskDto>('/tasks', {
        businessId: b.id,
        type: 'DELIVERY',
        title: t('flavors.testers'),
        dueAt: atDaysFromNow(1, 12),
        flavorIds: bag,
      })
      toast.ok(t('flavors.planned'))
      refresh(b.id)
      setPlanning(task)
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <div className="space-y-4">
      <div className="card flex flex-wrap items-center gap-2 p-3">
        <Link to={`/businesses/${b.id}`} className="min-w-0 flex-1 truncate text-base font-semibold hover:text-brand-700">{b.name}</Link>
        <StatusBadge status={b.status} />
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card title={t('flavors.boughtSyrups')} tone="text-brand-700">
          <UsageList rows={syrups} empty={t('business.none')} />
        </Card>
        <Card title={t('usageSource.FRESH')} tone="text-emerald-700">
          <UsageList rows={fresh} empty={t('flavors.noneFresh')} />
        </Card>
        <Card title={t('usageSource.HOUSE_MADE')} tone="text-amber-700">
          <UsageList rows={houseMade} empty={t('flavors.noneHouse')} />
        </Card>
        <Card title={t('flavors.otherBought')} tone="text-muted">
          <UsageList rows={otherBought} empty={t('business.none')} />
        </Card>
      </div>

      <Card title={t('business.whatTheyWant')}>
        {wanted.length === 0 ? <p className="px-1 text-sm text-muted">{t('business.none')}</p> : (
          <ul className="space-y-1.5 px-1">
            {wanted.map((i) => (
              <li key={i.id} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-medium">{i.flavor ? name(i.flavor) : i.product ? name(i.product) : '-'}</span>
                <span className="text-xs text-muted">{t(`interestStatus.${i.status}`)}</span>
                {i.feedback !== 'UNKNOWN' && <span className="chip border-line text-xs">{t(`feedback.${i.feedback}`)}</span>}
                {i.notes && <span className="text-xs text-muted">· {i.notes}</span>}
              </li>
            ))}
          </ul>
        )}
      </Card>

      {samples.length > 0 && (
        <Card title={t('flavors.alreadyLeft')}>
          <ul className="space-y-1 px-1 text-sm">
            {samples.map((s) => (
              <li key={s.refId} className="flex flex-wrap items-center gap-2">
                <span className="text-xs text-muted">{fmtShortDate(s.at, lang)}</span>
                <span>{(s.results ?? []).map((r) => t(`activityResult.${r}`)).join(', ') || t(`activityType.${s.type}`)}</span>
                {s.resultNote && <span className="text-xs italic text-muted">{s.resultNote}</span>}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {/* The bag itself: tick what goes in, then plan the delivery that carries it. */}
      <section className="card border-brand-200 p-3">
        <h2 className="mb-1 flex items-center gap-2 px-1 text-sm font-semibold text-brand-700">
          <FlaskConical className="size-4" /> {t('flavors.testers')} <span className="text-xs font-normal text-muted">{t('flavors.testersHint')}</span>
        </h2>
        <div className="flex flex-wrap gap-1.5 px-1 py-2">
          {(lookups.data?.flavors ?? []).filter((f) => f.active).map((f: FlavorDto) => {
            const on = bag.includes(f.id)
            const asked = suggested.includes(f.id)
            return (
              <button
                key={f.id}
                type="button"
                onClick={() => toggle(f.id)}
                className={`chip ${on ? 'border-brand-600 bg-brand-600 text-white' : asked ? 'border-brand-300 bg-brand-50 text-brand-800' : 'border-line bg-surface text-muted hover:text-ink'}`}
              >
                {on && <Check className="size-3" />} {name(f)}
              </button>
            )
          })}
        </div>
        <div className="flex flex-wrap items-center gap-2 px-1 pt-2">
          <span className="text-sm text-muted">{t('flavors.inBag', { n: bag.length })}</span>
          <button type="button" className="btn-secondary ml-auto" disabled={bag.length === 0} onClick={() => void copyBag()}>
            <ClipboardCopy className="size-4" /> {t('flavors.copyList')}
          </button>
          {editable && (
            <button type="button" className="btn-primary" disabled={bag.length === 0} onClick={() => void planDelivery()}>
              <Truck className="size-4" /> {t('flavors.planDelivery')}
            </button>
          )}
        </div>
      </section>

      <TaskDialog open={planning !== null} task={planning === 'new' ? null : planning} onClose={() => setPlanning(null)} business={{ id: b.id, name: b.name }} />
    </div>
  )
}

function Card({ title, tone = '', children }: { title: string; tone?: string; children: React.ReactNode }) {
  return (
    <section className="card p-3">
      <h2 className={`mb-1.5 px-1 text-sm font-semibold ${tone}`}>{title}</h2>
      {children}
    </section>
  )
}

function UsageList({ rows, empty }: { rows: UsageDto[]; empty: string }) {
  const { name } = useI18n()
  if (rows.length === 0) return <p className="px-1 text-sm text-muted">{empty}</p>
  const brands = [...new Set(rows.map((r) => r.brandName ?? ''))]
  return (
    <div className="space-y-1 px-1 text-sm">
      {brands.map((brand) => (
        <div key={brand}>
          {brand && <b className={rows.find((r) => r.brandName === brand)?.ownBrand ? 'text-brand-700' : ''}>{brand}: </b>}
          <span>
            {rows.filter((r) => (r.brandName ?? '') === brand)
              .map((r) => [r.flavor ? name(r.flavor) : r.productName, r.quantity].filter(Boolean).join(' '))
              .filter(Boolean)
              .join(', ') || '-'}
          </span>
        </div>
      ))}
    </div>
  )
}
