package com.tengames.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The production jar serves the built PWA from classpath:/static. Client-side
 * routes must resolve to the SPA shell so deep links and refreshes work.
 *
 * <p>Every route declared in frontend/src/App.tsx belongs here <em>and</em> in
 * the GET allow-list of {@code SecurityConfig}: a route missing from either one
 * answers 401 instead of the app, which is what happened to /rules and /forgot.
 * {@code SpaForwardingIT} reads App.tsx and fails when the lists drift apart,
 * so this is checked rather than remembered.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/login", "/signup", "/forgot", "/table", "/table/global", "/table/country", "/table/club",
            "/table/create", "/table/join", "/table/league/{id}", "/join/{code}",
            "/players/{playerId}", "/profile", "/rules", "/unsubscribe", "/admin"})
    public String spa() {
        return "forward:/index.html";
    }
}
