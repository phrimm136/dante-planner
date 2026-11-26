package org.danteplanner.backend.shared.restriction;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.danteplanner.backend.shared.security.SpringContextAdapter;

@Component
public class RestrictionDutyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SpringContextAdapter.storeRequestValue(RestrictionDuty.DUTY_ATTRIBUTE,
                RestrictionDuty.of(handler, SpringContextAdapter.viewer()));
        return true;
    }
}
