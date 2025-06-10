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
import java.time.Instant;

@RequiredArgsConstructor
public class TracingEnhancer<I, O> extends BaseAction<I, O> {
    private final LogicAction<I, O> delegate;
    private final Tracer tracer;

    @Override
    public O execute(I request) {
        LogicAction<I, O> actualAction = unwrapToInnermost(delegate);
        String actualActionName = actualAction.getClass().getSimpleName();

        // Create a span that clearly identifies this as an enhancer operation
        String spanName =  actualActionName + ".TracingEnhancer";
        Span span = tracer.spanBuilder(spanName)
                .setAttribute("action.class", actualActionName)
                .setAttribute("action.type", "logic_action")
                .setAttribute("enhancer.type", "tracing")
                // Node visualization attributes
                .setAttribute("node.name", actualActionName)
                .setAttribute("node.type", getNodeType(actualAction))
                .setAttribute("node.category", getNodeCategory(actualAction))
                .setAttribute("node.id", generateNodeId(actualActionName))
                // Execution context
                .setAttribute("execution.timestamp", Instant.now().toString())
                .setAttribute("execution.thread", Thread.currentThread().getName())

                // Request metadata (if available)
                .setAttribute("request.class", request.getClass().getSimpleName())
                .setAttribute("request.hash", String.valueOf(request.hashCode()))

                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            var ctx = span.getSpanContext();
            MDC.put("traceid", ctx.getTraceId());
            MDC.put("spanid", ctx.getSpanId());
            MDC.put("action", actualActionName);
            MDC.put("node.name", actualActionName);
            MDC.put("node.type", getNodeType(actualAction));
            log.info("Entering Action: {} [Node: {}]", actualActionName, getNodeType(actualAction));

            long startTime = System.currentTimeMillis();
            O result = delegate.execute(request);
            long duration = System.currentTimeMillis() - startTime;

            // Enhanced success attributes
            span.setStatus(StatusCode.OK);
            span.setAttribute("action.result", "success");
            span.setAttribute("execution.duration_ms", duration);
            span.setAttribute("result.class", result != null ? result.getClass().getSimpleName() : "null");

            // Add metrics for node performance
            span.setAttribute("node.status", "completed");
            span.setAttribute("node.performance.duration", duration);

            log.info("Exiting Action: {} [Duration: {}ms]", actualActionName, duration);
            return result;

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.setAttribute("action.result", "error");
            span.setAttribute("node.status", "failed");
            span.setAttribute("error.type", e.getClass().getSimpleName());
            span.setAttribute("error.message", e.getMessage());

            log.error("Action Failed: {} [Error: {}]", actualActionName, e.getMessage());
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

    private String getNodeType(LogicAction<I, O> action) {
        String className = action.getClass().getSimpleName();

        // Categorize based on naming conventions or interfaces
        if (className.contains("DB") || className.contains("Database") ||
                className.contains("Repository") || className.contains("Persistence")) {
            return "database";
        } else if (className.contains("HTTP") || className.contains("Rest") ||
                className.contains("Api") || className.contains("Client")) {
            return "http_client";
        } else if (className.contains("Validation") || className.contains("Validator")) {
            return "validation";
        } else if (className.contains("Transform") || className.contains("Mapper") ||
                className.contains("Converter")) {
            return "transformation";
        } else if (className.contains("Business") || className.contains("Logic") ||
                className.contains("Process")) {
            return "business_logic";
        } else if (className.contains("Notification") || className.contains("Email") ||
                className.contains("Message")) {
            return "notification";
        }

        return "generic";
    }

    private String getNodeCategory(LogicAction<I, O> action) {
        String nodeType = getNodeType(action);

        switch (nodeType) {
            case "database":
                return "storage";
            case "http_client":
                return "external";
            case "validation":
            case "transformation":
                return "processing";
            case "business_logic":
                return "core";
            case "notification":
                return "communication";
            default:
                return "utility";
        }
    }

    private String generateNodeId(String actionName) {
        // Generate a consistent ID for the node
        return "node_" + actionName.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_");
    }

}