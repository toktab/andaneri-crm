import { useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { BrandDto, FlavorDto, InterestReason, InterestStatus, TastingFeedback } from '../lib/types'
import { keys, useLookups, useProducts, useRefreshWork } from '../lib/queries'
import { useI18n } from '../i18n'
import { ChipPicker } from './ChipPicker'
import { Choice, Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

function useCatalogPickers() {
  const { name } = useI18n()
  const lookups = useLookups()
  const queryClient = useQueryClient()
  const brandOptions = useMemo(() => (lookups.data?.brands ?? []).filter((b) => b.active).map((b) => ({ id: b.id, label: b.name })), [lookups.data])
  const flavorOptions = useMemo(() => (lookups.data?.flavors ?? []).filter((f) => f.active).map((f) => ({ id: f.id, label: name(f), hint: f.nameEn })), [lookups.data, name])
  const createBrand = async (text: string) => {
    const brand = await api.post<BrandDto>('/brands', { name: text })
    queryClient.invalidateQueries({ queryKey: keys.lookups })
    return { id: brand.id, label: brand.name }
  }
  const createFlavor = async (text: string) => {
    const flavor = await api.post<FlavorDto>('/flavors', { nameKa: text, nameEn: '' })
    queryClient.invalidateQueries({ queryKey: keys.lookups })
    return { id: flavor.id, label: name(flavor) }
  }
  return { lookups, brandOptions, flavorOptions, createBrand, createFlavor }
}

/** "They use Monin: vanilla, caramel, about 6 bottles a month." */
export function UsageDialog({ open, onClose, businessId }: { open: boolean; onClose: () => void; businessId: number }) {
  const { t, name } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const { lookups, brandOptions, flavorOptions, createBrand, createFlavor } = useCatalogPickers()
  const [categoryId, setCategoryId] = useState('')
  const [brand, setBrand] = useState<number[]>([])
  const [flavors, setFlavors] = useState<number[]>([])
  const [quantity, setQuantity] = useState('')
  const [frequency, setFrequency] = useState('')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setCategoryId(String(lookups.data?.categories.find((c) => c.nameEn === 'Syrup')?.id ?? ''))
    setBrand([]); setFlavors([]); setQuantity(''); setFrequency(''); setNotes('')
  }, [open, lookups.data])

  const save = async () => {
    setSaving(true)
    try {
      await api.post(`/businesses/${businessId}/usages`, {
        categoryId: Number(categoryId), brandId: brand[0] ?? null, flavorIds: flavors, quantity, frequency, notes,
      })
      toast.ok(t('common.saved'))
      refresh(businessId)
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('business.whatTheyUse')}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || !categoryId || (!brand.length && !flavors.length)} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label={t('products.category')}>
          <select className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            {lookups.data?.categories.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </select>
        </Field>
        <div>
          <div className="label">{t('business.brand')}</div>
          <ChipPicker single options={brandOptions} selected={brand} onChange={setBrand} onCreate={createBrand} placeholder="Monin, 1883, Boiron..." initialCount={12} />
        </div>
        <div>
          <div className="label">{t('business.flavors')}</div>
          <ChipPicker options={flavorOptions} selected={flavors} onChange={setFlavors} onCreate={createFlavor} placeholder={t('common.search')} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('common.quantity')}><input className="input" placeholder="6 ბოთლი" value={quantity} onChange={(e) => setQuantity(e.target.value)} /></Field>
          <Field label={t('business.frequency')}><input className="input" placeholder="თვეში" value={frequency} onChange={(e) => setFrequency(e.target.value)} /></Field>
        </div>
        <Field label={t('common.notes')}><textarea rows={2} className="input" value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
      </div>
    </Modal>
  )
}

/** Flavors or our products they would like, how keen they are, and what they said after tasting. */
export function InterestDialog({ open, onClose, businessId }: { open: boolean; onClose: () => void; businessId: number }) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const products = useProducts()
  const { flavorOptions, createFlavor } = useCatalogPickers()
  const [flavors, setFlavors] = useState<number[]>([])
  const [productIds, setProductIds] = useState<number[]>([])
  const [status, setStatus] = useState<InterestStatus>('INTERESTED')
  const [reason, setReason] = useState<InterestReason>('GENERAL')
  const [feedback, setFeedback] = useState<TastingFeedback>('UNKNOWN')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setFlavors([]); setProductIds([]); setStatus('INTERESTED'); setReason('GENERAL'); setFeedback('UNKNOWN'); setNotes('')
    }
  }, [open])

  const productOptions = useMemo(
    () => (products.data ?? []).filter((p) => p.own && p.status !== 'INACTIVE').map((p) => ({ id: p.id, label: lang === 'ka' ? p.nameKa : p.nameEn, hint: p.nameEn })),
    [products.data, lang],
  )

  const save = async () => {
    setSaving(true)
    try {
      await api.post(`/businesses/${businessId}/interests`, { flavorIds: flavors, productIds, status, reason, feedback: feedback === 'UNKNOWN' ? null : feedback, notes })
      toast.ok(t('common.saved'))
      refresh(businessId)
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('business.whatTheyWant')}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || (!flavors.length && !productIds.length)} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <div>
          <div className="label">{t('business.flavors')}</div>
          <ChipPicker options={flavorOptions} selected={flavors} onChange={setFlavors} onCreate={createFlavor} placeholder={t('common.search')} tone="green" />
        </div>
        <div>
          <div className="label">{t('products.ours')}</div>
          <ChipPicker options={productOptions} selected={productIds} onChange={setProductIds} placeholder={t('common.search')} initialCount={0} tone="green" />
        </div>
        <Field label={t('activity.wantStatus')}>
          <Choice size="sm" options={(['INTERESTED', 'VERY_INTERESTED', 'SAMPLE_REQUESTED', 'TESTING', 'NOT_INTERESTED'] as InterestStatus[]).map((value) => ({ value, label: t(`interestStatus.${value}`) }))} value={status} onChange={setStatus} />
        </Field>
        <Field label={t('business.feedback')}>
          <Choice
            size="sm"
            options={(['UNKNOWN', 'LIKED', 'OK', 'DISLIKED'] as TastingFeedback[]).map((value) => ({ value, label: value === 'UNKNOWN' ? '-' : t(`feedback.${value}`) }))}
            value={feedback}
            onChange={setFeedback}
          />
        </Field>
        <Field label={t('business.reason')}>
          <select className="input" value={reason} onChange={(e) => setReason(e.target.value as InterestReason)}>
            {(['GENERAL', 'REPLACE_COMPETITOR', 'PUREE_GAP', 'NEW_MENU'] as InterestReason[]).map((r) => <option key={r} value={r}>{t(`interestReason.${r}`)}</option>)}
          </select>
        </Field>
        <Field label={t('common.notes')}><textarea rows={2} className="input" value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
      </div>
    </Modal>
  )
}
