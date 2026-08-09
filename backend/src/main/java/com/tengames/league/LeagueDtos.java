package com.tengames.league;

import com.tengames.league.LeagueTableService.MemberEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class LeagueDtos {

    private LeagueDtos() {
    }

    public record CreateLeagueRequest(@NotBlank @Size(min = 3, max = 60) String name) {
    }

    public record JoinLeagueRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record LeagueSummary(long id, String name, String inviteCode, boolean admin,
                                long memberCount, Long myRank, long myPoints) {
    }

    public record LeagueDetail(long id, String name, String inviteCode, boolean admin,
                               int maxMembers, List<MemberEntry> members, MemberEntry me) {
    }
}
