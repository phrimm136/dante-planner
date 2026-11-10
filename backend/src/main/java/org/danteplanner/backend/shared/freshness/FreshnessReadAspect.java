package org.danteplanner.backend.shared.freshness;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.danteplanner.backend.shared.config.ReadSourceSelector;

@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class FreshnessReadAspect {

    private final FreshnessReadGuard guard;

    public FreshnessReadAspect(FreshnessReadGuard guard) {
        this.guard = guard;
    }

    @Around("execution(org.danteplanner.backend.shared.freshness.ScopedRead *(..)) && @annotation(transactional)")
    public Object read(ProceedingJoinPoint joinPoint, Transactional transactional) throws Throwable {
        if (!transactional.readOnly() || TransactionSynchronizationManager.isActualTransactionActive()) {
            return joinPoint.proceed();
        }
        try {
            return guard.read(() -> (ScopedRead<?>) joinPoint.proceed());
        } finally {
            if (RequestContextHolder.getRequestAttributes() == null) {
                ReadSourceSelector.clearSkips();
            }
        }
    }
}
