package org.danteplanner.backend.shared.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import org.jspecify.annotations.Nullable;

public final class SpringContextAdapter {

    private SpringContextAdapter() {
    }

    public static Viewer viewer() {
        return viewerOf(SecurityContextHolder.getContext().getAuthentication());
    }

    public static TransactionScope transactionScope() {
        return TransactionScope.of(TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                TransactionSynchronizationManager.isActualTransactionActive());
    }

    public static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
    }

    public static <T> T requestValue(String name, Class<T> type, T absent) {
        return valueOf(attribute(name), type, absent);
    }

    public static void storeRequestValue(String name, Object value) {
        RequestContextHolder.currentRequestAttributes().setAttribute(name, value, RequestAttributes.SCOPE_REQUEST);
    }

    private static @Nullable Object attribute(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }

    private static Viewer viewerOf(@Nullable Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof Long userId
                ? new Viewer.Member(userId)
                : new Viewer.Guest();
    }

    private static <T> T valueOf(@Nullable Object stored, Class<T> type, T absent) {
        return type.isInstance(stored) ? type.cast(stored) : absent;
    }
}
