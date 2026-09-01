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
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    /**
     * Corps illisible : typiquement une date envoyée en "31/12/1990" au lieu de "1990-12-31". Jackson échoue avant la validation Jakarta, donc ça ne passe pas par MethodArgumentNotValidException. Sans handler dédié, ça sortait en 500 alors que la faute est côté appelant.
     */
    @Test
    void unreadableBody_returns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", (HttpInputMessage) null);
        ProblemDetail detail = handler.handleUnreadableBody(ex);

        assertEquals(400, detail.getStatus());
        assertEquals(Level.WARN, singleEvent().getLevel());
    }

    /** DELETE /patients/{id} n'est pas exposé : 405, jamais 500. */
    @Test
    void methodNotSupported_returns405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");
        ProblemDetail detail = handler.handleMethodNotSupported(ex);

        assertEquals(405, detail.getStatus());
        assertEquals(Level.WARN, singleEvent().getLevel());
    }

    /** Accept: application/xml sur une API qui ne produit que du JSON : 406, jamais 500. */
    @Test
    void mediaTypeNotAcceptable_returns406() {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException("aucune représentation acceptable");
        ProblemDetail detail = handler.handleMediaTypeNotAcceptable(ex);

        assertEquals(406, detail.getStatus());
        assertEquals(Level.WARN, singleEvent().getLevel());
    }

    /** Content-Type text/plain sur une API JSON : 415, jamais 500. */
    @Test
    void mediaTypeNotSupported_returns415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException("text/plain non supporté");
        ProblemDetail detail = handler.handleMediaTypeNotSupported(ex);

        assertEquals(415, detail.getStatus());
        assertEquals(Level.WARN, singleEvent().getLevel());
    }

    /** @RequestParam obligatoire absent (ex. lastName sur /patients/search) : 400, jamais 500. */
    @Test
    void missingParameter_returns400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("lastName", "String");
        ProblemDetail detail = handler.handleMissingParam(ex);

        assertEquals(400, detail.getStatus());
        assertEquals(Level.WARN, singleEvent().getLevel());
    }

}
