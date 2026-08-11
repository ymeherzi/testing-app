package com.tengames.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The competitions the app follows, for the two dropdowns that need them:
 * narrowing the club picker, and naming a favourite championship.
 */
@RestController
public class CompetitionController {

    private final CompetitionRepository competitions;

    public CompetitionController(CompetitionRepository competitions) {
        this.competitions = competitions;
    }

    public record CompetitionResponse(Long id, String code, String name, boolean domestic) {

        static CompetitionResponse from(Competition competition) {
            return new CompetitionResponse(competition.getId(), competition.getCode(),
                    competition.getName(), competition.isDomestic());
        }
    }

    /**
     * @param domesticOnly leagues only. What "my championship" means: nobody
     *                     supports the Coupe de France.
     */
    @GetMapping("/api/competitions")
    public List<CompetitionResponse> list(@RequestParam(required = false) Boolean domesticOnly) {
        List<Competition> found = Boolean.TRUE.equals(domesticOnly)
                ? competitions.findByDomesticTrueOrderByNameAsc()
                : competitions.findAllByOrderByNameAsc();
        return found.stream().map(CompetitionResponse::from).toList();
    }
}
