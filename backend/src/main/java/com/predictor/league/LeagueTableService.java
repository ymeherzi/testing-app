package com.predictor.league;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The global public league is a dynamic view (design §5.1): a ranking query
 * over all users' scored predictions, no membership rows. Country and club
 * leagues will be the same query with a WHERE clause.
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

    private static final String TOTALS = """
            select u.id, u.display_name, u.country,
                   coalesce(sum(p.points), 0) as points,
                   count(p.points) as scored,
                   rank() over (order by coalesce(sum(p.points), 0) desc) as rnk
            from users u
            left join predictions p on p.user_id = u.id
            group by u.id, u.display_name, u.country
            """;

    public Table globalTable(long currentUserId, int page, int size) {
        List<Entry> entries = jdbc.sql(TOTALS + " order by rnk, lower(u.display_name) limit :limit offset :offset")
                .param("limit", size)
                .param("offset", page * size)
                .query(this::mapEntry)
                .list();
        Entry me = jdbc.sql("select * from (" + TOTALS + ") t where t.id = :userId")
                .param("userId", currentUserId)
                .query(this::mapEntry)
                .optional()
                .orElse(null);
        long totalPlayers = jdbc.sql("select count(*) from users")
                .query(Long.class)
                .single();
        return new Table(entries, me, page, size, totalPlayers);
    }

    private Entry mapEntry(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Entry(rs.getLong("rnk"), rs.getLong("id"), rs.getString("display_name"),
                rs.getString("country"), rs.getLong("points"), rs.getLong("scored"));
    }
}
