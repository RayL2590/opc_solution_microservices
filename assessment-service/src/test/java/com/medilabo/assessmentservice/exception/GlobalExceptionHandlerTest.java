package com.medilabo.assessmentservice.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent singleEvent() {
        assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }

    private Throwable loggedThrowable(ILoggingEvent event) {
        assertNotNull(event.getThrowableProxy(), "no throwable attached to log event");
        return ((ch.qos.logback.classic.spi.ThrowableProxy) event.getThrowableProxy()).getThrowable();
    }

    @Test
    void upstreamNotFound_returns404() {
        ProblemDetail detail = handler.handleUpstreamNotFound(
                new UpstreamNotFoundException("Patient introuvable : id=42"));
        assertEquals(404, detail.getStatus());
    }

    @Test
    void upstreamNotFound_logsAtWarnWithFullException() {
        UpstreamNotFoundException ex = new UpstreamNotFoundException("Patient introuvable : id=42");
        handler.handleUpstreamNotFound(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }

    @Test
    void badGateway_returns502() {
        ProblemDetail detail = handler.handleBadGateway(
                new BadGatewayException("Erreur upstream patient-service pour id=42"));
        assertEquals(502, detail.getStatus());
    }

    @Test
    void badGateway_logsAtErrorWithFullException() {
        BadGatewayException ex = new BadGatewayException("Erreur upstream patient-service pour id=42");
        handler.handleBadGateway(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }

    @Test
    void gatewayTimeout_returns504() {
        ProblemDetail detail = handler.handleGatewayTimeout(
                new GatewayTimeoutException("patient-service inaccessible pour id=42",
                        new RuntimeException("connection refused")));
        assertEquals(504, detail.getStatus());
    }

    @Test
    void gatewayTimeout_logsAtErrorWithFullException() {
        GatewayTimeoutException ex = new GatewayTimeoutException(
                "patient-service inaccessible pour id=42", new RuntimeException("connection refused"));
        handler.handleGatewayTimeout(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }

    @Test
    void incompletePatientData_returns422() {
        ProblemDetail detail = handler.handleIncompletePatientData(
                new IncompletePatientDataException(42));
        assertEquals(422, detail.getStatus());
    }

    @Test
    void incompletePatientData_logsAtWarnWithFullException() {
        IncompletePatientDataException ex = new IncompletePatientDataException(42);
        handler.handleIncompletePatientData(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }

    @Test
    void uncaughtException_returns500() {
        ProblemDetail detail = handler.handleUncaught(new RuntimeException("unexpected"));
        assertEquals(500, detail.getStatus());
    }

    @Test
    void uncaughtException_logsAtErrorWithFullException() {
        RuntimeException ex = new RuntimeException("unexpected");
        handler.handleUncaught(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }
}
