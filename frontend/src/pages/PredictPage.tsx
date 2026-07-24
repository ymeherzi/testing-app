import { useState } from 'react'
import { useCurrentGameweek, useGameweek, useGameweekHistory, usePredictMutation } from '../api/queries'
import { ApiError } from '../api/client'
import type { FixtureView } from '../api/types'
import { GameweekPicker } from '../components/GameweekPicker'
import { MatchCard } from '../components/MatchCard'
import { kickoffDayLabel } from '../lib/format'
import { useT } from '../i18n'

function groupByDay(fixtures: FixtureView[]): Map<string, FixtureView[]> {
  const groups = new Map<string, FixtureView[]>()
  for (const fixture of fixtures) {
    const day = kickoffDayLabel(fixture.kickoffUtc)
    const group = groups.get(day) ?? []
    group.push(fixture)
    groups.set(day, group)
  }
  return groups
}

export function PredictPage() {
  const t = useT()
  const [selectedGameweek, setSelectedGameweek] = useState<number | null>(null)
  const { data: history } = useGameweekHistory()
  const current = useCurrentGameweek()
  const past = useGameweek(selectedGameweek)
  const { data: gameweek, isPending, error } = selectedGameweek ? past : current
  const predict = usePredictMutation(gameweek?.id ?? 0)

  if (isPending) {
    return <p className="p-6 text-center text-slate-400">{t('predict.loadingFixtures')}</p>
  }
  if (error || !gameweek) {
    const message =
      error instanceof ApiError && error.status === 404
        ? t('predict.noGameweek')
        : t('predict.loadFailed')
    return <p className="p-6 text-center text-slate-400">{message}</p>
  }

  const groups = groupByDay(gameweek.fixtures)
  const predicted = gameweek.fixtures.filter((f) => f.prediction != null).length
  const myPoints = gameweek.fixtures.reduce((sum, f) => sum + (f.prediction?.points ?? 0), 0)

  return (
    <div className="space-y-5 p-4">
      <header className="flex items-start justify-between gap-3 pt-2">
        <div className="min-w-0">
          <h1 className="text-xl font-bold">
            {t('predict.title', { index: gameweek.weekIndex })}
            <span className="ml-2 align-middle text-xs font-medium uppercase tracking-wide text-slate-400">
              {gameweek.season}
            </span>
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {t('predict.progress', { predicted, total: gameweek.fixtures.length })}
            {gameweek.status === 'SCORED' && <span className="ml-2 text-emerald-400">· {t('common.points', { count: myPoints })}</span>}
          </p>
        </div>
        <GameweekPicker history={history} value={selectedGameweek ?? gameweek.id} onChange={setSelectedGameweek} />
      </header>

      {[...groups.entries()].map(([day, fixtures]) => (
        <section key={day} className="space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">{day}</h2>
          {fixtures.map((fixture) => (
            <MatchCard
              key={fixture.fixtureId}
              fixture={fixture}
              onSave={(fixtureId, homeGoals, awayGoals) =>
                predict.mutateAsync({ fixtureId, homeGoals, awayGoals })
              }
            />
          ))}
        </section>
      ))}
    </div>
  )
}
