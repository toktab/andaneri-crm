import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Ellipsis, FileSpreadsheet, Folder, FolderOpen, Inbox, Layers, Plus } from 'lucide-react'
import { api, type Params } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { useMode } from '../../lib/mode'
import type { Workbook, WorkSheet, WorkspaceTree } from '../../lib/types'
import { useI18n } from '../../i18n'
import { Field, Modal, Spinner } from '../ui'
import { useToast } from '../Toast'

/** What the Main page is showing: everything, one project, one sheet, or what is in no sheet. */
export type Scope = { kind: 'all' } | { kind: 'unfiled' } | { kind: 'project'; id: number } | { kind: 'sheet'; id: number }

export function readScope(search: URLSearchParams): Scope {
  const sheet = Number(search.get('sheet'))
  if (sheet) return { kind: 'sheet', id: sheet }
  const project = Number(search.get('project'))
  if (project) return { kind: 'project', id: project }
  return search.get('unfiled') ? { kind: 'unfiled' } : { kind: 'all' }
}

/** The scope as URL parameters, so a sheet can be bookmarked and the back button works. */
export function scopeSearch(scope: Scope): Record<string, string | null> {
  return {
    sheet: scope.kind === 'sheet' ? String(scope.id) : null,
    project: scope.kind === 'project' ? String(scope.id) : null,
    unfiled: scope.kind === 'unfiled' ? '1' : null,
  }
}

export function scopeParams(scope: Scope): Params {
  switch (scope.kind) {
    case 'sheet': return { sheetId: scope.id }
    case 'project': return { workbookId: scope.id }
    case 'unfiled': return { unfiled: true }
    default: return {}
  }
}

type Dialog =
  | { kind: 'newProject' }
  | { kind: 'newSheet'; workbookId: number | null }
  | { kind: 'renameProject' | 'deleteProject' | 'mergeProject'; project: Workbook }
  | { kind: 'renameSheet' | 'deleteSheet' | 'mergeSheet' | 'moveSheet'; sheet: WorkSheet }

/** Projects (one per Excel file, usually) with their sheets, like the tabs along the bottom of a workbook. */
export function WorkspaceTreeNav({ tree, scope, onSelect }: { tree: WorkspaceTree; scope: Scope; onSelect: (scope: Scope) => void }) {
  const { t } = useI18n()
  const { isSupervisor } = useAuth()
  const { canEdit } = useMode()
  const [collapsed, setCollapsed] = useState<Set<number>>(() => new Set())
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const editable = canEdit()
  const manage = editable && isSupervisor

  const toggle = (id: number) => setCollapsed((set) => {
    const next = new Set(set)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })

  const sheetRow = (sheet: WorkSheet, indent: boolean) => (
    <TreeRow
      key={sheet.id}
      indent={indent}
      icon={<FileSpreadsheet className="size-4 text-emerald-600" />}
      label={sheet.name}
      count={sheet.businesses}
      active={scope.kind === 'sheet' && scope.id === sheet.id}
      onClick={() => onSelect({ kind: 'sheet', id: sheet.id })}
      menu={editable ? [
        { label: t('common.rename'), onClick: () => setDialog({ kind: 'renameSheet', sheet }) },
        { label: t('workspace.moveSheet'), onClick: () => setDialog({ kind: 'moveSheet', sheet }) },
        ...(manage ? [
          { label: t('workspace.mergeSheet'), onClick: () => setDialog({ kind: 'mergeSheet', sheet }) },
          { label: t('workspace.deleteSheet'), onClick: () => setDialog({ kind: 'deleteSheet', sheet }), danger: true },
        ] : []),
      ] : undefined}
    />
  )

  return (
    <>
      <nav className="space-y-0.5 text-sm">
        <TreeRow icon={<Layers className="size-4 text-brand-600" />} label={t('workspace.all')} count={tree.total} active={scope.kind === 'all'} onClick={() => onSelect({ kind: 'all' })} />

        <div className="flex items-center justify-between px-2 pb-1 pt-3">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">{t('workspace.projects')}</span>
          {editable && (
            <button type="button" className="grid size-6 place-items-center rounded-md text-brand-700 hover:bg-brand-50" title={t('workspace.newProject')} onClick={() => setDialog({ kind: 'newProject' })}>
              <Plus className="size-4" />
            </button>
          )}
        </div>

        {tree.workbooks.map((project) => {
          const open = !collapsed.has(project.id)
          return (
            <div key={project.id}>
              <TreeRow
                icon={
                  <span className="flex items-center" onClick={(event) => { event.stopPropagation(); toggle(project.id) }}>
                    {open ? <ChevronDown className="size-3.5 text-muted" /> : <ChevronRight className="size-3.5 text-muted" />}
                    {open ? <FolderOpen className="ml-0.5 size-4 text-amber-500" /> : <Folder className="ml-0.5 size-4 text-amber-500" />}
                  </span>
                }
                label={project.name}
                count={project.businesses}
                active={scope.kind === 'project' && scope.id === project.id}
                onClick={() => onSelect({ kind: 'project', id: project.id })}
                menu={editable ? [
                  { label: t('workspace.newSheet'), onClick: () => setDialog({ kind: 'newSheet', workbookId: project.id }) },
                  { label: t('common.rename'), onClick: () => setDialog({ kind: 'renameProject', project }) },
                  ...(manage ? [
                    { label: t('workspace.mergeProject'), onClick: () => setDialog({ kind: 'mergeProject', project }) },
                    { label: t('workspace.deleteProject'), onClick: () => setDialog({ kind: 'deleteProject', project }), danger: true },
                  ] : []),
                ] : undefined}
              />
              {open && project.sheets.map((sheet) => sheetRow(sheet, true))}
            </div>
          )
        })}

        {tree.looseSheets.length > 0 && (
          <>
            <div className="px-2 pb-1 pt-3 text-xs font-semibold uppercase tracking-wide text-muted">{t('workspace.looseSheets')}</div>
            {tree.looseSheets.map((sheet) => sheetRow(sheet, false))}
          </>
        )}

        <div className="pt-2">
          <TreeRow icon={<Inbox className="size-4 text-muted" />} label={t('workspace.unfiled')} count={tree.unfiled} active={scope.kind === 'unfiled'} onClick={() => onSelect({ kind: 'unfiled' })} />
        </div>
      </nav>

      {dialog && <TreeDialog dialog={dialog} tree={tree} onClose={() => setDialog(null)} scope={scope} onSelect={onSelect} />}
    </>
  )
}

