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

import com.medilabo.frontservice.client.AssessmentGatewayClient;
import com.medilabo.frontservice.client.NotesGatewayClient;
import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.dto.AssessmentView;
import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.NoteView;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;
import com.medilabo.frontservice.dto.PhoneCountry;
import com.medilabo.frontservice.util.PhoneNormalizer;

/**
 * Contrôleur UI patients (liste, formulaire d'ajout/édition, fiche détail + notes, Post-Redirect-Get).
 * PII (Personally Identifiable Information) : seuls les comptes et ids sont loggés.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PatientUiController {

    private final PatientGatewayClient patientGatewayClient;
    private final NotesGatewayClient notesGatewayClient;
    private final AssessmentGatewayClient assessmentGatewayClient;

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
        model.addAttribute("phoneCountries", PhoneCountry.values());
        return "patients/new";
    }

    @PostMapping("/ui/patients")
    public String createPatient(
            @Valid @ModelAttribute PatientForm patientForm,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {

        normalizePhone(patientForm, bindingResult);

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("phoneCountries", PhoneCountry.values());
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
        model.addAttribute("phoneCountries", PhoneCountry.values());
        return "patients/edit";
    }

    @PostMapping("/ui/patients/{id}/edit")
    public String updatePatient(
            @PathVariable Long id,
            @Valid @ModelAttribute PatientForm patientForm,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {

        normalizePhone(patientForm, bindingResult);

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("patientId", id);
            model.addAttribute("phoneCountries", PhoneCountry.values());
            return "patients/edit";
        }

        patientGatewayClient.updatePatient(id, patientForm);
        log.debug("Patient updated, id={}", id);
        return "redirect:/ui/patients";
    }

    @GetMapping("/ui/patients/{id}")
    public String showPatientDetail(@PathVariable Long id, Model model) {
        loadPatientDetailModel(id, model);
        model.addAttribute("noteForm", new NoteForm());
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
            loadPatientDetailModel(id, model);
            return "patients/detail";
        }

        PatientView patient = patientGatewayClient.getPatient(id);
        noteForm.setPatId(Math.toIntExact(id));
        noteForm.setPatient(patient.lastName());
        notesGatewayClient.addNote(noteForm);
        log.debug("Note added, patId={}", id);
        return "redirect:/ui/patients/" + id;
    }

    /** Charge patient + notes dans le modèle pour la fiche détail (GET initial et re-rendu après erreur de validation). */
    private void loadPatientDetailModel(Long id, Model model) {
        PatientView patient = patientGatewayClient.getPatient(id);
        List<NoteView> notes = notesGatewayClient.getNotesByPatId(id);
        AssessmentView assessment = assessmentGatewayClient.getAssessment(id);
        model.addAttribute("patient", patient);
        model.addAttribute("notes", notes);
        model.addAttribute("assessment", assessment);
        log.debug("Rendering patient detail, id={}, noteCount={}", id, notes.size());
    }

    /**
     * Normalise le téléphone saisi vers E.164 et réécrit {@code patientForm.phone} en place.
     * En cas d'échec, rejette le champ dans {@code bindingResult} pour un retour utilisateur
     * cohérent avec les autres erreurs de validation. Champ optionnel : une saisie vide passe.
     */
    private void normalizePhone(PatientForm patientForm, BindingResult bindingResult) {
        PhoneNormalizer.Result result =
                PhoneNormalizer.normalize(patientForm.getPhone(), patientForm.getPhoneCountry());
        if (result.isValid()) {
            patientForm.setPhone(result.e164());
        } else {
            bindingResult.rejectValue("phone", "phone.invalid", result.errorMessage());
        }
    }

    private PatientForm toForm(PatientView patient) {
        PatientForm form = new PatientForm();
        form.setFirstName(patient.firstName());
        form.setLastName(patient.lastName());
        form.setDateOfBirth(patient.dateOfBirth());
        form.setGender(patient.gender());
        form.setAddress(patient.address());
        form.setPhone(patient.phone());
        form.setPhoneCountry(detectCountry(patient.phone()));
        return form;
    }

    /**
     * Retrouve le pays d'un numéro E.164 stocké pour pré-sélectionner l'indicatif en édition.
     * Défaut FR si absent ou non reconnu (le numéro reste affiché tel quel).
     */
    private PhoneCountry detectCountry(String e164Phone) {
        if (e164Phone != null && e164Phone.startsWith("+")) {
            for (PhoneCountry candidate : PhoneCountry.values()) {
                if (e164Phone.startsWith(candidate.dialingCode())) {
                    return candidate;
                }
            }
        }
        return PhoneCountry.FR;
    }
}
