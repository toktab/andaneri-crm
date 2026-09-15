import { useState } from 'react'
import { Link } from 'react-router-dom'
import { GripVertical, Phone } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { useBusinesses, useRefreshWork } from '../lib/queries'
import { fmtShortDate } from '../lib/format'
import type { BusinessStatus, BusinessSummary } from '../lib/types'
import { STATUSES } from '../lib/types'
import { useI18n } from '../i18n'
import { Choice, PageHeader, PriorityMark, STATUS_STYLE, Spinner } from '../components/ui'
import { useToast } from '../components/Toast'

/** Drag a card to another column to move it along; on a phone, pick the stage from the card's menu. */
export function PipelinePage() {
  const { t } = useI18n()
  const { user } = useAuth()
  const [scope, setScope] = useState<'mine' | 'all'>('all')
  const [dragging, setDragging] = useState<BusinessSummary | null>(null)
  const toast = useToast()
  const refresh = useRefreshWork()
  const { canEdit } = useMode()
  const editable = canEdit()

  const move = async (business: BusinessSummary, status: BusinessStatus) => {
    if (business.status === status) return
    try {
      await api.post(`/businesses/${business.id}/status`, { status })
      refresh(business.id)
    } catch (error) {
      toast.error(error)
    }
  }

  return (
    <div>
      <PageHeader
        title={t('pipeline.title')}
        subtitle={editable ? t('pipeline.hint') : undefined}
        actions={<Choice size="sm" options={[{ value: 'all', label: t('common.all') }, { value: 'mine', label: t('common.me') }]} value={scope} onChange={setScope} />}
      />
      <div className="-mx-4 overflow-x-auto px-4 pb-4 sm:-mx-6 sm:px-6">
        <div className="flex min-w-max gap-3">
          {STATUSES.map((status) => (
            <Column
              key={status}
              status={status}
              assignedToId={scope === 'mine' ? user?.id ?? null : null}
              editable={editable}
              dragging={dragging}
              onDragStart={setDragging}
              onDrop={(business) => { setDragging(null); void move(business, status) }}
              onMove={move}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function Column({ status, assignedToId, editable, dragging, onDragStart, onDrop, onMove }: {
  status: BusinessStatus
  assignedToId: number | null
  editable: boolean
  dragging: BusinessSummary | null
  onDragStart: (b: BusinessSummary | null) => void
  onDrop: (b: BusinessSummary) => void
  onMove: (b: BusinessSummary, status: BusinessStatus) => void
}) {
  const { t, lang } = useI18n()
  const [over, setOver] = useState(false)
  const list = useBusinesses({ status, assignedToId, size: 100, sort: 'updated' })

  return (
    <div
      className={`flex w-72 shrink-0 flex-col rounded-2xl border bg-surface/60 ${over ? 'border-brand-400 bg-brand-50' : 'border-line'}`}
      onDragOver={(e) => { if (editable && dragging) { e.preventDefault(); setOver(true) } }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => { e.preventDefault(); setOver(false); if (dragging) onDrop(dragging) }}
    >
      <div className="flex items-center gap-2 border-b border-line px-3 py-2.5">
        <span className={`size-2.5 rounded-full ${STATUS_STYLE[status].dot}`} />
        <span className="text-sm font-semibold">{t(`status.${status}`)}</span>
        <span className="ml-auto rounded-full bg-canvas px-2 text-xs text-muted">{list.data?.total ?? ''}</span>
      </div>
      <div className="max-h-[calc(100dvh-14rem)] min-h-24 space-y-2 overflow-y-auto p-2">
        {list.isLoading && <div className="grid place-items-center py-6"><Spinner className="text-muted" /></div>}
        {list.data?.items.length === 0 && <p className="py-6 text-center text-xs text-muted">{t('pipeline.empty')}</p>}
        {list.data?.items.map((b) => (
          <div
            key={b.id}
            draggable={editable}
            onDragStart={() => onDragStart(b)}
            onDragEnd={() => onDragStart(null)}
            className="group rounded-xl border border-line bg-surface p-2.5 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
          >
            <div className="flex items-start gap-1.5">
              {editable && <GripVertical className="mt-0.5 hidden size-4 shrink-0 cursor-grab text-muted/50 md:block" />}
              <Link to={`/businesses/${b.id}`} className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5 text-sm font-medium"><PriorityMark priority={b.priority} /><span className="truncate">{b.name}</span></div>
                <div className="truncate text-xs text-muted">{[b.district, b.brands.join(', ')].filter(Boolean).join(' · ')}</div>
                <div className="mt-1 text-[11px] text-muted">
                  {b.nextTask ? `${t(`taskType.${b.nextTask.type}`)} · ${fmtShortDate(b.nextTask.dueAt, lang)}` : b.lastContactAt ? `${t('business.lastContact')}: ${fmtShortDate(b.lastContactAt, lang)}` : ''}
                </div>
              </Link>
              {b.phone && <Link to={`/calls/${b.id}`} className="grid size-7 shrink-0 place-items-center rounded-full text-brand-700 hover:bg-brand-50"><Phone className="size-3.5" /></Link>}
            </div>
            {editable && (
              <select
                className="mt-2 w-full rounded-lg border border-line bg-canvas px-2 py-1 text-xs md:hidden"
                value={b.status}
                onChange={(e) => onMove(b, e.target.value as BusinessStatus)}
              >
                {STATUSES.map((s) => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
              </select>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
