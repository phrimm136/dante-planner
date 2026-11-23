package org.danteplanner.backend.shared.config;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import org.danteplanner.backend.shared.security.Viewer;

@Component
public class ViewerArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Set<Class<?>> SUPPORTED = Set.of(Viewer.class, Viewer.Member.class);

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SUPPORTED.contains(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {

        Viewer viewer = Viewer.of(userIdOf(currentPrincipal()));
        return switch (Required.by(parameter.getParameterType())) {
            case ANYONE -> viewer;
            case MEMBER -> viewer.requireMember();
        };
    }

    private static @Nullable Object currentPrincipal() {
        return switch (SecurityContextHolder.getContext().getAuthentication()) {
            case null -> null;
            case Authentication authentication -> authentication.getPrincipal();
        };
    }

    private static @Nullable Long userIdOf(@Nullable Object principal) {
        return principal instanceof Long userId ? userId : null;
    }

    private enum Required {
        ANYONE,
        MEMBER;

        static Required by(Class<?> parameterType) {
            return parameterType == Viewer.Member.class ? MEMBER : ANYONE;
        }
    }
}
