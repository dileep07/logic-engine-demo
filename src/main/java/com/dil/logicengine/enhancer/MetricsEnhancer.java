package com.dil.logicengine.enhancer;

import com.dil.logicengine.api.BaseAction;
import com.dil.logicengine.api.LogicAction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.lang.reflect.Field;

@RequiredArgsConstructor
public class MetricsEnhancer<I, O> extends BaseAction<I, O> {
    private final LogicAction<I, O> delegate;
    private final MeterRegistry meterRegistry;

    // Cache for metrics to avoid recreation
    private static final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();
    private static final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private static final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();

    @Override
    public O execute(I request) {
        // Unwrap to get the actual action name, not the enhancer name
        LogicAction<I, O> actualAction = unwrapToInnermost(delegate);
        String actionName = actualAction.getClass().getSimpleName();



        // Get or create metrics
        Timer executionTimer = executionTimers.computeIfAbsent(actionName,
                name -> Timer.builder("logic_engine_action_duration")
                        .description("Time taken to execute action")
                        .tag("action_name", name)
                        .tag("node_type", getNodeType(name))
                        .register(meterRegistry));

        Counter successCounter = successCounters.computeIfAbsent(actionName,
                name -> Counter.builder("logic_engine_action_success_total")
                        .description("Total successful action executions")
                        .tag("action_name", name)
                        .tag("node_type", getNodeType(name))
                        .register(meterRegistry));

        Counter errorCounter = errorCounters.computeIfAbsent(actionName,
                name -> Counter.builder("logic_engine_action_error_total")
                        .description("Total failed action executions")
                        .tag("action_name", name)
                        .tag("node_type", getNodeType(name))
                        .register(meterRegistry));

        // Use Timer.Sample for manual timing to avoid Callable exception handling issues
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            O result = delegate.execute(request);

            // Record successful execution
            successCounter.increment();

            // Record node state change
            meterRegistry.counter("logic_engine_node_state_change",
                    "action_name", actionName,
                    "node_type", getNodeType(actionName),
                    "state", "completed").increment();

            return result;

        } catch (Exception e) {
            // Record error
            errorCounter.increment();

            // Record node failure
            meterRegistry.counter("logic_engine_node_state_change",
                    "action_name", actionName,
                    "node_type", getNodeType(actionName),
                    "state", "failed").increment();

            throw e;
        } finally {
            // Stop the timer regardless of success or failure
            sample.stop(executionTimer);
        }
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