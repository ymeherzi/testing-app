// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '../i18n'
import { PushOptIn } from './PushOptIn'

/**
 * Two things this screen has to get right.
 *
 * <p>The prompt on the predictions screen sat above the fixtures on every visit
 * with no way out — a suggestion you cannot decline is nagging.
 *
 * <p>And it reported the browser's permission rather than whether this device
 * is registered with us. Holding permission without a subscription is an
 * ordinary state — after switching them off, or after the site data was
 * cleared — and the screen then offered nothing at all: no way back on.
 */

function browser(permission: NotificationPermission = 'default') {
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  Object.defineProperty(navigator, 'serviceWorker', { value: {}, configurable: true })
  Object.defineProperty(window, 'PushManager', { value: class {}, configurable: true })
  Object.defineProperty(window, 'Notification', {
    value: { permission, requestPermission: vi.fn() },
    configurable: true,
  })
}

function server(subscribed: boolean) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ subscribed }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  ))
}

function show(compact: boolean) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <PushOptIn compact={compact} />
      </I18nProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('PushOptIn', () => {
  it('can be waved away on the predictions screen, and stays away', () => {
    browser()
    server(false)
    show(true)

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByText('Round notifications')).toBeNull()

    // and it does not come back on the next visit
    cleanup()
    show(true)
    expect(screen.queryByText('Round notifications')).toBeNull()
  })

  it('is still offered in the profile after being dismissed', () => {
    browser()
    server(false)
    show(true)
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    cleanup()

    show(false)
    expect(screen.getByText('Round notifications')).toBeDefined()
    // the profile is a settings screen: nothing to dismiss there
    expect(screen.queryByRole('button', { name: 'Dismiss' })).toBeNull()
  })

  it('offers the switch again when permission is granted but the device is not registered', async () => {
    browser('granted')
    server(false)
    show(false)

    expect(await screen.findByRole('button', { name: 'Turn on notifications' })).toBeDefined()
    expect(screen.queryByRole('button', { name: 'Turn off on this device' })).toBeNull()
  })

  it('offers to turn them off once the device really is registered', async () => {
    browser('granted')
    server(true)
    show(false)

    expect(await screen.findByRole('button', { name: 'Turn off on this device' })).toBeDefined()
    expect(screen.queryByRole('button', { name: 'Turn on notifications' })).toBeNull()
  })
})
