import { memo, useCallback, useEffect, useMemo, useRef, useState, type ClipboardEvent, type KeyboardEvent, type MouseEvent as ReactMouseEvent } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { ChevronRight, RotateCcw, RotateCw, X } from 'lucide-react'
import { api, ApiError } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { useMode } from '../../lib/mode'
import { fmtDate, fmtDateTime } from '../../lib/format'
import { keys, useLookups } from '../../lib/queries'
import type { BulkResult, GridRow, PageDto } from '../../lib/types'
import { STATUSES } from '../../lib/types'
import { useI18n } from '../../i18n'
import { EmptyState, Loading, Spinner, STATUS_STYLE } from '../ui'
import { useToast } from '../Toast'
import type { RowListProps } from './NamesList'

interface Col {
  /** The PATCH key for editable columns ("phone", "sheetId", "custom.7"); any unique key otherwise. */
  key: string
  label: string
  width: number
  edit?: 'text' | 'number' | 'select'
  options?: { value: string; label: string }[]
  /** The value as the editor starts with it. */
  raw: (row: GridRow) => string
  /** What the cell shows (and what copying takes). */
  show: (row: GridRow) => string
}

interface Cell { r: number; c: number }
/** 'enter': started by typing, arrows leave the cell. 'edit': F2 / double-click / formula bar, arrows move the caret. */
interface Editing { r: number; c: number; value: string; mode: 'enter' | 'edit'; source: 'cell' | 'bar' }
interface Change { rowId: number; key: string; before: string; after: string }
interface WriteCell { row: GridRow; col: Col; value: string }

const ROW = 30
const HEAD = 40
const NUM = 52
const PAGE = 15
const WIDTHS_KEY = 'andaneri.grid.widths'

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

/** A, B, ... Z, AA, AB: the column names Excel uses. */
function letters(index: number): string {
  let n = index + 1
  let name = ''
  while (n > 0) {
    const rest = (n - 1) % 26
    name = String.fromCharCode(65 + rest) + name
    n = Math.floor((n - 1) / 26)
  }
  return name
}

/**
 * The spreadsheet, behaving like Excel: click or drag to select, type to replace a cell, F2 or double-click
 * to edit it, Enter / Tab to move on, Delete to clear, copy and paste to and from Excel itself, Ctrl + D to
 * fill down, Ctrl + Z / Ctrl + Y to undo and redo. Every change saves straight to the CRM, one request per
 * row, and an edit based on an out-of-date row is refused by the server rather than overwriting someone.
 */
