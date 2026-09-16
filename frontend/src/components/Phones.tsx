import { useEffect, useState } from 'react'
import { Copy, Phone } from 'lucide-react'
import { splitPhones } from '../lib/format'
import { useI18n } from '../i18n'
import { useToast } from './Toast'

/**
 * A phone or tablet, where a number can actually be dialled by tapping it. On a laptop the call button
 * would do nothing, so the number itself is shown instead, with a button that copies it.
 */
export function useCanCall(): boolean {
  const [can, setCan] = useState(() => typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches)
  useEffect(() => {
    const media = window.matchMedia('(pointer: coarse)')
    const update = () => setCan(media.matches)
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  return can
}

/** Every number in a field, each written the same way (5XX XX XX XX), to copy or - on a phone - to call. */
export function Phones({ text, size = 'sm', className = '' }: { text: string | null | undefined; size?: 'sm' | 'lg'; className?: string }) {
  const { t } = useI18n()
  const toast = useToast()
  const canCall = useCanCall()
  const numbers = splitPhones(text)
  if (numbers.length === 0) return null

  const copy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      toast.ok(t('common.copied'))
    } catch {
      window.prompt(t('common.copyManually'), value)
    }
  }

  return (
    <div className={`flex flex-wrap items-center gap-1.5 ${className}`}>
      {numbers.map((number) => (
        <span key={number.href} className={`inline-flex items-center gap-1 rounded-lg border border-line bg-surface ${size === 'lg' ? 'px-2.5 py-1.5' : 'px-2 py-0.5'}`}>
          {canCall ? (
            <a href={number.href} className={`font-medium tabular-nums text-brand-700 ${size === 'lg' ? 'text-xl' : ''}`}>{number.display}</a>
          ) : (
            <span className={`font-medium tabular-nums ${size === 'lg' ? 'text-xl' : ''}`}>{number.display}</span>
          )}
          <button type="button" onClick={() => void copy(number.display)} title={t('common.copy')}
            className="grid size-6 shrink-0 place-items-center rounded-md text-muted hover:bg-canvas hover:text-ink">
            <Copy className="size-3.5" />
          </button>
          {canCall && (
            <a href={number.href} title={t('common.call')} className="grid size-6 shrink-0 place-items-center rounded-md text-brand-700 hover:bg-brand-50">
              <Phone className="size-3.5" />
            </a>
          )}
        </span>
      ))}
    </div>
  )
}
