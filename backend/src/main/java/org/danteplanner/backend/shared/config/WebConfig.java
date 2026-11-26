package org.danteplanner.backend.shared.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.danteplanner.backend.shared.ratelimit.RateLimitInterceptor;
import org.danteplanner.backend.shared.ratelimit.RateLimitProperties;
import org.danteplanner.backend.shared.restriction.RestrictionDutyInterceptor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    private final DeviceIdArgumentResolver deviceIdArgumentResolver;
    private final ViewerArgumentResolver viewerArgumentResolver;
    private final RestrictionDutyInterceptor restrictionDutyInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(deviceIdArgumentResolver);
        resolvers.add(viewerArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(restrictionDutyInterceptor);
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }
}