export function ExcelGrid({ rows, total, loading, hasMore, loadingMore, onLoadMore, onOpen }: RowListProps) {
  const { t } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  const { canEdit } = useMode()
  const { isSupervisor } = useAuth()

  const baseColumns = useColumns()
  const [widths, setWidths] = useState<Record<string, number>>(readWidths)
  const columns = useMemo(() => baseColumns.map((c) => ({ ...c, width: widths[c.key] ?? c.width })), [baseColumns, widths])
  const lefts = useMemo(() => {
    let x = NUM
    return columns.map((c) => {
      const left = x
      x += c.width
      return left
    })
  }, [columns])
  const width = NUM + columns.reduce((sum, c) => sum + c.width, 0)
  const template = `${NUM}px ${columns.map((c) => `${c.width}px`).join(' ')}`

  const scrollRef = useRef<HTMLDivElement>(null)
  const [anchor, setAnchorState] = useState<Cell>({ r: 0, c: 0 })
  const [focus, setFocusState] = useState<Cell>({ r: 0, c: 0 })
  // The selection is also kept in refs, read by key handling, so keys pressed in quick succession all
  // start from where the previous one left the cursor rather than from the last render.
  const anchorRef = useRef<Cell>(anchor)
  const focusRef = useRef<Cell>(focus)
  const setAnchor = useCallback((next: Cell | ((current: Cell) => Cell)) => {
    anchorRef.current = typeof next === 'function' ? next(anchorRef.current) : next
    setAnchorState(anchorRef.current)
  }, [])
  const setFocus = useCallback((next: Cell | ((current: Cell) => Cell)) => {
    focusRef.current = typeof next === 'function' ? next(focusRef.current) : next
    setFocusState(focusRef.current)
  }, [])
  const [editing, setEditingState] = useState<Editing | null>(null)
  // Read synchronously: Enter commits, and the editor's blur as it unmounts must not commit a second time.
  const editingRef = useRef<Editing | null>(null)
  const setEditing = useCallback((next: Editing | null) => {
    editingRef.current = next
    setEditingState(next)
  }, [])
  const undoStack = useRef<Change[][]>([])
  const redoStack = useRef<Change[][]>([])
  const dragging = useRef<'cells' | 'rows' | 'cols' | null>(null)
  const queues = useRef(new Map<number, Promise<unknown>>())
  const versions = useRef(new Map<number, number>())
  const [saving, setSaving] = useState(0)

  const lastRow = Math.max(rows.length - 1, 0)
  const lastCol = columns.length - 1
  const range = {
    top: Math.min(anchor.r, focus.r), bottom: Math.max(anchor.r, focus.r),
    left: Math.min(anchor.c, focus.c), right: Math.max(anchor.c, focus.c),
  }
  const multiRow = range.bottom > range.top

  const virtualizer = useVirtualizer({ count: rows.length, getScrollElement: () => scrollRef.current, estimateSize: () => ROW, overscan: 25 })
  const items = virtualizer.getVirtualItems()
  const lastIndex = items.length ? items[items.length - 1].index : 0
  useEffect(() => {
    if (hasMore && !loadingMore && lastIndex >= rows.length - 40) onLoadMore()
  }, [lastIndex, rows.length, hasMore, loadingMore, onLoadMore])

  // Keep the selection on rows that exist after a search or a sheet change.
  useEffect(() => {
    setAnchor((a) => (a.r > lastRow ? { r: lastRow, c: a.c } : a))
    setFocus((f) => (f.r > lastRow ? { r: lastRow, c: f.c } : f))
  }, [lastRow])

  useEffect(() => {
    const up = () => { dragging.current = null }
    window.addEventListener('mouseup', up)
    return () => window.removeEventListener('mouseup', up)
  }, [])

  // ------------------------------------------------------------------ saving

  const replaceRow = useCallback((updated: GridRow) => {
    client.setQueriesData<InfiniteData<PageDto<GridRow>>>({ queryKey: ['grid'] }, (data) => data && {
      ...data,
      pages: data.pages.map((page) => ({ ...page, items: page.items.map((r) => (r.id === updated.id ? updated : r)) })),
    })
  }, [client])

  /** Saves cells, one request per row with all of its cells, rows queued so quick edits never race. Returns what changed. */
  const write = useCallback(async (cells: WriteCell[], checkVersion: boolean): Promise<Change[]> => {
    const byRow = new Map<number, { row: GridRow; entries: { col: Col; value: string }[] }>()
    let refused = false
    for (const cell of cells) {
      if (!cell.col.edit) continue
      if (!canEdit(cell.row.canEdit)) {
        refused = true
        continue
      }
      const value = normalize(cell.col, cell.value)
      if (value === undefined || value === cell.col.raw(cell.row)) continue
      const group = byRow.get(cell.row.id) ?? { row: cell.row, entries: [] }
      group.entries = [...group.entries.filter((e) => e.col.key !== cell.col.key), { col: cell.col, value }]
      byRow.set(cell.row.id, group)
    }
    if (refused) toast.error(new ApiError(403, { code: 'FORBIDDEN' }))
    if (byRow.size === 0) return []

    const changes: Change[] = []
    setSaving((n) => n + 1)
    try {
      await Promise.all([...byRow.values()].map(({ row, entries }) => {
        const previous = queues.current.get(row.id) ?? Promise.resolve()
        const next = previous.catch(() => undefined).then(async () => {
          const version = checkVersion ? Math.max(row.version, versions.current.get(row.id) ?? 0) : null
          try {
            const updated = await api.patch<GridRow>(`/businesses/${row.id}`, {
              changes: Object.fromEntries(entries.map((e) => [e.col.key, payload(e.col, e.value)])),
              version,
            })
            versions.current.set(row.id, updated.version)
            replaceRow(updated)
            entries.forEach((e) => changes.push({ rowId: row.id, key: e.col.key, before: e.col.raw(row), after: e.value }))
            client.invalidateQueries({ queryKey: keys.business(row.id) })
          } catch (error) {
            toast.error(error)
            if (error instanceof ApiError && error.code === 'STALE') client.invalidateQueries({ queryKey: ['grid'] })
          }
        })
        queues.current.set(row.id, next)
        return next
      }))
    } finally {
      setSaving((n) => n - 1)
    }
    if (changes.some((c) => c.key === 'sheetId')) client.invalidateQueries({ queryKey: ['workspace'] })
    return changes
  }, [canEdit, client, replaceRow, toast])

  const record = useCallback((changes: Change[]) => {
    if (!changes.length) return
    undoStack.current.push(changes)
    if (undoStack.current.length > 100) undoStack.current.shift()
    redoStack.current = []
  }, [])

  const replay = async (from: { current: Change[][] }, to: { current: Change[][] }, side: 'before' | 'after') => {
    const batch = from.current.pop()
    if (!batch) return
    const cells = batch.flatMap((change) => {
      const row = rows.find((r) => r.id === change.rowId)
      const col = columns.find((c) => c.key === change.key)
      return row && col ? [{ row, col, value: side === 'before' ? change.before : change.after }] : []
    })
    const done = await write(cells, false)
    if (done.length) to.current.push(batch)
  }
  const undo = () => void replay(undoStack, redoStack, 'before')
  const redo = () => void replay(redoStack, undoStack, 'after')

  // ------------------------------------------------------------------ selection

  const reveal = (cell: Cell) => {
    virtualizer.scrollToIndex(cell.r)
    const el = scrollRef.current
    if (!el || cell.c === 0) return
    const pinned = NUM + columns[0].width
    const left = lefts[cell.c]
    const right = left + columns[cell.c].width
    if (left - pinned < el.scrollLeft) el.scrollLeft = left - pinned
    else if (right > el.scrollLeft + el.clientWidth) el.scrollLeft = right - el.clientWidth
  }

  const select = (cell: Cell, extend = false) => {
    const next = { r: clamp(cell.r, 0, lastRow), c: clamp(cell.c, 0, lastCol) }
    if (!extend) setAnchor(next)
    setFocus(next)
    reveal(next)
  }

  const keepFocus = () => scrollRef.current?.focus({ preventScroll: true })

  // ------------------------------------------------------------------ editing

  const beginEditAt = (r: number, c: number, mode: Editing['mode'], initial?: string, source: Editing['source'] = 'cell') => {
    const row = rows[r]
    const col = columns[c]
    if (!row || !col) return
    if (!col.edit) {
      toast.error(new ApiError(0, { code: 'READ_ONLY_CELL' }))
      return
    }
    if (!canEdit(row.canEdit)) {
      toast.error(new ApiError(403, { code: 'FORBIDDEN' }))
      return
    }
    setAnchor({ r, c })
    setFocus({ r, c })
    setEditing({
      r, c, source,
      mode: col.edit === 'select' ? 'edit' : mode,
      value: initial !== undefined && col.edit !== 'select' ? initial : col.raw(row),
    })
  }

  const commitEdit = (dr: number, dc: number, move = true) => {
    const current = editingRef.current
    if (!current) return
    setEditing(null)
    const row = rows[current.r]
    const col = columns[current.c]
    if (row && col) void write([{ row, col, value: current.value }], true).then(record)
    if (move) select({ r: current.r + dr, c: current.c + dc })
    keepFocus()
  }

  const cancelEdit = () => {
    setEditing(null)
    keepFocus()
  }

  const changeDraft = (value: string) => {
    const current = editingRef.current
    if (current) setEditing({ ...current, value })
  }

  // ------------------------------------------------------------------ range actions

  const rangeCells = (): WriteCell[] => {
    const cells: WriteCell[] = []
    for (let r = range.top; r <= Math.min(range.bottom, rows.length - 1); r++) {
      for (let c = range.left; c <= range.right; c++) cells.push({ row: rows[r], col: columns[c], value: '' })
    }
    return cells
  }

  const clearRange = () => {
    void write(rangeCells().filter((cell) => cell.col.edit && cell.col.key !== 'name'), true).then(record)
  }

  const fillDown = () => {
    if (!multiRow) return
    const top = rows[range.top]
    const cells: WriteCell[] = []
    for (let r = range.top + 1; r <= Math.min(range.bottom, rows.length - 1); r++) {
      for (let c = range.left; c <= range.right; c++) cells.push({ row: rows[r], col: columns[c], value: columns[c].raw(top) })
    }
    void write(cells, true).then(record)
  }

  const copyText = () => {
    const lines: string[] = []
    for (let r = range.top; r <= Math.min(range.bottom, rows.length - 1); r++) {
      lines.push(columns.slice(range.left, range.right + 1).map((col) => quoteTsv(col.show(rows[r]))).join('\t'))
    }
    return lines.join('\r\n')
  }

  const pasteText = (text: string) => {
    const grid = parseTsv(text)
    if (!grid.length || !rows.length) return
    // One value pasted into a selection fills the whole selection, as in Excel.
    const single = grid.length === 1 && grid[0].length === 1
    const height = single ? range.bottom - range.top + 1 : grid.length
    const across = single ? range.right - range.left + 1 : Math.max(...grid.map((line) => line.length))
    const cells: WriteCell[] = []
    for (let i = 0; i < height && range.top + i < rows.length; i++) {
      for (let j = 0; j < across && range.left + j <= lastCol; j++) {
        const value = single ? grid[0][0] : grid[i][j]
        if (value !== undefined) cells.push({ row: rows[range.top + i], col: columns[range.left + j], value })
      }
    }
    if (!single) {
      setAnchor({ r: range.top, c: range.left })
      setFocus({ r: Math.min(range.top + height - 1, lastRow), c: Math.min(range.left + across - 1, lastCol) })
    }
    void write(cells, true).then((changes) => {
      record(changes)
      if (changes.length > 1) toast.ok(t('workspace.pasted', { n: changes.length }))
    })
  }

  const onCopy = (event: ClipboardEvent) => {
    if (editingRef.current) return
    event.preventDefault()
    event.clipboardData.setData('text/plain', copyText())
  }
  const onCut = (event: ClipboardEvent) => {
    if (editingRef.current) return
    onCopy(event)
    clearRange()
  }
  const onPaste = (event: ClipboardEvent) => {
    if (editingRef.current) return
    event.preventDefault()
    pasteText(event.clipboardData.getData('text/plain'))
  }

  const onKeyDown = (event: KeyboardEvent) => {
    const open = editingRef.current
    if (open) {
      // Keys typed faster than the cell editor appears still land in it, as in Excel.
      if (open.source !== 'cell') return
      if (event.key === 'Enter') { event.preventDefault(); commitEdit(event.shiftKey ? -1 : 1, 0) }
      else if (event.key === 'Tab') { event.preventDefault(); commitEdit(0, event.shiftKey ? -1 : 1) }
      else if (event.key === 'Escape') { event.preventDefault(); cancelEdit() }
      else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) { event.preventDefault(); changeDraft(open.value + event.key) }
      return
    }
    if (!rows.length) return
    const focus = focusRef.current
    const ctrl = event.ctrlKey || event.metaKey
    const key = event.key
    const lower = key.toLowerCase()
    if (ctrl && (lower === 'c' || lower === 'v' || lower === 'x')) return // the copy / cut / paste events do the work
    if (ctrl && lower === 'z') { event.preventDefault(); if (event.shiftKey) redo(); else undo(); return }
    if (ctrl && lower === 'y') { event.preventDefault(); redo(); return }
    if (ctrl && lower === 'a') { event.preventDefault(); setAnchor({ r: 0, c: 0 }); setFocus({ r: lastRow, c: lastCol }); return }
    if (ctrl && lower === 'd') { event.preventDefault(); fillDown(); return }

    const step = (dr: number, dc: number) => {
      event.preventDefault()
      const from = focus
      const target = ctrl
        ? { r: dr < 0 ? 0 : dr > 0 ? lastRow : from.r, c: dc < 0 ? 0 : dc > 0 ? lastCol : from.c }
        : { r: from.r + dr, c: from.c + dc }
      select(target, event.shiftKey)
    }
    switch (key) {
      case 'ArrowUp': return step(-1, 0)
      case 'ArrowDown': return step(1, 0)
      case 'ArrowLeft': return step(0, -1)
      case 'ArrowRight': return step(0, 1)
      case 'PageDown': event.preventDefault(); return select({ r: focus.r + PAGE, c: focus.c }, event.shiftKey)
      case 'PageUp': event.preventDefault(); return select({ r: focus.r - PAGE, c: focus.c }, event.shiftKey)
      case 'Home': event.preventDefault(); return select(ctrl ? { r: 0, c: 0 } : { r: focus.r, c: 0 }, event.shiftKey)
      case 'End': event.preventDefault(); return select(ctrl ? { r: lastRow, c: lastCol } : { r: focus.r, c: lastCol }, event.shiftKey)
      case 'Tab': event.preventDefault(); return select({ r: focus.r, c: focus.c + (event.shiftKey ? -1 : 1) })
      case 'Enter': event.preventDefault(); return select({ r: focus.r + (event.shiftKey ? -1 : 1), c: focus.c })
      case 'F2': event.preventDefault(); return beginEditAt(focus.r, focus.c, 'edit')
      case 'Delete': event.preventDefault(); return clearRange()
      case 'Backspace': event.preventDefault(); return beginEditAt(focus.r, focus.c, 'enter', '')
      case 'Escape': setAnchor(focus); return
      default:
        if (key.length === 1 && !ctrl && !event.altKey) {
          event.preventDefault()
          beginEditAt(focus.r, focus.c, 'enter', key)
        }
    }
  }

  // Row components are memoised; they call the latest handlers through this ref.
  const handlers = useRef({
    cellDown: (_r: number, _c: number, _shift: boolean) => {},
    cellEnter: (_r: number, _c: number) => {},
    cellDouble: (_r: number, _c: number) => {},
    rowDown: (_r: number, _shift: boolean) => {},
    open: (_id: number) => {},
    draft: (_value: string) => {},
    commit: (_dr: number, _dc: number, _move?: boolean) => {},
    cancel: () => {},
  })
  handlers.current = {
    cellDown: (r, c, shift) => {
      if (editingRef.current) commitEdit(0, 0, false)
      select({ r, c }, shift)
      dragging.current = 'cells'
      keepFocus()
    },
    cellEnter: (r, c) => {
      if (dragging.current === 'cells') setFocus({ r, c })
      else if (dragging.current === 'rows') setFocus({ r, c: lastCol })
    },
    cellDouble: (r, c) => beginEditAt(r, c, 'edit'),
    rowDown: (r, shift) => {
      if (editingRef.current) commitEdit(0, 0, false)
      if (!shift) setAnchor({ r, c: 0 })
      setFocus({ r, c: lastCol })
      dragging.current = 'rows'
      keepFocus()
    },
    open: onOpen,
    draft: changeDraft,
    commit: commitEdit,
    cancel: cancelEdit,
  }
  const stable = useMemo(() => ({
    cellDown: (r: number, c: number, shift: boolean) => handlers.current.cellDown(r, c, shift),
    cellEnter: (r: number, c: number) => handlers.current.cellEnter(r, c),
    cellDouble: (r: number, c: number) => handlers.current.cellDouble(r, c),
    rowDown: (r: number, shift: boolean) => handlers.current.rowDown(r, shift),
    open: (id: number) => handlers.current.open(id),
    draft: (value: string) => handlers.current.draft(value),
    commit: (dr: number, dc: number, move?: boolean) => handlers.current.commit(dr, dc, move),
    cancel: () => handlers.current.cancel(),
  }), [])

  const columnDown = (event: ReactMouseEvent, c: number) => {
    if (!rows.length) return
    if (editingRef.current) commitEdit(0, 0, false)
    if (!event.shiftKey) setAnchor({ r: 0, c })
    setFocus({ r: lastRow, c })
    dragging.current = 'cols'
    keepFocus()
  }

  const startResize = (event: ReactMouseEvent, key: string, start: number) => {
    event.preventDefault()
    event.stopPropagation()
    const startX = event.clientX
    const move = (e: MouseEvent) => setWidths((w) => ({ ...w, [key]: clamp(start + e.clientX - startX, 50, 700) }))
    const up = () => {
      window.removeEventListener('mousemove', move)
      window.removeEventListener('mouseup', up)
      setWidths((w) => {
        saveWidths(w)
        return w
      })
    }
    window.addEventListener('mousemove', move)
    window.addEventListener('mouseup', up)
  }

  const bulk = async (change: Record<string, unknown>) => {
    try {
      const ids = rows.slice(range.top, range.bottom + 1).map((r) => r.id)
      const result = await api.post<BulkResult>('/businesses/bulk', { ids, ...change })
      toast.ok(t('workspace.bulkDone', { updated: result.updated, skipped: result.skipped }))
      await Promise.all([client.invalidateQueries({ queryKey: ['grid'] }), client.invalidateQueries({ queryKey: ['workspace'] })])
    } catch (error) {
      toast.error(error)
    }
  }

  if (loading) return <div className="card"><Loading /></div>

  const activeRow = rows[focus.r]
  const activeCol = columns[focus.c]
  const cellName = multiRow || range.right > range.left
    ? `${letters(range.left)}${range.top + 1}:${letters(range.right)}${range.bottom + 1}`
    : `${letters(focus.c)}${focus.r + 1}`
  const barEditable = Boolean(activeRow && activeCol && (activeCol.edit === 'text' || activeCol.edit === 'number') && canEdit(activeRow.canEdit))
  const barValue = editing && editing.r === focus.r && editing.c === focus.c
    ? editing.value
    : activeRow && activeCol ? (activeCol.edit === 'text' || activeCol.edit === 'number' ? activeCol.raw(activeRow) : activeCol.show(activeRow)) : ''
  const selectedRows = range.bottom - range.top + 1
  const selectedCols = range.right - range.left + 1
  const sheetOptions = columns.find((c) => c.key === 'sheetId')?.options ?? []
  const userOptions = columns.find((c) => c.key === 'assignedToId')?.options ?? []

  return (
    <div className="space-y-2">
      {multiRow && canEdit() && (
        <div className="card flex flex-wrap items-center gap-2 border-brand-200 bg-brand-50 p-2 text-sm">
          <b className="px-1 text-brand-700">{t('common.selected', { n: selectedRows })}</b>
          <BulkSelect label={t('workspace.moveTo')} options={[{ value: '-', label: t('workspace.removeFromSheet') }, ...sheetOptions.filter((o) => o.value)]}
            onPick={(v) => void bulk(v === '-' ? { clearSheet: true } : { sheetId: Number(v) })} />
          <BulkSelect label={t('workspace.setStatus')} options={STATUSES.map((s) => ({ value: s, label: t(`status.${s}`) }))} onPick={(v) => void bulk({ status: v })} />
          <BulkSelect label={t('workspace.setPriority')} options={['LOW', 'NORMAL', 'HIGH'].map((p) => ({ value: p, label: t(`priority.${p}`) }))} onPick={(v) => void bulk({ priority: v })} />
          {isSupervisor && (
            <BulkSelect label={t('workspace.assign')} options={[{ value: '-', label: t('common.unassigned') }, ...userOptions.filter((o) => o.value)]}
              onPick={(v) => void bulk(v === '-' ? { unassign: true } : { assignedToId: Number(v) })} />
          )}
          <button type="button" className="btn-ghost ml-auto px-2 py-1" onClick={() => setAnchor(focus)}><X className="size-4" /> {t('workspace.clearSelection')}</button>
        </div>
      )}

      <div className="card overflow-hidden">
        {/* Name box and formula bar */}
        <div className="flex items-center gap-2 border-b border-line px-2 py-1.5">
          <span className="w-24 shrink-0 truncate rounded-md border border-line bg-canvas px-1.5 py-0.5 text-center font-mono text-xs">{rows.length ? cellName : '-'}</span>
          <span className="select-none font-serif text-sm italic text-muted">fx</span>
          <input
            className="min-w-0 flex-1 rounded-md bg-transparent px-1.5 py-0.5 text-sm outline-none focus:bg-canvas"
            value={barValue}
            readOnly={!barEditable}
            onFocus={() => { if (!editingRef.current && barEditable) beginEditAt(focus.r, focus.c, 'edit', undefined, 'bar') }}
            onChange={(e) => changeDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') { e.preventDefault(); commitEdit(1, 0) }
              else if (e.key === 'Tab') { e.preventDefault(); commitEdit(0, e.shiftKey ? -1 : 1) }
              else if (e.key === 'Escape') { e.preventDefault(); cancelEdit() }
            }}
            onBlur={() => { if (editingRef.current?.source === 'bar') commitEdit(0, 0, false) }}
          />
          {saving > 0 && <Spinner className="size-4 text-muted" />}
          <button type="button" className="btn-ghost p-1.5" title={`${t('workspace.undo')} (Ctrl + Z)`} onClick={undo}><RotateCcw className="size-4" /></button>
          <button type="button" className="btn-ghost p-1.5" title={`${t('workspace.redo')} (Ctrl + Y)`} onClick={redo}><RotateCw className="size-4" /></button>
        </div>

        {total === 0 ? <EmptyState title={t('workspace.empty')} /> : (
          <div
            ref={scrollRef}
            tabIndex={0}
            onKeyDown={onKeyDown}
            onCopy={onCopy}
            onCut={onCut}
            onPaste={onPaste}
            className="scroll-area h-[calc(100dvh-16rem)] min-h-80 select-none text-[13px] outline-none"
          >
            <div style={{ width }} className="relative">
              {/* Column letters and names */}
              <div className="sticky top-0 z-20 grid border-b border-line bg-canvas text-muted" style={{ gridTemplateColumns: template, height: HEAD }}>
                <button type="button" aria-label="select all" className="sticky left-0 z-10 border-r border-line bg-canvas hover:bg-brand-50"
                  onMouseDown={(e) => { e.preventDefault(); setAnchor({ r: 0, c: 0 }); setFocus({ r: lastRow, c: lastCol }); keepFocus() }} />
                {columns.map((col, c) => {
                  const inRange = c >= range.left && c <= range.right
                  return (
                    <div
                      key={col.key}
                      onMouseDown={(e) => { e.preventDefault(); columnDown(e, c) }}
                      onMouseEnter={() => { if (dragging.current === 'cols') setFocus({ r: lastRow, c }) }}
                      className={`relative flex cursor-default flex-col justify-center border-r border-line px-2 leading-tight ${c === 0 ? 'sticky z-10' : ''} ${inRange ? 'bg-brand-100 text-brand-800' : 'bg-canvas'}`}
                      style={c === 0 ? { left: NUM } : undefined}
                      title={col.label}
                    >
                      <span className="text-[10px] font-semibold opacity-70">{letters(c)}</span>
                      <span className="truncate font-medium">{col.label}</span>
                      <span
                        className="absolute right-0 top-0 h-full w-1.5 cursor-col-resize hover:bg-brand-400"
                        onMouseDown={(e) => startResize(e, col.key, col.width)}
                        onDoubleClick={() => setWidths((w) => {
                          const next = { ...w }
                          delete next[col.key]
                          saveWidths(next)
                          return next
                        })}
                      />
                    </div>
                  )
                })}
              </div>

              <div className="relative" style={{ height: virtualizer.getTotalSize() }}>
                {items.map((item) => {
                  const r = item.index
                  const inRows = r >= range.top && r <= range.bottom
                  const edit = editing && editing.r === r ? editing : null
                  return (
                    <GridLine
                      key={rows[r].id}
                      row={rows[r]}
                      index={r}
                      top={item.start}
                      columns={columns}
                      template={template}
                      rangeLeft={inRows ? range.left : -1}
                      rangeRight={inRows ? range.right : -1}
                      focusCol={focus.r === r ? focus.c : -1}
                      editing={edit}
                      handlers={stable}
                    />
                  )
                })}
              </div>
            </div>
            {loadingMore && <div className="sticky left-0 flex w-full justify-center py-2"><Spinner className="size-4 text-muted" /></div>}
          </div>
        )}

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line px-3 py-1.5 text-xs text-muted">
          <span>{t('workspace.selection', { rows: selectedRows, cols: selectedCols })}</span>
          <span>{t('common.page', { from: rows.length ? 1 : 0, to: rows.length, total })}</span>
          <span className="hidden md:inline">{t('workspace.editHint')}</span>
        </div>
      </div>
    </div>
  )
}

