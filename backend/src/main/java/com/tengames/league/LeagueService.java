package com.tengames.league;

import com.tengames.gameweek.Gameweek;
import com.tengames.gameweek.GameweekRepository;
import com.tengames.league.LeagueDtos.LeagueDetail;
import com.tengames.league.LeagueDtos.LeagueSummary;
import com.tengames.league.LeagueTableService.MemberEntry;
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
    private final com.tengames.user.UserRepository users;
    private final GameweekRepository gameweeks;
    private final LeagueTableService tableService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LeagueService(LeagueRepository leagues, LeagueMemberRepository members,
                         com.tengames.user.UserRepository users,
                         GameweekRepository gameweeks, LeagueTableService tableService, Clock clock) {
        this.leagues = leagues;
        this.members = members;
        this.users = users;
        this.gameweeks = gameweeks;
        this.tableService = tableService;
        this.clock = clock;
    }

    @Transactional
    public LeagueDetail create(long userId, String name) {
        League league = leagues.save(new League(name.trim(), uniqueCode(), userId));
        Instant now = clock.instant();
        members.save(new LeagueMember(league, userId, resolveJoinGameweekId(now), now));
        return detailOf(userId, league);
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
        return detailOf(userId, league);
    }

    @Transactional
    public void leave(long userId, java.util.UUID publicId) {
        long leagueId = require(publicId).getId();
        LeagueMember membership = members.findByLeagueIdAndUserId(leagueId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
        League league = membership.getLeague();
        if (league.getAdminUserId().equals(userId)) {
            if (members.countByLeagueId(leagueId) > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The league admin can only leave once everyone else has left; leaving then deletes the league");
            }
            // the membership goes through JPA rather than the database cascade:
            // it is loaded in this session, and leaving it behind would leave a
            // managed row pointing at a league that no longer exists
            members.delete(membership);
            leagues.delete(league);
            return;
        }
        members.delete(membership);
    }

    @Transactional(readOnly = true)
    public List<LeagueSummary> myLeagues(long userId) {
        java.util.UUID publicId = publicIdOf(userId);
        List<LeagueSummary> summaries = new ArrayList<>();
        for (LeagueMember membership : members.findByUserIdOrderByJoinedAtAsc(userId)) {
            League league = membership.getLeague();
            List<MemberEntry> table = tableService.leagueMembersTable(league.getId());
            MemberEntry me = table.stream().filter(e -> e.userId().equals(publicId)).findFirst().orElse(null);
            summaries.add(new LeagueSummary(league.getPublicId(), league.getName(), league.getInviteCode(),
                    league.getAdminUserId().equals(userId), table.size(),
                    me == null ? null : me.rank(), me == null ? 0 : me.points()));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public LeagueDetail detail(long userId, java.util.UUID publicId) {
        return detailOf(userId, require(publicId));
    }

    private LeagueDetail detailOf(long userId, League league) {
        long leagueId = league.getId();
        // Non-members get 404, not 403: a league must not be enumerable.
        if (!members.existsByLeagueIdAndUserId(leagueId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
        }
        List<MemberEntry> table = tableService.leagueMembersTable(leagueId);
        java.util.UUID viewer = publicIdOf(userId);
        MemberEntry me = table.stream().filter(e -> e.userId().equals(viewer)).findFirst().orElse(null);
        return new LeagueDetail(league.getPublicId(), league.getName(), league.getInviteCode(),
                league.getAdminUserId().equals(userId), league.getMaxMembers(), table, me);
    }

    /**
     * The league behind a public id — or the same 404 a non-member gets, so
     * that an unknown id and someone else's league are indistinguishable.
     */
    private League require(java.util.UUID publicId) {
        return leagues.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
    }

    @Transactional
    public LeagueDetail regenerateCode(long userId, java.util.UUID publicId) {
        League league = require(publicId);
        if (!members.existsByLeagueIdAndUserId(league.getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
        }
        if (!league.getAdminUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the league admin can change the invite code");
        }
        league.setInviteCode(uniqueCode());
        return detailOf(userId, league);
    }

    private java.util.UUID publicIdOf(long userId) {
        return users.findById(userId).orElseThrow().getPublicId();
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
