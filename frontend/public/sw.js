// Service worker: shows reminders pushed by the CRM server, even while no CRM tab is open, and opens the
// right screen when one is tapped. It deliberately caches nothing, so a new version is never held back.

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { body: event.data ? event.data.text() : '' }
  }
  event.waitUntil(
    self.registration.showNotification(data.title || 'Andaneri CRM', {
      body: data.body || '',
      tag: data.tag || undefined,
      renotify: Boolean(data.tag),
      icon: '/icon-192.png',
      // Android draws this white-on-colour in the status bar; iPhone ignores it.
      badge: '/badge-72.png',
      vibrate: [80, 40, 80],
      timestamp: Date.now(),
      data: { url: data.url || '/' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL((event.notification.data && event.notification.data.url) || '/', self.location.origin).href
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      for (const client of windows) {
        if ('focus' in client) {
          await client.focus()
          if ('navigate' in client) await client.navigate(url)
          return
        }
      }
      await self.clients.openWindow(url)
    })(),
  )
})

/**
 * Android's push service hands out a new address from time to time (Chrome rotates them, and clearing
 * site data forces one). The subscription is renewed here with the same key so reminders keep arriving,
 * and any open tab is asked to tell the server the new address; if none is open, the next start does it.
 */
self.addEventListener('pushsubscriptionchange', (event) => {
  event.waitUntil(
    (async () => {
      const key = event.oldSubscription && event.oldSubscription.options
        ? event.oldSubscription.options.applicationServerKey
        : null
      if (!key) return
      try {
        await self.registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
      } catch {
        return
      }
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      for (const client of windows) client.postMessage({ type: 'push-resubscribed' })
    })(),
  )
})
