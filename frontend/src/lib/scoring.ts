/**
 * Mirror of the backend ScoringEngine (docs/DESIGN.md §2.1), used to show
 * provisional "on course" points while a match is live. The backend remains
 * the source of truth for official points at full-time.
 */
export function score(predHome: number, predAway: number, actualHome: number, actualAway: number): 0 | 1 | 2 | 3 {
  if (predHome === actualHome && predAway === actualAway) {
    return 3
  }
  const predDiff = predHome - predAway
  const actualDiff = actualHome - actualAway
  if (Math.sign(predDiff) !== Math.sign(actualDiff)) {
    return 0
  }
  if (predDiff === 0) {
    return 1 // drawn draw, wrong scoreline — never the goal-difference tier
  }
  return predDiff === actualDiff ? 2 : 1
}
