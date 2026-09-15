// Push notifications on this device: the service worker (public/sw.js) receives them even while the CRM is
// closed. iPhone only allows them for the CRM added to the Home Screen (iOS 16.4+), not in a Safari tab.

import { api } from './api'

export interface PushStatus { publicKey: string; devices: number; reminderMinutes: number }

const FLAG = 'andaneri.push'

export function pushSupported(): boolean {
  return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

export function isIos(): boolean {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

/** Opened from the Home Screen icon rather than a browser tab. */
export function isStandalone(): boolean {
  return window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true
}

/** Whether notifications were switched on here (remembered per device). */
export function pushOnHere(): boolean {
  try {
    return localStorage.getItem(FLAG) === '1' && 'Notification' in window && Notification.permission === 'granted'
  } catch {
    return false
  }
}

function remember(on: boolean) {
  try {
    if (on) localStorage.setItem(FLAG, '1')
    else localStorage.removeItem(FLAG)
  } catch {
    /* this visit only */
  }
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null
  const registration = await navigator.serviceWorker.getRegistration('/')
  return registration ? registration.pushManager.getSubscription() : null
}

/**
 * Asks for permission (must run straight from a tap), subscribes with the server's key and registers the
 * device. Throws Error('DENIED') when the person or the browser refuses.
 */
export async function enablePush(lang: string): Promise<PushStatus> {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') throw new Error('DENIED')
  const status = await api.get<PushStatus>('/push/status')
  const registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' })
  await navigator.serviceWorker.ready
  const key = fromBase64Url(status.publicKey)
  let subscription = await registration.pushManager.getSubscription()
  if (subscription && !sameKey(subscription, key)) {
    await subscription.unsubscribe()
    subscription = null
  }
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
  }
  const result = await register(subscription, lang)
  remember(true)
  return result
}

export async function disablePush(): Promise<void> {
  const subscription = await currentSubscription()
  if (subscription) {
    await api.post('/push/subscriptions/remove', { endpoint: subscription.endpoint }).catch(() => undefined)
    await subscription.unsubscribe().catch(() => false)
  }
  remember(false)
}

/**
 * On every start with notifications on: tell the server about this device again. It follows a change of
 * language, a different person signing in on the same browser, and a push address the browser renewed.
 */
export async function resyncPush(lang: string): Promise<void> {
  if (!pushOnHere()) return
  const subscription = await currentSubscription()
  if (subscription) await register(subscription, lang).catch(() => undefined)
}

function register(subscription: PushSubscription, lang: string) {
  const json = subscription.toJSON()
  return api.post<PushStatus>('/push/subscriptions', { endpoint: json.endpoint, keys: json.keys, lang })
}

function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
  const base64 = (text + '='.repeat((4 - (text.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
  return bytes
}

function sameKey(subscription: PushSubscription, key: Uint8Array): boolean {
  const current = subscription.options.applicationServerKey
  if (!current) return false
  const bytes = new Uint8Array(current)
  return bytes.length === key.length && bytes.every((b, i) => b === key[i])
}
