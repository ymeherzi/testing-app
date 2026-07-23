package com.predictor.league;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Ranking queries. Public leagues are dynamic views (design §5.1): the same
 * ranked aggregate over users' scored predictions, unfiltered (global) or
 * filtered by the caller's country / favourite club. Private league tables
 * additionally window each member's points to gameweeks from their join
 * gameweek onward (design §5.3).
 */
@Service
public class LeagueTableService {

    private final JdbcClient jdbc;

    public LeagueTableService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(long rank, long userId, String displayName, String country,
                        long points, long scoredPredictions) {
    }

    public record Table(List<Entry> entries, Entry me, int page, int size, long totalPlayers) {
    }

    /** Country/club table wrapper; available=false when the caller hasn't set the attribute. */
    public record ScopedTable(boolean available, String country, Long clubTeamId,
                              String clubName, String clubCrestUrl, Table table) {

        static ScopedTable unavailable() {
            return new ScopedTable(false, null, null, null, null, null);
        }
    }

    public record MemberEntry(long rank, long userId, String displayName, String country,
                              long points, long scoredPredictions, boolean admin) {
    }

    private static final String TOTALS_FILTERED = """
            select u.id, u.display_name, u.country,
                   coalesce(sum(p.points), 0) as points,
                   count(p.points) as scored,
                   rank() over (order by coalesce(sum(p.points), 0) desc) as rnk
            from users u
            left join predictions p on p.user_id = u.id
            %s
            group by u.id, u.display_name, u.country
            """;

    public Table globalTable(long currentUserId, int page, int size) {
        return rankedTable("", null, currentUserId, page, size, "select count(*) from users");
    }

    public ScopedTable countryTable(long currentUserId, int page, int size) {
        String country = jdbc.sql("select country from users where id = :id")
                .param("id", currentUserId).query(String.class).optional().orElse(null);
        if (country == null) {
            return ScopedTable.unavailable();
        }
        Table table = rankedTable("where u.country = :filter", country, currentUserId, page, size,
                "select count(*) from users where country = :filter");
        return new ScopedTable(true, country, null, null, null, table);
    }

    public ScopedTable clubTable(long currentUserId, int page, int size) {
        record Club(Long id, String name, String crest) {
        }
        Club club = jdbc.sql("""
                        select t.id, t.name, t.crest_url from users u
                        join teams t on t.id = u.favourite_club_team_id
                        where u.id = :id""")
                .param("id", currentUserId)
                .query((rs, i) -> new Club(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .optional().orElse(null);
        if (club == null) {
            return ScopedTable.unavailable();
        }
        Table table = rankedTable("where u.favourite_club_team_id = :filter", club.id(), currentUserId, page, size,
                "select count(*) from users where favourite_club_team_id = :filter");
        return new ScopedTable(true, null, club.id(), club.name(), club.crest(), table);
    }

    private Table rankedTable(String whereClause, Object filter, long currentUserId,
                              int page, int size, String countSql) {
        String totals = TOTALS_FILTERED.formatted(whereClause);
        var pageQuery = jdbc.sql(totals + " order by rnk, lower(u.display_name) limit :limit offset :offset")
                .param("limit", size).param("offset", page * size);
        var meQuery = jdbc.sql("select * from (" + totals + ") t where t.id = :userId")
                .param("userId", currentUserId);
        var countQuery = jdbc.sql(countSql);
        if (filter != null) {
            pageQuery = pageQuery.param("filter", filter);
            meQuery = meQuery.param("filter", filter);
            countQuery = countQuery.param("filter", filter);
        }
        List<Entry> entries = pageQuery.query(this::mapEntry).list();
        Entry me = meQuery.query(this::mapEntry).optional().orElse(null);
        long totalPlayers = countQuery.query(Long.class).single();
        return new Table(entries, me, page, size, totalPlayers);
    }

    /**
     * Private league standings. The join-gameweek window lives in the JOIN
     * condition so members with zero in-window points still appear with 0.
     * Global totals are untouched — late joiners keep their full points
     * everywhere else.
     */
    public List<MemberEntry> leagueMembersTable(long leagueId) {
        return jdbc.sql("""
                        select u.id, u.display_name, u.country,
                               (m.user_id = l.admin_user_id) as is_admin,
                               coalesce(sum(sp.points), 0) as points,
                               count(sp.points) as scored,
                               rank() over (order by coalesce(sum(sp.points), 0) desc) as rnk
                        from league_members m
                        join leagues l on l.id = m.league_id
                        join users u on u.id = m.user_id
                        left join gameweeks jgw on jgw.id = m.join_gameweek_id
                        left join (
                            select p.user_id, p.points, gw.window_start
                            from predictions p
                            join gameweek_fixtures gf on gf.id = p.gameweek_fixture_id
                            join gameweeks gw on gw.id = gf.gameweek_id
                        ) sp on sp.user_id = u.id
                           and (m.join_gameweek_id is null or sp.window_start >= jgw.window_start)
                        where m.league_id = :leagueId
                        group by u.id, u.display_name, u.country, m.user_id, l.admin_user_id
                        order by rnk, lower(u.display_name)
                        """)
                .param("leagueId", leagueId)
                .query((rs, i) -> new MemberEntry(rs.getLong("rnk"), rs.getLong("id"),
                        rs.getString("display_name"), rs.getString("country"),
                        rs.getLong("points"), rs.getLong("scored"), rs.getBoolean("is_admin")))
                .list();
    }

    private Entry mapEntry(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Entry(rs.getLong("rnk"), rs.getLong("id"), rs.getString("display_name"),
                rs.getString("country"), rs.getLong("points"), rs.getLong("scored"));
    }
}
