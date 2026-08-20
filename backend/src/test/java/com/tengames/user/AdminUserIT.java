package com.tengames.user;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.JwtService;
import com.tengames.league.LeagueService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Removing an account, which until now meant a hand-written statement against
 * the production database — not something to do from a phone, and not
 * something whose subject should be committed to a public repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminUserIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private LeagueService leagues;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.tengames.push.PushService push;

    private String adminToken;
    private User admin;

    @BeforeEach
    void setUp() {
        admin = users.save(account("admin-%s@example.com".formatted(UUID.randomUUID()), true));
        adminToken = jwtService.issueToken(admin);
    }

    private User account(String email, boolean isAdmin) {
        User user = new User(email, passwordEncoder.encode("correct horse battery staple"),
                "Someone", "TN", null);
        if (isAdmin) {
            user.setAdmin(true);
        }
        return user;
    }

    private int deleteAccount(UUID publicId, String token) throws Exception {
        return mockMvc.perform(delete("/api/admin/users/" + publicId).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void anAccountCanBeRemoved() throws Exception {
        User doomed = users.save(account("typo-%s@gmial.com".formatted(UUID.randomUUID()), false));

        assertThat(deleteAccount(doomed.getPublicId(), adminToken)).isEqualTo(204);
        assertThat(users.findById(doomed.getId())).isEmpty();
    }

    @Test
    void theSearchFindsAnAccountByAddress() throws Exception {
        String email = "findme-%s@example.com".formatted(UUID.randomUUID());
        users.save(account(email, false));

        mockMvc.perform(get("/api/admin/users").param("q", email.substring(0, 12))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(email)));
    }

    @Test
    void anAccountSaysWhetherItHearsAnything() throws Exception {
        // "who got the announcement?" is unanswerable from a count alone
        String email = "listener-%s@example.com".formatted(UUID.randomUUID());
        User player = users.save(account(email, false));
        push.subscribe(player.getId(), "https://push.example.net/" + UUID.randomUUID(), "key", "auth");

        String body = mockMvc.perform(get("/api/admin/users").param("q", email.substring(0, 12))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"devices\":1");
    }

    @Test
    void theOwnerOfALeagueIsKeptUntilTheLeagueIsHandedOver() throws Exception {
        User owner = users.save(account("owner-%s@example.com".formatted(UUID.randomUUID()), false));
        leagues.create(owner.getId(), "Les copains");

        // deleting them would leave the league without an admin
        assertThat(deleteAccount(owner.getPublicId(), adminToken)).isEqualTo(409);
        assertThat(users.findById(owner.getId())).isPresent();
    }

    @Test
    void anAdminCannotDeleteThemselvesByAccident() throws Exception {
        assertThat(deleteAccount(admin.getPublicId(), adminToken)).isEqualTo(409);
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void anUnknownAccountIsNotFound() throws Exception {
        assertThat(deleteAccount(UUID.randomUUID(), adminToken)).isEqualTo(404);
    }

    @Test
    void aPlayerCannotRemoveAnybody() throws Exception {
        User player = users.save(account("player-%s@example.com".formatted(UUID.randomUUID()), false));
        User other = users.save(account("other-%s@example.com".formatted(UUID.randomUUID()), false));

        assertThat(deleteAccount(other.getPublicId(), jwtService.issueToken(player))).isEqualTo(403);
        assertThat(users.findById(other.getId())).isPresent();
    }
}
