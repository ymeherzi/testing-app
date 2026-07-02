package com.predictor.scoring;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineProperties {

    @Property
    void threePointsIffExact(@ForAll @IntRange(max = 9) int ph, @ForAll @IntRange(max = 9) int pa,
                             @ForAll @IntRange(max = 9) int ah, @ForAll @IntRange(max = 9) int aa) {
        boolean exact = ph == ah && pa == aa;
        assertThat(ScoringEngine.score(ph, pa, ah, aa) == 3).isEqualTo(exact);
    }

    @Property
    void twoPointsImpliesNonDrawWithEqualGoalDifference(@ForAll @IntRange(max = 9) int ph, @ForAll @IntRange(max = 9) int pa,
                                                        @ForAll @IntRange(max = 9) int ah, @ForAll @IntRange(max = 9) int aa) {
        if (ScoringEngine.score(ph, pa, ah, aa) == 2) {
            assertThat(ph - pa).isEqualTo(ah - aa);
            assertThat(ph).isNotEqualTo(pa);
        }
    }

    @Property
    void inexactDrawnDrawIsExactlyOnePoint(@ForAll @IntRange(max = 9) int p, @ForAll @IntRange(max = 9) int a) {
        if (p != a) {
            assertThat(ScoringEngine.score(p, p, a, a)).isEqualTo(1);
        }
    }

    @Property
    void zeroPointsIffDifferentOutcome(@ForAll @IntRange(max = 9) int ph, @ForAll @IntRange(max = 9) int pa,
                                       @ForAll @IntRange(max = 9) int ah, @ForAll @IntRange(max = 9) int aa) {
        boolean sameOutcome = Integer.signum(ph - pa) == Integer.signum(ah - aa);
        assertThat(ScoringEngine.score(ph, pa, ah, aa) == 0).isEqualTo(!sameOutcome);
    }

    @Property
    void symmetricUnderHomeAwaySwap(@ForAll @IntRange(max = 9) int ph, @ForAll @IntRange(max = 9) int pa,
                                    @ForAll @IntRange(max = 9) int ah, @ForAll @IntRange(max = 9) int aa) {
        assertThat(ScoringEngine.score(ph, pa, ah, aa))
                .isEqualTo(ScoringEngine.score(pa, ph, aa, ah));
    }
}
