import { differenceInCalendarDays, format, isValid, parseISO } from 'date-fns'
import { enUS, ka as kaLocale } from 'date-fns/locale'
import type { Lang } from '../i18n'

// Times are shown in the browser's own time zone: the team works in Tbilisi and so do their phones.

function toDate(value: string | Date | null | undefined): Date | null {
  if (!value) return null
  const date = typeof value === 'string' ? parseISO(value) : value
  return isValid(date) ? date : null
}

const locale = (lang: Lang) => (lang === 'ka' ? kaLocale : enUS)

export function fmtDate(value: string | Date | null | undefined, lang: Lang): string {
  const date = toDate(value)
  return date ? format(date, 'd MMM yyyy', { locale: locale(lang) }) : '-'
}

export function fmtShortDate(value: string | Date | null | undefined, lang: Lang): string {
  const date = toDate(value)
  return date ? format(date, 'd MMM', { locale: locale(lang) }) : '-'
}

export function fmtTime(value: string | Date | null | undefined): string {
  const date = toDate(value)
  return date ? format(date, 'HH:mm') : ''
}

export function fmtDateTime(value: string | Date | null | undefined, lang: Lang): string {
  const date = toDate(value)
  return date ? format(date, 'd MMM, HH:mm', { locale: locale(lang) }) : '-'
}

export function fmtWeekday(value: string | Date, lang: Lang, pattern = 'EEEE, d MMMM'): string {
  const date = toDate(value)
  return date ? format(date, pattern, { locale: locale(lang) }) : ''
}

/** Whole calendar days from today: 0 today, 1 tomorrow, -2 two days ago. */
export function daysFromToday(value: string | Date | null | undefined): number | null {
  const date = toDate(value)
  return date ? differenceInCalendarDays(date, new Date()) : null
}

export function money(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  const rounded = Math.round(value * 100) / 100
  return `${rounded.toLocaleString('ka-GE', { minimumFractionDigits: rounded % 1 ? 2 : 0, maximumFractionDigits: 2 })} ₾`
}

export function number(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return (Math.round(value * 100) / 100).toLocaleString('ka-GE')
}

/**
 * Every phone number written in a field. Spreadsheet cells hold things like
 * "(596) 900 010,   (551) 91 51 81" or "593 19 05 52 (wrong)"; each number gets its own call button.
 */
export function splitPhones(text: string | null | undefined): { display: string; href: string }[] {
  if (!text) return []
  const found: { display: string; href: string }[] = []
  const matches = text.match(/\+?\(?\d[\d\s()-]{5,}\d/g) ?? []
  for (const raw of matches) {
    const digits = raw.replace(/\D/g, '')
    if (digits.length < 6) continue
    const href = `tel:${raw.trim().startsWith('+') ? '+' : ''}${digits}`
    if (!found.some((f) => f.href === href)) found.push({ display: raw.trim(), href })
  }
  return found
}

export function telHref(phone: string | null | undefined): string | null {
  return splitPhones(phone)[0]?.href ?? null
}

/** The saved Google Maps link, or a search for the address. */
export function mapsHref(b: { mapsUrl?: string | null; address?: string | null; city?: string | null; name?: string }): string | null {
  if (b.mapsUrl) return b.mapsUrl
  const place = [b.name, b.address, b.city ?? 'Tbilisi'].filter(Boolean).join(', ')
  return b.address ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(place)}` : null
}

/** An ISO instant as the value of an <input type="datetime-local">, in local time. */
export function toLocalInput(value: string | Date | null | undefined): string {
  const date = toDate(value)
  return date ? format(date, "yyyy-MM-dd'T'HH:mm") : ''
}

export function fromLocalInput(value: string): string | null {
  if (!value) return null
  const date = new Date(value)
  return isValid(date) ? date.toISOString() : null
}

export function todayIso(): string {
  return format(new Date(), 'yyyy-MM-dd')
}

/** A moment a number of days from now at a given hour, for the "next step" shortcuts. */
export function atDaysFromNow(days: number, hour: number, minute = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, minute, 0, 0)
  return date.toISOString()
}

export function inHours(hours: number): string {
  const date = new Date(Date.now() + hours * 3_600_000)
  date.setSeconds(0, 0)
  return date.toISOString()
}

export function nextWeekday(weekday: number, hour: number): string {
  // weekday: 1 Monday ... 7 Sunday
  const date = new Date()
  const current = date.getDay() === 0 ? 7 : date.getDay()
  let add = weekday - current
  if (add <= 0) add += 7
  date.setDate(date.getDate() + add)
  date.setHours(hour, 0, 0, 0)
  return date.toISOString()
}
