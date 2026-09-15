import { useEffect, type ReactNode } from 'react'
import { CircleAlert, LoaderCircle, X } from 'lucide-react'
import type { BusinessStatus, Priority } from '../lib/types'
import { useI18n } from '../i18n'
import { errorText } from './Toast'

export function Spinner({ className = 'size-5' }: { className?: string }) {
  return <LoaderCircle className={`animate-spin ${className}`} aria-hidden />
}

/** A bottom sheet on phones, a centred dialog on wider screens. Escape and a click outside close it. */
export function Modal({
  open, onClose, title, children, footer, wide,
}: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previous
    }
  }, [open, onClose])

  if (!open) return null
  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 backdrop-blur-[2px] sm:items-center sm:p-4"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        data-modal=""
        className={`flex max-h-[94dvh] w-full flex-col rounded-t-3xl bg-surface shadow-2xl sm:rounded-2xl ${wide ? 'sm:max-w-3xl' : 'sm:max-w-lg'}`}
      >
        <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-3.5">
          <h2 className="min-w-0 truncate text-base font-semibold">{title}</h2>
          <button type="button" className="btn-ghost -mr-2 p-2" onClick={onClose} aria-label="close">
            <X className="size-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-line px-5 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * A panel that slides in from the right over the page, full width on phones: a business's whole file
 * opened from a list without losing the list's place. Escape or a click on the dimmed page closes it.
 */
export function Drawer({
  open, onClose, title, actions, children,
}: { open: boolean; onClose: () => void; title: ReactNode; actions?: ReactNode; children: ReactNode }) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      // A dialog opened from inside the drawer handles its own Escape first.
      if (event.key === 'Escape' && !document.querySelector('[role="dialog"][data-modal]')) onClose()
    }
    document.addEventListener('keydown', onKey)
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previous
    }
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-black/40" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" className="drawer-in flex h-full w-full max-w-5xl flex-col bg-canvas shadow-2xl">
        <div className="flex items-center gap-2 border-b border-line bg-surface px-4 py-2.5">
          <h2 className="min-w-0 flex-1 truncate text-base font-semibold">{title}</h2>
          {actions}
          <button type="button" className="btn-ghost -mr-2 p-2" onClick={onClose} aria-label="close">
            <X className="size-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto overscroll-contain p-3 sm:p-4">{children}</div>
      </div>
    </div>
  )
}

export function Field({ label, hint, error, children, className = '' }: {
  label?: ReactNode; hint?: ReactNode; error?: string | null; children: ReactNode; className?: string
}) {
  return (
    <label className={`block ${className}`}>
      {label && <span className="label">{label}</span>}
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-muted">{hint}</span>}
      {error && <span className="mt-1 block text-xs text-rose-600">{error}</span>}
    </label>
  )
}

export function EmptyState({ icon, title, children }: { icon?: ReactNode; title: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-4 py-10 text-center text-muted">
      {icon && <div className="text-brand-300">{icon}</div>}
      <p className="text-sm font-medium text-ink/70">{title}</p>
      {children}
    </div>
  )
}

export const STATUS_STYLE: Record<BusinessStatus, { badge: string; dot: string }> = {
  NEW: { badge: 'bg-slate-100 text-slate-700', dot: 'bg-slate-400' },
  CONTACTED: { badge: 'bg-sky-100 text-sky-800', dot: 'bg-sky-500' },
  INTERESTED: { badge: 'bg-violet-100 text-violet-800', dot: 'bg-violet-500' },
  MEETING: { badge: 'bg-amber-100 text-amber-800', dot: 'bg-amber-500' },
  TESTING: { badge: 'bg-orange-100 text-orange-800', dot: 'bg-orange-500' },
  NEGOTIATION: { badge: 'bg-indigo-100 text-indigo-800', dot: 'bg-indigo-500' },
  CUSTOMER: { badge: 'bg-emerald-100 text-emerald-800', dot: 'bg-emerald-500' },
  REPEAT_CUSTOMER: { badge: 'bg-emerald-600 text-white', dot: 'bg-emerald-700' },
  FOLLOW_UP_LATER: { badge: 'bg-stone-100 text-stone-700', dot: 'bg-stone-400' },
  NOT_INTERESTED: { badge: 'bg-rose-100 text-rose-800', dot: 'bg-rose-500' },
  LOST: { badge: 'bg-red-200 text-red-900', dot: 'bg-red-700' },
}

export function StatusBadge({ status, className = '' }: { status: BusinessStatus; className?: string }) {
  const { t } = useI18n()
  return (
    <span className={`inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status].badge} ${className}`}>
      {t(`status.${status}`)}
    </span>
  )
}

export function PriorityMark({ priority }: { priority: Priority }) {
  const { t } = useI18n()
  if (priority === 'NORMAL') return null
  return (
    <span
      title={t(`priority.${priority}`)}
      className={`inline-block size-2 shrink-0 rounded-full ${priority === 'HIGH' ? 'bg-raspberry' : 'bg-slate-300'}`}
    />
  )
}

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="no-print flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Tabs<T extends string>({ tabs, value, onChange }: {
  tabs: { key: T; label: ReactNode; count?: number }[]; value: T; onChange: (key: T) => void
}) {
  return (
    <div className="no-print -mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <div className="flex min-w-max gap-1 border-b border-line">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(tab.key)}
            className={`-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium transition ${
              value === tab.key ? 'border-brand-600 text-brand-700' : 'border-transparent text-muted hover:text-ink'
            }`}
          >
            {tab.label}
            {tab.count !== undefined && tab.count > 0 && (
              <span className={`rounded-full px-1.5 text-xs ${value === tab.key ? 'bg-brand-100 text-brand-700' : 'bg-canvas text-muted'}`}>{tab.count}</span>
            )}
          </button>
        ))}
      </div>
    </div>
  )
}

/** A row of mutually exclusive buttons, big enough to hit with a thumb. */
export function Choice<T extends string>({ options, value, onChange, size = 'md' }: {
  options: { value: T; label: ReactNode; tone?: string }[]; value: T | null; onChange: (value: T) => void; size?: 'sm' | 'md'
}) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((option) => {
        const on = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            onClick={() => onChange(option.value)}
            className={`rounded-xl border font-medium transition ${size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3 py-2 text-sm'} ${
              on ? option.tone ?? 'border-brand-600 bg-brand-600 text-white' : 'border-line bg-surface text-ink hover:border-brand-300'
            }`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}

export function ErrorBlock({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n()
  return (
    <div className="card flex items-center gap-3 border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
      <CircleAlert className="size-5 shrink-0" />
      <span className="flex-1">{errorText(error, t)}</span>
      {onRetry && (
        <button type="button" className="btn-secondary" onClick={onRetry}>
          {t('common.retry')}
        </button>
      )}
    </div>
  )
}

export function Stat({ label, value, sub, tone = 'text-ink' }: { label: ReactNode; value: ReactNode; sub?: ReactNode; tone?: string }) {
  return (
    <div className="card p-3.5">
      <div className="text-xs text-muted">{label}</div>
      <div className={`mt-1 text-2xl font-semibold tabular-nums ${tone}`}>{value}</div>
      {sub && <div className="mt-0.5 text-xs text-muted">{sub}</div>}
    </div>
  )
}

export function Loading() {
  return (
    <div className="grid place-items-center py-16">
      <Spinner className="size-7 text-brand-500" />
    </div>
  )
}
