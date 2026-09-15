import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ka } from './ka'
import { en } from './en'

export type Lang = 'ka' | 'en'

const DICTS = { ka, en }
const STORAGE_KEY = 'andaneri.lang'

type Vars = Record<string, string | number | null | undefined>

interface I18n {
  lang: Lang
  setLang: (lang: Lang) => void
  /** A dotted path into the dictionary, e.g. t('status.NEW') or t('common.daysAgo', { n: 3 }). */
  t: (key: string, vars?: Vars) => string
  /** The Georgian or English name of a catalog item, whichever the screen is in. */
  name: (item: { nameKa: string; nameEn: string } | null | undefined) => string
}

const I18nContext = createContext<I18n | null>(null)

function lookup(dict: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((node, part) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined), dict)
}

function readStoredLang(): Lang {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'ka'
  } catch {
    return 'ka'
  }
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(readStoredLang)

  useEffect(() => {
    document.documentElement.lang = lang
  }, [lang])

  const setLang = useCallback((next: Lang) => {
    setLangState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* the choice lasts for this visit only */
    }
  }, [])

  const value = useMemo<I18n>(() => {
    const t = (key: string, vars?: Vars) => {
      const found = lookup(DICTS[lang], key) ?? lookup(ka, key)
      if (typeof found !== 'string') return key
      return vars ? found.replace(/\{(\w+)\}/g, (_, name: string) => String(vars[name] ?? '')) : found
    }
    const name = (item: { nameKa: string; nameEn: string } | null | undefined) => (item ? (lang === 'ka' ? item.nameKa : item.nameEn) : '')
    return { lang, setLang, t, name }
  }, [lang, setLang])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18n {
  const context = useContext(I18nContext)
  if (!context) throw new Error('useI18n outside I18nProvider')
  return context
}
