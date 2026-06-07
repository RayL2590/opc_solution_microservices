package com.medilabo.patientservice.controller;

import com.medilabo.patientservice.config.SecurityConfig;
import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.exception.GlobalExceptionHandler;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link PatientController} — Story 2.2 read-endpoint contract.
 *
 * <p>Imports the real {@link SecurityConfig} so the HTTP Basic + BCrypt chain is exercised:
 * no credentials → 401, valid {@code user}/{@code user123} → 200. {@link GlobalExceptionHandler}
 * is imported so {@link PatientNotFoundException} maps to a 404 {@code ProblemDetail}. The
 * service is a {@link MockitoBean}, so the slice needs no DataSource and stays DB-free.
 */
@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private PatientDTO samplePatient() {
        return PatientDTO.builder()
                .id(1L)
                .firstName("Test")
                .lastName("TestNone")
                .dateOfBirth(LocalDate.of(1966, 12, 31))
                .gender("F")
                .build();
    }

    @Test
    void listPatients_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPatients_withCredentials_returns200AndJsonArray() throws Exception {
        given(patientService.getAllPatients()).willReturn(List.of(samplePatient()));

        mockMvc.perform(get("/patients").with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].lastName").value("TestNone"));
    }

    @Test
    void getPatientById_existingId_returns200AndDto() throws Exception {
        given(patientService.getPatientById(1L)).willReturn(samplePatient());

        mockMvc.perform(get("/patients/1").with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastName").value("TestNone"));
    }

    @Test
    void getPatientById_missingId_returns404ProblemDetail() throws Exception {
        given(patientService.getPatientById(999L))
                .willThrow(new PatientNotFoundException(999L));

        mockMvc.perform(get("/patients/999").with(httpBasic("user", "user123")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getPatientById_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/patients/1"))
                .andExpect(status().isUnauthorized());
    }
}
