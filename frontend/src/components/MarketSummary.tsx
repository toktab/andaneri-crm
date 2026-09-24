import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { api } from '../lib/api'
import type { UsageSource } from '../lib/types'
import { useI18n } from '../i18n'
import { EmptyState, Loading, Modal, StatusBadge } from './ui'

interface BrandCount { brandId: number | null; name: string; own: boolean; businesses: number }
interface FlavorCount { flavorId: number; nameKa: string; nameEn: string; businesses: number }
interface CategoryFlavors { categoryId: number; nameKa: string; nameEn: string; flavors: FlavorCount[] }
interface Market { brands: BrandCount[]; categories: CategoryFlavors[] }
interface Who {
  businessId: number; name: string; status: string; assignedTo: { id: number; fullName: string } | null
  brand: string | null; flavor: string | null; source: UsageSource; quantity: string | null; frequency: string | null
}

/**
 * What the market pours, counted from what the team wrote down: the brands on the bars, and the flavors
 * of each category apart - syrups next to purees, never mixed. Every bar of every row opens onto the
 * places behind the number, because "eleven use vanilla" is only useful when you can see which eleven.
 */
export function MarketSummary() {
  const { t, lang } = useI18n()
  const [ownToo, setOwnToo] = useState(false)
  const [open, setOpen] = useState<{ title: string; params: Record<string, string | number> } | null>(null)

  const market = useQuery({ queryKey: ['market', ownToo], queryFn: () => api.get<Market>('/market', { ownToo }) })
  const who = useQuery({
    queryKey: ['market-who', open?.params],
    queryFn: () => api.get<Who[]>('/market/who', open!.params),
    enabled: Boolean(open),
  })

  if (market.isLoading) return <Loading />
  const data = market.data
  const name = (f: { nameKa: string; nameEn: string }) => (lang === 'ka' ? f.nameKa || f.nameEn : f.nameEn || f.nameKa)

  return (
    <div className="space-y-4">
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="size-4 accent-brand-600" checked={ownToo} onChange={(e) => setOwnToo(e.target.checked)} />
        {t('market.ownToo')}
      </label>

      <section className="card p-3">
        <h3 className="mb-2 px-1 text-sm font-semibold">{t('market.brands')}</h3>
        <Bars
          rows={(data?.brands ?? []).map((b) => ({
            key: b.name,
            label: b.own ? `${b.name} (${t('reports.ours')})` : b.name,
            value: b.businesses,
            highlight: b.own,
            onOpen: () => setOpen({ title: b.name, params: b.brandId ? { brandId: b.brandId } : {} }),
          }))}
          empty={t('business.none')}
        />
      </section>

      {(data?.categories ?? []).map((category) => (
        <section key={category.categoryId} className="card p-3">
          <h3 className="mb-2 px-1 text-sm font-semibold">{t('market.flavorsOf', { category: name(category) })}</h3>
          <Bars
            rows={category.flavors.map((f) => ({
              key: `${category.categoryId}-${f.flavorId}`,
              label: name(f),
              value: f.businesses,
              onOpen: () => setOpen({
                title: `${name(category)}: ${name(f)}`,
                params: { categoryId: category.categoryId, flavorId: f.flavorId },
              }),
            }))}
            empty={t('business.none')}
          />
        </section>
      ))}

      <Modal open={Boolean(open)} onClose={() => setOpen(null)} title={open?.title ?? ''} wide>
        {who.isLoading ? <Loading /> : (who.data ?? []).length === 0 ? <EmptyState title={t('business.none')} /> : (
          <ul className="divide-y divide-line">
            {(who.data ?? []).map((row) => (
              <li key={`${row.businessId}-${row.flavor}-${row.brand}`} className="flex flex-wrap items-center gap-2 py-2 text-sm">
                <Link to={`/businesses/${row.businessId}`} className="font-medium hover:text-brand-700" onClick={() => setOpen(null)}>
                  {row.name}
                </Link>
                <StatusBadge status={row.status as never} />
                {row.brand && <span className="text-xs text-muted">{row.brand}</span>}
                {row.source !== 'BRAND' && <span className="chip border-line text-xs">{t(`usageSource.${row.source}`)}</span>}
                {row.quantity && <span className="text-xs text-muted">{row.quantity}{row.frequency ? ` / ${row.frequency}` : ''}</span>}
                <span className="ml-auto text-xs text-muted">{row.assignedTo?.fullName ?? t('common.unassigned')}</span>
              </li>
            ))}
          </ul>
        )}
      </Modal>
    </div>
  )
}

function Bars({ rows, empty }: { rows: { key: string; label: string; value: number; highlight?: boolean; onOpen: () => void }[]; empty: string }) {
  if (rows.length === 0) return <p className="px-1 text-sm text-muted">{empty}</p>
  const max = Math.max(1, ...rows.map((r) => r.value))
  return (
    <ul className="space-y-1">
      {rows.map((row) => (
        <li key={row.key}>
          <button type="button" className="group flex w-full items-center gap-2 rounded-lg px-1 py-1 hover:bg-canvas" onClick={row.onOpen}>
            <span className={`w-40 shrink-0 truncate text-left text-sm ${row.highlight ? 'font-semibold text-brand-700' : ''}`}>{row.label}</span>
            <span className="h-2.5 flex-1 overflow-hidden rounded-full bg-canvas">
              <span className={`block h-full rounded-full ${row.highlight ? 'bg-brand-600' : 'bg-brand-300'}`} style={{ width: `${(row.value / max) * 100}%` }} />
            </span>
            <span className="w-8 text-right text-xs font-semibold tabular-nums">{row.value}</span>
            <ChevronRight className="size-4 shrink-0 text-muted opacity-0 group-hover:opacity-100" />
          </button>
        </li>
      ))}
    </ul>
  )
}
