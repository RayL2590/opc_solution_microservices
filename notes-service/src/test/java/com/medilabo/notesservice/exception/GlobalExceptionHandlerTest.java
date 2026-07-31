package com.medilabo.notesservice.exception;

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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
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
        // sert de cible au MethodParameter fabriqué pour construire MethodArgumentNotValidException
    }

    private MethodArgumentNotValidException validationException() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("dummyHandlerMethod", Object.class), 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "note");
        bindingResult.rejectValue(null, "NotBlank", "ne doit pas être vide");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "note", "note", "ne doit pas être vide"));
        return new MethodArgumentNotValidException(parameter, bindingResult);
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
        assertTrue(event.getFormattedMessage().contains("note"),
                "field summary should still be part of the message");
    }

    @Test
    void noteNotFound_returns404() {
        ProblemDetail detail = handler.handleNoteNotFound(new NoteNotFoundException("Note introuvable : id=1"));
        assertEquals(404, detail.getStatus());
    }

    @Test
    void noteNotFound_logsAtWarnWithFullException() {
        NoteNotFoundException ex = new NoteNotFoundException("Note introuvable : id=1");
        handler.handleNoteNotFound(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
    }

    @Test
    void missingParameter_logsAtWarnWithFullException() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("patId", "Integer");
        handler.handleMissingParameter(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
        assertTrue(event.getFormattedMessage().contains("patId"));
    }

    @Test
    void typeMismatch_logsAtWarnWithFullException() throws Exception {
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("dummyHandlerMethod", Object.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "patId", parameter, new IllegalArgumentException("nope"));
        handler.handleTypeMismatch(ex);

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertSame(ex, loggedThrowable(event));
        assertTrue(event.getFormattedMessage().contains("patId"));
    }

    @Test
    void unreadableBody_logsAtWarnWithFullException() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("malformed", (org.springframework.http.HttpInputMessage) null);
        handler.handleUnreadableBody(ex);

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
