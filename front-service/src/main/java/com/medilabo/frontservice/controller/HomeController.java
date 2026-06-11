package com.medilabo.frontservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entry-point controller (Story 5.1, FR-10, G-1 URL convention).
 *
 * <p>Redirects the application root {@code /} to {@code /ui/patients}. The UI is served under
 * the {@code /ui/} prefix to avoid colliding with the back-end {@code /patients} API route the
 * Gateway proxies (G-1). The {@code /ui/patients} page itself ships in Story 5.2; this story
 * provides only the redirect.
 */
@Controller
public class HomeController {

    /**
     * Redirects the root path to the patient list page.
     *
     * @return the redirect view name targeting {@code /ui/patients}
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/ui/patients";
    }
}
