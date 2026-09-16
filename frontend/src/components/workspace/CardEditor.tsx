import { useEffect, useRef, useState } from 'react'
import { useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { Check, ChevronRight, Pencil, X } from 'lucide-react'
import { api } from '../../lib/api'
import { useMode } from '../../lib/mode'
import { keys } from '../../lib/queries'
import { fmtDateTime } from '../../lib/format'
import { tap } from '../../lib/mobile'
import type { GridRow, PageDto } from '../../lib/types'
import { useI18n } from '../../i18n'
import { EmptyState, Loading, Modal, Spinner, StatusBadge } from '../ui'
import { Phones } from '../Phones'
import { useToast } from '../Toast'
import { payload, useColumns, type Col } from './ExcelGrid'
import type { RowListProps } from './NamesList'

/** What a card shows without being opened, in this order. */
const FACE = ['status', 'phone', 'address', 'assignedToId', 'nextTask']

/**
 * The spreadsheet on a phone. A grid of thirty columns cannot be worked with a thumb, so each business
 * becomes a card: what matters on its face, and one press opens every column of that row as a list where
 * each line can be changed on the spot. The same columns, the same save as Excel mode - just reachable.
 */
export function CardEditor({ rows, total, loading, hasMore, loadingMore, onLoadMore, onOpen }: RowListProps) {
  const { t } = useI18n()
  const columns = useColumns()
  const [fields, setFields] = useState<GridRow | null>(null)
  const sentinel = useRef<HTMLDivElement>(null)

  // The next page loads by itself as the end of the list comes into view.
  useEffect(() => {
    const mark = sentinel.current
    if (!mark || !hasMore) return
    const watcher = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !loadingMore) onLoadMore()
    }, { rootMargin: '400px' })
    watcher.observe(mark)
    return () => watcher.disconnect()
  }, [hasMore, loadingMore, onLoadMore])

  // The row in the sheet follows the list: a save updates it underneath.
  const open = fields ? rows.find((r) => r.id === fields.id) ?? fields : null

  if (loading) return <div className="card"><Loading /></div>
  if (total === 0) return <div className="card"><EmptyState title={t('workspace.empty')} /></div>

  const face = FACE.map((key) => columns.find((c) => c.key === key)).filter((c) => c !== undefined)

  return (
    <div className="space-y-2">
      {rows.map((row) => (
        <section key={row.id} className="card p-3">
          <div className="flex items-start gap-2">
            <button type="button" className="min-w-0 flex-1 text-left" onClick={() => onOpen(row.id)}>
              <span className="block truncate font-semibold">{row.name}</span>
              <span className="mt-1 flex flex-wrap items-center gap-1.5">
                <StatusBadge status={row.status} />
                {row.nextTask && (
                  <span className="text-xs text-muted">
                    {t(`taskType.${row.nextTask.type}`)} · {fmtDateTime(row.nextTask.dueAt, 'ka')}
                  </span>
                )}
              </span>
            </button>
            <button
              type="button"
              className="btn-secondary shrink-0 px-3 py-2"
              onClick={() => { tap(); setFields(row) }}
              title={t('workspace.allFields')}
            >
              <Pencil className="size-4" />
            </button>
          </div>
          {row.phone && <Phones text={row.phone} className="mt-2" />}
          <div className="mt-2 grid gap-1 text-xs">
            {face.filter((col) => col.key !== 'status' && col.key !== 'phone' && col.show(row)).map((col) => (
              <div key={col.key} className="flex gap-2">
                <span className="w-24 shrink-0 text-muted">{col.label}</span>
                <span className="min-w-0 flex-1 truncate">{col.show(row)}</span>
              </div>
            ))}
          </div>
        </section>
      ))}

      <div ref={sentinel} className="h-4" />
      {loadingMore && <div className="grid place-items-center py-3"><Spinner className="size-5 text-brand-500" /></div>}
      {!hasMore && <p className="py-2 text-center text-xs text-muted">{t('workspace.count', { n: total })}</p>}

      <Modal open={Boolean(open)} onClose={() => setFields(null)} title={open?.name ?? ''}>
        {open && <FieldList row={open} columns={columns} />}
      </Modal>
    </div>
  )
}

