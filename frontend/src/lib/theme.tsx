import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type ThemeChoice = 'light' | 'dark' | 'system'

interface Theme {
  choice: ThemeChoice
  /** What is showing right now, after "system" has been resolved. */
  dark: boolean
  setChoice: (choice: ThemeChoice) => void
}

const KEY = 'andaneri.theme'
const ThemeContext = createContext<Theme | null>(null)

function readChoice(): ThemeChoice {
  try {
    const saved = localStorage.getItem(KEY)
    return saved === 'light' || saved === 'dark' ? saved : 'system'
  } catch {
    return 'system'
  }
}

const systemDark = () => typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches

/**
 * Light, dark, or whatever the phone or computer is set to. The class goes on <html> so the palette
 * swap in index.css applies everywhere; index.html sets it before the first paint to avoid a white flash.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>(readChoice)
  const [system, setSystem] = useState(systemDark)

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystem(media.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  const dark = choice === 'dark' || (choice === 'system' && system)

  useEffect(() => {
    const root = document.documentElement
    root.classList.toggle('dark', dark)
    root.style.colorScheme = dark ? 'dark' : 'light'
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#16111a' : '#b0127f')
  }, [dark])

  const value = useMemo<Theme>(() => ({
    choice,
    dark,
    setChoice: (next) => {
      setChoiceState(next)
      try {
        if (next === 'system') localStorage.removeItem(KEY)
        else localStorage.setItem(KEY, next)
      } catch {
        /* remembered for this visit only */
      }
    },
  }), [choice, dark])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): Theme {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme outside ThemeProvider')
  return context
}
