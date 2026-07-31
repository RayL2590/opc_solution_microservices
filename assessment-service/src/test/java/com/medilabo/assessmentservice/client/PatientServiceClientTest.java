package com.medilabo.assessmentservice.client;

import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.exception.BadGatewayException;
import com.medilabo.assessmentservice.exception.GatewayTimeoutException;
import com.medilabo.assessmentservice.exception.IncompletePatientDataException;
import com.medilabo.assessmentservice.exception.UpstreamNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
class PatientServiceClientTest {

    @Mock private RestClient gatewayClient;
    @Mock private RestClient.RequestHeadersUriSpec uriSpec;
    @Mock private RestClient.RequestHeadersSpec headersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private PatientServiceClient client;

    @BeforeEach
    void setUp() {
        client = new PatientServiceClient(gatewayClient);
    }

    private void stubBody(PatientView returnValue) {
        doReturn(uriSpec).when(gatewayClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object.class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(returnValue).when(responseSpec).body(PatientView.class);
    }

    private void stubBodyThrows(Throwable t) {
        doReturn(uriSpec).when(gatewayClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object.class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doThrow(t).when(responseSpec).body(PatientView.class);
    }

    @Test
    void happyPath_returnsPatientView() {
        PatientView expected = new PatientView(1, "Jean", "Dupont",
                LocalDate.of(1980, 5, 15), "M");
        stubBody(expected);

        assertEquals(expected, client.getPatient(1));
    }

    @Test
    void nullDateOfBirth_throwsIncompletePatientData() {
        stubBody(new PatientView(1, "Jean", "Dupont", null, "M"));

        assertThrows(IncompletePatientDataException.class, () -> client.getPatient(1));
    }

    @Test
    void nullResponse_throwsIncompletePatientData() {
        stubBody(null);

        assertThrows(IncompletePatientDataException.class, () -> client.getPatient(1));
    }

    @Test
    void upstream404_throwsUpstreamNotFoundException() {
        stubBodyThrows(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(UpstreamNotFoundException.class, () -> client.getPatient(1));
    }

    @Test
    void upstream404_preservesOriginalExceptionAsCause() {
        HttpClientErrorException original = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
        stubBodyThrows(original);

        UpstreamNotFoundException thrown = assertThrows(UpstreamNotFoundException.class,
                () -> client.getPatient(1));

        assertSame(original, thrown.getCause());
    }

    @Test
    void upstream5xx_throwsBadGatewayException() {
        stubBodyThrows(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(BadGatewayException.class, () -> client.getPatient(1));
    }

    @Test
    void upstream5xx_preservesOriginalExceptionAsCause() {
        HttpServerErrorException original = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
        stubBodyThrows(original);

        BadGatewayException thrown = assertThrows(BadGatewayException.class,
                () -> client.getPatient(1));

        assertSame(original, thrown.getCause());
    }

    @Test
    void connectionRefused_throwsGatewayTimeoutException() {
        stubBodyThrows(new ResourceAccessException("Connection refused"));

        assertThrows(GatewayTimeoutException.class, () -> client.getPatient(1));
    }

    @Test
    void connectionRefused_preservesOriginalExceptionAsCause() {
        ResourceAccessException original = new ResourceAccessException("Connection refused");
        stubBodyThrows(original);

        GatewayTimeoutException thrown = assertThrows(GatewayTimeoutException.class,
                () -> client.getPatient(1));

        assertSame(original, thrown.getCause());
    }

    @Test
    void realJsonWithLongId_deserializesIdAsInteger() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient realClient = builder.build();
        server.expect(requestTo("http://localhost:8080/patients/42"))
                .andRespond(withSuccess("""
                        {
                          "id": 42,
                          "firstName": "Jean",
                          "lastName": "Dupont",
                          "dateOfBirth": "1980-05-15",
                          "gender": "M",
                          "address": "1 rue de Paris",
                          "phone": "0123456789"
                        }""", MediaType.APPLICATION_JSON));

        PatientServiceClient realJsonClient = new PatientServiceClient(realClient);
        PatientView patient = realJsonClient.getPatient(42);

        assertEquals(42, patient.id());
        assertEquals(LocalDate.of(1980, 5, 15), patient.dateOfBirth());
        server.verify();
    }
}