const GridLine = memo(function GridLine({ row, index, top, columns, template, rangeLeft, rangeRight, focusCol, editing, handlers }: {
  row: GridRow; index: number; top: number; columns: Col[]; template: string; rangeLeft: number; rangeRight: number; focusCol: number
  editing: Editing | null
  handlers: {
    cellDown: (r: number, c: number, shift: boolean) => void; cellEnter: (r: number, c: number) => void; cellDouble: (r: number, c: number) => void
    rowDown: (r: number, shift: boolean) => void; open: (id: number) => void; draft: (value: string) => void
    commit: (dr: number, dc: number, move?: boolean) => void; cancel: () => void
  }
}) {
  const inRows = rangeLeft >= 0
  const wholeRow = inRows && rangeLeft === 0 && rangeRight === columns.length - 1
  return (
    <div className="absolute left-0 grid" style={{ gridTemplateColumns: template, height: ROW, transform: `translateY(${top}px)`, width: '100%' }}>
      <div
        onMouseDown={(e) => { e.preventDefault(); handlers.rowDown(index, e.shiftKey) }}
        onMouseEnter={() => handlers.cellEnter(index, 0)}
        className={`sticky left-0 z-10 flex items-center justify-end border-b border-r border-line pr-2 text-xs tabular-nums ${inRows ? 'bg-brand-100 font-semibold text-brand-800' : 'bg-canvas text-muted'}`}
      >
        {index + 1}
      </div>
      {columns.map((col, c) => {
        const selected = inRows && c >= rangeLeft && c <= rangeRight
        const active = focusCol === c
        const editingHere = editing && editing.c === c
        const first = c === 0
        return (
          <div
            key={col.key}
            onMouseDown={(e) => { if (e.button !== 0) return; e.preventDefault(); handlers.cellDown(index, c, e.shiftKey) }}
            onMouseEnter={() => handlers.cellEnter(index, c)}
            onDoubleClick={() => handlers.cellDouble(index, c)}
            className={`group relative flex min-w-0 items-center border-b border-r border-line/70 ${first ? 'sticky z-[5] font-medium' : ''} ${selected && !active ? 'bg-brand-50' : wholeRow ? 'bg-brand-50' : 'bg-surface'} ${active ? 'z-[6] outline-2 -outline-offset-2 outline-brand-600' : ''} ${col.edit ? '' : 'text-muted'}`}
            style={first ? { left: NUM } : undefined}
          >
            {editingHere && editing.source === 'cell' ? (
              <CellEditor col={col} editing={editing} handlers={handlers} />
            ) : editingHere ? (
              <span className="truncate px-2 text-brand-700">{editing.value}</span>
            ) : col.key === 'status' ? (
              <span className={`mx-1.5 truncate rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[row.status].badge}`}>{col.show(row)}</span>
            ) : (
              <span className="truncate px-2">{col.show(row)}</span>
            )}
            {first && !editingHere && (
              <button type="button" aria-label="open"
                onMouseDown={(e) => e.stopPropagation()}
                onClick={(e) => { e.stopPropagation(); handlers.open(row.id) }}
                className="absolute right-1 grid size-6 shrink-0 place-items-center rounded-md bg-surface text-muted opacity-0 hover:bg-brand-600 hover:text-white group-hover:opacity-100">
                <ChevronRight className="size-4" />
              </button>
            )}
          </div>
        )
      })}
    </div>
  )
})

