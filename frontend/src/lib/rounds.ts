/**
 * Default windows for the rounds still to prepare.
 *
 * <p>Dates only, in UTC: a round is scheduled by the day it opens and the day
 * it ends, and the composer lets the editor move either edge afterwards.
 */

const DAY = 24 * 60 * 60 * 1000

function parse(isoDate: string): Date {
  return new Date(`${isoDate}T00:00:00Z`)
}

function iso(date: Date): string {
  return date.toISOString().slice(0, 10)
}

/** The next given weekday (0 = Sunday) strictly after this date. */
function next(after: string, weekday: number): Date {
  const start = parse(after)
  const ahead = (weekday - start.getUTCDay() + 7) % 7 || 7
  return new Date(start.getTime() + ahead * DAY)
}

/**
 * Friday to Monday: a round routinely opens on a Friday night and ends with a
 * Monday-night game, and a fixture outside the window can never be picked.
 */
export function weekendWindow(after: string): { from: string; to: string } {
  const friday = next(after, 5)
  return { from: iso(friday), to: iso(new Date(friday.getTime() + 3 * DAY)) }
}

/** Tuesday to Thursday: the Champions League nights, then the Europa ones. */
export function midweekWindow(after: string): { from: string; to: string } {
  const tuesday = next(after, 2)
  return { from: iso(tuesday), to: iso(new Date(tuesday.getTime() + 2 * DAY)) }
}

/** The season label a round created today belongs to, e.g. "2026-27". */
export function currentSeason(today: string): string {
  const date = parse(today)
  const year = date.getUTCFullYear()
  // July onwards belongs to the season that is about to start
  const start = date.getUTCMonth() >= 6 ? year : year - 1
  return `${start}-${String((start + 1) % 100).padStart(2, '0')}`
}

export type RoundKind = 'WEEKEND' | 'MIDWEEK'

export interface PlannedRound {
  season: string
  weekIndex: number
  type: RoundKind
  from: string
  to: string
  countsTowardsTable: boolean
}

interface ExistingRound {
  season: string
  weekIndex: number
  type: string
  windowStart: string
  windowEnd: string
  countsTowardsTable: boolean
}

/**
 * The season so far, then the rounds still to prepare.
 *
 * <p>Existing rounds keep the dates they were given; the ones ahead are dated
 * from the end of the round before them, so a card is never proposed over a
 * weekend that is already spoken for.
 */
export function planRounds(existing: ExistingRound[], upcoming: RoundKind[], today: string): PlannedRound[] {
  const season = existing.map((gw) => gw.season).sort().at(-1) ?? currentSeason(today)
  const rounds: PlannedRound[] = existing
    .filter((gw) => gw.season === season)
    .sort((a, b) => a.weekIndex - b.weekIndex)
    .map((gw) => ({
      season: gw.season,
      weekIndex: gw.weekIndex,
      type: gw.type === 'MIDWEEK' ? 'MIDWEEK' : 'WEEKEND',
      from: gw.windowStart.slice(0, 10),
      to: gw.windowEnd.slice(0, 10),
      countsTowardsTable: gw.countsTowardsTable,
    }))

  // never behind today: a fresh round is prepared forwards, even when the last
  // one on record ended weeks ago
  const last = rounds.at(-1)
  let cursor = last && last.to > today ? last.to : today
  let weekIndex = (last?.weekIndex ?? -1) + 1
  for (const kind of upcoming) {
    const window = kind === 'MIDWEEK' ? midweekWindow(cursor) : weekendWindow(cursor)
    rounds.push({ season, weekIndex, type: kind, ...window, countsTowardsTable: true })
    cursor = window.to
    weekIndex += 1
  }
  return rounds
}
