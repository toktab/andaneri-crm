import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { tap } from '../lib/mobile'

const CATCH = 72

/**
 * Pull the page down at the top to fetch everything again, the way a phone list does. It only listens
 * to touches that start with the page already scrolled to the top, so nothing is in the way of normal
 * scrolling, and a mouse never triggers it.
 */
export function PullToRefresh({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const [pull, setPull] = useState(0)
  const [busy, setBusy] = useState(false)
  const from = useRef<number | null>(null)
  const armed = useRef(false)

  useEffect(() => {
    const start = (event: TouchEvent) => {
      if (event.touches.length !== 1) return
      armed.current = window.scrollY <= 0 && !document.querySelector('[role="dialog"]')
      from.current = armed.current ? event.touches[0].clientY : null
    }
    const move = (event: TouchEvent) => {
      if (from.current === null) return
      const moved = event.touches[0].clientY - from.current
      if (moved <= 0) {
        setPull(0)
        return
      }
      // Heavier the further it goes, so it stops well before the whole page has slid down.
      const shown = Math.min(CATCH * 1.6, moved * 0.5)
      if (shown >= CATCH && pull < CATCH) tap(10)
      setPull(shown)
    }
    const end = async () => {
      const reached = pull >= CATCH
      from.current = null
      if (!reached) {
        setPull(0)
        return
      }
      setBusy(true)
      setPull(CATCH * 0.7)
      try {
        await client.refetchQueries({ type: 'active' })
      } finally {
        setBusy(false)
        setPull(0)
      }
    }
    window.addEventListener('touchstart', start, { passive: true })
    window.addEventListener('touchmove', move, { passive: true })
    window.addEventListener('touchend', end)
    window.addEventListener('touchcancel', end)
    return () => {
      window.removeEventListener('touchstart', start)
      window.removeEventListener('touchmove', move)
      window.removeEventListener('touchend', end)
      window.removeEventListener('touchcancel', end)
    }
  }, [client, pull])

  return (
    <>
      <div
        className="pointer-events-none fixed inset-x-0 top-0 z-50 flex justify-center md:hidden"
        style={{ transform: `translateY(${pull}px)`, opacity: pull > 8 ? 1 : 0, transition: pull === 0 ? 'transform 200ms, opacity 200ms' : undefined }}
        aria-hidden
      >
        <span className="mt-2 grid size-9 place-items-center rounded-full border border-line bg-surface shadow-lg">
          <RefreshCw className={`size-4 text-brand-600 ${busy ? 'animate-spin' : ''}`} style={{ transform: busy ? undefined : `rotate(${pull * 3}deg)` }} />
        </span>
      </div>
      {children}
    </>
  )
}