function CellEditor({ col, editing, handlers }: {
  col: Col; editing: Editing
  handlers: { draft: (value: string) => void; commit: (dr: number, dc: number, move?: boolean) => void; cancel: () => void }
}) {
  const selectRef = useRef<HTMLSelectElement>(null)
  useEffect(() => {
    // A drop-down cell opens its list straight away, like Excel's data validation lists.
    try {
      selectRef.current?.showPicker?.()
    } catch {
      /* not allowed without a direct click in some browsers */
    }
  }, [])

  const keys = (event: KeyboardEvent) => {
    event.stopPropagation()
    const { key, shiftKey } = event
    if (key === 'Enter' && !event.altKey) { event.preventDefault(); handlers.commit(shiftKey ? -1 : 1, 0) }
    else if (key === 'Tab') { event.preventDefault(); handlers.commit(0, shiftKey ? -1 : 1) }
    else if (key === 'Escape') { event.preventDefault(); handlers.cancel() }
    else if (editing.mode === 'enter' && col.edit !== 'select') {
      const moves: Record<string, [number, number]> = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }
      if (moves[key]) { event.preventDefault(); handlers.commit(moves[key][0], moves[key][1]) }
    }
  }
  const base = 'absolute inset-0 z-20 w-full min-w-40 border-2 border-brand-600 bg-surface px-2 text-[13px] text-ink shadow-lg outline-none'

  if (col.edit === 'select') {
    return (
      <select ref={selectRef} autoFocus className={base} value={editing.value} onKeyDown={keys} onBlur={() => handlers.commit(0, 0, false)}
        onChange={(e) => { handlers.draft(e.target.value); handlers.commit(0, 0, false) }}>
        {col.options?.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    )
  }
  return (
    <input
      autoFocus
      className={base}
      inputMode={col.edit === 'number' ? 'numeric' : undefined}
      value={editing.value}
      onFocus={(e) => { const end = e.currentTarget.value.length; e.currentTarget.setSelectionRange(end, end) }}
      onChange={(e) => handlers.draft(e.target.value)}
      onKeyDown={keys}
      onBlur={() => handlers.commit(0, 0, false)}
    />
  )
}

