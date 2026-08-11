import { useState, type FormEvent } from 'react'
import { useAdminActions, useAdminGameweeks, useAdminMatchPool } from '../api/queries'
import { RoundComposer, type RoundSpec } from '../components/RoundComposer'
import { TeamBadge } from '../components/TeamBadge'
import type { MatchView } from '../api/types'
import { kickoffTimeLabel, kickoffDayLabel } from '../lib/format'
import { useT } from '../i18n'

function matchLabel(match: MatchView): string {
  return `${match.homeTeam.shortName ?? match.homeTeam.name} v ${match.awayTeam.shortName ?? match.awayTeam.name}`
}

export function AdminPage() {
  const t = useT()
  const { data: gameweeks } = useAdminGameweeks(true)
  const { data: pool } = useAdminMatchPool(true)
  const { createGameweek, setFixtures, publish, sync, simulateResult } = useAdminActions()

  const [season, setSeason] = useState('')
  const [weekIndex, setWeekIndex] = useState(1)
  const [selection, setSelection] = useState<Set<number>>(new Set())
  const [targetGameweek, setTargetGameweek] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  const run = async (action: () => Promise<unknown>, success: string) => {
    setMessage(null)
    try {
      await action()
      setMessage(success)
    } catch (e) {
      setMessage(e instanceof Error ? e.message : t('admin.actionFailed'))
    }
  }

  const create = (event: FormEvent) => {
    event.preventDefault()
    const now = Date.now()
    void run(
      () =>
        createGameweek.mutateAsync({
          season,
          weekIndex,
          windowStart: new Date(now - 86_400_000).toISOString(),
          windowEnd: new Date(now + 6 * 86_400_000).toISOString(),
        }),
      t('admin.created'),
    )
  }

  const toggle = (matchId: number) => {
    setSelection((current) => {
      const next = new Set(current)
      if (next.has(matchId)) {
        next.delete(matchId)
      } else {
        next.add(matchId)
      }
      return next
    })
  }

  // The two rounds that have to exist before the season opens. Kept here
  // rather than typed in every week: composing them is the whole job, and a
  // wrong date silently produces an empty card.
  const rounds: RoundSpec[] = [
    {
      title: t('admin.roundZero'),
      season: '2026-27',
      weekIndex: 0,
      from: '2026-08-15',
      to: '2026-08-16',
      countsTowardsTable: false,
    },
    {
      title: t('admin.roundOne'),
      season: '2026-27',
      weekIndex: 1,
      from: '2026-08-21',
      to: '2026-08-23',
      countsTowardsTable: true,
    },
  ]
  const roundFor = (spec: RoundSpec) =>
    gameweeks?.find((gw) => gw.season === spec.season && gw.weekIndex === spec.weekIndex)

  const inputClass =
    'rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-emerald-500'

  return (
    <div className="space-y-6 p-4">
      <header className="flex items-center justify-between pt-2">
        <h1 className="text-xl font-bold">{t('admin.title')}</h1>
        <button
          type="button"
          onClick={() => run(() => sync.mutateAsync(), t('admin.syncedFixtures'))}
          className="rounded-lg bg-slate-800 px-3 py-2 text-sm font-medium"
        >
          {t('admin.syncFixtures')}
        </button>
      </header>

      {message && <p className="rounded-lg bg-slate-800/80 px-3 py-2 text-sm text-slate-200">{message}</p>}

      {rounds.map((spec) => (
        <RoundComposer key={`${spec.season}-${spec.weekIndex}`} spec={spec} existing={roundFor(spec)} />
      ))}

      <details className="rounded-2xl border border-slate-800 bg-slate-950 p-4">
        <summary className="cursor-pointer text-sm font-semibold text-slate-300">
          {t('admin.advanced')}
        </summary>
        <div className="mt-4 space-y-6">

      <section className="space-y-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">{t('admin.gameweeks')}</h2>
        {gameweeks?.map((gw) => (
          <div key={gw.id} className="flex items-center gap-3 rounded-xl border border-slate-800 bg-slate-900 px-4 py-3 text-sm">
            <span className="flex-1">
              #{gw.id} · {gw.season} GW{gw.weekIndex} · {t('admin.fixtures', { count: gw.fixtures.length })}
            </span>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                gw.status === 'PUBLISHED'
                  ? 'bg-emerald-500/20 text-emerald-300'
                  : gw.status === 'SCORED'
                    ? 'bg-sky-500/20 text-sky-300'
                    : 'bg-slate-700/60 text-slate-300'
              }`}
            >
              {gw.status}
            </span>
            {gw.status === 'DRAFT' && (
              <button
                type="button"
                onClick={() => run(() => publish.mutateAsync(gw.id), t('admin.published', { id: gw.id }))}
                className="rounded-lg bg-emerald-500 px-3 py-1.5 text-xs font-semibold text-emerald-950"
              >
                {t('admin.publish')}
              </button>
            )}
          </div>
        ))}
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">{t('admin.newGameweek')}</h2>
        <form onSubmit={create} className="flex flex-wrap items-center gap-2">
          <input type="text" required placeholder={t('admin.seasonPlaceholder')} value={season}
                 onChange={(e) => setSeason(e.target.value)} className={`${inputClass} w-36`} />
          <input type="number" required min={1} value={weekIndex}
                 onChange={(e) => setWeekIndex(Number(e.target.value))} className={`${inputClass} w-20`} />
          <button type="submit" className="rounded-lg bg-emerald-500 px-3 py-2 text-sm font-semibold text-emerald-950">
            {t('admin.create')}
          </button>
        </form>
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            {t('admin.fixturePool', { count: selection.size })}
          </h2>
          <div className="flex items-center gap-2">
            <select value={targetGameweek} onChange={(e) => setTargetGameweek(e.target.value)} className={inputClass}>
              <option value="">{t('admin.targetGameweek')}</option>
              {gameweeks
                ?.filter((gw) => gw.status === 'DRAFT')
                .map((gw) => (
                  <option key={gw.id} value={gw.id}>
                    #{gw.id} GW{gw.weekIndex}
                  </option>
                ))}
            </select>
            <button
              type="button"
              disabled={!targetGameweek || selection.size === 0}
              onClick={() =>
                run(
                  () => setFixtures.mutateAsync({ gameweekId: Number(targetGameweek), matchIds: [...selection] }),
                  t('admin.assigned'),
                )
              }
              className="rounded-lg bg-emerald-500 px-3 py-2 text-sm font-semibold text-emerald-950 disabled:opacity-30"
            >
              {t('admin.assign')}
            </button>
          </div>
        </div>

        <div className="space-y-2">
          {pool?.map((match) => (
            <label
              key={match.id}
              className="block rounded-xl border border-slate-800 bg-slate-900 px-4 py-2.5 text-sm"
            >
              <span className="flex items-start gap-3">
                <input type="checkbox" checked={selection.has(match.id)} onChange={() => toggle(match.id)}
                       className="mt-0.5 size-4 shrink-0 accent-emerald-500" />
                {/* the fixture gets its own line: squeezed onto one row with the
                    date and the simulator, the names collapsed to nothing */}
                <span className="flex min-w-0 flex-1 items-center gap-2 font-semibold">
                  <TeamBadge team={match.homeTeam} size={18} />
                  <span className="min-w-0 truncate">{matchLabel(match)}</span>
                </span>
              </span>
              <span className="mt-1 flex items-center justify-between gap-2 pl-7">
                <span className="text-xs text-slate-400">
                  {match.competitionCode} · {kickoffDayLabel(match.kickoffUtc)}{' '}
                  {kickoffTimeLabel(match.kickoffUtc)}
                </span>
                {match.status === 'FINISHED' ? (
                  <span className="text-xs font-semibold text-slate-300">
                    {match.homeScore}-{match.awayScore}
                  </span>
                ) : (
                  <SimulateResult
                    onSubmit={(home, away) =>
                      run(
                        () => simulateResult.mutateAsync({ matchId: match.id, homeScore: home, awayScore: away }),
                        t('admin.resultSet', { match: matchLabel(match) }),
                      )
                    }
                  />
                )}
              </span>
            </label>
          ))}
        </div>
        <p className="text-xs text-slate-500">
          {t('admin.resultHint')}
        </p>
      </section>
        </div>
      </details>
    </div>
  )
}

function SimulateResult({ onSubmit }: { onSubmit: (home: number, away: number) => void }) {
  const [home, setHome] = useState(0)
  const [away, setAway] = useState(0)
  const numberClass =
    'w-11 rounded-lg border border-slate-700 bg-slate-950 px-1.5 py-1 text-center text-xs outline-none focus:border-emerald-500'
  return (
    <span className="flex items-center gap-1">
      <input type="number" min={0} max={20} value={home} onChange={(e) => setHome(Number(e.target.value))}
             className={numberClass} aria-label="Home score" />
      <input type="number" min={0} max={20} value={away} onChange={(e) => setAway(Number(e.target.value))}
             className={numberClass} aria-label="Away score" />
      <button type="button" onClick={() => onSubmit(home, away)}
              className="rounded-lg bg-slate-700 px-2 py-1 text-xs font-semibold">
        FT
      </button>
    </span>
  )
}
