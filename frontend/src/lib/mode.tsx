import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

// Two ways of looking without touching.
//
// Observation mode: browse everything with every edit control hidden, so nothing changes by accident.
// It is a per-device preference, not a permission; the server's own rules still apply underneath.
//
// Someone else's view: a supervisor picks a colleague and every screen answers as if that colleague had
// signed in - their day, their calendar, their tasks, their notes. Editing is off the whole time, on
// purpose: it is a window, not a seat at their desk.

const STORAGE_KEY = 'andaneri.observe'
const VIEW_KEY = 'andaneri.viewAs'

/** The colleague being looked at, if any. */
export interface ViewAs {
  id: number
  fullName: string
}

interface Mode {
  observe: boolean
  setObserve: (on: boolean) => void
  /** Whose screens are being shown, or null for one's own. */
  viewAs: ViewAs | null
  setViewAs: (who: ViewAs | null) => void
  /** The person whose data a screen should ask for: the colleague being watched, or nobody in particular. */
  asUserId: number | null
  /** Whether to show edit controls: the server allows it, observation is off, and this is your own view. */
  canEdit: (serverAllows?: boolean) => boolean
}

const ModeContext = createContext<Mode | null>(null)

function readViewAs(): ViewAs | null {
  try {
    const raw = sessionStorage.getItem(VIEW_KEY)
    return raw ? (JSON.parse(raw) as ViewAs) : null
  } catch {
    return null
  }
}

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export function ModeProvider({ children }: { children: ReactNode }) {
  const [observe, setObserveState] = useState(readStored)
  // Kept for the tab only: closing it ends the visit to someone else's view.
  const [viewAs, setViewAsState] = useState<ViewAs | null>(readViewAs)

  const setViewAs = useCallback((who: ViewAs | null) => {
    setViewAsState(who)
    try {
      if (who) sessionStorage.setItem(VIEW_KEY, JSON.stringify(who))
      else sessionStorage.removeItem(VIEW_KEY)
    } catch {
      /* this visit only */
    }
  }, [])

  const setObserve = useCallback((on: boolean) => {
    setObserveState(on)
    try {
      localStorage.setItem(STORAGE_KEY, on ? '1' : '0')
    } catch {
      /* this visit only */
    }
  }, [])

  const value = useMemo<Mode>(() => ({
    observe,
    setObserve,
    viewAs,
    setViewAs,
    asUserId: viewAs?.id ?? null,
    canEdit: (serverAllows = true) => serverAllows && !observe && !viewAs,
  }), [observe, setObserve, viewAs, setViewAs])

  return <ModeContext.Provider value={value}>{children}</ModeContext.Provider>
}

export function useMode(): Mode {
  const context = useContext(ModeContext)
  if (!context) throw new Error('useMode outside ModeProvider')
  return context
}
