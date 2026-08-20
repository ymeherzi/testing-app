package com.tengames.user;

import com.tengames.common.web.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Removing an account.
 *
 * <p>Signups go wrong in ordinary ways — a mistyped address that will never
 * receive its code, someone who wants out — and until now the only cure was a
 * hand-written statement against the production database. That is not a thing
 * to do from a phone, and the address involved has no business being written
 * into a migration this repository publishes.
 *
 * <p>Deliberately narrow. It refuses to remove the account making the request,
 * and refuses to remove anyone who runs a private league, since the league
 * would lose its owner. Everything the account leaves behind goes with it:
 * predictions, memberships, devices and codes.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final JdbcClient jdbc;

    public AdminUserController(UserRepository users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    /**
     * Enough to recognise an account, to see what removing it costs, and to
     * answer the question the announcement raises: who actually hears us?
     *
     * @param devices       how many devices this player has registered for
     *                      notifications; zero means they hear nothing
     * @param notified      how many notifications we have sent them, ever
     * @param lastNotifiedAt when the last one went out, null if never
     */
    public record AccountView(UUID id, String email, String displayName, boolean emailVerified,
                              boolean admin, long predictions, long leagues, long ownedLeagues,
                              long devices, long notified, java.time.Instant lastNotifiedAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AccountView> list(@RequestParam(required = false) String q) {
        String needle = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return jdbc.sql("""
                        select u.public_id, u.email, u.display_name, u.email_verified, u.is_admin,
                               (select count(*) from predictions p where p.user_id = u.id) as predictions,
                               (select count(*) from league_members m where m.user_id = u.id) as leagues,
                               (select count(*) from leagues l where l.admin_user_id = u.id) as owned,
                               (select count(*) from push_subscriptions s where s.user_id = u.id) as devices,
                               (select count(*) from notifications n where n.user_id = u.id) as notified,
                               (select max(n.sent_at) from notifications n where n.user_id = u.id) as last_notified
                        from users u
                        -- the cast is not decoration: Postgres cannot infer the
                        -- type of a bare parameter compared against null
                        where cast(:needle as text) is null
                           or lower(u.email) like :needle
                           or lower(u.display_name) like :needle
                        order by u.created_at desc
                        limit 100""")
                .param("needle", needle)
                .query((rs, row) -> new AccountView(
                        UUID.fromString(rs.getString("public_id")), rs.getString("email"),
                        rs.getString("display_name"), rs.getBoolean("email_verified"),
                        rs.getBoolean("is_admin"), rs.getLong("predictions"),
                        rs.getLong("leagues"), rs.getLong("owned"),
                        rs.getLong("devices"), rs.getLong("notified"),
                        rs.getTimestamp("last_notified") == null
                                ? null
                                : rs.getTimestamp("last_notified").toInstant()))
                .list();
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(Authentication authentication, @PathVariable UUID publicId) {
        User user = users.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such account"));
        if (user.getId().equals(CurrentUser.id(authentication))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account here");
        }
        Long owned = jdbc.sql("select count(*) from leagues where admin_user_id = :id")
                .param("id", user.getId()).query(Long.class).single();
        if (owned > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That account runs a private league — hand the league over first");
        }
        // predictions and memberships hold the account by a plain reference;
        // devices, codes and push subscriptions cascade on their own
        jdbc.sql("delete from predictions where user_id = :id").param("id", user.getId()).update();
        jdbc.sql("delete from league_members where user_id = :id").param("id", user.getId()).update();
        users.delete(user);
    }
}
