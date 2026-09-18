import { Suspense, useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  ArrowDownUp, Bell, CalendarDays, CalendarPlus, ChartColumn, Clock, Eye, EyeOff, FlaskConical, House, KeyRound, LayoutGrid, ListChecks, LogOut, Menu, Monitor, Moon,
  Package, PenLine, Phone, Plus, Search, Settings, ShieldCheck, SquareKanban, StickyNote, Store, Sun, X,
} from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useMode } from '../lib/mode'
import { pushOnHere, resyncPush, watchResubscribe } from '../lib/push'
import { useTheme, type ThemeChoice } from '../lib/theme'
import { useDashboard } from '../lib/queries'
import { tap, useIsPhone } from '../lib/mobile'
import { TaskDialog } from './TaskDialog'
import { RemindersDialog } from './RemindersDialog'
import { BackupReminder } from './BackupReminder'
import { fmtTime } from '../lib/format'
import type { NoteDto, TaskDto } from '../lib/types'
import { useI18n } from '../i18n'
import { GlobalSearch } from './GlobalSearch'
import { PullToRefresh } from './PullToRefresh'
import { QuickAddDialog } from './QuickAddDialog'
import { NoteDialog } from './NoteDialog'
import { Field, Loading, Modal, Spinner } from './ui'
import { useToast } from './Toast'

const NAV = [
  { to: '/main', key: 'main', icon: LayoutGrid },
  { to: '/', key: 'today', icon: House, end: true },
  { to: '/calls', key: 'calls', icon: Phone },
  { to: '/businesses', key: 'businesses', icon: Store },
  { to: '/pipeline', key: 'pipeline', icon: SquareKanban },
  { to: '/flavors', key: 'flavors', icon: FlaskConical },
  { to: '/calendar', key: 'calendar', icon: CalendarDays },
  { to: '/tasks', key: 'tasks', icon: ListChecks },
  { to: '/notes', key: 'notes', icon: StickyNote },
  { to: '/history', key: 'history', icon: Clock },
  { to: '/products', key: 'products', icon: Package },
  { to: '/reports', key: 'reports', icon: ChartColumn },
  { to: '/import', key: 'import', icon: ArrowDownUp },
]
const MOBILE_MAIN = ['main', 'today', 'calls', 'calendar']
const THEME_NEXT: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' }

