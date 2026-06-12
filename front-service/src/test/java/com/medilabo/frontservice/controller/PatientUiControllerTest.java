package com.medilabo.frontservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * Tranche @WebMvcTest pour PatientUiController — SecurityConfig réelle (HTTP Basic exercé),
 * PatientGatewayClient mocké. CSRF désactivé dans SecurityConfig, les POST n'ont pas besoin de csrf().
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

    @Test
    void showNewPatientForm_authenticated_returns200WithEmptyForm() throws Exception {
        mockMvc.perform(get("/ui/patients/new").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeExists("patientForm"));
    }

    @Test
    void showNewPatientForm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/ui/patients/new"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPatient_validForm_redirectsToDetail() throws Exception {
        PatientView created = new PatientView(42L, "Alice", "Martin",
                LocalDate.of(1990, 3, 20), "F", null, null);
        given(patientGatewayClient.createPatient(any(PatientForm.class))).willReturn(created);

        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Alice")
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "F"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/patients/42"));
    }

    @Test
    void createPatient_gatewayReturnsNull_throwsIllegalState() {
        // @WebMvcTest sans handler global : MockMvc rethrow l'IllegalStateException directement (Spring 6+ retire NestedServletException).
        given(patientGatewayClient.createPatient(any(PatientForm.class))).willReturn(null);

        assertThrows(Exception.class, () ->
            mockMvc.perform(post("/ui/patients")
                            .with(httpBasic("medilabo", "medilabo123"))
                            .param("firstName", "Alice")
                            .param("lastName", "Martin")
                            .param("dateOfBirth", "1990-03-20")
                            .param("gender", "F")));
    }

    @Test
    void createPatient_invalidForm_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "")        // blank — @NotBlank
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "F"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "firstName"));
    }
}
