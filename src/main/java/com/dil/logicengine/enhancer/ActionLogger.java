package com.dil.logicengine.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;

public class ActionLogger {
    private final Logger logger;

    public ActionLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public void info(String message, Object... args) {
        logger.info(message,args);

    }

    public void error(String message, Throwable t, Object... args) {
        logger.error(message,args);
    }
}