function BulkSelect({ label, options, onPick }: { label: string; options: { value: string; label: string }[]; onPick: (value: string) => void }) {
  return (
    <select className="input w-auto max-w-full py-1 text-xs" value="" onChange={(e) => { if (e.target.value) onPick(e.target.value) }}>
      <option value="">{label}...</option>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}

// ------------------------------------------------------------------ values in and out

/** What a typed or pasted value means for a column; undefined when it cannot go there (skipped, like Excel's validation). */
function normalize(col: Col, input: string): string | undefined {
  const value = input.replace(/\r$/, '')
  if (col.edit === 'number') {
    if (!value.trim()) return ''
    const n = Number(value.trim().replace(',', '.'))
    return Number.isFinite(n) && n >= 0 ? String(Math.round(n)) : undefined
  }
  if (col.edit === 'select') {
    const wanted = value.trim().toLowerCase()
    const option = col.options?.find((o) => o.value === value.trim()) ?? col.options?.find((o) => o.label.trim().toLowerCase() === wanted)
    return option ? option.value : undefined
  }
  if (col.key === 'name' && !value.trim()) return undefined
  return value
}

function payload(col: Col, value: string): string | number | null {
  if (col.edit === 'number') return value === '' ? null : Number(value)
  if (col.edit === 'select') {
    if (value === '') return null
    return col.key.endsWith('Id') && /^\d+$/.test(value) ? Number(value) : value
  }
  return value
}

function quoteTsv(value: string): string {
  return /[\t\n\r"]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

/** Tab-separated text as Excel puts it on the clipboard, quoted cells with line breaks included. */
function parseTsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++ }
      else if (ch === '"') quoted = false
      else cell += ch
    } else if (ch === '"' && cell === '') {
      quoted = true
    } else if (ch === '\t') {
      row.push(cell)
      cell = ''
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      row.push(cell)
      rows.push(row)
      row = []
      cell = ''
    } else {
      cell += ch
    }
  }
  if (cell !== '' || row.length) {
    row.push(cell)
    rows.push(row)
  }
  return rows
}

