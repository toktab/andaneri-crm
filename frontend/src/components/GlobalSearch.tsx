import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Phone, Search } from 'lucide-react'
import { api } from '../lib/api'
import type { BusinessSummary, PageDto } from '../lib/types'
import { useI18n } from '../i18n'
import { Modal, Spinner, StatusBadge } from './ui'

/** Ctrl + K from anywhere: find a business by name, phone, address, ID code or contact, and open it. */
export function GlobalSearch({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useI18n()
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [debounced, setDebounced] = useState('')
  const [active, setActive] = useState(0)

  useEffect(() => {
    const handle = setTimeout(() => setDebounced(text.trim()), 180)
    return () => clearTimeout(handle)
  }, [text])

  useEffect(() => {
    if (open) {
      setText('')
      setActive(0)
    }
  }, [open])

  const results = useQuery({
    queryKey: ['global-search', debounced],
    queryFn: () => api.get<PageDto<BusinessSummary>>('/businesses', { q: debounced, size: 10, sort: 'name' }),
    enabled: open && debounced.length > 0,
  })
  const items = results.data?.items ?? []

  const go = (path: string) => {
    onClose()
    navigate(path)
  }

  return (
    <Modal open={open} onClose={onClose} title={t('common.search')}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
        <input
          autoFocus
          className="input pl-9 text-base"
          placeholder={t('business.searchPlaceholder')}
          value={text}
          onChange={(event) => { setText(event.target.value); setActive(0) }}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') { event.preventDefault(); setActive((i) => Math.min(i + 1, items.length - 1)) }
            if (event.key === 'ArrowUp') { event.preventDefault(); setActive((i) => Math.max(i - 1, 0)) }
            if (event.key === 'Enter' && items[active]) go(`/businesses/${items[active].id}`)
          }}
        />
        {results.isFetching && <Spinner className="absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted" />}
      </div>
      <div className="mt-3 space-y-1">
        {debounced && !results.isFetching && items.length === 0 && <p className="px-2 py-4 text-center text-sm text-muted">{t('common.noResults')}</p>}
        {items.map((b, index) => (
          <div
            key={b.id}
            className={`flex items-center gap-2 rounded-xl px-3 py-2.5 ${index === active ? 'bg-brand-50' : 'hover:bg-canvas'}`}
            onMouseEnter={() => setActive(index)}
          >
            <button type="button" className="min-w-0 flex-1 text-left" onClick={() => go(`/businesses/${b.id}`)}>
              <span className="block truncate text-sm font-medium">{b.name}</span>
              <span className="block truncate text-xs text-muted">{[b.address, b.district, b.phone].filter(Boolean).join(' · ')}</span>
            </button>
            <StatusBadge status={b.status} />
            <button type="button" className="btn-ghost p-2" title={t('callMode.title')} onClick={() => go(`/calls/${b.id}`)}>
              <Phone className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </Modal>
  )
}