export function Layout() {
  const { t, lang, setLang } = useI18n()
  const { user, isAdmin, isRoot, logout } = useAuth()
  const theme = useTheme()
  const ThemeIcon = theme.choice === 'system' ? Monitor : theme.choice === 'dark' ? Moon : Sun
  const { observe, setObserve } = useMode()
  const location = useLocation()
  const [searchOpen, setSearchOpen] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const [moreOpen, setMoreOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [remindersOpen, setRemindersOpen] = useState(false)
  const [actionsOpen, setActionsOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)
  const phone = useIsPhone()

  // What is still waiting today, as a number on the bottom bar: the reason to open the app at all.
  const dashboard = useDashboard('mine', phone)
  const waiting = [...(dashboard.data?.overdue ?? []), ...(dashboard.data?.today ?? [])]
    .filter((task) => task.status === 'OPEN').length

  useReminders(user?.id ?? null)

  // A device with notifications on re-registers on every start: new sign-in, new language, renewed address.
  const language = useRef(lang)
  language.current = lang
  useEffect(() => {
    if (user?.id) void resyncPush(lang)
  }, [user?.id, lang])

  // ...and again the moment the browser hands the service worker a new push address, which is what
  // Android does on its own schedule.
  useEffect(() => watchResubscribe(() => language.current), [])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setSearchOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    setMoreOpen(false)
    setMenuOpen(false)
    setActionsOpen(false)
  }, [location.pathname])

  const nav = [
    ...NAV,
    ...(isAdmin ? [{ to: '/admin', key: 'admin', icon: Settings }] : []),
    ...(isRoot ? [{ to: '/security', key: 'security', icon: ShieldCheck }] : []),
  ]
  // The Main page's spreadsheet wants every pixel of width; the other screens read better narrower.
  const wide = location.pathname.startsWith('/main')

  return (
    <div className="min-h-dvh md:flex">
      {/* Sidebar on tablets and computers */}
      <aside className="no-print sticky top-0 hidden h-dvh w-60 shrink-0 flex-col border-r border-line bg-surface md:flex">
        <div className="flex items-center gap-2.5 px-5 py-5">
          <Logo />
          <div>
            <div className="text-sm font-semibold leading-tight">ANDANERI</div>
            <div className="text-xs text-muted">{t('app.tagline')}</div>
          </div>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-3">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-brand-50 text-brand-700' : 'text-ink/75 hover:bg-canvas hover:text-ink'
                }`
              }
            >
              <item.icon className="size-[18px]" />
              {t(`nav.${item.key}`)}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-line p-3 text-xs text-muted">
          {user?.fullName} · {t(`role.${user?.role ?? 'SALES'}`)}
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="no-print sticky top-0 z-30 border-b border-line bg-surface/90 backdrop-blur">
          <div className="flex items-center gap-2 px-4 py-2.5 sm:px-6">
            <div className="flex items-center gap-2 md:hidden">
              <Logo small />
            </div>
            <button
              type="button"
              onClick={() => setSearchOpen(true)}
              className="flex min-w-0 flex-1 items-center gap-2 rounded-xl border border-line bg-canvas px-3 py-2 text-left text-sm text-muted hover:border-brand-300 md:max-w-md"
            >
              <Search className="size-4 shrink-0" />
              <span className="truncate">{t('nav.search')}</span>
            </button>
            <div className="ml-auto flex items-center gap-1">
              {!observe && (
                <button type="button" className="btn-primary px-2.5 sm:px-3.5" onClick={() => setAddOpen(true)} title={t('business.new')}>
                  <Plus className="size-4" /> <span className="hidden sm:inline">{t('business.new')}</span>
                </button>
              )}
              <button
                type="button"
                className={`btn-ghost p-2 ${observe ? 'bg-amber-100 text-amber-800' : ''}`}
                onClick={() => setObserve(!observe)}
                title={t('common.observeMode')}
              >
                {observe ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
              </button>
              <button
                type="button"
                className="btn-ghost hidden p-2 sm:inline-flex"
                onClick={() => theme.setChoice(THEME_NEXT[theme.choice])}
                title={`${t('common.theme')}: ${t(`common.theme_${theme.choice}`)}`}
              >
                <ThemeIcon className="size-5" />
              </button>
              <button type="button" className="btn-ghost px-2 py-2 text-xs font-semibold" onClick={() => setLang(lang === 'ka' ? 'en' : 'ka')}>
                {lang === 'ka' ? 'EN' : 'ქა'}
              </button>
              <UserMenu
                open={menuOpen}
                setOpen={setMenuOpen}
                onPassword={() => setPasswordOpen(true)}
                onReminders={() => setRemindersOpen(true)}
                onLogout={logout}
                name={user?.fullName ?? ''}
              />
            </div>
          </div>
          {observe && (
            <div className="flex items-center justify-center gap-3 bg-amber-100 px-4 py-1.5 text-xs text-amber-900">
              <Eye className="size-3.5" /> {t('common.observeBanner')}
              <button type="button" className="font-semibold underline" onClick={() => setObserve(false)}>{t('common.observeOff')}</button>
            </div>
          )}
          <BackupReminder />
        </header>

        <main className={`mx-auto w-full px-4 pb-[calc(7rem+env(safe-area-inset-bottom))] pt-4 sm:px-6 md:pb-10 ${wide ? 'max-w-none' : 'max-w-7xl'}`}>
          <Suspense fallback={<Loading />}>
            <PullToRefresh>
              <Outlet />
            </PullToRefresh>
          </Suspense>
        </main>
      </div>

      {/* Start something, one tap from anywhere */}
      {!observe && (
        <button
          type="button"
          onClick={() => { tap(); setActionsOpen(true) }}
          className="no-print fixed bottom-[calc(4.75rem+env(safe-area-inset-bottom))] right-4 z-30 grid size-14 place-items-center rounded-full bg-brand-600 text-white shadow-lg transition active:scale-95 hover:brightness-110 md:bottom-6 md:right-6 md:size-12"
          title={t('nav.start')}
        >
          {actionsOpen ? <X className="size-6" /> : <Plus className="size-6" />}
        </button>
      )}

      {/* Bottom bar on phones */}
      <nav className="no-print fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 backdrop-blur pb-[env(safe-area-inset-bottom)] md:hidden">
        <div className="grid grid-cols-5">
          {nav.filter((item) => MOBILE_MAIN.includes(item.key)).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => tap(8)}
              className={({ isActive }) => `relative flex min-w-0 flex-col items-center gap-0.5 px-0.5 pb-1.5 pt-2 text-[10px] font-medium transition active:scale-95 sm:text-[11px] ${isActive ? 'text-brand-700' : 'text-muted'}`}
            >
              {({ isActive }) => (
                <>
                  <span className={`absolute inset-x-3 top-0 h-0.5 rounded-full ${isActive ? 'bg-brand-600' : 'bg-transparent'}`} />
                  <span className="relative">
                    <item.icon className="size-6 shrink-0" />
                    {item.key === 'calls' && waiting > 0 && (
                      <span className="absolute -right-2 -top-1 grid min-w-4 place-items-center rounded-full bg-raspberry px-1 text-[10px] font-bold leading-4 text-white">{waiting}</span>
                    )}
                  </span>
                  <span className="w-full truncate text-center">{t(`nav.${item.key}`)}</span>
                </>
              )}
            </NavLink>
          ))}
          <button type="button" onClick={() => { tap(8); setMoreOpen(true) }} className="flex min-w-0 flex-col items-center gap-0.5 px-0.5 pb-1.5 pt-2 text-[10px] font-medium text-muted transition active:scale-95 sm:text-[11px]">
            <Menu className="size-6 shrink-0" />
            <span className="w-full truncate text-center">{t('nav.more')}</span>
          </button>
        </div>
      </nav>

      <Modal open={moreOpen} onClose={() => setMoreOpen(false)} title={t('nav.more')}>
        <div className="grid grid-cols-3 gap-2">
          {nav.filter((item) => !MOBILE_MAIN.includes(item.key)).map((item) => (
            <NavLink key={item.to} to={item.to} className="pressable flex min-h-24 flex-col items-center justify-center gap-2 rounded-2xl border border-line p-3 text-center text-xs font-medium">
              <item.icon className="size-7 text-brand-600" />
              {t(`nav.${item.key}`)}
            </NavLink>
          ))}
        </div>
      </Modal>

      {/* What the round button opens: the four things a day is made of. */}
      <Modal open={actionsOpen} onClose={() => setActionsOpen(false)} title={t('nav.start')}>
        <div className="grid grid-cols-2 gap-2.5">
          {[
            { key: 'note', icon: PenLine, run: () => setNoteOpen(true) },
            { key: 'business', icon: Store, run: () => setAddOpen(true) },
            { key: 'task', icon: CalendarPlus, run: () => setTaskOpen(true) },
            { key: 'call', icon: Phone, to: '/calls' },
          ].map((action) => {
            const inside = (
              <>
                <action.icon className="size-7 text-brand-600" />
                <span className="text-sm font-medium">{t(`start.${action.key}`)}</span>
              </>
            )
            const className = 'pressable flex min-h-28 flex-col items-center justify-center gap-2 rounded-2xl border border-line bg-surface p-4 text-center'
            return action.to ? (
              <NavLink key={action.key} to={action.to} className={className} onClick={() => setActionsOpen(false)}>{inside}</NavLink>
            ) : (
              <button key={action.key} type="button" className={className} onClick={() => { setActionsOpen(false); action.run?.() }}>{inside}</button>
            )
          })}
        </div>
      </Modal>
      <TaskDialog open={taskOpen} onClose={() => setTaskOpen(false)} />

      <GlobalSearch open={searchOpen} onClose={() => setSearchOpen(false)} />
      <QuickAddDialog open={addOpen} onClose={() => setAddOpen(false)} />
      <NoteDialog open={noteOpen} onClose={() => setNoteOpen(false)} />
      <PasswordDialog open={passwordOpen} onClose={() => setPasswordOpen(false)} />
      <RemindersDialog open={remindersOpen} onClose={() => setRemindersOpen(false)} />
    </div>
  )
}

function Logo({ small }: { small?: boolean }) {
  return (
    <div className={`grid place-items-center rounded-xl bg-brand-600 text-white ${small ? 'size-8' : 'size-9'}`}>
      <svg viewBox="0 0 64 64" className={small ? 'size-5' : 'size-6'} aria-hidden>
        <path d="M22 50c-6 0-10-4-10-8 0-3 2-6 5-8l12-24h6l12 24c3 2 5 5 5 8 0 4-4 8-10 8-3 0-5-1-7-3-2 2-4 3-6 3-2 0-4-1-5-2-1 1-1 2-2 2z" fill="currentColor" />
      </svg>
    </div>
  )
}

function UserMenu({ open, setOpen, onPassword, onReminders, onLogout, name }: {
  open: boolean; setOpen: (open: boolean) => void; onPassword: () => void; onReminders: () => void; onLogout: () => void; name: string
}) {
  const { t } = useI18n()
  const theme = useTheme()
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open, setOpen])

  return (
    <div className="relative" ref={ref}>
      <button type="button" className="grid size-9 place-items-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700" onClick={() => setOpen(!open)}>
        {name.slice(0, 1).toUpperCase() || '?'}
      </button>
      {open && (
        <div className="absolute right-0 top-full z-40 mt-2 w-60 rounded-2xl border border-line bg-surface p-1.5 shadow-xl">
          <div className="px-3 py-2 text-sm font-medium">{name}</div>
          <button type="button" className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm hover:bg-canvas" onClick={() => { setOpen(false); onReminders() }}>
            <Bell className="size-4" /> {t('notify.title')}
          </button>
          <NavLink to="/history" className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm hover:bg-canvas" onClick={() => setOpen(false)}>
            <Clock className="size-4" /> {t('nav.history')}
          </NavLink>
          {/* The header has no room for the theme switch on a phone, so it lives here too. */}
          <div className="flex items-center gap-1 px-2 py-1.5">
            {([['light', Sun], ['dark', Moon], ['system', Monitor]] as const).map(([choice, Icon]) => (
              <button key={choice} type="button" onClick={() => theme.setChoice(choice)} title={t(`common.theme_${choice}`)}
                className={`flex flex-1 items-center justify-center gap-1 rounded-lg px-2 py-1.5 text-xs ${theme.choice === choice ? 'bg-brand-600 text-white' : 'text-muted hover:bg-canvas'}`}>
                <Icon className="size-3.5" />
              </button>
            ))}
          </div>
          <button type="button" className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm hover:bg-canvas" onClick={() => { setOpen(false); onPassword() }}>
            <KeyRound className="size-4" /> {t('common.changePassword')}
          </button>
          <button type="button" className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-sm text-rose-700 hover:bg-rose-50" onClick={onLogout}>
            <LogOut className="size-4" /> {t('common.logout')}
          </button>
        </div>
      )}
    </div>
  )
}

function PasswordDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setCurrent('')
      setNext('')
    }
  }, [open])

  const save = async () => {
    setSaving(true)
    try {
      await api.post('/auth/password', { currentPassword: current, newPassword: next })
      toast.ok(t('auth.passwordChanged'))
      onClose()
    } catch (error) {
      toast.error(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('common.changePassword')}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button type="button" className="btn-primary" disabled={saving || next.length < 8 || !current} onClick={() => void save()}>
            {saving && <Spinner className="size-4" />} {t('common.save')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label={t('auth.currentPassword')}>
          <input type="password" className="input" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label={t('auth.newPassword')}>
          <input type="password" className="input" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
      </div>
    </Modal>
  )
}

/**
 * While the CRM is open, checks every minute for tasks starting in the next 15 minutes and notes
 * whose reminder time has come, and says so once each: on screen, and as a browser notification
 * when that was allowed. A closed tab reminds nobody; that needs push, which is not built yet.
 */
function useReminders(userId: number | null) {
  const { t } = useI18n()
  const toast = useToast()
  const translate = useRef(t)
  translate.current = t

  useEffect(() => {
    if (!userId) return
    const seen = new Set<string>(readSeen())
    const say = (text: string) => {
      toast.ok(text)
      // With push on, the service worker already shows it (even with the tab closed): no second pop-up.
      if ('Notification' in window && Notification.permission === 'granted' && !pushOnHere()) {
        try {
          new Notification('Andaneri CRM', { body: text })
        } catch {
          /* some mobile browsers only allow notifications from a service worker */
        }
      }
    }
    const check = async () => {
      const now = new Date()
      const soon = new Date(now.getTime() + 15 * 60_000)
      try {
        const tasks = await api.get<TaskDto[]>('/tasks', { from: now.toISOString(), to: soon.toISOString(), userId, status: 'OPEN' })
        for (const task of tasks) {
          const key = `t${task.id}@${task.dueAt}`
          if (seen.has(key)) continue
          seen.add(key)
          say([task.businessName ?? task.title ?? '', `${fmtTime(task.dueAt)} · ${translate.current(`taskType.${task.type}`)}`, task.contact?.name]
            .filter(Boolean).join(' · '))
        }
        const notes = await api.get<NoteDto[]>('/notes')
        for (const note of notes) {
          if (note.done || !note.remindAt || new Date(note.remindAt) > now) continue
          const key = `n${note.id}@${note.remindAt}`
          if (seen.has(key)) continue
          seen.add(key)
          say(translate.current('notes.reminderDue', { body: note.body.slice(0, 80) }))
        }
        writeSeen([...seen])
      } catch {
        /* offline for a moment; try again next minute */
      }
    }
    void check()
    const timer = setInterval(() => void check(), 60_000)
    return () => clearInterval(timer)
  }, [userId, toast])
}

function readSeen(): string[] {
  try {
    return JSON.parse(sessionStorage.getItem('andaneri.reminded') ?? '[]') as string[]
  } catch {
    return []
  }
}

function writeSeen(keys: string[]) {
  try {
    sessionStorage.setItem('andaneri.reminded', JSON.stringify(keys.slice(-200)))
  } catch {
    /* ignore */
  }
}
