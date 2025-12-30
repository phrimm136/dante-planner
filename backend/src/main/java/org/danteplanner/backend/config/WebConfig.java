package org.danteplanner.backend.config;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.entity.MDCategory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration for custom argument resolvers and converters.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final DeviceIdArgumentResolver deviceIdArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(deviceIdArgumentResolver);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToMDCategoryConverter());
    }

    /**
     * Converter for MDCategory that uses the JSON value format (e.g., "5F", "10F", "15F")
     * instead of enum constant names.
     */
    private static class StringToMDCategoryConverter implements Converter<String, MDCategory> {
        @Override
        public MDCategory convert(String source) {
            return MDCategory.fromValue(source);
        }
    }
}
