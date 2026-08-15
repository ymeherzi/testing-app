// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { I18nProvider } from '../i18n'
import { JoinDeepLinkPage } from './JoinDeepLinkPage'
import { JoinLeaguePage } from './JoinLeaguePage'
import { peekPendingInvite } from '../lib/invite'

/**
 * Tapping an invite link is how everybody arrives. When the join is refused,
 * the player used to land on an empty box asking for a code — the very code
 * that was in the link they had just tapped, and which the screen had thrown
 * away along with the reason.
 */

function answer(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' },
  })
}

function signedIn() {
  localStorage.setItem('tengames.token', 'a-token')
  localStorage.setItem(
    'tengames.user',
    JSON.stringify({ id: 'u1', email: 'player@example.com', displayName: 'Player', admin: false }),
  )
}

function open() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/join/ABCD1234']}>
        <I18nProvider>
          <AuthProvider>
            <Routes>
              <Route path="/join/:code" element={<JoinDeepLinkPage />} />
              <Route path="/table/join" element={<JoinLeaguePage />} />
              <Route path="/table/league/:id" element={<p>league screen</p>} />
              <Route path="/signup" element={<p>signup screen</p>} />
            </Routes>
          </AuthProvider>
        </I18nProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => localStorage.clear())

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('the invite link', () => {
  it('joins the league and opens it', async () => {
    signedIn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(200, { id: 7, name: 'Les copains' })))
    open()

    expect(await screen.findByText('league screen')).toBeDefined()
    expect(peekPendingInvite()).toBeNull()
  })

  it('hands the code over when the join is refused, with the reason', async () => {
    signedIn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      answer(404, { status: 404, detail: 'Invite code not found' }),
    ))
    open()

    // the manual screen, with the code already in the box
    const field = (await screen.findByPlaceholderText('INVITE CODE')) as HTMLInputElement
    expect(field.value).toBe('ABCD1234')
    expect(screen.getByText('Invite code not found')).toBeDefined()
  })

  it('keeps the code for after signup when nobody is signed in', async () => {
    vi.stubGlobal('fetch', vi.fn())
    open()

    expect(await screen.findByText('signup screen')).toBeDefined()
    // PendingInviteHandler picks this up on the other side of the account
    expect(peekPendingInvite()).toBe('ABCD1234')
  })

  it('holds on to the code until the server has answered', async () => {
    signedIn()
    let resolve: ((value: Response) => void) | undefined
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>((r) => (resolve = r))))
    open()

    // mid-flight — a reload here must not lose the invitation
    await waitFor(() => expect(peekPendingInvite()).toBe('ABCD1234'))
    resolve?.(answer(200, { id: 7, name: 'Les copains' }))
    await screen.findByText('league screen')
  })
})
