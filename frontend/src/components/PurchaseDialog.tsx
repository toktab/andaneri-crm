import { useEffect, useMemo, useState } from 'react'
import { Plus, X } from 'lucide-react'
import { api } from '../lib/api'
import type { ProductDto } from '../lib/types'
import { useProducts, useRefreshWork } from '../lib/queries'
import { money, todayIso } from '../lib/format'
import { useI18n } from '../i18n'
import { Field, Modal, Spinner } from './ui'
import { useToast } from './Toast'

interface Line { productId: string; description: string; quantity: string; unitPrice: string }

const emptyLine = (): Line => ({ productId: '', description: '', quantity: '1', unitPrice: '' })

/** An order: lines from our price list (price filled in, still editable) or anything else typed by hand. */
export function PurchaseDialog({ open, onClose, business }: { open: boolean; onClose: () => void; business: { id: number; name: string } }) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const products = useProducts()
  const [date, setDate] = useState(todayIso())
  const [lines, setLines] = useState<Line[]>([emptyLine()])
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setDate(todayIso())
      setLines([emptyLine()])
      setNotes('')
    }
  }, [open])

  const ours = useMemo(() => (products.data ?? []).filter((p) => p.own && p.status !== 'INACTIVE'), [products.data])
  const sections = useMemo(() => {
    const grouped = new Map<string, ProductDto[]>()
    for (const p of ours) {
      const key = (lang === 'ka' ? p.sectionKa : p.sectionEn) ?? '-'
      grouped.set(key, [...(grouped.get(key) ?? []), p])
    }
    return [...grouped.entries()]
  }, [ours, lang])

  const update = (index: number, patch: Partial<Line>) => setLines((current) => current.map((line, i) => (i === index ? { ...line, ...patch } : line)))
  const total = lines.reduce((sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0), 0)
  const valid = lines.some((line) => (line.productId || line.description.trim()) && Number(line.quantity) > 0)

  const save = async () => {
    setSaving(true)
    try {
      await api.post(`/businesses/${business.id}/purchases`, {
        purchaseDate: date,
        notes,
        items: lines
          .filter((line) => (line.productId || line.description.trim()) && Number(line.quantity) > 0)
          .map((line) => ({
            productId: line.productId ? Number(line.productId) : null,
            description: line.description || null,
            quantity: Number(line.quantity),
            unitPrice: Number(line.unitPrice) || 0,
          })),
      })
      toast.ok(t('common.saved'))
      refresh(business.id)
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
      wide
      title={`${t('business.addPurchase')}: ${business.name}`}
      footer={
        <>
          <span className="mr-auto text-lg font-semibold tabular-nums">{money(total)}</span>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || !valid} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label={t('common.date')} className="max-w-48">
          <input type="date" className="input" value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        {lines.map((line, index) => (
          <div key={index} className="grid grid-cols-12 items-end gap-2 rounded-xl bg-canvas p-2.5">
            <Field label={t('reports.col.product')} className="col-span-12 sm:col-span-6">
              <select
                className="input"
                value={line.productId}
                onChange={(e) => {
                  const product = ours.find((p) => String(p.id) === e.target.value)
                  update(index, { productId: e.target.value, unitPrice: product?.price != null ? String(product.price) : line.unitPrice })
                }}
              >
                <option value="">-</option>
                {sections.map(([section, items]) => (
                  <optgroup key={section} label={section}>
                    {items.map((p) => (
                      <option key={p.id} value={p.id}>
                        {lang === 'ka' ? p.nameKa : p.nameEn} · {money(p.price)}{p.status === 'COMING_SOON' ? ` (${t('products.comingSoon')})` : ''}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
              {!line.productId && (
                <input className="input mt-1.5" placeholder={t('reports.col.product')} value={line.description} onChange={(e) => update(index, { description: e.target.value })} />
              )}
            </Field>
            <Field label={t('common.quantity')} className="col-span-4 sm:col-span-2">
              <input type="number" min="0" step="1" inputMode="numeric" className="input" value={line.quantity} onChange={(e) => update(index, { quantity: e.target.value })} />
            </Field>
            <Field label={t('common.price')} className="col-span-5 sm:col-span-3">
              <input type="number" min="0" step="0.01" inputMode="decimal" className="input" value={line.unitPrice} onChange={(e) => update(index, { unitPrice: e.target.value })} />
            </Field>
            <div className="col-span-3 flex justify-end sm:col-span-1">
              <button type="button" className="btn-ghost p-2" disabled={lines.length === 1} onClick={() => setLines((c) => c.filter((_, i) => i !== index))} aria-label="remove">
                <X className="size-4" />
              </button>
            </div>
          </div>
        ))}
        <button type="button" className="btn-secondary" onClick={() => setLines((c) => [...c, emptyLine()])}>
          <Plus className="size-4" /> {t('common.add')}
        </button>
        <Field label={t('common.notes')}>
          <textarea rows={2} className="input" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </div>
    </Modal>
  )
}
