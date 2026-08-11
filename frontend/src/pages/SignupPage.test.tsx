// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { SignupPage } from './SignupPage'
import { I18nProvider } from '../i18n'

/**
 * Signing up is where an invited friend lands, and the only moment they fill
 * in a club and a championship willingly. Both are what puts them in a public
 * league, so what matters here is that both reach the server.
 */

function json(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Answers the catalogue calls the club picker and the championship list make. */
function stubApi() {
  const fetchMock = vi.fn(async (url: string, _options?: RequestInit) => {
    if (url.startsWith('/api/competitions')) {
      return json([{ id: 7, code: 'PL', name: 'Premier League', domestic: true }])
    }
    if (url.startsWith('/api/teams')) {
      return json([{ id: 42, name: 'Paris Saint-Germain FC', shortName: 'PSG', crestUrl: null }])
    }
    return json({ verificationRequired: true, email: 'friend@example.com' })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <I18nProvider>
          <AuthProvider>
            <SignupPage />
          </AuthProvider>
        </I18nProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function fill(placeholder: string, value: string) {
  fireEvent.change(screen.getByPlaceholderText(placeholder), { target: { value } })
}

function signupBody(fetchMock: ReturnType<typeof stubApi>) {
  const call = fetchMock.mock.calls.find(([url]) => url === '/api/auth/signup')
  return JSON.parse(String(call?.[1]?.body))
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('SignupPage', () => {
  it('sends the club found by searching and the chosen championship', async () => {
    const fetchMock = stubApi()
    renderPage()

    fill('Email', 'friend@example.com')
    fill('Password (8+ characters)', 'correct-horse')
    fill('Display name', 'Friend')

    // the club is searched for, not scrolled to: two letters list nothing
    fireEvent.change(screen.getByPlaceholderText('Search a club'), { target: { value: 'psg' } })
    fireEvent.click(await screen.findByText('Paris Saint-Germain FC'))
    fireEvent.change(await screen.findByDisplayValue('Favourite championship (optional)'), {
      target: { value: '7' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Start predicting' }))

    await waitFor(() => expect(signupBody(fetchMock).favouriteClubTeamId).toBe(42))
    expect(signupBody(fetchMock).favouriteCompetitionId).toBe(7)
  })

  it('leaves both out when the player skips them', async () => {
    const fetchMock = stubApi()
    renderPage()

    fill('Email', 'plain@example.com')
    fill('Password (8+ characters)', 'correct-horse')
    fill('Display name', 'Plain')
    fireEvent.click(screen.getByRole('button', { name: 'Start predicting' }))

    await waitFor(() => expect(signupBody(fetchMock).email).toBe('plain@example.com'))
    // null, not undefined: the server reads the field either way, and a form
    // nobody finished is still a valid account
    expect(signupBody(fetchMock).favouriteClubTeamId).toBeNull()
    expect(signupBody(fetchMock).favouriteCompetitionId).toBeNull()
  })
})
