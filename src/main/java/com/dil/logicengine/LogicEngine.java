package com.dil.logicengine;

import com.dil.logicengine.api.*;
import com.dil.logicengine.config.OtelConfiguration;
import com.dil.logicengine.enhancer.TracingEnhancer;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.dil.logicengine.enhancer.LoggingEnhancer;

import java.util.ArrayList;
import java.util.List;

@Component
public class LogicEngine {
    public <REQUEST, RESPONSE> RESPONSE executeActions(
            REQUEST request,
            List<LogicAction<REQUEST, ?>> actions,
            ResponseBuilder<REQUEST, RESPONSE> responseBuilder) {

        List<CompensationEntry<REQUEST>> completedCompensatableActions = new ArrayList<>();

        for (LogicAction<REQUEST, ?> action : actions) {
            @SuppressWarnings("unchecked")
            Class<? extends LogicAction<REQUEST, ?>> actionClass =
                    (Class<? extends LogicAction<REQUEST, ?>>) action.getClass();
            try {
                LogicAction<REQUEST, ?> enhanced = enhanceAction(action);

                Object result = enhanced.execute(request);

                if (action instanceof CompensatableAction) {
                    completedCompensatableActions.add(new CompensationEntry<>(
                            (CompensatableAction<REQUEST, Object>) action,
                            result
                    ));
                }

                responseBuilder.addResult(actionClass, result);

            } catch (Exception e) {
                compensateActions(completedCompensatableActions, request);
                responseBuilder.handleException(actionClass, new ActionException(action.getClass(), e.getMessage(), e, false));
                return responseBuilder.build(request);
            }
        }

        return responseBuilder.build(request);
    }

    private <REQUEST> void compensateActions(List<CompensationEntry<REQUEST>> completedActions, REQUEST request) {
        for (int i = completedActions.size() - 1; i >= 0; i--) {
            CompensationEntry<REQUEST> entry = completedActions.get(i);
            try {
                entry.action.compensate(request, entry.result);
            } catch (Exception ignored) {
                // log if needed
            }
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
        enhanced = new TracingEnhancer<>(enhanced, OtelConfiguration.getTracer());
        return enhanced;
    }
}
