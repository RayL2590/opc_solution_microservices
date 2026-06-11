package com.medilabo.frontservice.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.medilabo.frontservice.config.SecurityConfig;

/**
 * {@code @WebMvcTest} slice for {@link HomeController} (Story 5.1).
 *
 * <p>Imports the real {@link SecurityConfig} so the HTTP Basic chain is exercised: an
 * unauthenticated request gets the Basic challenge (401), and an authenticated request
 * ({@code medilabo}/{@code medilabo123}, resolved from the {@code application.properties} bridge
 * defaults) gets the {@code / → /ui/patients} redirect (FR-10, G-1). The slice needs no
 * DataSource — front-service is DB-free.
 */
@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void root_withoutCredentials_returns401BasicChallenge() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void root_authenticated_redirectsToUiPatients() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/patients"));
    }
}
