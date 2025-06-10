package com.dil.logicengine.enhancer;

import com.dil.logicengine.api.DBAction;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

@RequiredArgsConstructor
public class DatabaseExceptionEnhancer<I, O> implements DBAction<I, O> {
    private final DBAction<I, O> delegate;

    @Override
    public O execute(I i) {
        try {
            return delegate.execute(i);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Data integrity violation: " + e.getMessage(), e);
        } catch (OptimisticLockingFailureException e) {
            throw new IllegalStateException("Optimistic locking failure: " + e.getMessage(), e);
        } catch (DataAccessException e) {
            throw new RuntimeException("Database access error: " + e.getMessage(), e);
        }
    }
}