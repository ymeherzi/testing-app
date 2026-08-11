// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nProvider } from '../i18n'
import { PushOptIn } from './PushOptIn'

/**
 * The prompt on the predictions screen sat above the fixtures on every visit
 * with no way out. A suggestion you cannot decline is nagging, and the profile
 * still carries the full control for anyone who changes their mind.
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

function show(compact: boolean) {
  render(
    <I18nProvider>
      <PushOptIn compact={compact} />
    </I18nProvider>,
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
    show(true)
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    cleanup()

    show(false)
    expect(screen.getByText('Round notifications')).toBeDefined()
    // the profile is a settings screen: nothing to dismiss there
    expect(screen.queryByRole('button', { name: 'Dismiss' })).toBeNull()
  })
})
