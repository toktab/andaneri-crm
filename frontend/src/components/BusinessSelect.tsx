import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Store, X } from 'lucide-react'
import { api } from '../lib/api'
import type { BusinessSummary, PageDto } from '../lib/types'
import { useI18n } from '../i18n'
import { StatusBadge } from './ui'

export interface BusinessPick { id: number; name: string }

/** Type part of a name, phone or address and pick the business. */
export function BusinessSelect({ value, onChange, autoFocus }: {
  value: BusinessPick | null; onChange: (value: BusinessPick | null) => void; autoFocus?: boolean
}) {
  const { t } = useI18n()
  const [text, setText] = useState('')
  const [debounced, setDebounced] = useState('')
  const [open, setOpen] = useState(false)

  useEffect(() => {
    const handle = setTimeout(() => setDebounced(text.trim()), 200)
    return () => clearTimeout(handle)
  }, [text])

  const results = useQuery({
    queryKey: ['business-select', debounced],
    queryFn: () => api.get<PageDto<BusinessSummary>>('/businesses', { q: debounced, size: 8, sort: 'name' }),
    enabled: open && debounced.length > 0,
  })

  if (value) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2 text-sm">
        <Store className="size-4 text-brand-600" />
        <span className="flex-1 truncate font-medium">{value.name}</span>
        <button type="button" className="text-muted hover:text-ink" onClick={() => onChange(null)} aria-label="clear">
          <X className="size-4" />
        </button>
      </div>
    )
  }

  return (
    <div className="relative">
      <input
        className="input"
        autoFocus={autoFocus}
        value={text}
        placeholder={t('business.searchPlaceholder')}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onChange={(event) => { setText(event.target.value); setOpen(true) }}
      />
      {open && debounced && (
        <div className="absolute inset-x-0 top-full z-20 mt-1 max-h-64 overflow-y-auto rounded-xl border border-line bg-surface p-1 shadow-lg">
          {results.data?.items.length === 0 && <div className="px-3 py-2 text-sm text-muted">{t('common.noResults')}</div>}
          {results.data?.items.map((b) => (
            <button
              key={b.id}
              type="button"
              className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-brand-50"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => { onChange({ id: b.id, name: b.name }); setText(''); setOpen(false) }}
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium">{b.name}</span>
                <span className="block truncate text-xs text-muted">{[b.address, b.district, b.phone].filter(Boolean).join(' · ')}</span>
              </span>
              <StatusBadge status={b.status} />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
