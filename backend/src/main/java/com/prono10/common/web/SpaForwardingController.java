package com.prono10.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The production jar serves the built PWA from classpath:/static. Client-side
 * routes must resolve to the SPA shell so deep links and refreshes work.
 * Add new frontend routes here when they are added to frontend/src/App.tsx.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/login", "/signup", "/table", "/table/global", "/table/country", "/table/club",
            "/table/create", "/table/join", "/table/league/{id}", "/join/{code}",
            "/players/{playerId}", "/profile", "/admin"})
    public String spa() {
        return "forward:/index.html";
    }
}
