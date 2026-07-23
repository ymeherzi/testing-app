package com.predictor.league;

import com.predictor.gameweek.Gameweek;
import com.predictor.gameweek.GameweekRepository;
import com.predictor.league.LeagueDtos.LeagueDetail;
import com.predictor.league.LeagueDtos.LeagueSummary;
import com.predictor.league.LeagueTableService.MemberEntry;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeagueService {

    private static final int CODE_RETRIES = 10;

    private final LeagueRepository leagues;
    private final LeagueMemberRepository members;
    private final GameweekRepository gameweeks;
    private final LeagueTableService tableService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LeagueService(LeagueRepository leagues, LeagueMemberRepository members,
                         GameweekRepository gameweeks, LeagueTableService tableService, Clock clock) {
        this.leagues = leagues;
        this.members = members;
        this.gameweeks = gameweeks;
        this.tableService = tableService;
        this.clock = clock;
    }

    @Transactional
    public LeagueDetail create(long userId, String name) {
        League league = leagues.save(new League(name.trim(), uniqueCode(), userId));
        Instant now = clock.instant();
        members.save(new LeagueMember(league, userId, resolveJoinGameweekId(now), now));
        return detail(userId, league.getId());
    }

    @Transactional
    public LeagueDetail join(long userId, String rawCode) {
        League league = leagues.findByInviteCode(InviteCodes.normalize(rawCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite code not found"));
        if (members.existsByLeagueIdAndUserId(league.getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already in this league");
        }
        if (members.countByLeagueId(league.getId()) >= league.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This league is full");
        }
        Instant now = clock.instant();
        members.save(new LeagueMember(league, userId, resolveJoinGameweekId(now), now));
        return detail(userId, league.getId());
    }

    @Transactional
    public void leave(long userId, long leagueId) {
        LeagueMember membership = members.findByLeagueIdAndUserId(leagueId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
        League league = membership.getLeague();
        if (league.getAdminUserId().equals(userId)) {
            if (members.countByLeagueId(leagueId) > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The league admin can only leave once everyone else has left; leaving then deletes the league");
            }
            leagues.delete(league); // cascade removes the membership
            return;
        }
        members.delete(membership);
    }

    @Transactional(readOnly = true)
    public List<LeagueSummary> myLeagues(long userId) {
        List<LeagueSummary> summaries = new ArrayList<>();
        for (LeagueMember membership : members.findByUserIdOrderByJoinedAtAsc(userId)) {
            League league = membership.getLeague();
            List<MemberEntry> table = tableService.leagueMembersTable(league.getId());
            MemberEntry me = table.stream().filter(e -> e.userId() == userId).findFirst().orElse(null);
            summaries.add(new LeagueSummary(league.getId(), league.getName(), league.getInviteCode(),
                    league.getAdminUserId().equals(userId), table.size(),
                    me == null ? null : me.rank(), me == null ? 0 : me.points()));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public LeagueDetail detail(long userId, long leagueId) {
        // Non-members get 404, not 403: league ids must not be enumerable.
        if (!members.existsByLeagueIdAndUserId(leagueId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
        }
        League league = leagues.findById(leagueId).orElseThrow();
        List<MemberEntry> table = tableService.leagueMembersTable(leagueId);
        MemberEntry me = table.stream().filter(e -> e.userId() == userId).findFirst().orElse(null);
        return new LeagueDetail(league.getId(), league.getName(), league.getInviteCode(),
                league.getAdminUserId().equals(userId), league.getMaxMembers(), table, me);
    }

    @Transactional
    public LeagueDetail regenerateCode(long userId, long leagueId) {
        if (!members.existsByLeagueIdAndUserId(leagueId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
        }
        League league = leagues.findById(leagueId).orElseThrow();
        if (!league.getAdminUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the league admin can change the invite code");
        }
        league.setInviteCode(uniqueCode());
        return detail(userId, leagueId);
    }

    private Long resolveJoinGameweekId(Instant now) {
        return gameweeks.findFirstByWindowEndAfterOrderByWindowStartAsc(now)
                .map(Gameweek::getId)
                .orElse(null);
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < CODE_RETRIES; attempt++) {
            String code = InviteCodes.generate(random);
            if (!leagues.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique invite code");
    }
}
