package com.tengames.common.web;

import com.tengames.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SpaForwardingIT {

    /** Matches the path of every {@code <Route path="…">} declared in App.tsx. */
    private static final Pattern ROUTE = Pattern.compile("<Route\\s+path=\"([^\"]+)\"");

    private static final Path APP_TSX = Path.of("..", "frontend", "src", "App.tsx");

    @Autowired
    private MockMvc mockMvc;

    /**
     * Every client-side route must reach the SPA shell.
     *
     * <p>The list is read from App.tsx rather than typed here, because a route
     * added to the frontend and forgotten in {@code SpaForwardingController} or
     * in the security allow-list answers 401 in production while every
     * hand-written test still passes — which is exactly how /rules and /forgot
     * went out broken. MockMvc runs the security chain, so a route missing from
     * either list fails here.
     */
    @Test
    void everyRouteDeclaredByTheFrontendForwardsToTheShell() throws Exception {
        for (String route : frontendRoutes()) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    private static List<String> frontendRoutes() throws Exception {
        assertThat(APP_TSX).as("the frontend sources sit next to the backend in this repository").exists();
        List<String> routes = new ArrayList<>();
        Matcher matcher = ROUTE.matcher(Files.readString(APP_TSX));
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.equals("*")) {
                continue; // the client-side catch-all, not a deep link anyone can open
            }
            // ":code" and friends stand for any value; give them one
            routes.add(path.replaceAll(":[A-Za-z]+", "x"));
        }
        assertThat(routes).as("routes parsed out of App.tsx").hasSizeGreaterThan(5).contains("/rules", "/forgot");
        return routes;
    }

    @Test
    void apiRoutesAreNeverForwarded() throws Exception {
        // unauthenticated API access stays a 401, not the SPA shell
        mockMvc.perform(get("/api/gameweeks/current")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
