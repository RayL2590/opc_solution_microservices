package com.medilabo.assessmentservice.client;

import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.exception.BadGatewayException;
import com.medilabo.assessmentservice.exception.GatewayTimeoutException;
import com.medilabo.assessmentservice.exception.UpstreamNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
class NotesServiceClientTest {

    @Mock private RestClient gatewayClient;
    @Mock private RestClient.RequestHeadersUriSpec uriSpec;
    @Mock private RestClient.RequestHeadersSpec headersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private NotesServiceClient client;

    @BeforeEach
    void setUp() {
        client = new NotesServiceClient(gatewayClient);
    }

    private void stubBody(List<NoteView> returnValue) {
        doReturn(uriSpec).when(gatewayClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object.class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(returnValue).when(responseSpec).body(any(ParameterizedTypeReference.class));
    }

    private void stubBodyThrows(Throwable t) {
        doReturn(uriSpec).when(gatewayClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object.class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doThrow(t).when(responseSpec).body(any(ParameterizedTypeReference.class));
    }

    @Test
    void happyPath_returnsNoteViewList() {
        List<NoteView> expected = List.of(
                new NoteView("1", 1, "Dupont", "Poids stable", Instant.parse("2026-01-01T00:00:00Z")));
        stubBody(expected);

        assertEquals(expected, client.getNotesByPatId(1));
    }

    @Test
    void nullResponse_returnsEmptyList() {
        stubBody(null);

        assertEquals(List.of(), client.getNotesByPatId(1));
    }

    @Test
    void emptyResponse_returnsEmptyList() {
        stubBody(List.of());

        assertEquals(List.of(), client.getNotesByPatId(1));
    }

    @Test
    void upstream404_throwsUpstreamNotFoundException() {
        stubBodyThrows(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(UpstreamNotFoundException.class, () -> client.getNotesByPatId(1));
    }

    @Test
    void upstream404_preservesOriginalExceptionAsCause() {
        HttpClientErrorException original = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
        stubBodyThrows(original);

        UpstreamNotFoundException thrown = assertThrows(UpstreamNotFoundException.class,
                () -> client.getNotesByPatId(1));

        assertSame(original, thrown.getCause());
    }

    @Test
    void upstream5xx_throwsBadGatewayException() {
        stubBodyThrows(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(BadGatewayException.class, () -> client.getNotesByPatId(1));
    }

    @Test
    void upstream5xx_preservesOriginalExceptionAsCause() {
        HttpServerErrorException original = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
        stubBodyThrows(original);

        BadGatewayException thrown = assertThrows(BadGatewayException.class,
                () -> client.getNotesByPatId(1));

        assertSame(original, thrown.getCause());
    }

    @Test
    void connectionRefused_throwsGatewayTimeoutException() {
        stubBodyThrows(new ResourceAccessException("Connection refused"));

        assertThrows(GatewayTimeoutException.class, () -> client.getNotesByPatId(1));
    }

    @Test
    void connectionRefused_preservesOriginalExceptionAsCause() {
        ResourceAccessException original = new ResourceAccessException("Connection refused");
        stubBodyThrows(original);

        GatewayTimeoutException thrown = assertThrows(GatewayTimeoutException.class,
                () -> client.getNotesByPatId(1));

        assertSame(original, thrown.getCause());
    }
}
