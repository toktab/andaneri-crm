import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'

/**
 * Being on a phone, as far as the layout is concerned: a narrow screen held in one hand.
 * The pointer is asked about separately ({@link useCanCall}), because what a finger can do and how
 * much room there is are two different questions.
 */
export function useIsPhone(): boolean {
  return useMedia('(max-width: 767px)')
}

export function useMedia(query: string): boolean {
  const [matches, setMatches] = useState(() => typeof window !== 'undefined' && window.matchMedia(query).matches)
  useEffect(() => {
    const media = window.matchMedia(query)
    const update = () => setMatches(media.matches)
    update()
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [query])
  return matches
}

/**
 * A short buzz to confirm something happened under the thumb - ticking a task off, a swipe catching.
 * Android answers; iPhone Safari ignores it, and nothing is lost when it does.
 */
export function tap(pattern: number | number[] = 12) {
  try {
    navigator.vibrate?.(pattern)
  } catch {
    /* not allowed here, never mind */
  }
}

/**
 * Drag a sheet down to close it. Returns handlers for the grab area: the sheet follows the finger,
 * and a drag past a third of its height - or a quick flick - closes it; anything less springs back.
 */
export function useDragToClose(onClose: () => void, enabled: boolean) {
  const ref = useRef<HTMLDivElement>(null)
  const start = useRef<{ y: number; at: number } | null>(null)

  const move = (y: number) => {
    const sheet = ref.current
    if (sheet) sheet.style.transform = y > 0 ? `translateY(${y}px)` : ''
  }

  const settle = (y: number, speed: number) => {
    const sheet = ref.current
    const height = sheet?.getBoundingClientRect().height ?? 0
    if (y > height / 3 || speed > 0.8) {
      tap(8)
      onClose()
      return
    }
    if (sheet) {
      sheet.style.transition = 'transform 180ms cubic-bezier(0.32, 0.72, 0, 1)'
      sheet.style.transform = ''
      setTimeout(() => { if (sheet) sheet.style.transition = '' }, 200)
    }
  }

  const handlers = enabled
    ? {
      onPointerDown: (event: ReactPointerEvent) => {
        if (event.pointerType === 'mouse') return
        start.current = { y: event.clientY, at: Date.now() }
      },
      onPointerMove: (event: ReactPointerEvent) => {
        if (!start.current) return
        move(event.clientY - start.current.y)
      },
      onPointerUp: (event: ReactPointerEvent) => {
        const from = start.current
        start.current = null
        if (!from) return
        const distance = event.clientY - from.y
        settle(distance, distance / Math.max(1, Date.now() - from.at))
      },
      onPointerCancel: () => {
        start.current = null
        move(0)
      },
    }
    : {}

  return { ref, handlers }
}
