// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from '../i18n'
import { RoundComposer } from './RoundComposer'
import type { GameweekView } from '../api/types'

/**
 * The ranking puts a Championship Saturday below everything else — no derby it
 * knows, no club it calls elite, a competition worth three out of ten. On a
 * busy weekend those fixtures fall past the end of the list, which is how a
 * competition that is fully imported still cannot be picked.
 */

function team(name: string) {
  return { id: name.length, name, shortName: name, crestUrl: null }
}

/** Thirty Premier League fixtures ahead of it, then the one we are after. */
function pool() {
  const busy = Array.from({ length: 30 }, (_, i) => ({
    match: {
      id: i + 1,
      competitionCode: 'PL',
      homeTeam: team(`Home ${i}`),
      awayTeam: team(`Away ${i}`),
      kickoffUtc: '2026-08-22T14:00:00Z',
      status: 'TIMED',
      homeScore: null,
      awayScore: null,
    },
    score: 50,
    reasons: ['derby'],
  }))
  return [
    ...busy,
    {
      match: {
        id: 99,
        competitionCode: 'ELC',
        homeTeam: team('West Brom'),
        awayTeam: team('Burnley'),
        kickoffUtc: '2026-08-23T11:00:00Z',
        status: 'TIMED',
        homeScore: null,
        awayScore: null,
      },
      score: 3,
      reasons: ['fills the card'],
    },
  ]
}

function stubApi() {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    const body = url.startsWith('/api/competitions')
      ? [
          { id: 1, code: 'PL', name: 'Premier League', domestic: true },
          { id: 2, code: 'ELC', name: 'Championship', domestic: true },
        ]
      : pool()
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }))
}

const round: GameweekView = {
  id: 1,
  season: '2026-27',
  weekIndex: 1,
  type: 'WEEKEND',
  status: 'PUBLISHED',
  windowStart: '2026-08-21T00:00:00Z',
  windowEnd: '2026-08-23T23:59:59Z',
  countsTowardsTable: true,
  // one fixture already on the card: an empty round only offers "Compose"
  fixtures: [
    {
      fixtureId: 500,
      matchId: 500,
      competitionCode: 'PL',
      competitionName: 'Premier League',
      homeTeam: team('Arsenal'),
      awayTeam: team('Chelsea'),
      kickoffUtc: '2026-08-22T16:30:00Z',
      matchStatus: 'TIMED',
      homeScore: null,
      awayScore: null,
      locked: false,
      prediction: null,
    },
  ],
}

function show() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <RoundComposer
          spec={{
            title: 'Round 1',
            season: '2026-27',
            weekIndex: 1,
            from: '2026-08-21',
            to: '2026-08-23',
            countsTowardsTable: true,
          }}
          existing={round}
        />
      </I18nProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('adding a fixture', () => {
  it('reaches a competition the ranking buried', async () => {
    stubApi()
    show()

    fireEvent.click(screen.getByRole('button', { name: 'Add a fixture' }))
    // thirty better-ranked fixtures come first, so it is not on screen yet
    expect(screen.queryByText('West Brom')).toBeNull()

    fireEvent.change(await screen.findByLabelText('Filter by competition'), { target: { value: 'ELC' } })

    expect(await screen.findByText('West Brom')).toBeDefined()
    // and the Premier League crowd is out of the way
    expect(screen.queryByText('Home 0')).toBeNull()
  })

  it('draws from the dates the editor sets, and remembers them', async () => {
    stubApi()
    show()

    // Monday night, which the hardcoded window used to cut off
    fireEvent.change(screen.getByLabelText('to'), { target: { value: '2026-08-24' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add a fixture' }))

    await waitFor(() =>
      expect(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.some(
          (call) => String(call[0]).includes('from=2026-08-21&to=2026-08-24'),
        ),
      ).toBe(true),
    )
    // and the next visit opens on the same dates
    expect(localStorage.getItem('tengames.roundRange.2026-27-1')).toContain('2026-08-24')
  })

  it('names the competition rather than showing its code', async () => {
    stubApi()
    show()

    fireEvent.click(screen.getByRole('button', { name: 'Add a fixture' }))

    // the filter offers names from the catalogue, not "ELC"
    expect(await screen.findByRole('option', { name: 'Championship' })).toBeDefined()
  })
})
