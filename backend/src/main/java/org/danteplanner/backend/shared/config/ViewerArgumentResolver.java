package org.danteplanner.backend.shared.config;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import org.danteplanner.backend.shared.security.SpringContextAdapter;
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

        Viewer viewer = SpringContextAdapter.viewer();
        return switch (Required.by(parameter.getParameterType())) {
            case ANYONE -> viewer;
            case MEMBER -> viewer.requireMember();
        };
    }

    private enum Required {
        ANYONE,
        MEMBER;

        static Required by(Class<?> parameterType) {
            return parameterType == Viewer.Member.class ? MEMBER : ANYONE;
        }
    }
}
