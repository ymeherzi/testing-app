import { useEffect, useRef, useState } from 'react'
import type { FixtureView } from '../api/types'
import { countdown, kickoffTimeLabel, pointsLabel } from '../lib/format'
import { ScoreStepper } from './ScoreStepper'
import { TeamBadge } from './TeamBadge'

interface Props {
  fixture: FixtureView
  onSave: (fixtureId: number, homeGoals: number, awayGoals: number) => Promise<unknown>
}

type SaveState = 'idle' | 'saving' | 'saved' | 'error'

function statusBadge(fixture: FixtureView): string | null {
  switch (fixture.matchStatus) {
    case 'IN_PLAY':
    case 'PAUSED':
      return 'LIVE'
    case 'FINISHED':
    case 'AWARDED':
      return 'FT'
    case 'POSTPONED':
    case 'CANCELLED':
      return 'OFF'
    default:
      return null
  }
}

export function MatchCard({ fixture, onSave }: Props) {
  const [homeGoals, setHomeGoals] = useState(fixture.prediction?.homeGoals ?? 0)
  const [awayGoals, setAwayGoals] = useState(fixture.prediction?.awayGoals ?? 0)
  const [touched, setTouched] = useState(fixture.prediction != null)
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [tick, setTick] = useState(0)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)

  // Re-render each minute so the lock countdown stays current.
  useEffect(() => {
    const interval = setInterval(() => setTick((t) => t + 1), 60_000)
    return () => clearInterval(interval)
  }, [])
  void tick

  const change = (home: number, away: number) => {
    setHomeGoals(home)
    setAwayGoals(away)
    setTouched(true)
    setSaveState('saving')
    clearTimeout(timer.current)
    timer.current = setTimeout(async () => {
      try {
        await onSave(fixture.fixtureId, home, away)
        setSaveState('saved')
      } catch {
        setSaveState('error')
      }
    }, 600)
  }

  const badge = statusBadge(fixture)
  const remaining = countdown(fixture.kickoffUtc)
  const points = fixture.prediction?.points ?? null

  return (
    <article className="rounded-2xl border border-slate-800 bg-slate-900 p-4">
      <header className="mb-3 flex items-center justify-between text-xs text-slate-400">
        <span>{fixture.competitionName}</span>
        {badge ? (
          <span
            className={`rounded-full px-2 py-0.5 font-semibold ${
              badge === 'LIVE' ? 'bg-red-500/20 text-red-400' : 'bg-slate-700/60 text-slate-300'
            }`}
          >
            {badge}
          </span>
        ) : (
          <span>
            {kickoffTimeLabel(fixture.kickoffUtc)}
            {!fixture.locked && remaining && <span className="ml-2 text-emerald-400">locks in {remaining}</span>}
          </span>
        )}
      </header>

      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col items-center gap-1 text-center">
          <TeamBadge team={fixture.homeTeam} />
          <span className="w-full truncate text-sm font-medium">{fixture.homeTeam.shortName ?? fixture.homeTeam.name}</span>
        </div>

        {fixture.locked ? (
          <div className="flex flex-col items-center gap-1 px-2">
            <span className="text-2xl font-bold tabular-nums">
              {fixture.homeScore ?? '–'} : {fixture.awayScore ?? '–'}
            </span>
            {fixture.prediction && (
              <span className="text-xs text-slate-400">
                you: {fixture.prediction.homeGoals}-{fixture.prediction.awayGoals}
              </span>
            )}
          </div>
        ) : (
          <div className="flex items-start gap-2 px-1">
            <ScoreStepper label={fixture.homeTeam.name} value={homeGoals} onChange={(v) => change(v, awayGoals)} />
            <span className="pt-9 text-lg font-bold text-slate-500">:</span>
            <ScoreStepper label={fixture.awayTeam.name} value={awayGoals} onChange={(v) => change(homeGoals, v)} />
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col items-center gap-1 text-center">
          <TeamBadge team={fixture.awayTeam} />
          <span className="w-full truncate text-sm font-medium">{fixture.awayTeam.shortName ?? fixture.awayTeam.name}</span>
        </div>
      </div>

      <footer className="mt-3 flex h-5 items-center justify-center text-xs">
        {points != null ? (
          <span
            className={`rounded-full px-2 py-0.5 font-semibold ${
              points === 3
                ? 'bg-emerald-500/20 text-emerald-300'
                : points > 0
                  ? 'bg-sky-500/20 text-sky-300'
                  : 'bg-slate-700/60 text-slate-400'
            }`}
          >
            {points} pts · {pointsLabel(points)}
          </span>
        ) : fixture.locked ? (
          fixture.prediction == null && <span className="text-slate-500">no prediction</span>
        ) : !touched ? (
          <span className="text-slate-500">set your score</span>
        ) : saveState === 'saving' ? (
          <span className="text-slate-400">saving…</span>
        ) : saveState === 'error' ? (
          <span className="text-red-400">couldn't save — try again</span>
        ) : (
          <span className="text-emerald-400">saved ✓</span>
        )}
      </footer>
    </article>
  )
}
