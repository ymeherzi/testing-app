// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '../i18n'
import { AdminPage } from './AdminPage'

/**
 * Until the season had two rounds written by hand, the screen offered exactly
 * those two and nothing after them: preparing J2 meant the raw tools.
 */
const played = [
  {
    id: 1, season: '2026-27', weekIndex: 1, type: 'WEEKEND', status: 'SCORED',
    windowStart: '2026-08-21T00:00:00Z', windowEnd: '2026-08-24T23:59:59Z',
    countsTowardsTable: true, fixtures: [],
  },
  {
    id: 2, season: '2026-27', weekIndex: 0, type: 'WEEKEND', status: 'SCORED',
    windowStart: '2026-08-12T00:00:00Z', windowEnd: '2026-08-18T23:59:59Z',
    countsTowardsTable: false, fixtures: [],
  },
]

function stubApi() {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    let body: unknown = []
    if (url.startsWith('/api/admin/gameweeks')) {
      body = played
    } else if (url.startsWith('/api/admin/notifications/audience')) {
      body = { subscribers: 0, listeners: [] }
    }
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }))
}

function show() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <AdminPage />
      </I18nProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('preparing the rounds ahead', () => {
  it('offers the next three rounds, and one more on demand', async () => {
    vi.setSystemTime(new Date('2026-08-25T09:00:00Z'))
    stubApi()
    show()

    // wait for the season itself: nothing is proposed until it has loaded
    expect(await screen.findByText('Past rounds (2)')).toBeDefined()
    expect(screen.getByText('Round 2')).toBeDefined()
    expect(screen.getByText('Round 3')).toBeDefined()
    expect(screen.getByText('Round 4')).toBeDefined()
    expect(screen.queryByText('Round 5')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'One more round' }))
    expect(screen.getByText('Round 5')).toBeDefined()

    // and a European week is one click away
    fireEvent.click(screen.getByRole('button', { name: 'Midweek round' }))
    expect(screen.getByText('Round 6 — midweek')).toBeDefined()

    vi.useRealTimers()
  })

  it('folds the rounds already played out of the way', async () => {
    vi.setSystemTime(new Date('2026-08-25T09:00:00Z'))
    stubApi()
    show()

    // J0 and J1 are done: they stay reachable, but behind one summary line
    expect(await screen.findByText('Past rounds (2)')).toBeDefined()
    vi.useRealTimers()
  })
})
