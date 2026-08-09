package com.prono10.scoring;

/**
 * Pure implementation of the scoring rules (docs/DESIGN.md §2.1):
 *
 * <pre>
 * exact scoreline                                  → 3
 * correct goal difference, wins only               → 2
 * correct outcome only (incl. any drawn draw)      → 1
 * wrong outcome                                    → 0
 * </pre>
 *
 * The draw rule is deliberate: every draw has goal difference 0, so without a
 * special case any correct draw prediction would land in the 2-point tier.
 * The agreed rule is that a draw prediction with the wrong scoreline is worth
 * exactly 1 point — draws never reach the goal-difference tier.
 */
public final class ScoringEngine {

    public static final int EXACT = 3;
    public static final int GOAL_DIFFERENCE = 2;
    public static final int OUTCOME = 1;
    public static final int MISS = 0;

    private ScoringEngine() {
    }

    public static int score(int predictedHome, int predictedAway, int actualHome, int actualAway) {
        if (predictedHome == actualHome && predictedAway == actualAway) {
            return EXACT;
        }
        int predictedDiff = predictedHome - predictedAway;
        int actualDiff = actualHome - actualAway;
        if (Integer.signum(predictedDiff) != Integer.signum(actualDiff)) {
            return MISS;
        }
        if (predictedDiff == 0) {
            return OUTCOME; // drawn draw, wrong scoreline — never the GD tier
        }
        return predictedDiff == actualDiff ? GOAL_DIFFERENCE : OUTCOME;
    }
}
