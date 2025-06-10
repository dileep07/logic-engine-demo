package com.dil.logicengine.enhancer;

import com.dil.logicengine.api.DBAction;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@RequiredArgsConstructor
public class TransactionEnhancer<I, O> implements DBAction<I, O> {
    private final DBAction<I, O> delegate;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public O execute(I request) {
        return delegate.execute(request);
    }
}