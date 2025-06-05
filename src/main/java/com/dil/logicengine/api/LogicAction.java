package com.dil.logicengine.api;

@FunctionalInterface
public interface LogicAction<I,O> {
    O execute(I input);
}
