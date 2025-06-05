package com.dil.logicengine.api;

public interface ResponseBuilder<REQUEST, RESPONSE> {
    void addResult(Class<? extends LogicAction<REQUEST, ?>> actionType, Object result);

    void handleException(Class<? extends LogicAction<REQUEST, ?>> actionType, Exception exception);

    RESPONSE build(REQUEST request);
}
