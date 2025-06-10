package com.dil.logicengine;

import com.dil.logicengine.api.*;
import com.dil.logicengine.config.OtelConfiguration;
import com.dil.logicengine.enhancer.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class LogicEngine {

    private final Tracer tracer = OtelConfiguration.getTracer();
    @Autowired
    private MeterRegistry meterRegistry;

    // Cache for edge metrics
    private static final Map<String, Counter> callEdgeCounters = new ConcurrentHashMap<>();

    @Transactional(
            propagation = Propagation.REQUIRED,
            rollbackFor  = Exception.class
    )
    public <I, O> O executeActions(
            I request,
            List<LogicAction<I, ?>> actions,
            ResponseBuilder<I, O> responseBuilder) {

        // Create a parent span for the entire logic engine execution
        Span engineSpan = tracer.spanBuilder("LogicEngine.executeActions")
                .setAttribute("logic.engine.actions.count", actions.size())
                .startSpan();

        try (Scope engineScope = engineSpan.makeCurrent()) {
            List<CompensationEntry<I>> completedCompensatableActions = new ArrayList<>();
            String previousActionName = null;

            for (int i = 0; i < actions.size(); i++) {
                LogicAction<I, ?> action = actions.get(i);
                @SuppressWarnings("unchecked")
                Class<? extends LogicAction<I, ?>> actionClass =
                        (Class<? extends LogicAction<I, ?>>) action.getClass();

                String currentActionName = actionClass.getSimpleName();

                // Record edge metric if this is not the first action
                if (previousActionName != null) {
                    recordCallEdge(previousActionName, currentActionName);
                }

                // Create a span for each action execution
                Span actionSpan = tracer.spanBuilder(currentActionName)
                        .setAttribute("action.class", currentActionName)
                        .setAttribute("action.index", i)
                        .setAttribute("action.total", actions.size())
                        .startSpan();

                try (Scope actionScope = actionSpan.makeCurrent()) {
                    LogicAction<I, ?> enhanced = enhanceAction(action);
                    Object result = enhanced.execute(request);

                    if (action instanceof CompensatableAction) {
                        completedCompensatableActions.add(new CompensationEntry<>(
                                (CompensatableAction<I, Object>) action,
                                result
                        ));
                    }

                    responseBuilder.addResult(actionClass, result);
                    actionSpan.setStatus(StatusCode.OK);
                    actionSpan.setAttribute("action.result", "success");

                } catch (Exception e) {
                    actionSpan.recordException(e);
                    actionSpan.setStatus(StatusCode.ERROR, e.getMessage());
                    actionSpan.setAttribute("action.result", "error");

                    compensateActions(completedCompensatableActions, request);
                    responseBuilder.handleException(actionClass, new ActionException(action.getClass(), e.getMessage(), e, false));

                    engineSpan.setStatus(StatusCode.ERROR, "Action execution failed");
                    return responseBuilder.build(request);
                } finally {
                    actionSpan.end();
                }

                // Update for next iteration
                previousActionName = currentActionName;
            }

            engineSpan.setStatus(StatusCode.OK);
            engineSpan.setAttribute("logic.engine.result", "success");
            return responseBuilder.build(request);

        } catch (Exception e) {
            engineSpan.recordException(e);
            engineSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            engineSpan.end();
        }
    }

    private void recordCallEdge(String fromAction, String toAction) {
        String edgeKey = fromAction + "->" + toAction;

        Counter callEdgeCounter = callEdgeCounters.computeIfAbsent(edgeKey,
                key -> Counter.builder("logic_engine_call_edge_total")
                        .description("Total calls between actions in sequence")
                        .tag("source_action", fromAction)
                        .tag("target_action", toAction)
                        .tag("source_node_type", getNodeType(fromAction))
                        .tag("target_node_type", getNodeType(toAction))
                        .register(meterRegistry));

        callEdgeCounter.increment();
    }

    private String getNodeType(String actionName) {
        if (actionName.contains("DB") || actionName.contains("Database")) {
            return "database";
        } else if (actionName.contains("HTTP") || actionName.contains("Rest")) {
            return "http_client";
        } else if (actionName.contains("Validation")) {
            return "validation";
        } else if (actionName.contains("Transform")) {
            return "transformation";
        }
        return "generic";
    }

    private <REQUEST> void compensateActions(List<CompensationEntry<REQUEST>> completedActions, REQUEST request) {
        if (completedActions.isEmpty()) {
            return;
        }

        Span compensationSpan = tracer.spanBuilder("LogicEngine.compensateActions")
                .setAttribute("compensation.actions.count", completedActions.size())
                .startSpan();

        try (Scope scope = compensationSpan.makeCurrent()) {
            for (int i = completedActions.size() - 1; i >= 0; i--) {
                CompensationEntry<REQUEST> entry = completedActions.get(i);
                try {
                    entry.action.compensate(request, entry.result);
                } catch (Exception ignored) {
                    // log if needed
                }
            }
            compensationSpan.setStatus(StatusCode.OK);
        } catch (Exception e) {
            compensationSpan.recordException(e);
            compensationSpan.setStatus(StatusCode.ERROR, e.getMessage());
        } finally {
            compensationSpan.end();
        }
    }

    private static class CompensationEntry<REQUEST> {
        final CompensatableAction<REQUEST, Object> action;
        final Object result;

        CompensationEntry(CompensatableAction<REQUEST, Object> action, Object result) {
            this.action = action;
            this.result = result;
        }
    }

    public <I, O> LogicAction<I, O> enhanceAction(LogicAction<I, O> action) {
        LogicAction<I, O> enhanced = action;
        if (action instanceof DBAction) {
            DBAction<I, O> dbAction = (DBAction<I, O>) enhanced;
            // CRITICAL: Add transaction management (closest to DB operation)
            dbAction = new TransactionEnhancer<>(dbAction);
            // HIGH VALUE: Add exception handling (catch DB exceptions)
            dbAction = new DatabaseExceptionEnhancer<>(dbAction);
            enhanced = dbAction;
        }

        // Apply general enhancers in order of importance
        enhanced = new MetricsEnhancer<>(enhanced, meterRegistry);  // NEW: Add metrics
        enhanced = new LoggingEnhancer<>(enhanced);
        enhanced = new TracingEnhancer<>(enhanced, tracer);

        return enhanced;
    }
}