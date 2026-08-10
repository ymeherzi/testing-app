import { describe, expect, it } from 'vitest'
import { pointsFor, tierFor, type ScoringScale } from './scoring'

// mirrors the backend ScoringEngineTest table
describe('tierFor', () => {
  it('exact scoreline', () => {
    expect(tierFor(2, 1, 2, 1)).toBe('EXACT')
    expect(tierFor(0, 0, 0, 0)).toBe('EXACT')
  })

  it('correct goal difference on wins', () => {
    expect(tierFor(2, 1, 3, 2)).toBe('GOAL_DIFFERENCE')
    expect(tierFor(0, 2, 1, 3)).toBe('GOAL_DIFFERENCE')
  })

  it('correct outcome only, including the draw rule', () => {
    expect(tierFor(2, 0, 1, 0)).toBe('OUTCOME')
    expect(tierFor(1, 1, 2, 2)).toBe('OUTCOME') // drawn draw, wrong line — never the GD tier
    expect(tierFor(0, 0, 3, 3)).toBe('OUTCOME')
  })

  it('wrong outcome', () => {
    expect(tierFor(2, 1, 1, 2)).toBe('MISS')
    expect(tierFor(1, 1, 2, 1)).toBe('MISS')
    expect(tierFor(2, 1, 1, 1)).toBe('MISS')
  })
})

describe('pointsFor', () => {
  const scale: ScoringScale = { EXACT: 3, GOAL_DIFFERENCE: 2, OUTCOME: 1, MISS: 0 }

  it('reads the value from the scale the server published', () => {
    expect(pointsFor(scale, 2, 1, 2, 1)).toBe(3)
    expect(pointsFor(scale, 2, 1, 3, 2)).toBe(2)
    expect(pointsFor(scale, 2, 0, 1, 0)).toBe(1)
    expect(pointsFor(scale, 2, 1, 1, 2)).toBe(0)
  })

  it('follows the scale rather than hard-coded numbers', () => {
    // the whole point of publishing it: change the values server-side and the
    // app follows, instead of quietly disagreeing with the official points
    const doubled: ScoringScale = { EXACT: 6, GOAL_DIFFERENCE: 4, OUTCOME: 2, MISS: 0 }
    expect(pointsFor(doubled, 2, 1, 2, 1)).toBe(6)
    expect(pointsFor(doubled, 2, 1, 3, 2)).toBe(4)
  })

  it('shows nothing until the scale has loaded', () => {
    expect(pointsFor(undefined, 2, 1, 2, 1)).toBeNull()
  })
})
