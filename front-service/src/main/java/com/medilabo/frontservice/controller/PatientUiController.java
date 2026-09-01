package com.medilabo.frontservice.controller;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;
import com.medilabo.frontservice.dto.PhoneCountry;
import com.medilabo.frontservice.service.PatientUiService;
import com.medilabo.frontservice.util.PhoneNormalizer;
import com.medilabo.frontservice.util.UpstreamValidationErrors;

/**
 * Contrôleur UI patients (liste, formulaire d'ajout/édition, fiche détail + notes, Post-Redirect-Get).
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PatientUiController {

    private final PatientUiService patientUiService;

    @GetMapping("/ui/patients")
    public String listPatients(Model model) {
        model.addAttribute("patients", patientUiService.getAllPatients());
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

        try {
            patientUiService.createPatient(patientForm);
        } catch (RestClientResponseException upstreamError) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("phoneCountries", PhoneCountry.values());
            applyUpstreamErrors(upstreamError, bindingResult);
            return "patients/new";
        }
        return "redirect:/ui/patients";
    }

    // Édition d'un patient existant. URL en /{id}/edit pour ne pas se marcher dessus avec la fiche détail /ui/patients/{id}.
    @GetMapping("/ui/patients/{id}/edit")
    public String showEditPatientForm(@PathVariable Long id, Model model) {
        PatientView patient = patientUiService.getPatient(id);
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

        try {
            patientUiService.updatePatient(id, patientForm);
        } catch (RestClientResponseException upstreamError) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("patientId", id);
            model.addAttribute("phoneCountries", PhoneCountry.values());
            applyUpstreamErrors(upstreamError, bindingResult);
            return "patients/edit";
        }
        return "redirect:/ui/patients";
    }

    @GetMapping("/ui/patients/{id}")
    public String showPatientDetail(@PathVariable Long id, Model model) {
        addPatientDetailToModel(id, model);
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
            addPatientDetailToModel(id, model);
            return "patients/detail";
        }

        patientUiService.addNote(id, noteForm);
        return "redirect:/ui/patients/" + id;
    }

    /** Peuple le modèle de la fiche détail (GET initial et re-rendu après erreur de validation). */
    private void addPatientDetailToModel(Long id, Model model) {
        PatientUiService.PatientDetail detail = patientUiService.loadPatientDetail(id);
        model.addAttribute("patient", detail.patient());
        model.addAttribute("notes", detail.notes());
        model.addAttribute("assessment", detail.assessment());
    }

    /**
     * Normalise le téléphone saisi vers E.164 et réécrit {@code patientForm.phone} en place.
     * En cas d'échec, rejette le champ dans {@code bindingResult} — comme ça l'erreur remonte avec les autres erreurs de validation. Champ optionnel : une saisie vide passe.
     */
    /**
     * Reporte sur le formulaire les erreurs d'un 4xx venu d'un service amont, au lieu de laisser l'exception remonter en page d'erreur.
     *
     * <p>Sans ça, une règle appliquée en amont mais pas ici (indicatif téléphonique accepté par le front et refusé par patient-service, par exemple) sortait en 500 Whitelabel : l'utilisateur perdait sa saisie et le message utile n'existait que dans les logs du conteneur.</p>
     *
     * <p>Les erreurs par champ sont remappées sur les champs du formulaire quand le nom correspond ; le reste devient une erreur globale. Un 5xx amont n'est pas rattrapé : ce n'est pas une faute de saisie, il doit rester visible comme une panne.</p>
     */
    private void applyUpstreamErrors(RestClientResponseException upstreamError, BindingResult bindingResult) {
        if (!upstreamError.getStatusCode().is4xxClientError()) {
            throw upstreamError;
        }
        log.warn("Upstream rejected the patient payload with status {}", upstreamError.getStatusCode());

        Map<String, String> fieldErrors = UpstreamValidationErrors.fieldErrorsOf(upstreamError);
        fieldErrors.forEach((field, message) -> {
            if (bindingResult.getFieldError(field) != null) {
                return; // deja signale localement, ne pas doubler le message sous le champ
            }
            try {
                bindingResult.rejectValue(field, "upstream.invalid", message);
            } catch (NotReadablePropertyException unknownField) {
                // Champ inconnu du formulaire (renomme en amont, ou propre au DTO du service) : rien a quoi l'accrocher, on le remonte en erreur globale plutot que de le perdre.
                bindingResult.reject("upstream.invalid", message);
            }
        });

        if (fieldErrors.isEmpty()) {
            String detail = UpstreamValidationErrors.detailOf(upstreamError);
            bindingResult.reject("upstream.invalid",
                    detail != null ? detail : "L'enregistrement a été refusé par le service patient");
        }
    }

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
     * Retrouve le pays d'un numéro stocké, pour pré-sélectionner l'indicatif en édition.
     *
     * <p>Deux cas, dans cet ordre :</p>
     * <ol>
     *   <li>numéro déjà en E.164 ({@code +33...}) : on compare aux indicatifs connus, le plus long d'abord — sans ça {@code +1} capturerait un futur indicatif {@code +1x} ;</li>
     *   <li>numéro national hérité, sans {@code +} (les jeux de données de démo sont au format nord-américain {@code 200-333-4444}) : 10 chiffres ne commençant pas par 0 → US.
     *       C'est ce qui évite qu'une simple modification d'adresse échoue sur un téléphone jamais touché, faute d'avoir été deviné en FR.</li>
     * </ol>
     *
     * <p>Défaut FR si absent ou non reconnu — le numéro s'affiche quand même tel quel.</p>
     */
    private PhoneCountry detectCountry(String phone) {
        if (phone == null || phone.isBlank()) {
            return PhoneCountry.FR;
        }

        if (phone.startsWith("+")) {
            return Arrays.stream(PhoneCountry.values())
                    .filter(candidate -> phone.startsWith(candidate.dialingCode()))
                    .max(Comparator.comparingInt(candidate -> candidate.dialingCode().length()))
                    .orElse(PhoneCountry.FR);
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == PhoneCountry.US.nationalDigits() && !digits.startsWith("0")) {
            return PhoneCountry.US;
        }
        return PhoneCountry.FR;
    }
}