function TreeRow({ icon, label, count, active, onClick, indent, menu }: {
  icon: ReactNode; label: string; count: number; active: boolean; onClick: () => void; indent?: boolean
  menu?: { label: string; onClick: () => void; danger?: boolean }[]
}) {
  return (
    <div className={`group flex items-center rounded-lg ${indent ? 'ml-5' : ''} ${active ? 'bg-brand-50 text-brand-700' : 'text-ink/80 hover:bg-canvas'}`}>
      <button type="button" onClick={onClick} className="flex min-w-0 flex-1 items-center gap-2 px-2 py-1.5 text-left">
        {icon}
        <span className="min-w-0 flex-1 truncate font-medium">{label}</span>
        <span className="text-xs tabular-nums text-muted">{count}</span>
      </button>
      {menu && menu.length > 0 && <RowMenu items={menu} />}
    </div>
  )
}

function RowMenu({ items }: { items: { label: string; onClick: () => void; danger?: boolean }[] }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])
  return (
    <div className="relative" ref={ref}>
      <button type="button" onClick={() => setOpen(!open)} className={`mr-1 grid size-6 place-items-center rounded-md text-muted hover:bg-ink/5 ${open ? '' : 'sm:opacity-0 sm:group-hover:opacity-100'}`} aria-label="menu">
        <Ellipsis className="size-4" />
      </button>
      {open && (
        <div className="absolute right-0 top-full z-30 mt-1 w-52 rounded-xl border border-line bg-surface p-1 shadow-xl">
          {items.map((item) => (
            <button key={item.label} type="button" onClick={() => { setOpen(false); item.onClick() }}
              className={`block w-full rounded-lg px-3 py-1.5 text-left text-sm ${item.danger ? 'text-rose-700 hover:bg-rose-50' : 'hover:bg-canvas'}`}>
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function TreeDialog({ dialog, tree, onClose, scope, onSelect }: {
  dialog: Dialog; tree: WorkspaceTree; onClose: () => void; scope: Scope; onSelect: (scope: Scope) => void
}) {
  const { t } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  const initialName = dialog.kind === 'renameProject' ? dialog.project.name : dialog.kind === 'renameSheet' ? dialog.sheet.name : ''
  const [name, setName] = useState(initialName)
  const [target, setTarget] = useState('')
  const [withSheets, setWithSheets] = useState(false)
  const [busy, setBusy] = useState(false)

  const allSheets = [...tree.workbooks.flatMap((w) => w.sheets), ...tree.looseSheets]
  const projectName = (id: number | null) => tree.workbooks.find((w) => w.id === id)?.name ?? t('workspace.noProject')

  const run = async () => {
    setBusy(true)
    try {
      switch (dialog.kind) {
        case 'newProject': {
          const created = await api.post<Workbook>('/workbooks', { name })
          onSelect({ kind: 'project', id: created.id })
          break
        }
        case 'newSheet': {
          const created = await api.post<WorkSheet>('/sheets', { name, workbookId: dialog.workbookId })
          onSelect({ kind: 'sheet', id: created.id })
          break
        }
        case 'renameProject': {
          const p = dialog.project
          await api.put(`/workbooks/${p.id}`, { name, description: p.description, color: p.color, sortOrder: p.sortOrder })
          break
        }
        case 'renameSheet':
        case 'moveSheet': {
          const s = dialog.sheet
          const workbookId = dialog.kind === 'moveSheet' ? (target ? Number(target) : null) : s.workbookId
          await api.put(`/sheets/${s.id}`, { name: dialog.kind === 'renameSheet' ? name : s.name, workbookId, sortOrder: s.sortOrder, color: s.color })
          break
        }
        case 'deleteProject':
          await api.del(`/workbooks/${dialog.project.id}`, { deleteSheets: withSheets })
          if (scope.kind === 'project' && scope.id === dialog.project.id) onSelect({ kind: 'all' })
          break
        case 'deleteSheet':
          await api.del(`/sheets/${dialog.sheet.id}`)
          if (scope.kind === 'sheet' && scope.id === dialog.sheet.id) onSelect({ kind: 'all' })
          break
        case 'mergeProject':
          await api.post(`/workbooks/${dialog.project.id}/merge-into/${target}`)
          onSelect({ kind: 'project', id: Number(target) })
          break
        case 'mergeSheet':
          await api.post(`/sheets/${dialog.sheet.id}/merge-into/${target}`)
          onSelect({ kind: 'sheet', id: Number(target) })
          break
      }
      await Promise.all(['workspace', 'grid', 'lookups', 'businesses', 'business'].map((key) => client.invalidateQueries({ queryKey: [key] })))
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }

  const title = {
    newProject: t('workspace.newProject'), newSheet: t('workspace.newSheet'), renameProject: t('common.rename'), renameSheet: t('common.rename'),
    deleteProject: t('workspace.deleteProject'), deleteSheet: t('workspace.deleteSheet'), mergeProject: t('workspace.mergeProject'),
    mergeSheet: t('workspace.mergeSheet'), moveSheet: t('workspace.moveSheet'),
  }[dialog.kind]
  const needsName = dialog.kind === 'newProject' || dialog.kind === 'newSheet' || dialog.kind === 'renameProject' || dialog.kind === 'renameSheet'
  const needsTarget = dialog.kind === 'mergeProject' || dialog.kind === 'mergeSheet'
  const danger = dialog.kind === 'deleteProject' || dialog.kind === 'deleteSheet'

  return (
    <Modal
      open
      onClose={onClose}
      title={title}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className={danger ? 'btn-danger' : 'btn-primary'} disabled={busy || (needsName && !name.trim()) || (needsTarget && !target)} onClick={() => void run()}>
            {busy && <Spinner className="size-4" />} {danger ? t('common.delete') : needsTarget ? t('common.merge') : t('common.save')}
          </button>
        </>
      }
    >
      <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); void run() }}>
        {needsName && (
          <Field label={dialog.kind.includes('Project') ? t('workspace.projectName') : t('workspace.sheetName')}>
            <input className="input" autoFocus maxLength={dialog.kind.includes('Project') ? 120 : 80} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
        )}
        {dialog.kind === 'deleteProject' && (
          <>
            <p className="text-sm">{dialog.project.name} - {t('workspace.deleteProjectHint')}</p>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="size-4 accent-brand-600" checked={withSheets} onChange={(e) => setWithSheets(e.target.checked)} /> {t('workspace.deleteWithSheets')}
            </label>
          </>
        )}
        {dialog.kind === 'deleteSheet' && <p className="text-sm">{t('workspace.deleteSheetHint', { name: dialog.sheet.name })}</p>}
        {dialog.kind === 'mergeProject' && (
          <>
            <p className="text-sm text-muted">{t('workspace.mergeProjectHint', { name: dialog.project.name })}</p>
            <Field label={t('workspace.mergeTarget')}>
              <select className="input" value={target} onChange={(e) => setTarget(e.target.value)}>
                <option value="">{t('common.select')}</option>
                {tree.workbooks.filter((w) => w.id !== dialog.project.id).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </Field>
          </>
        )}
        {dialog.kind === 'mergeSheet' && (
          <>
            <p className="text-sm text-muted">{t('workspace.mergeSheetHint', { name: dialog.sheet.name })}</p>
            <Field label={t('workspace.mergeTarget')}>
              <select className="input" value={target} onChange={(e) => setTarget(e.target.value)}>
                <option value="">{t('common.select')}</option>
                {allSheets.filter((s) => s.id !== dialog.sheet.id).map((s) => <option key={s.id} value={s.id}>{projectName(s.workbookId)} / {s.name}</option>)}
              </select>
            </Field>
          </>
        )}
        {dialog.kind === 'moveSheet' && (
          <Field label={t('workspace.mergeTarget')}>
            <select className="input" value={target} onChange={(e) => setTarget(e.target.value)}>
              <option value="">{t('workspace.noProject')}</option>
              {tree.workbooks.filter((w) => w.id !== dialog.sheet.workbookId).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </Field>
        )}
      </form>
    </Modal>
  )
}
