import { useRef, useState, type ReactNode } from 'react'
import { tap } from '../lib/mobile'

export interface SwipeAction {
  /** Shown in the strip that appears behind the row while it is being pushed aside. */
  icon: ReactNode
  label: string
  /** Background of that strip, e.g. 'bg-emerald-600'. */
  tone: string
  run: () => void
}

const CATCH = 88

/**
 * A list row that can be pushed aside with a thumb: right for the first action, left for the second,
 * the way a phone's mail and message lists work. Push far enough and it catches (with a short buzz);
 * let go earlier and it springs back. A mouse does nothing here - on a laptop the row's own buttons do
 * the same jobs - and a vertical drag is left alone so the list still scrolls.
 */
export function SwipeRow({ right, left, disabled, children }: {
  right?: SwipeAction
  left?: SwipeAction
  disabled?: boolean
  children: ReactNode
}) {
  const [dx, setDx] = useState(0)
  const [gliding, setGliding] = useState(false)
  const from = useRef<{ x: number; y: number } | null>(null)
  const axis = useRef<'none' | 'x' | 'y'>('none')

  const active = dx > 0 ? right : dx < 0 ? left : undefined
  const caught = Math.abs(dx) >= CATCH

  const end = () => {
    from.current = null
    if (axis.current !== 'x') {
      axis.current = 'none'
      return
    }
    axis.current = 'none'
    setGliding(true)
    if (caught && active) {
      // Let the row finish sliding out from under the thumb before the list changes beneath it.
      setDx(dx > 0 ? window.innerWidth : -window.innerWidth)
      const run = active.run
      setTimeout(() => { run(); setGliding(false); setDx(0) }, 160)
    } else {
      setDx(0)
      setTimeout(() => setGliding(false), 200)
    }
  }

  if (disabled || (!right && !left)) return <>{children}</>

  return (
    <div className="relative overflow-hidden">
      {active && (
        <div
          className={`absolute inset-y-0 flex items-center gap-2 px-5 text-sm font-semibold text-white ${active.tone} ${
            dx > 0 ? 'left-0 right-0 justify-start' : 'left-0 right-0 justify-end'
          } ${caught ? '' : 'opacity-70'}`}
        >
          {active.icon}
          {caught && active.label}
        </div>
      )}
      <div
        className="relative touch-pan-y bg-surface"
        style={{ transform: `translateX(${dx}px)`, transition: gliding ? 'transform 180ms cubic-bezier(0.32, 0.72, 0, 1)' : undefined }}
        onPointerDown={(event) => {
          if (event.pointerType === 'mouse') return
          from.current = { x: event.clientX, y: event.clientY }
          axis.current = 'none'
        }}
        onPointerMove={(event) => {
          const start = from.current
          if (!start) return
          const moveX = event.clientX - start.x
          const moveY = event.clientY - start.y
          if (axis.current === 'none') {
            if (Math.abs(moveX) < 8 && Math.abs(moveY) < 8) return
            // Whichever way it went first wins, so a scroll is never mistaken for a swipe.
            axis.current = Math.abs(moveX) > Math.abs(moveY) ? 'x' : 'y'
            if (axis.current === 'x') event.currentTarget.setPointerCapture(event.pointerId)
          }
          if (axis.current !== 'x') return
          const limited = moveX > 0 ? (right ? moveX : 0) : (left ? moveX : 0)
          // Past the catch point it gets heavy, so the thumb feels where the line is.
          const shown = Math.abs(limited) <= CATCH ? limited : Math.sign(limited) * (CATCH + (Math.abs(limited) - CATCH) * 0.35)
          if (Math.abs(shown) >= CATCH && Math.abs(dx) < CATCH) tap(10)
          setDx(shown)
        }}
        onPointerUp={end}
        onPointerCancel={() => { from.current = null; axis.current = 'none'; setGliding(true); setDx(0) }}
      >
        {children}
      </div>
    </div>
  )
}
