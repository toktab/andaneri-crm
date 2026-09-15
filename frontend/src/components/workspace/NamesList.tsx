import { useEffect, useRef } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { CalendarClock, ChevronRight, Phone } from 'lucide-react'
import { fmtDate, fmtDateTime } from '../../lib/format'
import type { GridRow } from '../../lib/types'
import { useI18n } from '../../i18n'
import { EmptyState, Loading, PriorityMark, Spinner, StatusBadge } from '../ui'

export interface RowListProps {
  rows: GridRow[]
  total: number
  loading: boolean
  hasMore: boolean
  loadingMore: boolean
  onLoadMore: () => void
  onOpen: (id: number) => void
}

const ROW_HEIGHT = 64

/**
 * Just the names, with what matters at a glance: stage, phone, the next planned step and when we last
 * spoke. Only the rows on screen are rendered, so a sheet of thousands scrolls as smoothly as one of ten.
 */
export function NamesList({ rows, total, loading, hasMore, loadingMore, onLoadMore, onOpen }: RowListProps) {
  const { t, lang } = useI18n()
  const scrollRef = useRef<HTMLDivElement>(null)
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  })
  const items = virtualizer.getVirtualItems()
  const lastIndex = items.length ? items[items.length - 1].index : 0

  // Fetch the next 200 a little before the end is reached.
  useEffect(() => {
    if (hasMore && !loadingMore && lastIndex >= rows.length - 30) onLoadMore()
  }, [lastIndex, rows.length, hasMore, loadingMore, onLoadMore])

  if (loading) return <div className="card"><Loading /></div>
  if (total === 0) return <div className="card"><EmptyState title={t('workspace.empty')} /></div>

  const now = Date.now()
  return (
    <div className="card overflow-hidden">
      <div ref={scrollRef} className="scroll-area h-[calc(100dvh-13rem)] min-h-80">
        <div className="relative w-full" style={{ height: virtualizer.getTotalSize() }}>
          {items.map((item) => {
            const row = rows[item.index]
            const overdue = row.nextTask && new Date(row.nextTask.dueAt).getTime() < now
            return (
              <button
                key={row.id}
                type="button"
                onClick={() => onOpen(row.id)}
                className="group absolute inset-x-0 flex items-center gap-3 border-b border-line/70 px-3 text-left hover:bg-brand-50/60 sm:px-4"
                style={{ height: ROW_HEIGHT, transform: `translateY(${item.start}px)` }}
              >
                <div className="min-w-0 flex-1">
                  <div className="flex min-w-0 items-center gap-2">
                    <PriorityMark priority={row.priority} />
                    <span className="truncate font-medium">{row.name}</span>
                    {/* On a phone the badge would squeeze the name to a letter, so there it moves to the second line. */}
                    <span className="hidden sm:contents"><StatusBadge status={row.status} /></span>
                  </div>
                  <div className="mt-0.5 flex min-w-0 items-center gap-x-3 gap-y-0 truncate text-xs text-muted">
                    <span className="contents sm:hidden"><StatusBadge status={row.status} /></span>
                    {row.phone && <span className="inline-flex shrink-0 items-center gap-1"><Phone className="size-3" /> {row.phone}</span>}
                    {row.nextTask ? (
                      <span className={`inline-flex shrink-0 items-center gap-1 ${overdue ? 'font-medium text-rose-600' : 'text-ink/70'}`}>
                        <CalendarClock className="size-3" /> {t(`taskType.${row.nextTask.type}`)} {fmtDateTime(row.nextTask.dueAt, lang)}
                      </span>
                    ) : (
                      <span className="shrink-0 text-rose-500/80">{t('business.noNextStep')}</span>
                    )}
                    <span className="hidden truncate md:inline">
                      {row.lastContactAt ? `${t('business.lastContact')}: ${fmtDate(row.lastContactAt, lang)}` : t('callMode.neverContacted')}
                    </span>
                    {row.brands.length > 0 && <span className="hidden truncate lg:inline">{row.brands.join(', ')}</span>}
                  </div>
                </div>
                <span className="grid size-8 shrink-0 place-items-center rounded-full text-muted group-hover:bg-brand-600 group-hover:text-white" title={t('workspace.openDetails')}>
                  <ChevronRight className="size-4" />
                </span>
              </button>
            )
          })}
        </div>
        {loadingMore && <div className="flex justify-center py-3 text-xs text-muted"><Spinner className="size-4" /></div>}
      </div>
    </div>
  )
}
