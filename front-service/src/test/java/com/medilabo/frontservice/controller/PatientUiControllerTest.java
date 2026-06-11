package com.medilabo.frontservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.config.SecurityConfig;
import com.medilabo.frontservice.dto.PatientView;

/**
 * {@code @WebMvcTest} slice for {@link PatientUiController} (Story 5.2, FR-10).
 *
 * <p>The real {@link SecurityConfig} is imported so the HTTP Basic chain is exercised.
 * {@link PatientGatewayClient} is mocked via {@code @MockitoBean} — no real HTTP call to the
 * Gateway is made; the test verifies the view name, model attribute, and security behaviour
 * only. front-service is DB-free; no datasource is needed.
 */
@WebMvcTest(PatientUiController.class)
@Import(SecurityConfig.class)
class PatientUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientGatewayClient patientGatewayClient;

    @Test
    void listPatients_authenticated_returns200WithPatientsList() throws Exception {
        PatientView p1 = new PatientView(1L, "Jean", "Dupont",
                LocalDate.of(1980, 1, 15), "M", null, null);
        PatientView p2 = new PatientView(2L, "Marie", "Martin",
                LocalDate.of(1975, 6, 30), "F", null, null);
        given(patientGatewayClient.getAllPatients()).willReturn(List.of(p1, p2));

        mockMvc.perform(get("/ui/patients").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/list"))
                .andExpect(model().attributeExists("patients"))
                .andExpect(model().attribute("patients", hasSize(2)));
    }

    @Test
    void listPatients_unauthenticated_returns401BasicChallenge() throws Exception {
        mockMvc.perform(get("/ui/patients"))
                .andExpect(status().isUnauthorized());
    }
}
