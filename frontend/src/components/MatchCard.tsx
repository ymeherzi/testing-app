import { useEffect, useRef, useState } from 'react'
import type { FixtureView } from '../api/types'
import { countdown, kickoffTimeLabel, pointsLabelKey } from '../lib/format'
import { useT } from '../i18n'
import { score } from '../lib/scoring'
import { ScoreStepper } from './ScoreStepper'
import { TeamBadge } from './TeamBadge'

interface Props {
  fixture: FixtureView
  onSave: (fixtureId: number, homeGoals: number, awayGoals: number) => Promise<unknown>
}

type SaveState = 'idle' | 'saving' | 'saved' | 'error'

type Badge = 'live' | 'ft' | 'off'

function statusBadge(fixture: FixtureView): Badge | null {
  switch (fixture.matchStatus) {
    case 'IN_PLAY':
    case 'PAUSED':
      return 'live'
    case 'FINISHED':
    case 'AWARDED':
      return 'ft'
    case 'POSTPONED':
    case 'CANCELLED':
      return 'off'
    default:
      return null
  }
}

export function MatchCard({ fixture, onSave }: Props) {
  const t = useT()
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
  // provisional points while a real score exists but official scoring hasn't run
  const onCourse =
    fixture.prediction && fixture.homeScore != null && fixture.awayScore != null && points == null
      ? score(fixture.prediction.homeGoals, fixture.prediction.awayGoals, fixture.homeScore, fixture.awayScore)
      : null

  return (
    <article className="rounded-2xl border border-slate-800 bg-slate-900 p-4">
      <header className="mb-3 flex items-center justify-between text-xs text-slate-400">
        <span>{fixture.competitionName}</span>
        {badge ? (
          <span
            className={`rounded-full px-2 py-0.5 font-semibold ${
              badge === 'live' ? 'bg-red-500/20 text-red-400' : 'bg-slate-700/60 text-slate-300'
            }`}
          >
            {t(`match.${badge === 'live' ? 'live' : badge === 'ft' ? 'fullTime' : 'off'}`)}
          </span>
        ) : fixture.locked ? (
          <span className="text-slate-400">{t('match.awaitingResult')}</span>
        ) : (
          <span>
            {kickoffTimeLabel(fixture.kickoffUtc)}
            {remaining && <span className="ml-2 text-emerald-400">{t('match.locksIn', { time: remaining })}</span>}
          </span>
        )}
      </header>

      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col items-center gap-1 text-center">
          <TeamBadge team={fixture.homeTeam} />
          <span className="w-full truncate text-sm font-medium">{fixture.homeTeam.shortName ?? fixture.homeTeam.name}</span>
        </div>

        {fixture.locked ? (
          fixture.homeScore != null ? (
            <div className="flex flex-col items-center gap-1 px-2">
              <span className="text-2xl font-bold tabular-nums">
                {fixture.homeScore} : {fixture.awayScore}
              </span>
              {fixture.prediction && (
                <span className="text-xs text-slate-400">
                  {t('match.you', { home: fixture.prediction.homeGoals, away: fixture.prediction.awayGoals })}
                </span>
              )}
            </div>
          ) : (
            // locked but no result yet: the user's prediction stays the headline
            <div className="flex flex-col items-center gap-1 px-2">
              <span className="text-2xl font-bold tabular-nums text-emerald-300">
                {fixture.prediction
                  ? `${fixture.prediction.homeGoals} : ${fixture.prediction.awayGoals}`
                  : '– : –'}
              </span>
              <span className="text-xs text-slate-400">
                {t(fixture.prediction ? 'match.yourCall' : 'match.noPrediction')}
              </span>
            </div>
          )
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
            {t('common.points', { count: points })} · {t(pointsLabelKey(points))}
          </span>
        ) : onCourse != null ? (
          <span className="rounded-full bg-amber-500/15 px-2 py-0.5 font-semibold text-amber-300">
            {t('match.onCourse', { count: onCourse })}
          </span>
        ) : fixture.locked ? null : !touched ? (
          <span className="text-slate-500">{t('match.setYourScore')}</span>
        ) : saveState === 'saving' ? (
          <span className="text-slate-400">{t('common.saving')}</span>
        ) : saveState === 'error' ? (
          <span className="text-red-400">{t('match.saveFailed')}</span>
        ) : (
          <span className="text-emerald-400">{t('match.saved')}</span>
        )}
      </footer>
    </article>
  )
}
