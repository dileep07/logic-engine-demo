package com.dil.logicengine.api;

public interface CompensatableAction<I, O> extends LogicAction<I, O> {
    void compensate(I request, O result);
}