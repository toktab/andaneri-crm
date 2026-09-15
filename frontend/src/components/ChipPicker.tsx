import { useMemo, useState } from 'react'
import { Plus, X } from 'lucide-react'
import { Spinner } from './ui'

export interface ChipOption { id: number; label: string; hint?: string }

/**
 * Tap chips to pick flavors or brands; type to narrow the list; add a new one when it is not there
 * ("Monin" is on the list, "Barbados Syrups" might not be). Selected chips sit on top.
 */
export function ChipPicker({
  options, selected, onChange, onCreate, placeholder, single = false, initialCount = 18, tone = 'brand',
}: {
  options: ChipOption[]
  selected: number[]
  onChange: (ids: number[]) => void
  onCreate?: (text: string) => Promise<ChipOption>
  placeholder?: string
  single?: boolean
  initialCount?: number
  tone?: 'brand' | 'green' | 'rose'
}) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState<ChipOption[]>([])

  const all = useMemo(() => {
    const known = new Set(options.map((o) => o.id))
    return [...options, ...created.filter((o) => !known.has(o.id))]
  }, [options, created])
  const chosen = new Set(selected)
  const query = text.trim().toLowerCase()
  const matches = all
    .filter((o) => !chosen.has(o.id))
    .filter((o) => !query || o.label.toLowerCase().includes(query) || (o.hint ?? '').toLowerCase().includes(query))
    .slice(0, query ? 40 : initialCount)
  const exact = all.some((o) => o.label.toLowerCase() === query || (o.hint ?? '').toLowerCase() === query)

  const toggle = (id: number) => {
    if (single) onChange(chosen.has(id) ? [] : [id])
    else onChange(chosen.has(id) ? selected.filter((x) => x !== id) : [...selected, id])
  }

  const create = async () => {
    if (!onCreate || !query) return
    setBusy(true)
    try {
      const option = await onCreate(text.trim())
      setCreated((current) => [...current, option])
      if (single) onChange([option.id])
      else if (!chosen.has(option.id)) onChange([...selected, option.id])
      setText('')
    } finally {
      setBusy(false)
    }
  }

  const onStyle = tone === 'green' ? 'border-emerald-600 bg-emerald-600 text-white' : tone === 'rose' ? 'border-rose-600 bg-rose-600 text-white' : ''

  return (
    <div className="space-y-2">
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selected.map((id) => {
            const option = all.find((o) => o.id === id)
            return (
              <button key={id} type="button" onClick={() => toggle(id)} className={`chip-on ${onStyle}`}>
                {option?.label ?? `#${id}`}
                <X className="size-3" />
              </button>
            )
          })}
        </div>
      )}
      <input
        className="input"
        value={text}
        placeholder={placeholder}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            if (matches[0]) {
              toggle(matches[0].id)
              setText('')
            } else if (!exact) {
              void create()
            }
          }
        }}
      />
      <div className="flex max-h-40 flex-wrap gap-1.5 overflow-y-auto">
        {matches.map((option) => (
          <button key={option.id} type="button" className="chip-off" onClick={() => { toggle(option.id); setText('') }} title={option.hint}>
            {option.label}
          </button>
        ))}
        {onCreate && query && !exact && (
          <button type="button" className="chip border-dashed border-brand-400 bg-brand-50 text-brand-700" onClick={() => void create()} disabled={busy}>
            {busy ? <Spinner className="size-3" /> : <Plus className="size-3" />}
            {text.trim()}
          </button>
        )}
      </div>
    </div>
  )
}
