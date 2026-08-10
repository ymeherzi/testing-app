/**
 * Which tier a prediction falls into, mirroring the backend ScoringEngine
 * (docs/DESIGN.md §2.1) so a live match can show "on course" points without
 * a round trip.
 *
 * Only the *shape* of the rules lives here. What each tier is worth comes
 * from the server (`GET /api/rules/scoring`), so the numbers have exactly one
 * home — `ScoringEngine` — and cannot drift between the two sides.
 */
export type ScoringTier = 'EXACT' | 'GOAL_DIFFERENCE' | 'OUTCOME' | 'MISS'

export type ScoringScale = Record<ScoringTier, number>

export function tierFor(
  predHome: number,
  predAway: number,
  actualHome: number,
  actualAway: number,
): ScoringTier {
  if (predHome === actualHome && predAway === actualAway) {
    return 'EXACT'
  }
  const predDiff = predHome - predAway
  const actualDiff = actualHome - actualAway
  if (Math.sign(predDiff) !== Math.sign(actualDiff)) {
    return 'MISS'
  }
  if (predDiff === 0) {
    return 'OUTCOME' // drawn draw, wrong scoreline — never the goal-difference tier
  }
  return predDiff === actualDiff ? 'GOAL_DIFFERENCE' : 'OUTCOME'
}

/** Points for a prediction, given the scale the server published. */
export function pointsFor(
  scale: ScoringScale | undefined,
  predHome: number,
  predAway: number,
  actualHome: number,
  actualAway: number,
): number | null {
  if (!scale) {
    return null // scale not loaded yet: show nothing rather than a wrong number
  }
  return scale[tierFor(predHome, predAway, actualHome, actualAway)]
}
