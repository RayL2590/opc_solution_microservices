package com.medilabo.patientservice.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return ((ThrowableProxy) event.getThrowableProxy()).getThrowable();
    }

    void dummyHandlerMethod(Object body) {
        // sert de cible au MethodParameter fabriqué pour construire les exceptions de validation
    }

    private MethodParameter dummyParameter() throws NoSuchMethodException {
        return new MethodParameter(getClass().getDeclaredMethod("dummyHandlerMethod", Object.class), 0);
    }

    private MethodArgumentNotValidException validationException() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "patient");
        bindingResult.addError(new FieldError("patient", "lastName", "ne doit pas être vide"));
        return new MethodArgumentNotValidException(dummyParameter(), bindingResult);
    }

    @Test
    void patientNotFound_returns404() {
        ProblemDetail detail = handler.handlePatientNotFound(new PatientNotFoundException(42L));
        assertEquals(404, detail.getStatus());
    }

    @Test
    void patientNotFound_logsAtWarnWithFullException() {
        PatientNotFoundException ex = new PatientNotFoundException(42L);
        handler.handlePatientNotFound(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }

    @Test
    void validation_returns400() throws Exception {
        ProblemDetail detail = handler.handleValidation(validationException());
        assertEquals(400, detail.getStatus());
    }

    @Test
    void validation_logsAtWarnWithFullException() throws Exception {
        MethodArgumentNotValidException ex = validationException();
        handler.handleValidation(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
        assertTrue(event.getFormattedMessage().contains("lastName"),
                "field summary should still be part of the message");
    }

    @Test
    void typeMismatch_returns400() throws Exception {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", dummyParameter(), new IllegalArgumentException("nope"));
        ProblemDetail detail = handler.handleTypeMismatch(ex);
        assertEquals(400, detail.getStatus());
    }

    @Test
    void typeMismatch_logsAtWarnWithFullException() throws Exception {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", dummyParameter(), new IllegalArgumentException("nope"));
        handler.handleTypeMismatch(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
        assertTrue(event.getFormattedMessage().contains("id"));
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
