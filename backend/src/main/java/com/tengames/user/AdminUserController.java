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

    /** Enough to recognise an account, and to see what removing it costs. */
    public record AccountView(UUID id, String email, String displayName, boolean emailVerified,
                              boolean admin, long predictions, long leagues, long ownedLeagues) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AccountView> list(@RequestParam(required = false) String q) {
        String needle = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return jdbc.sql("""
                        select u.public_id, u.email, u.display_name, u.email_verified, u.is_admin,
                               (select count(*) from predictions p where p.user_id = u.id) as predictions,
                               (select count(*) from league_members m where m.user_id = u.id) as leagues,
                               (select count(*) from leagues l where l.admin_user_id = u.id) as owned
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
                        rs.getLong("leagues"), rs.getLong("owned")))
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
