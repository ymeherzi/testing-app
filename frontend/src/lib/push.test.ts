// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enablePush, pushAvailability } from './push'

/**
 * What a player is offered depends entirely on this. Getting it wrong on
 * iPhone is the expensive case: Safari refuses to subscribe in a browser tab,
 * so a button shown there fails with nothing anyone can act on.
 */

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1'
const ANDROID = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36'

function browser({
  userAgent = ANDROID,
  standalone = false,
  supported = true,
  permission = 'default' as NotificationPermission,
}) {
  vi.stubGlobal('navigator', { userAgent, standalone, serviceWorker: {} })
  vi.stubGlobal('matchMedia', () => ({ matches: standalone }))
  if (supported) {
    vi.stubGlobal('PushManager', class {})
    vi.stubGlobal('Notification', { permission, requestPermission: vi.fn() })
  }
  // jsdom's window is where the code looks for these
  Object.defineProperty(window, 'PushManager', { value: supported ? class {} : undefined, configurable: true })
  Object.defineProperty(window, 'Notification', {
    value: supported ? { permission, requestPermission: vi.fn() } : undefined,
    configurable: true,
  })
}

beforeEach(() => {
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('push availability', () => {
  it('tells an iPhone in a tab to install the app first', () => {
    browser({ userAgent: IPHONE, standalone: false })

    // Safari only allows push from the Home Screen (iOS 16.4+), and offering
    // a button here would simply fail
    expect(pushAvailability()).toBe('needs-install')
  })

  it('offers the button once the iPhone app is installed', () => {
    browser({ userAgent: IPHONE, standalone: true })

    expect(pushAvailability()).toBe('ready')
  })

  it('reports a browser that was already answered', () => {
    browser({ permission: 'denied' })
    expect(pushAvailability()).toBe('denied')

    browser({ permission: 'granted' })
    expect(pushAvailability()).toBe('granted')
  })

  it('says nothing is possible on a browser without push', () => {
    browser({ supported: false })

    expect(pushAvailability()).toBe('unsupported')
  })
})

describe('subscribing', () => {
  it('registers the device with keys the server can use', async () => {
    const subscription = {
      endpoint: 'https://push.example.net/abc',
      getKey: (name: string) => new Uint8Array(name === 'auth' ? [1, 2, 3] : [4, 5, 6]).buffer,
    }
    const fetchMock = vi.fn((path: string, options?: RequestInit) =>
      Promise.resolve(
        path === '/api/push/key' && !options
          ? new Response(JSON.stringify({ publicKey: 'BClient_key-value' }), { status: 200 })
          : new Response(null, { status: 204 }),
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('navigator', {
      userAgent: ANDROID,
      serviceWorker: { ready: Promise.resolve({ pushManager: { getSubscription: async () => subscription } }) },
    })
    Object.defineProperty(window, 'Notification', {
      value: { permission: 'default', requestPermission: async () => 'granted' },
      configurable: true,
    })

    await expect(enablePush()).resolves.toBe(true)

    const body = JSON.parse(String(fetchMock.mock.calls[1][1]?.body))
    expect(body.endpoint).toBe('https://push.example.net/abc')
    // base64url, as the server decodes it — '+' and '/' would not survive
    expect(body.auth).toMatch(/^[A-Za-z0-9_-]+$/)
    expect(body.p256dh).toMatch(/^[A-Za-z0-9_-]+$/)
  })

  it('does not register anything when the player says no', async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(new Response(JSON.stringify({ publicKey: 'BClient_key-value' }), { status: 200 })),
    )
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('navigator', { userAgent: ANDROID, serviceWorker: { ready: Promise.resolve({}) } })
    Object.defineProperty(window, 'Notification', {
      value: { permission: 'default', requestPermission: async () => 'denied' },
      configurable: true,
    })

    await expect(enablePush()).resolves.toBe(false)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
