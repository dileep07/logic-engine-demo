package com.dil.logicengine.enhancer;

import com.dil.logicengine.api.BaseAction;
import com.dil.logicengine.api.LogicAction;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LoggingEnhancer<I, O> extends BaseAction<I, O> {

    private final LogicAction<I, O> delegate;

    @Override
    public O execute(I input) {
        log.info("Entering Enhancer: " + this.getClass().getSimpleName());
        try {
            O result = delegate.execute(input);
            log.info("Exiting Enhancer: " + this.getClass().getSimpleName());
            return result;
        } catch (Exception e) {
            log.info("Exception in Enhancer: " + this.getClass().getSimpleName() + " → " + e.getMessage());
            throw e;
        }
    }
}
