package com.tengames.scoring;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the scoring scale so the app never restates it.
 *
 * <p>The numbers exist once, in {@link ScoringEngine}. The client knows the
 * shape of the rules (which tier a prediction falls into) but asks the server
 * what each tier is worth, so changing a value here changes it everywhere —
 * rules screen, welcome guide and the provisional points shown during a live
 * match alike.
 */
@RestController
@RequestMapping("/api/rules")
public class ScoringController {

    @GetMapping("/scoring")
    public Map<String, Integer> scoring() {
        return Map.of(
                "EXACT", ScoringEngine.EXACT,
                "GOAL_DIFFERENCE", ScoringEngine.GOAL_DIFFERENCE,
                "OUTCOME", ScoringEngine.OUTCOME,
                "MISS", ScoringEngine.MISS);
    }
}
