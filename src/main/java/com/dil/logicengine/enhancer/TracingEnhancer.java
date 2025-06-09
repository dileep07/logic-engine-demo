package com.dil.logicengine.enhancer;

import com.dil.logicengine.api.BaseAction;
import com.dil.logicengine.api.LogicAction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

import java.lang.reflect.Field;

@RequiredArgsConstructor
public class TracingEnhancer<I, O> extends BaseAction<I, O> {
    private final LogicAction<I, O> delegate;
    private final Tracer tracer;

    @Override
    public O execute(I request) {
        LogicAction<I, O> actualAction = unwrapToInnermost(delegate);
        String actualActionName = actualAction.getClass().getSimpleName();

        // Create a span that clearly identifies this as an enhancer operation
        String spanName = "TracingEnhancer." + actualActionName;
        Span span = tracer.spanBuilder(spanName)
                .setAttribute("action.class", actualActionName)
                .setAttribute("action.type", "logic_action")
                .setAttribute("enhancer.type", "tracing")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            var ctx = span.getSpanContext();
            MDC.put("traceid", ctx.getTraceId());
            MDC.put("spanid", ctx.getSpanId());
            MDC.put("action", actualActionName);

            log.info("Entering Enhancer: " + this.getClass().getSimpleName());

            O result = delegate.execute(request);

            // Mark span as successful
            span.setStatus(StatusCode.OK);
            span.setAttribute("action.result", "success");

            log.info("Exiting Enhancer: " + this.getClass().getSimpleName());
            return result;

        } catch (Exception e) {
            // Record the exception in the span
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.setAttribute("action.result", "error");
            throw e;
        } finally {
            MDC.clear();
            span.end();
        }
    }

    /**
     * Unwraps nested enhancers by reflectively getting the `delegate` field.
     */
    @SuppressWarnings("unchecked")
    private LogicAction<I, O> unwrapToInnermost(LogicAction<I, O> action) {
        try {
            while (true) {
                Field delegateField = findDelegateField(action.getClass());
                if (delegateField == null) break;

                delegateField.setAccessible(true);
                Object next = delegateField.get(action);
                if (!(next instanceof LogicAction)) break;

                action = (LogicAction<I, O>) next;
            }
        } catch (Exception e) {
            // Fall back silently if reflection fails
            return action;
        }
        return action;
    }

    private Field findDelegateField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (LogicAction.class.isAssignableFrom(field.getType()) && field.getName().equals("delegate")) {
                return field;
            }
        }
        return null;
    }
}