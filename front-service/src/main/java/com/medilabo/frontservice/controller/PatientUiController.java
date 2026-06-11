package com.medilabo.frontservice.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * UI controller for patient pages (Stories 5.2 & 5.4, FR-10 & FR-12).
 *
 * <p>Serves:
 * <ul>
 *   <li>{@code GET /ui/patients} — patient list (Story 5.2)</li>
 *   <li>{@code GET /ui/patients/new} — empty add-patient form (Story 5.4)</li>
 *   <li>{@code POST /ui/patients} — create patient, PRG on success (Story 5.4)</li>
 * </ul>
 *
 * <p>PII contract: only patient counts and IDs are logged — never names, addresses,
 * phones, or dates of birth.
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

    /**
     * Renders the empty add-patient form.
     *
     * @param model the Spring MVC model populated with an empty {@code "patientForm"}
     * @return Thymeleaf view name {@code "patients/new"}
     */
    @GetMapping("/ui/patients/new")
    public String showNewPatientForm(Model model) {
        model.addAttribute("patientForm", new PatientForm());
        return "patients/new";
    }

    /**
     * Processes the add-patient form submission (Post/Redirect/Get pattern).
     *
     * <p>If validation fails, the form is re-rendered with HTTP 400 and inline field errors.
     * If validation passes, the patient is created via the Gateway and the browser is
     * redirected to the new patient's detail page ({@code /ui/patients/{id}}).
     *
     * @param patientForm   the bound and validated form data
     * @param bindingResult Spring MVC binding and validation result
     * @param response      the HTTP response, used to set the 400 status on validation failure
     * @return redirect URL on success; view name {@code "patients/new"} on validation failure
     */
    @PostMapping("/ui/patients")
    public String createPatient(
            @Valid @ModelAttribute PatientForm patientForm,
            BindingResult bindingResult,
            HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "patients/new";
        }

        PatientView created = patientGatewayClient.createPatient(patientForm);
        log.debug("Patient created, id={}", created.id());
        return "redirect:/ui/patients/" + created.id();
    }
}
