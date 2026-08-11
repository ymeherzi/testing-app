// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { WelcomeGuide } from './WelcomeGuide'
import { I18nProvider } from '../i18n'
import type { UserProfile } from '../api/types'

/**
 * A welcome screen that will not go away is worse than no welcome screen.
 * What matters: it appears once, Skip ends it, and the account remembers.
 */

const player = (guideSeen: boolean): UserProfile => ({
  id: 'a-uuid',
  email: 'player@example.com',
  displayName: 'Player',
  country: 'TN',
  favouriteClubTeamId: null,
  admin: false,
  guideSeen,
  notifyEmail: true,
})

let currentUser: UserProfile | null = null
const updateUser = vi.fn()

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: currentUser, updateUser }),
}))

function renderGuide(user: UserProfile | null) {
  currentUser = user
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <I18nProvider>
          <WelcomeGuide />
        </I18nProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  updateUser.mockClear()
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('WelcomeGuide', () => {
  it('stays out of the way once the player has seen it', () => {
    vi.stubGlobal('fetch', vi.fn())
    renderGuide(player(true))

    expect(screen.queryByText('Skip')).toBeNull()
  })

  it('greets a newcomer and records the skip on the account', async () => {
    // a fresh Response per call: the component also fetches the scoring
    // scale, and a shared instance would arrive with its body already read
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify(player(true)), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    renderGuide(player(false))

    fireEvent.click(screen.getByText('Skip'))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/me/guide-seen', expect.objectContaining({ method: 'POST' })),
    )
    // and it disappears immediately rather than waiting for the round trip
    expect(screen.queryByText('Skip')).toBeNull()
    await waitFor(() => expect(updateUser).toHaveBeenCalled())
  })

  it('walks through every step before offering to start', () => {
    vi.stubGlobal('fetch', vi.fn())
    renderGuide(player(false))

    expect(screen.getByText('Step 1 of 3')).toBeDefined()
    fireEvent.click(screen.getByText('Next'))
    expect(screen.getByText('Step 2 of 3')).toBeDefined()
    fireEvent.click(screen.getByText('Next'))
    expect(screen.getByText('Step 3 of 3')).toBeDefined()
    expect(screen.getByText('Start playing')).toBeDefined()
  })

  it('does not block play when recording the skip fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    renderGuide(player(false))

    fireEvent.click(screen.getByText('Skip'))

    expect(screen.queryByText('Skip')).toBeNull()
  })
})
