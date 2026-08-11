import { useState } from 'react'
import { useAdminActions, useAdminSuggestions } from '../api/queries'
import type { GameweekView, MatchView, SuggestionView, Team } from '../api/types'
import { TeamBadge } from './TeamBadge'
import { kickoffDayLabel, kickoffTimeLabel } from '../lib/format'
import { useT } from '../i18n'

export interface RoundSpec {
  title: string
  season: string
  weekIndex: number
  /** ISO dates, inclusive, for both the window and the suggestion pool */
  from: string
  to: string
  countsTowardsTable: boolean
}

interface Fixture {
  homeTeam: Team
  awayTeam: Team
}

/**
 * One fixture, on two lines.
 *
 * <p>The name comes first and alone: cramming it onto a single row with the
 * date and the actions is what made it collapse to nothing on a phone, and a
 * fixture you cannot read is a fixture you cannot choose.
 */
function FixtureRow({
  fixture,
  competition,
  kickoffUtc,
  reasons,
  action,
}: {
  fixture: Fixture
  competition: string
  kickoffUtc: string
  reasons?: string[]
  action?: React.ReactNode
}) {
  return (
    <li className="rounded-xl border border-slate-800 bg-slate-900 p-3">
      <p className="flex items-center gap-2 font-semibold text-slate-100">
        <TeamBadge team={fixture.homeTeam} size={20} />
        {/* short names keep a fixture on one line: "RCD Espanyol de
            Barcelona — Levante UD" truncates to nothing useful on a phone */}
        <span className="min-w-0 truncate">{fixture.homeTeam.shortName ?? fixture.homeTeam.name}</span>
        <span className="text-slate-500">—</span>
        <TeamBadge team={fixture.awayTeam} size={20} />
        <span className="min-w-0 truncate">{fixture.awayTeam.shortName ?? fixture.awayTeam.name}</span>
      </p>
      <div className="mt-1 flex items-center justify-between gap-2">
        <p className="text-xs text-slate-400">
          {competition} · {kickoffDayLabel(kickoffUtc)} {kickoffTimeLabel(kickoffUtc)}
          {reasons?.length ? <span className="ml-1 text-emerald-400">· {reasons.join(', ')}</span> : null}
        </p>
        {action}
      </div>
    </li>
  )
}

/**
 * A round to prepare: one button composes it, then each fixture can be
 * swapped for another from the same window.
 */
export function RoundComposer({ spec, existing }: { spec: RoundSpec; existing?: GameweekView }) {
  const t = useT()
  const { composeGameweek, setFixtures, publish } = useAdminActions()
  const [swapping, setSwapping] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const suggestions = useAdminSuggestions(spec.from, spec.to, swapping !== null)

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : t('admin.composeFailed'))
    }
  }

  const compose = () =>
    run(() =>
      composeGameweek.mutateAsync({
        season: spec.season,
        weekIndex: spec.weekIndex,
        windowStart: `${spec.from}T00:00:00Z`,
        windowEnd: `${spec.to}T23:59:59Z`,
        countsTowardsTable: spec.countsTowardsTable,
        size: 10,
      }),
    )

  /** Replaces one fixture, keeping the rest of the card as it is. */
  const swap = (fixtureId: number, replacement: MatchView) => {
    if (!existing) {
      return
    }
    const matchIds = existing.fixtures.map((f) => (f.fixtureId === fixtureId ? replacement.id : f.matchId))
    setSwapping(null)
    return run(() => setFixtures.mutateAsync({ gameweekId: existing.id, matchIds }))
  }

  const chosen = new Set(existing?.fixtures.map((f) => f.matchId) ?? [])
  const alternatives = (suggestions.data ?? []).filter((s: SuggestionView) => !chosen.has(s.match.id))

  return (
    <section className="space-y-3 rounded-2xl border border-slate-800 bg-slate-950 p-4">
      <header className="flex items-start justify-between gap-3">
        <div>
          <h2 className="font-bold text-slate-100">{spec.title}</h2>
          {existing && existing.fixtures.length > 0 && (
            <p className="text-xs text-slate-400">
              {t('admin.fixtureCount', { count: existing.fixtures.length })}
            </p>
          )}
          {!spec.countsTowardsTable && (
            <span className="mt-1 inline-block rounded-lg bg-amber-500/15 px-2 py-0.5 text-xs text-amber-300">
              {t('admin.warmUp')}
            </span>
          )}
        </div>
        {!existing || existing.fixtures.length === 0 ? (
          <button
            type="button"
            onClick={compose}
            disabled={composeGameweek.isPending}
            className="rounded-xl bg-emerald-500 px-4 py-2 text-sm font-semibold text-emerald-950 disabled:opacity-50"
          >
            {composeGameweek.isPending ? t('admin.composing') : t('admin.compose')}
          </button>
        ) : existing.status === 'DRAFT' ? (
          <div className="flex shrink-0 flex-col items-end gap-2">
            <button
              type="button"
              onClick={() => run(() => publish.mutateAsync(existing.id))}
              className="rounded-xl bg-emerald-500 px-4 py-2 text-sm font-semibold text-emerald-950"
            >
              {t('admin.publish')}
            </button>
            {/* fixtures keep arriving — a cup final imported after the first
                attempt is invisible without a way to ask for a fresh card */}
            <button
              type="button"
              onClick={compose}
              disabled={composeGameweek.isPending}
              className="text-xs font-medium text-emerald-400 underline disabled:opacity-50"
            >
              {composeGameweek.isPending ? t('admin.composing') : t('admin.recompose')}
            </button>
          </div>
        ) : (
          <span className="rounded-lg bg-slate-800 px-2 py-1 text-xs text-slate-300">{existing.status}</span>
        )}
      </header>

      {error && <p className="text-sm text-red-400">{error}</p>}

      {existing && (
        <ul className="space-y-2">
          {existing.fixtures.map((fixture) => (
            <FixtureRow
              key={fixture.fixtureId}
              fixture={fixture}
              competition={fixture.competitionCode}
              kickoffUtc={fixture.kickoffUtc}
              action={
                existing.status === 'DRAFT' ? (
                  <button
                    type="button"
                    onClick={() => setSwapping(swapping === fixture.fixtureId ? null : fixture.fixtureId)}
                    className="shrink-0 text-xs font-medium text-emerald-400 underline"
                  >
                    {t('admin.replace')}
                  </button>
                ) : undefined
              }
            />
          ))}
        </ul>
      )}

      {swapping !== null && (
        <div className="space-y-2 rounded-xl border border-emerald-900/60 bg-slate-900/60 p-3">
          <p className="text-xs uppercase tracking-wide text-slate-500">{t('admin.pickReplacement')}</p>
          {alternatives.length === 0 ? (
            <p className="text-sm text-slate-400">{t('admin.noAlternatives')}</p>
          ) : (
            <ul className="space-y-2">
              {alternatives.slice(0, 12).map((s) => (
                <FixtureRow
                  key={s.match.id}
                  fixture={s.match}
                  competition={s.match.competitionCode}
                  kickoffUtc={s.match.kickoffUtc}
                  reasons={s.reasons}
                  action={
                    <button
                      type="button"
                      onClick={() => swap(swapping, s.match)}
                      className="shrink-0 rounded-lg bg-emerald-500 px-3 py-1 text-xs font-semibold text-emerald-950"
                    >
                      {t('admin.choose')}
                    </button>
                  }
                />
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
