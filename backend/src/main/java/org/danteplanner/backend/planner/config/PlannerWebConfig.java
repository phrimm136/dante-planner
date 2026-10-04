package org.danteplanner.backend.planner.config;

import org.danteplanner.backend.planner.entity.MDCategory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlannerWebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToMDCategoryConverter());
    }

    private static class StringToMDCategoryConverter implements Converter<String, MDCategory> {
        @Override
        public MDCategory convert(String source) {
            return MDCategory.fromValue(source);
        }
    }
}
