import { useState } from 'react'
import { useAdminActions, useAdminSuggestions, useCompetitions } from '../api/queries'
import type { GameweekView, MatchView, SuggestionView, Team } from '../api/types'
import { TeamBadge } from './TeamBadge'
import { kickoffDayLabel, kickoffTimeLabel } from '../lib/format'
import { useT } from '../i18n'
import { readStored, writeStored } from '../lib/storage'

export interface RoundSpec {
  title: string
  season: string
  weekIndex: number
  /**
   * ISO dates, inclusive, for both the window and the suggestion pool. Only a
   * starting point: the editor sets the real dates on the screen, and a round
   * that runs to Monday night or opens on a Wednesday should not need a
   * developer.
   */
  from: string
  to: string
  countsTowardsTable: boolean
}

/** The dates the editor last chose for this round, kept between visits. */
function storedRange(spec: RoundSpec): { from: string; to: string } {
  const raw = readStored(`roundRange.${spec.season}-${spec.weekIndex}`)
  if (!raw) {
    return { from: spec.from, to: spec.to }
  }
  try {
    const saved = JSON.parse(raw) as { from?: string; to?: string }
    return { from: saved.from ?? spec.from, to: saved.to ?? spec.to }
  } catch {
    return { from: spec.from, to: spec.to }
  }
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
  footer,
}: {
  fixture: Fixture
  competition: string
  kickoffUtc: string
  reasons?: string[]
  action?: React.ReactNode
  /** Shown under the row: the confirmation before a fixture is taken off. */
  footer?: React.ReactNode
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
      {footer}
    </li>
  )
}

function AddFixtureButton({ open, onToggle, label }: { open: boolean; onToggle: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      className="rounded-xl border border-emerald-600 px-3 py-1.5 text-xs font-semibold text-emerald-300 active:bg-emerald-900/40"
    >
      {label}
    </button>
  )
}

/**
 * A round to prepare: one button composes it, then each fixture can be
 * swapped for another from the same window, or the card topped up with one
 * more.
 */
