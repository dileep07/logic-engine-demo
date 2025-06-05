package com.dil.logicengine.api;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleResponseBuilder<REQUEST> implements ResponseBuilder<REQUEST, Map<String, Object>> {

    private final Map<String, Object> results = new LinkedHashMap<>();

    @Override
    public void addResult(Class<? extends LogicAction<REQUEST, ?>> actionType, Object result) {
        results.put(actionType.getSimpleName(), result);
    }

    @Override
    public void handleException(Class<? extends LogicAction<REQUEST, ?>> actionType, Exception exception) {
        results.put(actionType.getSimpleName() + "_error", exception.getMessage());
    }

    @Override
    public Map<String, Object> build(REQUEST request) {
        return results;
    }
}