/** Every column of one business, each line a press away from being changed. */
function FieldList({ row, columns }: { row: GridRow; columns: Col[] }) {
  const { t } = useI18n()
  const { canEdit, observe } = useMode()
  const editable = canEdit(row.canEdit)
  const [editing, setEditing] = useState<string | null>(null)

  return (
    <div className="divide-y divide-line">
      {columns.map((col) => (
        <div key={col.key} className="py-2">
          {editing === col.key && col.edit ? (
            <FieldEditor row={row} col={col} onDone={() => setEditing(null)} />
          ) : (
            <button
              type="button"
              disabled={!col.edit || !editable}
              onClick={() => { tap(); setEditing(col.key) }}
              className="flex w-full items-center gap-3 text-left disabled:cursor-default"
            >
              <span className="w-28 shrink-0 text-xs text-muted">{col.label}</span>
              <span className={`min-w-0 flex-1 text-sm ${col.show(row) ? '' : 'text-muted/60'}`}>{col.show(row) || '-'}</span>
              {col.edit && editable && <ChevronRight className="size-4 shrink-0 text-muted" />}
            </button>
          )}
        </div>
      ))}
      {/* Why every line is grey: either this row belongs to someone else, or the device is set to look only. */}
      {!editable && <p className="py-2 text-xs text-muted">{observe ? t('common.observeBanner') : t('workspace.readOnly')}</p>}
    </div>
  )
}

/** One field, open for changing: the right keyboard for it, and saved on its own. */
function FieldEditor({ row, col, onDone }: { row: GridRow; col: Col; onDone: () => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  const [value, setValue] = useState(col.raw(row))
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (value === col.raw(row)) {
      onDone()
      return
    }
    setSaving(true)
    try {
      const updated = await api.patch<GridRow>(`/businesses/${row.id}`, {
        changes: { [col.key]: payload(col, value) },
        version: row.version,
      })
      // Put the saved row straight back into the list, the way the grid does.
      client.setQueriesData<InfiniteData<PageDto<GridRow>>>({ queryKey: ['grid'] }, (data) => data && {
        ...data,
        pages: data.pages.map((page) => ({ ...page, items: page.items.map((r) => (r.id === updated.id ? updated : r)) })),
      })
      client.invalidateQueries({ queryKey: keys.business(row.id) })
      if (col.key === 'sheetId') client.invalidateQueries({ queryKey: ['workspace'] })
      tap(14)
      onDone()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="mb-1 text-xs font-medium text-muted">{col.label}</div>
      <div className="flex items-center gap-2">
        {col.edit === 'select' ? (
          <select className="input" autoFocus value={value} onChange={(e) => setValue(e.target.value)}>
            {col.options?.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        ) : col.key === 'notes' || col.key === 'competitorNotes' ? (
          <textarea className="input" rows={3} autoFocus value={value} onChange={(e) => setValue(e.target.value)} />
        ) : (
          <input
            className="input"
            autoFocus
            inputMode={col.edit === 'number' ? 'numeric' : col.key === 'phone' ? 'tel' : col.key === 'email' ? 'email' : 'text'}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void save() }}
          />
        )}
        <button type="button" className="btn-primary shrink-0 px-3" disabled={saving} onClick={() => void save()} title={t('common.save')}>
          {saving ? <Spinner className="size-4" /> : <Check className="size-4" />}
        </button>
        <button type="button" className="btn-ghost shrink-0 px-2" onClick={onDone} title={t('common.cancel')}>
          <X className="size-4" />
        </button>
      </div>
    </div>
  )
}
