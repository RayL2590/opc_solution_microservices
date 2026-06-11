package com.medilabo.frontservice.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.dto.PatientView;

/**
 * UI controller for the patient list page (Story 5.2, FR-10).
 *
 * <p>Serves {@code GET /ui/patients} — the target of the {@code HomeController} redirect
 * (Story 5.1, G-1). Calls the Gateway through {@link PatientGatewayClient} and renders
 * {@code templates/patients/list.html} with the full patient list as a model attribute.
 *
 * <p>PII contract: only patient counts are logged — never names, addresses, or dates.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PatientUiController {

    private final PatientGatewayClient patientGatewayClient;

    /**
     * Renders the patient list page.
     *
     * @param model the Spring MVC model populated with the {@code "patients"} attribute
     * @return Thymeleaf view name {@code "patients/list"}
     */
    @GetMapping("/ui/patients")
    public String listPatients(Model model) {
        List<PatientView> patients = patientGatewayClient.getAllPatients();
        model.addAttribute("patients", patients);
        log.debug("Rendering patient list, count={}", patients.size());
        return "patients/list";
    }
}