function readWidths(): Record<string, number> {
  try {
    return JSON.parse(localStorage.getItem(WIDTHS_KEY) ?? '{}') as Record<string, number>
  } catch {
    return {}
  }
}

function saveWidths(widths: Record<string, number>) {
  try {
    localStorage.setItem(WIDTHS_KEY, JSON.stringify(widths))
  } catch {
    /* this visit only */
  }
}

/** The columns, in the order of the old Sales Report Form, then everything else, then the team's extra fields. */
function useColumns(): Col[] {
  const { t, lang, name } = useI18n()
  const lookups = useLookups()
  return useMemo(() => {
    const data = lookups.data
    const text = (key: keyof GridRow, label: string, width = 160): Col => ({
      key, label, width, edit: 'text', raw: (r) => String(r[key] ?? ''), show: (r) => String(r[key] ?? ''),
    })
    const number = (key: 'branches' | 'reorderDays', label: string): Col => ({
      key, label, width: 110, edit: 'number', raw: (r) => (r[key] === null ? '' : String(r[key])), show: (r) => (r[key] === null ? '' : String(r[key])),
    })
    const choice = (key: 'status' | 'priority' | 'switchOpenness' | 'priceSensitivity' | 'satisfaction', label: string, group: string, values: string[], width = 150): Col => ({
      key, label, width, edit: 'select', options: values.map((v) => ({ value: v, label: t(`${group}.${v}`) })),
      raw: (r) => r[key], show: (r) => t(`${group}.${r[key]}`),
    })
    const readOnly = (key: string, label: string, show: (r: GridRow) => string, width = 150): Col => ({ key, label, width, raw: show, show })
    const sheets = data?.sheets ?? []
    const projectOf = (id: number | null) => data?.workbooks.find((w) => w.id === id)?.name
    const sheetLabel = (id: number | null) => {
      const sheet = sheets.find((s) => s.id === id)
      return sheet ? [projectOf(sheet.workbookId), sheet.name].filter(Boolean).join(' / ') : ''
    }
    const unknown = 'UNKNOWN'
    return [
      text('name', t('common.name'), 230),
      choice('status', t('common.status'), 'status', [...STATUSES], 170),
      choice('priority', t('common.priority'), 'priority', ['LOW', 'NORMAL', 'HIGH'], 110),
      {
        key: 'typeId', label: t('common.type'), width: 140, edit: 'select',
        options: [{ value: '', label: '-' }, ...(data?.businessTypes ?? []).map((ty) => ({ value: String(ty.id), label: name(ty) }))],
        raw: (r) => (r.typeId ? String(r.typeId) : ''), show: (r) => name(data?.businessTypes.find((ty) => ty.id === r.typeId)),
      },
      text('phone', t('common.phone'), 150),
      readOnly('contacts', t('business.contacts'), (r) => r.contacts ?? '', 200),
      text('address', t('common.address'), 200),
      text('district', t('common.district'), 120),
      text('city', t('common.city'), 110),
      {
        key: 'sheetId', label: t('business.sheet'), width: 180, edit: 'select',
        options: [{ value: '', label: t('business.noSheet') }, ...sheets.map((s) => ({ value: String(s.id), label: sheetLabel(s.id) }))],
        raw: (r) => (r.sheetId ? String(r.sheetId) : ''), show: (r) => sheetLabel(r.sheetId),
      },
      {
        key: 'assignedToId', label: t('common.assignedTo'), width: 150, edit: 'select',
        options: [{ value: '', label: t('common.unassigned') }, ...(data?.users ?? []).filter((u) => u.active).map((u) => ({ value: String(u.id), label: u.fullName }))],
        raw: (r) => (r.assignedTo ? String(r.assignedTo.id) : ''), show: (r) => r.assignedTo?.fullName ?? '',
      },
      readOnly('nextTask', t('business.nextStep'), (r) => (r.nextTask ? `${t(`taskType.${r.nextTask.type}`)} ${fmtDateTime(r.nextTask.dueAt, lang)}` : ''), 190),
      readOnly('lastContactAt', t('business.lastContact'), (r) => fmtDate(r.lastContactAt, lang), 120),
      readOnly('brands', t('business.brand'), (r) => r.brands.join(', '), 160),
      readOnly('flavors', t('business.flavors'), (r) => r.flavorIds.map((id) => name(data?.flavors.find((f) => f.id === id))).filter(Boolean).join(', '), 200),
      readOnly('purchaseCount', t('business.purchases'), (r) => (r.purchaseCount ? String(r.purchaseCount) : ''), 90),
      readOnly('lastPurchaseDate', t('reports.purchases'), (r) => fmtDate(r.lastPurchaseDate, lang), 120),
      text('idCode', t('business.idCode'), 120),
      text('legalName', t('business.legalName'), 180),
      text('email', t('common.email'), 180),
      text('website', t('business.website'), 180),
      text('mapsUrl', t('business.mapsUrl'), 180),
      text('visitHours', t('business.visitHours'), 150),
      number('branches', t('business.branches')),
      number('reorderDays', t('business.reorderDays')),
      choice('switchOpenness', t('business.switchOpenness'), 'openness', [unknown, 'YES', 'MAYBE', 'NO']),
      choice('priceSensitivity', t('business.priceSensitivity'), 'priceSensitivity', [unknown, 'LOW', 'MEDIUM', 'HIGH']),
      choice('satisfaction', t('business.satisfaction'), 'satisfaction', [unknown, 'SATISFIED', 'NEUTRAL', 'UNSATISFIED']),
      text('menuChange', t('business.menuChange'), 150),
      text('competitorNotes', t('business.competitorNotes'), 240),
      text('notes', t('common.notes'), 260),
      readOnly('createdAt', t('business.created'), (r) => fmtDate(r.createdAt, lang), 110),
      ...(data?.customFields ?? []).filter((f) => f.active).map<Col>((f) => ({
        key: `custom.${f.id}`, label: f.label, width: 170, edit: 'text',
        raw: (r) => r.customValues[f.id] ?? '', show: (r) => r.customValues[f.id] ?? '',
      })),
    ]
  }, [lookups.data, t, lang, name])
}
