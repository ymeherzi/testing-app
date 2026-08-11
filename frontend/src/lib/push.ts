import { api } from '../api/client'

/**
 * Turning browser notifications on, and the several ways that can be
 * unavailable.
 *
 * <p>The awkward case is iPhone: Safari only allows push once the app has been
 * added to the Home Screen (iOS 16.4+). In a normal tab the subscribe call
 * fails with a bare error, so we detect it beforehand and say what to do —
 * otherwise half the players conclude the feature is broken.
 */
export type PushAvailability =
  | 'ready'
  | 'granted'
  | 'denied'
  /** iOS, in a browser tab: possible only once installed to the Home Screen */
  | 'needs-install'
  /** an old browser, or a desktop with no push service */
  | 'unsupported'

function isIos(): boolean {
  return /iP(hone|ad|od)/.test(navigator.userAgent)
}

function isInstalled(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    // Safari's own flag, which predates display-mode and is still the only
    // reliable signal on iOS
    ('standalone' in navigator && (navigator as { standalone?: boolean }).standalone === true)
  )
}

export function pushAvailability(): PushAvailability {
  // checking the values, not just the property names: a browser that declares
  // the name without an implementation would otherwise pass this guard and
  // fail later, where there is nothing useful to tell the player
  if (!navigator.serviceWorker || !window.PushManager || !window.Notification) {
    return isIos() && !isInstalled() ? 'needs-install' : 'unsupported'
  }
  if (isIos() && !isInstalled()) {
    return 'needs-install'
  }
  if (Notification.permission === 'granted') {
    return 'granted'
  }
  if (Notification.permission === 'denied') {
    return 'denied'
  }
  return 'ready'
}

/** The server's VAPID key, in the byte form pushManager.subscribe expects. */
function decodeKey(base64Url: string): Uint8Array {
  const padded = (base64Url + '='.repeat((4 - (base64Url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(padded)
  return Uint8Array.from([...raw].map((c) => c.charCodeAt(0)))
}

function keyOf(subscription: PushSubscription, name: 'p256dh' | 'auth'): string {
  const key = subscription.getKey(name)
  if (!key) {
    throw new Error('The browser did not provide its ' + name + ' key')
  }
  return btoa(String.fromCharCode(...new Uint8Array(key)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

/**
 * Asks for permission, subscribes, and registers the device.
 *
 * <p>Must be called from a tap: browsers ignore a permission request that did
 * not come from a user gesture.
 */
export async function enablePush(): Promise<boolean> {
  const { publicKey } = await api<{ publicKey: string }>('/api/push/key')
  if (!publicKey) {
    throw new Error('Notifications are not configured on the server yet')
  }
  if ((await Notification.requestPermission()) !== 'granted') {
    return false
  }
  const registration = await navigator.serviceWorker.ready
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      // required by Chrome: every push must result in something visible
      userVisibleOnly: true,
      applicationServerKey: decodeKey(publicKey),
    }))

  await api<void>('/api/push/subscriptions', {
    method: 'POST',
    body: JSON.stringify({
      endpoint: subscription.endpoint,
      p256dh: keyOf(subscription, 'p256dh'),
      auth: keyOf(subscription, 'auth'),
    }),
  })
  return true
}

/** Stops notifications on this device, on both sides. */
export async function disablePush(): Promise<void> {
  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.getSubscription()
  if (!subscription) {
    return
  }
  // tell the server first: if unsubscribing locally succeeded and the call
  // failed, we would keep pushing to an endpoint nobody reads
  await api<void>('/api/push/subscriptions', {
    method: 'DELETE',
    body: JSON.stringify({ endpoint: subscription.endpoint }),
  })
  await subscription.unsubscribe()
}
