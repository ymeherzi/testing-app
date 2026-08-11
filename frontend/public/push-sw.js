/**
 * Push handling, imported into the generated service worker.
 *
 * <p>Kept as a plain file rather than folded into the Workbox build: it must
 * survive every change to the caching strategy, and a service worker that
 * fails to parse silently stops handling push altogether.
 */

self.addEventListener('push', (event) => {
  // A push with no readable payload still has to show something: browsers
  // display their own "site updated in the background" notice otherwise.
  let data = { title: 'Ten Games', body: '', url: '/', tag: 'tengames' }
  try {
    data = { ...data, ...(event.data ? event.data.json() : {}) }
  } catch {
    // not our payload shape — show the fallback rather than nothing
  }

  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      // same tag replaces an older notification, so a phone that was off all
      // day shows the current state instead of a pile of stale reminders
      tag: data.tag,
      data: { url: data.url || '/' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const target = new URL(event.notification.data?.url || '/', self.location.origin).href

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      // reuse the open app if there is one — opening a second copy loses
      // whatever the player was in the middle of
      for (const client of windows) {
        if (client.url.startsWith(self.location.origin) && 'focus' in client) {
          client.navigate(target)
          return client.focus()
        }
      }
      return self.clients.openWindow(target)
    }),
  )
})
