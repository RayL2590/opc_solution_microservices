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
 * Contrôleur UI patients (liste + formulaire d'ajout, PRG).
 * PII : seuls les comptes et ids sont loggés.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PatientUiController {

    private final PatientGatewayClient patientGatewayClient;

    @GetMapping("/ui/patients")
    public String listPatients(Model model) {
        List<PatientView> patients = patientGatewayClient.getAllPatients();
        model.addAttribute("patients", patients);
        log.debug("Rendering patient list, count={}", patients.size());
        return "patients/list";
    }

    @GetMapping("/ui/patients/new")
    public String showNewPatientForm(Model model) {
        model.addAttribute("patientForm", new PatientForm());
        return "patients/new";
    }

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
        if (created == null || created.id() == null) {
            throw new IllegalStateException(
                    "Gateway returned a null patient or null id after creation — cannot redirect");
        }
        log.debug("Patient created, id={}", created.id());
        return "redirect:/ui/patients/" + created.id();
    }
}
