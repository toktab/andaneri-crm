import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { CircleCheck, TriangleAlert, X } from 'lucide-react'
import { ApiError } from '../lib/api'
import { useI18n } from '../i18n'

interface ToastItem { id: number; kind: 'ok' | 'error'; text: string }

interface Toasts {
  ok: (text: string) => void
  /** Shows an API error in words: the translated code, and the first field problem if there is one. */
  error: (error: unknown) => void
}

const ToastContext = createContext<Toasts | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const { t } = useI18n()

  const push = useCallback((kind: ToastItem['kind'], text: string) => {
    const id = Date.now() + Math.random()
    setItems((current) => [...current.slice(-3), { id, kind, text }])
    setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), kind === 'error' ? 6000 : 3000)
  }, [])

  const value = useMemo<Toasts>(() => ({
    ok: (text) => push('ok', text),
    error: (error) => push('error', errorText(error, t)),
  }), [push, t])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="no-print pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 md:bottom-6">
        {items.map((item) => (
          <div
            key={item.id}
            role="status"
            className={`pointer-events-auto flex max-w-md items-start gap-2 rounded-xl px-4 py-3 text-sm shadow-lg ${
              item.kind === 'ok' ? 'bg-[#1c1523] text-white' : 'bg-rose-600 text-white'
            }`}
          >
            {item.kind === 'ok' ? <CircleCheck className="mt-0.5 size-4 shrink-0" /> : <TriangleAlert className="mt-0.5 size-4 shrink-0" />}
            <span className="flex-1">{item.text}</span>
            <button className="opacity-70 hover:opacity-100" onClick={() => setItems((c) => c.filter((i) => i.id !== item.id))} aria-label="close">
              <X className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function errorText(error: unknown, t: (key: string) => string): string {
  if (error instanceof ApiError) {
    const translated = t(`errors.${error.code}`)
    const base = translated === `errors.${error.code}` ? t('errors.generic') : translated
    const firstField = Object.entries(error.fields)[0]
    if (firstField) {
      const reason = t(`errors.${firstField[1]}`)
      return `${base}: ${firstField[0]} - ${reason.startsWith('errors.') ? firstField[1] : reason}`
    }
    return base
  }
  return t('errors.generic')
}

export function useToast(): Toasts {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast outside ToastProvider')
  return context
}
