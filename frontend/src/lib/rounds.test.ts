import { describe, expect, it } from 'vitest'
import { midweekWindow, planRounds, weekendWindow } from './rounds'

describe('the next round to prepare', () => {
  it('runs from Friday to Monday', () => {
    // Monday 24 August 2026, the night the round before it ended
    expect(weekendWindow('2026-08-24')).toEqual({ from: '2026-08-28', to: '2026-08-31' })
  })

  it('never proposes the day it starts from', () => {
    // asked from a Friday, it means the *next* one, not today
    expect(weekendWindow('2026-08-28')).toEqual({ from: '2026-09-04', to: '2026-09-07' })
  })

  it('runs from Tuesday to Thursday in a European week', () => {
    expect(midweekWindow('2026-08-31')).toEqual({ from: '2026-09-01', to: '2026-09-03' })
  })

  it('crosses months and years without drifting', () => {
    expect(weekendWindow('2026-12-28')).toEqual({ from: '2027-01-01', to: '2027-01-04' })
    expect(midweekWindow('2026-09-28')).toEqual({ from: '2026-09-29', to: '2026-10-01' })
  })
})

describe('planning the season', () => {
  const played = [
    {
      season: '2026-27', weekIndex: 0, type: 'WEEKEND',
      windowStart: '2026-08-12T00:00:00Z', windowEnd: '2026-08-18T23:59:59Z',
      countsTowardsTable: false,
    },
    {
      season: '2026-27', weekIndex: 1, type: 'WEEKEND',
      windowStart: '2026-08-21T00:00:00Z', windowEnd: '2026-08-24T23:59:59Z',
      countsTowardsTable: true,
    },
  ]

  it('carries on where the season stopped', () => {
    const plan = planRounds(played, ['WEEKEND', 'WEEKEND', 'WEEKEND'], '2026-08-25')

    expect(plan.map((r) => r.weekIndex)).toEqual([0, 1, 2, 3, 4])
    expect(plan[2]).toMatchObject({ weekIndex: 2, from: '2026-08-28', to: '2026-08-31' })
    // each round is dated from the end of the one before it, never overlapping
    expect(plan[3]).toMatchObject({ from: '2026-09-04', to: '2026-09-07' })
    expect(plan[4]).toMatchObject({ from: '2026-09-11', to: '2026-09-14' })
  })

  it('slots a European week in without disturbing the numbering', () => {
    const plan = planRounds(played, ['WEEKEND', 'MIDWEEK', 'WEEKEND'], '2026-08-25')

    expect(plan[3]).toMatchObject({ weekIndex: 3, type: 'MIDWEEK', from: '2026-09-01', to: '2026-09-03' })
    expect(plan[4]).toMatchObject({ weekIndex: 4, type: 'WEEKEND', from: '2026-09-04', to: '2026-09-07' })
  })

  it('starts from today when the last round on record is long gone', () => {
    const plan = planRounds(played, ['WEEKEND'], '2026-10-05')

    expect(plan[2]).toMatchObject({ weekIndex: 2, from: '2026-10-09', to: '2026-10-12' })
  })

  it('names the season when there is nothing to go on', () => {
    expect(planRounds([], ['WEEKEND'], '2026-08-25')[0].season).toBe('2026-27')
    expect(planRounds([], ['WEEKEND'], '2027-03-01')[0].season).toBe('2026-27')
  })
})
