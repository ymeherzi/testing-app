import { useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useGameweekHistory, usePlayerGameweek, useScoringScale } from '../api/queries'
import type { FixtureView } from '../api/types'
import { GameweekPicker } from '../components/GameweekPicker'
import { TeamBadge } from '../components/TeamBadge'
import { useAuth } from '../auth/AuthContext'
import { countryFlag, kickoffDayLabel, kickoffTimeLabel, pointsLabelKey } from '../lib/format'
import { useT } from '../i18n'
import { pointsFor } from '../lib/scoring'

function PredictionRow({ fixture }: { fixture: FixtureView }) {
  const t = useT()
  const { data: scale } = useScoringScale()
  const prediction = fixture.prediction
  const points = prediction?.points ?? null
  const onCourse =
    prediction && fixture.homeScore != null && fixture.awayScore != null && points == null
      ? pointsFor(scale, prediction.homeGoals, prediction.awayGoals, fixture.homeScore, fixture.awayScore)
      : null

  return (
    <article className="rounded-2xl border border-slate-800 bg-slate-900 p-3">
      <header className="mb-2 flex items-center justify-between text-xs text-slate-400">
        <span className="truncate">{fixture.competitionName}</span>
        <span>{kickoffTimeLabel(fixture.kickoffUtc)}</span>
      </header>
      <div className="flex items-center gap-2">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <TeamBadge team={fixture.homeTeam} size={24} />
          <span className="truncate text-sm">{fixture.homeTeam.shortName ?? fixture.homeTeam.name}</span>
        </div>
        <span className="w-14 text-center text-base font-bold tabular-nums">
          {fixture.homeScore != null ? `${fixture.homeScore}-${fixture.awayScore}` : '–'}
        </span>
        <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
          <span className="truncate text-right text-sm">{fixture.awayTeam.shortName ?? fixture.awayTeam.name}</span>
          <TeamBadge team={fixture.awayTeam} size={24} />
        </div>
      </div>
      <footer className="mt-2 flex items-center justify-center gap-2 text-xs">
        {!fixture.locked ? (
          <span className="text-slate-500">{t('match.hiddenUntilKickoff')}</span>
        ) : prediction ? (
          <>
            <span className="rounded-full bg-slate-800 px-2 py-0.5 font-semibold text-slate-200">
              {t('match.picked', { home: prediction.homeGoals, away: prediction.awayGoals })}
            </span>
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
            ) : null}
          </>
        ) : (
          <span className="text-slate-500">{t('match.noPrediction')}</span>
        )}
      </footer>
    </article>
  )
}

export function PlayerPage() {
  const t = useT()
  const { playerId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [gameweekId, setGameweekId] = useState<number | null>(
    searchParams.get('gw') ? Number(searchParams.get('gw')) : null,
  )
  const { data: history } = useGameweekHistory()
  const { data: view, isPending, error } = usePlayerGameweek(gameweekId, playerId)

  if (isPending) {
    return <p className="p-6 text-center text-slate-400">{t('common.loading')}</p>
  }
  if (error || !view) {
    return <p className="p-6 text-center text-slate-400">{t('player.notFound')}</p>
  }

  const isMe = view.playerId === user?.id
  const groups = new Map<string, FixtureView[]>()
  for (const fixture of view.fixtures) {
    const day = kickoffDayLabel(fixture.kickoffUtc)
    groups.set(day, [...(groups.get(day) ?? []), fixture])
  }

  return (
    <div className="space-y-4 p-4">
      <button type="button" onClick={() => navigate(-1)} className="text-sm text-slate-400">
        {t('nav.back')}
      </button>

      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="truncate text-xl font-bold">
            {countryFlag(view.country)} {view.displayName}
            {isMe && <span className="ml-1 text-sm font-normal text-slate-400">{t('common.you')}</span>}
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {t('predict.gameweekLabel', { index: view.weekIndex })} · {view.season} ·{' '}
            <span className="font-semibold text-emerald-400">{t('common.points', { count: view.points })}</span>
            {view.hiddenCount > 0 && <span> · {t('player.hiddenPicks', { count: view.hiddenCount })}</span>}
          </p>
        </div>
        <GameweekPicker history={history} value={gameweekId ?? view.gameweekId} onChange={setGameweekId}
                        showPoints={false} />
      </header>

      {[...groups.entries()].map(([day, fixtures]) => (
        <section key={day} className="space-y-2">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">{day}</h2>
          {fixtures.map((fixture) => (
            <PredictionRow key={fixture.fixtureId} fixture={fixture} />
          ))}
        </section>
      ))}
    </div>
  )
}
