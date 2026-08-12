package org.danteplanner.backend.architecture;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

import org.springframework.boot.web.servlet.FilterRegistrationBean;

import jakarta.servlet.Filter;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every filter the security chain wires is a bean; Spring Boot registers a bean of type
 * {@link Filter} with the servlet container as well, so such a filter runs twice per request —
 * once at the servlet level, ahead of the chain and outside its ordering, and once inside it.
 *
 * <p>{@code OncePerRequestFilter} hides the second run behind a request attribute. It is not the
 * guarantee: a filter that stops extending it, or that clears its attribute, or that needs the
 * SecurityContext the servlet-level pass has not populated yet, loses the dedup silently.</p>
 *
 * <p>The scan derives the chain's filters from the configuration, so a filter added later is
 * covered without editing this test.</p>
 */
class SecurityChainFilterRegistrationTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend");

    @Test
    @DisplayName("every component-scanned filter in the security chain is disabled for the servlet container")
    void chainFilter_WhenComponentScanned_IsDisabledForTheServletContainer() {
        List<Class<?>> chainFilters = chainFilters();

        assertThat(chainFilters)
                .as("no filter was found in any SecurityFilterChain bean, so the scan below would "
                        + "pass against an empty set and prove nothing")
                .isNotEmpty();

        Set<String> doubleRegistered = new TreeSet<>();
        for (Class<?> filter : chainFilters) {
            if (filter.isAnnotationPresent(Component.class) && !isDisabledForTheContainer(filter)) {
                doubleRegistered.add(filter.getName());
            }
        }

        assertThat(doubleRegistered)
                .as("these run once in the servlet container and once in the security chain; give "
                        + "each one a FilterRegistrationBean with setEnabled(false), as "
                        + "SecurityConfig does for the filters already covered")
                .isEmpty();
    }

    /** The filter-typed fields every {@link SecurityFilterChain} bean method reads. */
    private static List<Class<?>> chainFilters() {
        List<Class<?>> filters = new ArrayList<>();
        MAIN_CLASSES.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.getRawReturnType().isAssignableTo(SecurityFilterChain.class))
                .flatMap(method -> method.getFieldAccesses().stream())
                .map(access -> access.getTarget().getRawType())
                .filter(type -> type.isAssignableTo(Filter.class))
                .map(JavaClass::reflect)
                .distinct()
                .forEach(filters::add);
        return filters;
    }

    private static boolean isDisabledForTheContainer(Class<?> filter) {
        return MAIN_CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Configuration.class))
                .map(JavaClass::reflect)
                .flatMap(configuration -> Arrays.stream(configuration.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> FilterRegistrationBean.class.isAssignableFrom(method.getReturnType()))
                .filter(method -> registeredFilter(method) == filter)
                .anyMatch(method -> !invoke(method).isEnabled());
    }

    /** The {@code T} of a {@code FilterRegistrationBean<T>} bean method, null when it is raw. */
    private static Class<?> registeredFilter(Method beanMethod) {
        if (beanMethod.getGenericReturnType() instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            return argument instanceof Class<?> type ? type : null;
        }
        return null;
    }

    private static FilterRegistrationBean<?> invoke(Method beanMethod) {
        try {
            Constructor<?> constructor = beanMethod.getDeclaringClass().getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object configuration = constructor.newInstance(new Object[constructor.getParameterCount()]);
            Object[] arguments = Arrays.stream(beanMethod.getParameterTypes())
                    .map(Mockito::mock)
                    .toArray();
            beanMethod.setAccessible(true);
            return (FilterRegistrationBean<?>) beanMethod.invoke(configuration, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "could not evaluate the registration bean " + beanMethod.getName(), e);
        }
    }
}
