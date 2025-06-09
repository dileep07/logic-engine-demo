package com.dil.logicengine;

import com.dil.logicengine.api.*;
import com.dil.logicengine.config.OtelConfiguration;
import com.dil.logicengine.enhancer.TracingEnhancer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.dil.logicengine.enhancer.LoggingEnhancer;

import java.util.ArrayList;
import java.util.List;

@Component
public class LogicEngine {

    private final Tracer tracer = OtelConfiguration.getTracer();

    public <REQUEST, RESPONSE> RESPONSE executeActions(
            REQUEST request,
            List<LogicAction<REQUEST, ?>> actions,
            ResponseBuilder<REQUEST, RESPONSE> responseBuilder) {

        // Create a parent span for the entire logic engine execution
        Span engineSpan = tracer.spanBuilder("LogicEngine.executeActions")
                .setAttribute("logic.engine.actions.count", actions.size())
                .startSpan();

        try (Scope engineScope = engineSpan.makeCurrent()) {
            List<CompensationEntry<REQUEST>> completedCompensatableActions = new ArrayList<>();

            for (int i = 0; i < actions.size(); i++) {
                LogicAction<REQUEST, ?> action = actions.get(i);
                @SuppressWarnings("unchecked")
                Class<? extends LogicAction<REQUEST, ?>> actionClass =
                        (Class<? extends LogicAction<REQUEST, ?>>) action.getClass();

                // Create a span for each action execution
                Span actionSpan = tracer.spanBuilder("Action." + actionClass.getSimpleName())
                        .setAttribute("action.class", actionClass.getSimpleName())
                        .setAttribute("action.index", i)
                        .setAttribute("action.total", actions.size())
                        .startSpan();

                try (Scope actionScope = actionSpan.makeCurrent()) {
                    LogicAction<REQUEST, ?> enhanced = enhanceAction(action);
                    Object result = enhanced.execute(request);

                    if (action instanceof CompensatableAction) {
                        completedCompensatableActions.add(new CompensationEntry<>(
                                (CompensatableAction<REQUEST, Object>) action,
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

    public <REQUEST, RESULT> LogicAction<REQUEST, RESULT> enhanceAction(LogicAction<REQUEST, RESULT> action) {
        LogicAction<REQUEST, RESULT> enhanced = action;
        enhanced = new LoggingEnhancer<>(enhanced);
        enhanced = new TracingEnhancer<>(enhanced, tracer);
        return enhanced;
    }
}