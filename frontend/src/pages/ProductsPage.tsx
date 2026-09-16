import { useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { keys, useLookups, useProducts } from '../lib/queries'
import { money } from '../lib/format'
import type { BrandDto, DrinkType, FlavorDto, ProductDto, ProductStatus } from '../lib/types'
import { DRINK_TYPES } from '../lib/types'
import { useI18n } from '../i18n'
import { ChipPicker } from '../components/ChipPicker'
import { EmptyState, Field, Loading, Modal, PageHeader, Spinner, Tabs } from '../components/ui'
import { useToast } from '../components/Toast'

type Tab = 'ours' | 'brands' | 'flavors'

export function ProductsPage() {
  const { t, lang, name } = useI18n()
  const { isAdmin } = useAuth()
  const { canEdit } = useMode()
  const products = useProducts()
  const lookups = useLookups()
  const [tab, setTab] = useState<Tab>('ours')
  const [filter, setFilter] = useState('')
  const [editing, setEditing] = useState<ProductDto | 'new' | null>(null)
  const admin = isAdmin && canEdit()

  const flavorName = useMemo(() => {
    const map = new Map((lookups.data?.flavors ?? []).map((f) => [f.id, name(f)]))
    return (id: number) => map.get(id) ?? ''
  }, [lookups.data, name])

  const sections = useMemo(() => {
    const q = filter.trim().toLowerCase()
    const grouped = new Map<string, ProductDto[]>()
    for (const p of products.data ?? []) {
      if (!p.own) continue
      const haystack = `${p.nameKa} ${p.nameEn} ${p.flavorIds.map(flavorName).join(' ')}`.toLowerCase()
      if (q && !haystack.includes(q)) continue
      const key = (lang === 'ka' ? p.sectionKa : p.sectionEn) ?? '-'
      grouped.set(key, [...(grouped.get(key) ?? []), p])
    }
    return [...grouped.entries()]
  }, [products.data, filter, lang, flavorName])

  return (
    <div>
      <PageHeader
        title={t('products.title')}
        actions={admin && tab === 'ours' && <button type="button" className="btn-primary" onClick={() => setEditing('new')}><Plus className="size-4" /> {t('products.addProduct')}</button>}
      />
      <Tabs<Tab> value={tab} onChange={setTab} tabs={[
        { key: 'ours', label: t('products.ours') },
        { key: 'brands', label: t('products.competitors'), count: lookups.data?.brands.length },
        { key: 'flavors', label: t('products.flavors'), count: lookups.data?.flavors.length },
      ]} />

      <div className="mt-4">
        {tab === 'ours' && (
          <>
            <input className="input mb-3 max-w-sm" placeholder={t('products.filter')} value={filter} onChange={(e) => setFilter(e.target.value)} />
            {products.isLoading ? <Loading /> : sections.length === 0 ? <div className="card"><EmptyState title={t('common.noResults')} /></div> : (
              <div className="grid gap-4 lg:grid-cols-2">
                {sections.map(([section, items]) => (
                  <section key={section} className="card min-w-0 p-3">
                    <h2 className="mb-2 px-1 text-sm font-semibold text-raspberry">{section}</h2>
                    <ul className="divide-y divide-line">
                      {items.map((p) => (
                        <li key={p.id} className={`flex items-center gap-2 px-1 py-2 ${p.status === 'INACTIVE' ? 'opacity-50' : ''}`}>
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-1.5 text-sm font-medium">
                              {lang === 'ka' ? p.nameKa : p.nameEn}
                              {p.status === 'COMING_SOON' && <span className="rounded bg-raspberry px-1.5 text-[10px] font-bold uppercase text-white">{t('products.comingSoon')}</span>}
                              {p.status === 'INACTIVE' && <span className="rounded bg-stone-200 px-1.5 text-[10px]">{t('productStatus.INACTIVE')}</span>}
                            </div>
                            <div className="truncate text-xs text-muted">
                              {[p.juicePercent ? t('products.juice', { n: p.juicePercent }) : null, lang === 'ka' ? p.nameEn : p.nameKa].filter(Boolean).join(' · ')}
                            </div>
                          </div>
                          <span className="font-semibold tabular-nums">{money(p.price)}</span>
                          {admin && <button type="button" className="btn-ghost p-1.5" onClick={() => setEditing(p)} aria-label={t('common.edit')}><Pencil className="size-3.5" /></button>}
                        </li>
                      ))}
                    </ul>
                  </section>
                ))}
              </div>
            )}
          </>
        )}
        {tab === 'brands' && <BrandsTab brands={lookups.data?.brands ?? []} admin={admin} canAdd={canEdit()} />}
        {tab === 'flavors' && <FlavorsTab flavors={lookups.data?.flavors ?? []} admin={admin} canAdd={canEdit()} />}
      </div>

      {editing && <ProductDialog product={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function BrandsTab({ brands, admin, canAdd }: { brands: BrandDto[]; admin: boolean; canAdd: boolean }) {
  const { t } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      queryClient.invalidateQueries({ queryKey: keys.lookups })
    } catch (error) {
      toast.error(error)
    }
  }
  return (
    <div className="card max-w-2xl p-3">
      {canAdd && (
        <form className="mb-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (text.trim()) void run(() => api.post('/brands', { name: text })).then(() => setText('')) }}>
          <input className="input" placeholder={t('products.addBrand')} value={text} onChange={(e) => setText(e.target.value)} />
          <button type="submit" className="btn-primary" disabled={!text.trim()}><Plus className="size-4" /></button>
        </form>
      )}
      <ul className="divide-y divide-line">
        {brands.map((brand) => (
          <li key={brand.id} className={`flex items-center gap-2 px-1 py-2 text-sm ${brand.active ? '' : 'opacity-50'}`}>
            {admin ? (
              <input className="input flex-1 py-1" defaultValue={brand.name} onBlur={(e) => { if (e.target.value.trim() && e.target.value !== brand.name) void run(() => api.put(`/admin/brands/${brand.id}`, { name: e.target.value, own: brand.own, active: brand.active })) }} />
            ) : <span className="flex-1 font-medium">{brand.name}</span>}
            {brand.own && <span className="rounded bg-brand-100 px-1.5 text-xs text-brand-700">{t('products.ownBrand')}</span>}
            {admin && (
              <label className="flex items-center gap-1 text-xs text-muted">
                <input type="checkbox" className="accent-brand-600" checked={brand.active} onChange={(e) => void run(() => api.put(`/admin/brands/${brand.id}`, { name: brand.name, own: brand.own, active: e.target.checked }))} />
                {t('common.active')}
              </label>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

function FlavorsTab({ flavors, admin, canAdd }: { flavors: FlavorDto[]; admin: boolean; canAdd: boolean }) {
  const { t, name } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [ka, setKa] = useState('')
  const [en, setEn] = useState('')
  const sorted = [...flavors].sort((a, b) => name(a).localeCompare(name(b)))
  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      queryClient.invalidateQueries({ queryKey: keys.lookups })
    } catch (error) {
      toast.error(error)
    }
  }
  return (
    <div className="card max-w-3xl p-3">
      {canAdd && (
        <form className="mb-3 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); void run(() => api.post('/flavors', { nameKa: ka, nameEn: en })).then(() => { setKa(''); setEn('') }) }}>
          <input className="input flex-1" placeholder={t('products.nameKa')} value={ka} onChange={(e) => setKa(e.target.value)} />
          <input className="input flex-1" placeholder={t('products.nameEn')} value={en} onChange={(e) => setEn(e.target.value)} />
          <button type="submit" className="btn-primary" disabled={!ka.trim() && !en.trim()}><Plus className="size-4" /> {t('products.addFlavor')}</button>
        </form>
      )}
      <ul className="grid gap-x-4 sm:grid-cols-2">
        {sorted.map((f) => (
          <li key={f.id} className={`flex items-center gap-2 border-b border-line py-1.5 text-sm ${f.active ? '' : 'opacity-50'}`}>
            {admin ? (
              <>
                <input className="input py-1" defaultValue={f.nameKa} onBlur={(e) => { if (e.target.value !== f.nameKa) void run(() => api.put(`/admin/flavors/${f.id}`, { nameKa: e.target.value, nameEn: f.nameEn, active: f.active })) }} />
                <input className="input py-1" defaultValue={f.nameEn} onBlur={(e) => { if (e.target.value !== f.nameEn) void run(() => api.put(`/admin/flavors/${f.id}`, { nameKa: f.nameKa, nameEn: e.target.value, active: f.active })) }} />
              </>
            ) : (
              <span className="flex-1">{f.nameKa} <span className="text-muted">· {f.nameEn}</span></span>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

function ProductDialog({ product, onClose }: { product: ProductDto | null; onClose: () => void }) {
  const { t, name } = useI18n()
  const toast = useToast()
  const queryClient = useQueryClient()
  const lookups = useLookups()
  const products = useProducts()
  const [form, setForm] = useState({
    brandId: '', categoryId: '', sectionKa: '', sectionEn: '', nameKa: '', nameEn: '', packSize: '', unit: '', price: '', juicePercent: '',
    status: 'ACTIVE' as ProductStatus, description: '', sortOrder: '', flavorIds: [] as number[], applications: [] as DrinkType[],
  })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const own = lookups.data?.brands.find((b) => b.own)
    const syrup = lookups.data?.categories.find((c) => c.nameEn === 'Syrup')
    setForm({
      brandId: String(product?.brandId ?? own?.id ?? ''), categoryId: String(product?.categoryId ?? syrup?.id ?? ''),
      sectionKa: product?.sectionKa ?? '', sectionEn: product?.sectionEn ?? '', nameKa: product?.nameKa ?? '', nameEn: product?.nameEn ?? '',
      packSize: product?.packSize ?? '', unit: product?.unit ?? '', price: product?.price?.toString() ?? '', juicePercent: product?.juicePercent?.toString() ?? '',
      status: product?.status ?? 'ACTIVE', description: product?.description ?? '', sortOrder: product?.sortOrder?.toString() ?? '',
      flavorIds: product?.flavorIds ?? [], applications: product?.applications ?? [],
    })
  }, [product, lookups.data])

  const sections = useMemo(() => {
    const seen = new Map<string, string>()
    for (const p of products.data ?? []) if (p.sectionKa) seen.set(p.sectionKa, p.sectionEn ?? '')
    return [...seen.entries()]
  }, [products.data])

  const save = async () => {
    setSaving(true)
    try {
      const body = {
        ...form,
        brandId: Number(form.brandId), categoryId: Number(form.categoryId),
        price: form.price ? Number(form.price) : null, juicePercent: form.juicePercent ? Number(form.juicePercent) : null,
        sortOrder: form.sortOrder ? Number(form.sortOrder) : null,
      }
      if (product) await api.put(`/admin/products/${product.id}`, body)
      else await api.post('/admin/products', body)
      queryClient.invalidateQueries({ queryKey: keys.products })
      toast.ok(t('common.saved'))
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  const input = (key: keyof typeof form) => ({ value: form[key] as string, onChange: (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value })) })

  return (
    <Modal
      open
      wide
      onClose={onClose}
      title={product ? t('products.editProduct') : t('products.addProduct')}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || !form.nameKa.trim() || !form.nameEn.trim()} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label={t('products.nameKa')}><input className="input" {...input('nameKa')} /></Field>
        <Field label={t('products.nameEn')}><input className="input" {...input('nameEn')} /></Field>
        <Field label={t('business.brand')}>
          <select className="input" {...input('brandId')}>{lookups.data?.brands.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</select>
        </Field>
        <Field label={t('products.category')}>
          <select className="input" {...input('categoryId')}>{lookups.data?.categories.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}</select>
        </Field>
        <Field label={`${t('products.section')} (ქა)`}>
          <input className="input" list="sections-ka" {...input('sectionKa')} onChange={(e) => {
            const match = sections.find(([ka]) => ka === e.target.value)
            setForm((f) => ({ ...f, sectionKa: e.target.value, sectionEn: match ? match[1] : f.sectionEn }))
          }} />
          <datalist id="sections-ka">{sections.map(([ka]) => <option key={ka} value={ka} />)}</datalist>
        </Field>
        <Field label={`${t('products.section')} (EN)`}><input className="input" {...input('sectionEn')} /></Field>
        <div className="grid grid-cols-3 gap-2 sm:col-span-2">
          <Field label={`${t('common.price')} ₾`}><input type="number" step="0.01" className="input" {...input('price')} /></Field>
          <Field label="%"><input type="number" min="0" max="100" className="input" {...input('juicePercent')} /></Field>
          <Field label={t('common.status')}>
            <select className="input" {...input('status')}>{(['ACTIVE', 'COMING_SOON', 'INACTIVE'] as ProductStatus[]).map((s) => <option key={s} value={s}>{t(`productStatus.${s}`)}</option>)}</select>
          </Field>
        </div>
        <div className="sm:col-span-2">
          <div className="label">{t('products.flavors')}</div>
          <ChipPicker options={(lookups.data?.flavors ?? []).map((f) => ({ id: f.id, label: name(f), hint: f.nameEn }))} selected={form.flavorIds} onChange={(ids) => setForm((f) => ({ ...f, flavorIds: ids }))} placeholder={t('common.search')} initialCount={0} />
        </div>
        <div className="sm:col-span-2">
          <div className="label">{t('products.applications')}</div>
          <div className="flex flex-wrap gap-1.5">
            {DRINK_TYPES.map((d) => {
              const on = form.applications.includes(d)
              return <button key={d} type="button" className={on ? 'chip-on' : 'chip-off'} onClick={() => setForm((f) => ({ ...f, applications: on ? f.applications.filter((x) => x !== d) : [...f.applications, d] }))}>{t(`drink.${d}`)}</button>
            })}
          </div>
        </div>
        <Field label={t('common.notes')} className="sm:col-span-2"><textarea rows={2} className="input" {...input('description')} /></Field>
      </div>
    </Modal>
  )
}
