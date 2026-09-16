import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { List, Plus, Search, Table2 } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { useMode } from '../lib/mode'
import { useIsPhone } from '../lib/mobile'
import { useGrid, useRefreshWork, useWorkspace } from '../lib/queries'
import type { BusinessDetail } from '../lib/types'
import { useI18n } from '../i18n'
import { ErrorBlock, Field, Loading, Modal, Spinner } from '../components/ui'
import { useToast } from '../components/Toast'
import { readScope, scopeParams, scopeSearch, WorkspaceTreeNav, type Scope } from '../components/workspace/WorkspaceTree'
import { BusinessDrawer } from '../components/workspace/BusinessDrawer'
import { NamesList } from '../components/workspace/NamesList'
import { ExcelGrid } from '../components/workspace/ExcelGrid'
import { CardEditor } from '../components/workspace/CardEditor'

/**
 * The spreadsheet, rebuilt: projects and sheets on the left, the names of the chosen sheet on the right.
 * A name opens everything about that business in a drawer; Excel mode shows and edits every column.
 * Where you are (sheet, mode, search, open business) lives in the URL, so reload and back both keep it.
 */
export function MainPage() {
  const { t } = useI18n()
  const { canEdit } = useMode()
  const phone = useIsPhone()
  const [search, setSearch] = useSearchParams()
  const scope = readScope(search)
  const mode = search.get('view') === 'excel' ? 'excel' : 'list'
  const openId = Number(search.get('open')) || null
  const [text, setText] = useState(search.get('q') ?? '')
  const q = useDebounced(text.trim(), 250)
  const [adding, setAdding] = useState(false)

  const update = useCallback((changes: Record<string, string | null>) => {
    setSearch((previous) => {
      const next = new URLSearchParams(previous)
      for (const [key, value] of Object.entries(changes)) {
        if (value === null || value === '') next.delete(key)
        else next.set(key, value)
      }
      return next
    }, { replace: true })
  }, [setSearch])

  useEffect(() => {
    if ((search.get('q') ?? '') !== q) update({ q: q || null })
  }, [q, search, update])

  const tree = useWorkspace()
  const params = useMemo(() => ({ ...scopeParams(scope), q: q || undefined, sort: 'name' }), [scope.kind, scope.kind === 'all' || scope.kind === 'unfiled' ? 0 : scope.id, q]) // eslint-disable-line react-hooks/exhaustive-deps
  const grid = useGrid(params)
  const rows = useMemo(() => grid.data?.pages.flatMap((page) => page.items) ?? [], [grid.data])
  const total = grid.data?.pages[0]?.total ?? 0

  const select = (next: Scope) => update(scopeSearch(next))
  const open = useCallback((id: number | null) => update({ open: id ? String(id) : null }), [update])

  const heading = (() => {
    const data = tree.data
    if (!data) return t('workspace.title')
    if (scope.kind === 'all') return t('workspace.all')
    if (scope.kind === 'unfiled') return t('workspace.unfiled')
    if (scope.kind === 'project') return data.workbooks.find((w) => w.id === scope.id)?.name ?? t('workspace.title')
    const sheet = [...data.workbooks.flatMap((w) => w.sheets), ...data.looseSheets].find((s) => s.id === scope.id)
    const project = data.workbooks.find((w) => w.id === sheet?.workbookId)
    return sheet ? [project?.name, sheet.name].filter(Boolean).join(' / ') : t('workspace.title')
  })()

  const listProps = {
    rows, total, loading: grid.isLoading, hasMore: Boolean(grid.hasNextPage), loadingMore: grid.isFetchingNextPage,
    onLoadMore: () => { if (grid.hasNextPage && !grid.isFetchingNextPage) void grid.fetchNextPage() },
    onOpen: open,
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[17rem_minmax(0,1fr)]">
      <aside className="card h-fit p-2 lg:sticky lg:top-20 lg:max-h-[calc(100dvh-6rem)] lg:overflow-y-auto">
        {tree.isLoading ? <Loading /> : tree.error || !tree.data ? <ErrorBlock error={tree.error} onRetry={() => tree.refetch()} /> : (
          <WorkspaceTreeNav tree={tree.data} scope={scope} onSelect={select} />
        )}
      </aside>

      <section className="min-w-0">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <div className="min-w-0 flex-1 basis-60">
            <h1 className="truncate text-xl font-semibold tracking-tight">{heading}</h1>
            <p className="text-xs text-muted">{t('workspace.count', { n: total })}</p>
          </div>
          <label className="relative min-w-0 flex-1 basis-56 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
            <input className="input pl-9" placeholder={t('workspace.search')} value={text} onChange={(e) => setText(e.target.value)} />
          </label>
          <div className="flex rounded-xl border border-line bg-surface p-0.5">
            <button type="button" onClick={() => update({ view: null })} className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium ${mode === 'list' ? 'bg-brand-600 text-white' : 'text-muted hover:text-ink'}`}>
              <List className="size-4" /> {t('workspace.listMode')}
            </button>
            <button type="button" onClick={() => update({ view: 'excel' })} className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium ${mode === 'excel' ? 'bg-brand-600 text-white' : 'text-muted hover:text-ink'}`}>
              <Table2 className="size-4" /> {phone ? t('workspace.cardsMode') : t('workspace.excelMode')}
            </button>
          </div>
          {canEdit() && (
            <button type="button" className="btn-primary" onClick={() => setAdding(true)}>
              <Plus className="size-4" /> <span className="hidden sm:inline">{t('workspace.addRow')}</span>
            </button>
          )}
        </div>

        {grid.error ? <ErrorBlock error={grid.error} onRetry={() => grid.refetch()} /> : mode === 'list' ? (
          <NamesList {...listProps} />
        ) : phone ? (
          <CardEditor {...listProps} />
        ) : (
          <ExcelGrid {...listProps} />
        )}
      </section>

      <BusinessDrawer id={openId} onClose={() => open(null)} />
      {adding && <AddRowDialog sheetId={scope.kind === 'sheet' ? scope.id : null} onClose={() => setAdding(false)} onCreated={(id) => open(id)} />}
    </div>
  )
}

function AddRowDialog({ sheetId, onClose, onCreated }: { sheetId: number | null; onClose: () => void; onCreated: (id: number) => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const refresh = useRefreshWork()
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [busy, setBusy] = useState(false)

  const save = async (force = false) => {
    setBusy(true)
    try {
      const created = await api.post<BusinessDetail>('/businesses', { name, phone: phone || null, sheetId }, force ? { force: true } : undefined)
      refresh(created.id)
      onClose()
      onCreated(created.id)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE' && window.confirm(`${t('business.duplicateTitle')}. ${t('business.saveAnyway')}?`)) {
        await save(true)
        return
      }
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={t('workspace.addRow')}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={busy || !name.trim()} onClick={() => void save()}>
            {busy && <Spinner className="size-4" />} {t('common.create')}
          </button>
        </>
      }
    >
      <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); if (name.trim()) void save() }}>
        <Field label={t('workspace.newRowName')}><input className="input" autoFocus value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label={t('common.phone')}><input className="input" inputMode="tel" value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
      </form>
    </Modal>
  )
}

function useDebounced<T>(value: T, ms: number): T {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), ms)
    return () => clearTimeout(timer)
  }, [value, ms])
  return settled
}
