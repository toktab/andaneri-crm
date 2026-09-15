import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

// Observation mode: browse everything with every edit control hidden, so nothing changes by accident.
// It is a per-device preference, not a permission; the server's own rules still apply underneath.

const STORAGE_KEY = 'andaneri.observe'

interface Mode {
  observe: boolean
  setObserve: (on: boolean) => void
  /** Whether to show edit controls: the server allows it and observation mode is off. */
  canEdit: (serverAllows?: boolean) => boolean
}

const ModeContext = createContext<Mode | null>(null)

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export function ModeProvider({ children }: { children: ReactNode }) {
  const [observe, setObserveState] = useState(readStored)

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
    canEdit: (serverAllows = true) => serverAllows && !observe,
  }), [observe, setObserve])

  return <ModeContext.Provider value={value}>{children}</ModeContext.Provider>
}

export function useMode(): Mode {
  const context = useContext(ModeContext)
  if (!context) throw new Error('useMode outside ModeProvider')
  return context
}
