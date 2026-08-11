// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { UnsubscribePage } from './UnsubscribePage'
import { I18nProvider } from '../i18n'

/**
 * The page someone lands on when they want the emails to stop. If it fails
 * quietly, or asks them to sign in, the next step they take is marking us as
 * spam — which costs every other player their notifications too.
 */

const fetchMock = vi.fn()

function renderAt(query: string) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
      <MemoryRouter initialEntries={[`/unsubscribe${query}`]}>
        <I18nProvider>
          <UnsubscribePage />
        </I18nProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('unsubscribe page', () => {
  it('acts on the link without asking for a confirming click', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(new Response(null, { status: 204 })))

    renderAt('?u=a-uuid&t=a-token')

    await waitFor(() => expect(screen.getByText(/won't email you|ne recevras plus/i)).toBeTruthy())
    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/notifications/unsubscribe')
    // a POST: mail scanners follow links, and a GET would unsubscribe people
    // who never clicked
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ u: 'a-uuid', t: 'a-token' })
  })

  it('says so when the link has been tampered with', async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ detail: 'That unsubscribe link is not valid' }), { status: 400 }),
      ),
    )

    renderAt('?u=a-uuid&t=wrong')

    await waitFor(() => expect(screen.getByText(/no longer valid|plus valable/i)).toBeTruthy())
  })

  it('does not call the server when the link is missing its token', async () => {
    renderAt('?u=a-uuid')

    await waitFor(() => expect(screen.getByText(/no longer valid|plus valable/i)).toBeTruthy())
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
