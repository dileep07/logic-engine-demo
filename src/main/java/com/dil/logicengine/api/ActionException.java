package com.dil.logicengine.api;

public class ActionException extends RuntimeException {
    private final Class<?> actionType;
    private final boolean retryable;

    public ActionException(Class<?> actionType, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.actionType = actionType;
        this.retryable = retryable;
    }

    public ActionException(Class<?> actionType, String message, boolean retryable) {
        super(message);
        this.actionType = actionType;
        this.retryable = retryable;
    }

    public Class<?> getActionType() {
        return actionType;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
