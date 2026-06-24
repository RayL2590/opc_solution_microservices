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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.medilabo.frontservice.client.NotesGatewayClient;
import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.NoteView;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * Contrôleur UI patients (liste, formulaire d'ajout/édition, fiche détail + notes, PRG).
 * PII : seuls les comptes et ids sont loggés.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PatientUiController {

    private final PatientGatewayClient patientGatewayClient;
    private final NotesGatewayClient notesGatewayClient;

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
        return "redirect:/ui/patients";
    }

    // Édition d'un patient existant. URL en /{id}/edit pour ne pas entrer en collision avec
    // la fiche détail /ui/patients/{id}.
    @GetMapping("/ui/patients/{id}/edit")
    public String showEditPatientForm(@PathVariable Long id, Model model) {
        PatientView patient = patientGatewayClient.getPatient(id);
        model.addAttribute("patientForm", toForm(patient));
        model.addAttribute("patientId", id);
        return "patients/edit";
    }

    @PostMapping("/ui/patients/{id}/edit")
    public String updatePatient(
            @PathVariable Long id,
            @Valid @ModelAttribute PatientForm patientForm,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("patientId", id);
            return "patients/edit";
        }

        patientGatewayClient.updatePatient(id, patientForm);
        log.debug("Patient updated, id={}", id);
        return "redirect:/ui/patients";
    }

    @GetMapping("/ui/patients/{id}")
    public String showPatientDetail(@PathVariable Long id, Model model) {
        PatientView patient = patientGatewayClient.getPatient(id);
        List<NoteView> notes = notesGatewayClient.getNotesByPatId(id);
        model.addAttribute("patient", patient);
        model.addAttribute("notes", notes);
        model.addAttribute("noteForm", new NoteForm());
        log.debug("Rendering patient detail, id={}, noteCount={}", id, notes.size());
        return "patients/detail";
    }

    @PostMapping("/ui/patients/{id}/notes")
    public String addNote(
            @PathVariable Long id,
            @Valid @ModelAttribute NoteForm noteForm,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PatientView patient = patientGatewayClient.getPatient(id);
            model.addAttribute("patient", patient);
            model.addAttribute("notes", notesGatewayClient.getNotesByPatId(id));
            return "patients/detail";
        }

        PatientView patient = patientGatewayClient.getPatient(id);
        noteForm.setPatId(id.intValue());
        noteForm.setPatient(patient.lastName());
        notesGatewayClient.addNote(noteForm);
        log.debug("Note added, patId={}", id);
        return "redirect:/ui/patients/" + id;
    }

    /** Pré-remplit le formulaire d'édition depuis le patient renvoyé par le Gateway. */
    private PatientForm toForm(PatientView patient) {
        PatientForm form = new PatientForm();
        form.setFirstName(patient.firstName());
        form.setLastName(patient.lastName());
        form.setDateOfBirth(patient.dateOfBirth());
        form.setGender(patient.gender());
        form.setAddress(patient.address());
        form.setPhone(patient.phone());
        return form;
    }
}
