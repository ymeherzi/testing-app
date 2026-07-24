import { describe, expect, it } from 'vitest'
import { score } from './scoring'

// mirrors the backend ScoringEngineTest table
describe('score', () => {
  it('exact scoreline is 3', () => {
    expect(score(2, 1, 2, 1)).toBe(3)
    expect(score(0, 0, 0, 0)).toBe(3)
  })

  it('correct goal difference on wins is 2', () => {
    expect(score(2, 1, 3, 2)).toBe(2)
    expect(score(0, 2, 1, 3)).toBe(2)
  })

  it('correct outcome only is 1, including the draw rule', () => {
    expect(score(2, 0, 1, 0)).toBe(1)
    expect(score(1, 1, 2, 2)).toBe(1) // drawn draw, wrong line — never 2
    expect(score(0, 0, 3, 3)).toBe(1)
  })

  it('wrong outcome is 0', () => {
    expect(score(2, 1, 1, 2)).toBe(0)
    expect(score(1, 1, 2, 1)).toBe(0)
    expect(score(2, 1, 1, 1)).toBe(0)
  })
})
