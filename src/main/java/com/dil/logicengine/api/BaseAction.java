package com.dil.logicengine.api;

import com.dil.logicengine.enhancer.ActionLogger;

public abstract class BaseAction<REQ, RES> implements LogicAction<REQ, RES> {
    protected final ActionLogger log;

    protected BaseAction() {
        this.log = new ActionLogger(getClass());
    }

    // No execute() here — that's okay because the class is abstract
}