export function RoundComposer({ spec, existing }: { spec: RoundSpec; existing?: GameweekView }) {
  const t = useT()
  const { composeGameweek, removeFixture, replaceFixture, addFixtures, publish } = useAdminActions()
  const [swapping, setSwapping] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<number | null>(null)
  const [competition, setCompetition] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [range, setRange] = useState(() => storedRange(spec))
  const suggestions = useAdminSuggestions(range.from, range.to, swapping !== null || adding)

  /** Both the pool to choose from and the window a fresh card is composed over. */
  const setDate = (edge: 'from' | 'to', value: string) => {
    const next = { ...range, [edge]: value }
    setRange(next)
    writeStored(`roundRange.${spec.season}-${spec.weekIndex}`, JSON.stringify(next))
  }
  const { data: catalogue } = useCompetitions()

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
        windowStart: `${range.from}T00:00:00Z`,
        windowEnd: `${range.to}T23:59:59Z`,
        countsTowardsTable: spec.countsTowardsTable,
        size: 10,
      }),
    )

  /** Replaces one fixture, keeping the rest of the card — and its predictions — as it is. */
  const swap = (fixtureId: number, replacement: MatchView) => {
    if (!existing) {
      return
    }
    setSwapping(null)
    return run(() =>
      replaceFixture.mutateAsync({ gameweekId: existing.id, fixtureId, matchId: replacement.id }),
    )
  }

  /** Takes one fixture off the card. What players predicted on it goes too. */
  const remove = (fixtureId: number) => {
    if (!existing) {
      return
    }
    setRemoving(null)
    return run(() => removeFixture.mutateAsync({ gameweekId: existing.id, fixtureId }))
  }

  // one panel, two purposes: opening either mode closes the other, and the
  // filter starts fresh each time it opens
  const openAdd = () => {
    setSwapping(null)
    setCompetition('')
    setAdding(!adding)
  }
  const openSwap = (fixtureId: number) => {
    setAdding(false)
    setCompetition('')
    setSwapping(swapping === fixtureId ? null : fixtureId)
  }

  /** Adds one more fixture, leaving the rest of the card untouched. */
  const add = (match: MatchView) => {
    if (!existing) {
      return
    }
    setAdding(false)
    return run(() => addFixtures.mutateAsync({ gameweekId: existing.id, matchIds: [match.id] }))
  }

  const chosen = new Set(existing?.fixtures.map((f) => f.matchId) ?? [])
  const candidates = (suggestions.data ?? []).filter(
    (s: SuggestionView) =>
      !chosen.has(s.match.id) &&
      // a kicked-off match cannot be added to a card: nobody could predict it
      (!adding || new Date(s.match.kickoffUtc).getTime() > Date.now()),
  )
  // Every competition with something to offer in this window, in ranked order.
  // Built from the candidates themselves, so no option can lead to an empty
  // list, and the names come from the catalogue the app already loads.
  const competitionName = (code: string) =>
    catalogue?.find((c) => c.code === code)?.name ?? code
  const offered = [...new Set(candidates.map((s) => s.match.competitionCode))]
  const alternatives = competition
    ? candidates.filter((s) => s.match.competitionCode === competition)
    : // unfiltered, the top of the ranking is the useful part; a whole weekend
      // of fixtures is a list nobody reads to the end
      candidates.slice(0, 30)

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
            <AddFixtureButton open={adding} onToggle={openAdd} label={t('admin.addFixture')} />
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
          <div className="flex shrink-0 flex-col items-end gap-2">
            <span className="rounded-lg bg-slate-800 px-2 py-1 text-xs text-slate-300">{existing.status}</span>
            {/* a published round is exactly when a thin card shows; topping it
                up adds a fixture without disturbing the predictions already in */}
            {existing.status === 'PUBLISHED' && (
              <AddFixtureButton open={adding} onToggle={openAdd} label={t('admin.addFixture')} />
            )}
          </div>
        )}
      </header>

      {/* The dates the round draws from. Editable here rather than in the
          source: a Monday night fixture, a Wednesday super cup, a round that
          runs long — none of that should need a deployment. */}
      <div className="flex flex-wrap items-end gap-2">
        <label className="flex-1 space-y-1">
          <span className="text-xs text-slate-500">{t('admin.rangeFrom')}</span>
          <input
            type="date"
            value={range.from}
            onChange={(e) => setDate('from', e.target.value)}
            className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-emerald-500"
          />
        </label>
        <label className="flex-1 space-y-1">
          <span className="text-xs text-slate-500">{t('admin.rangeTo')}</span>
          <input
            type="date"
            value={range.to}
            onChange={(e) => setDate('to', e.target.value)}
            className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-emerald-500"
          />
        </label>
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      {existing && (
        <ul className="space-y-2">
          {existing.fixtures.map((fixture) => (
            <FixtureRow
              key={fixture.fixtureId}
              fixture={fixture}
              competition={fixture.competitionName}
              kickoffUtc={fixture.kickoffUtc}
              action={
                // a published round is exactly when a card is looked at properly:
                // one fixture too many, or one that has since been called off
                existing.status !== 'SCORED' ? (
                  <span className="flex shrink-0 items-center gap-3">
                    <button
                      type="button"
                      onClick={() => openSwap(fixture.fixtureId)}
                      className="text-xs font-medium text-emerald-400 underline"
                    >
                      {t('admin.replace')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setRemoving(removing === fixture.fixtureId ? null : fixture.fixtureId)}
                      className="text-xs font-medium text-red-400 underline"
                    >
                      {t('admin.removeFixture')}
                    </button>
                  </span>
                ) : undefined
              }
              footer={
                removing === fixture.fixtureId ? (
                  <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-slate-800 pt-2">
                    <p className="w-full text-xs text-amber-300">{t('admin.removeWarning')}</p>
                    <button
                      type="button"
                      onClick={() => remove(fixture.fixtureId)}
                      className="rounded-lg bg-red-500 px-3 py-1.5 text-xs font-semibold text-red-950"
                    >
                      {t('admin.confirmRemove')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setRemoving(null)}
                      className="text-xs font-medium text-slate-400"
                    >
                      {t('admin.cancel')}
                    </button>
                  </div>
                ) : undefined
              }
            />
          ))}
        </ul>
      )}

      {(swapping !== null || adding) && (
        <div className="space-y-2 rounded-xl border border-emerald-900/60 bg-slate-900/60 p-3">
          <p className="text-xs uppercase tracking-wide text-slate-500">
            {adding ? t('admin.pickAddition') : t('admin.pickReplacement')}
          </p>
          <p className="text-xs text-slate-400">
            {t('admin.candidateCount', { count: candidates.length })}
          </p>
          {offered.length > 1 && (
            <select
              value={competition}
              onChange={(e) => setCompetition(e.target.value)}
              aria-label={t('admin.filterCompetition')}
              className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-emerald-500"
            >
              <option value="">{t('admin.allCompetitions')}</option>
              {offered.map((code) => (
                <option key={code} value={code}>
                  {competitionName(code)}
                </option>
              ))}
            </select>
          )}
          {alternatives.length === 0 ? (
            <p className="text-sm text-slate-400">{t('admin.noAlternatives')}</p>
          ) : (
            <ul className="space-y-2">
              {alternatives.map((s) => (
                <FixtureRow
                  key={s.match.id}
                  fixture={s.match}
                  competition={competitionName(s.match.competitionCode)}
                  kickoffUtc={s.match.kickoffUtc}
                  reasons={s.reasons}
                  action={
                    <button
                      type="button"
                      onClick={() => (adding ? add(s.match) : swap(swapping!, s.match))}
                      className="shrink-0 rounded-lg bg-emerald-500 px-3 py-1 text-xs font-semibold text-emerald-950"
                    >
                      {adding ? t('admin.add') : t('admin.choose')}
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
