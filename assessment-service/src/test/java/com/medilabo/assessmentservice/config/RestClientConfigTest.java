package com.medilabo.assessmentservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestClientConfigTest {

    @Test
    void connectTimeoutIs5Seconds() {
        assertEquals(Duration.ofSeconds(5), RestClientConfig.CONNECT_TIMEOUT);
    }

    @Test
    void readTimeoutIs10Seconds() {
        assertEquals(Duration.ofSeconds(10), RestClientConfig.READ_TIMEOUT);
    }

    @Test
    void requestFactory_isConfiguredWithConnectAndReadTimeouts() throws Exception {
        SimpleClientHttpRequestFactory requestFactory = RestClientConfig.requestFactory();

        assertEquals((int) RestClientConfig.CONNECT_TIMEOUT.toMillis(),
                readIntField(requestFactory, "connectTimeout"));
        assertEquals((int) RestClientConfig.READ_TIMEOUT.toMillis(),
                readIntField(requestFactory, "readTimeout"));
    }

    @Test
    void gatewayClientBean_buildsSuccessfully() {
        RestClientConfig config = new RestClientConfig();
        RestClient client = config.gatewayClient("http://localhost:8080");

        assertNotNull(client);
    }

    private int readIntField(Object target, String fieldName) throws Exception {
        Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
