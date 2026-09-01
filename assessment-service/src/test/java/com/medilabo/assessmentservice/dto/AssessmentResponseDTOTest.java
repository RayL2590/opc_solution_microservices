package com.medilabo.assessmentservice.dto;

import tools.jackson.databind.ObjectMapper;
import com.medilabo.assessmentservice.model.RiskBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrouille la forme JSON de l'enveloppe de réponse. Le champ {@code riskBand} est bien typé {@link RiskBand} côté Java, mais il doit sérialiser vers la chaîne exacte attendue sur le fil.
 */
class AssessmentResponseDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest(name = "{0} serializes to \"{1}\"")
    @CsvSource({
            "NONE, None",
            "BORDERLINE, Borderline",
            "IN_DANGER, In Danger",
            "EARLY_ONSET, Early Onset"
    })
    @DisplayName("riskBand serializes to the exact wire string, not the enum constant name")
    void riskBand_serializesToWireString(RiskBand band, String expectedWireValue) throws Exception {
        AssessmentResponseDTO dto = new AssessmentResponseDTO(
                4,
                new AssessmentResponseDTO.PatientBlock("Test", "Patient", 23),
                band,
                0,
                List.of());

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"riskBand\":\"" + expectedWireValue + "\"");
    }

    @Test
    @DisplayName("the full envelope keeps its expected field names and shape")
    void envelope_keepsExpectedShape() throws Exception {
        AssessmentResponseDTO dto = new AssessmentResponseDTO(
                4,
                new AssessmentResponseDTO.PatientBlock("TestEarlyOnset", "Patient4", 23),
                RiskBand.EARLY_ONSET,
                2,
                List.of("Anticorps", "Réaction"));

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).isEqualTo("{\"patId\":4,"
                + "\"patient\":{\"firstName\":\"TestEarlyOnset\",\"lastName\":\"Patient4\",\"age\":23},"
                + "\"riskBand\":\"Early Onset\","
                + "\"triggerCount\":2,"
                + "\"triggersDetected\":[\"Anticorps\",\"Réaction\"]}");
    }
}
