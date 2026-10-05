package org.danteplanner.backend.architecture;

import java.util.List;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoBeans;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBeans;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class MockBeanRule {

    static final ArchRule NO_MOCKITO_BEAN_OVERRIDES =
            classes()
                    .should(overrideNoBeanWithAMockitoMock())
                    .as("no Mockito mock-bean override (Spring's @MockitoBean/@MockitoSpyBean"
                            + " or Boot's @MockBean/@SpyBean, directly or through a meta-annotation);"
                            + " a test observes the effect where it leaves the process, and injects a fault"
                            + " through TestBean");

    private static final List<String> MOCK_OVERRIDES = List.of(
            MockitoBean.class.getName(),
            MockitoBeans.class.getName(),
            MockitoSpyBean.class.getName(),
            MockitoSpyBeans.class.getName(),
            "org.springframework.boot.test.mock.mockito.MockBean",
            "org.springframework.boot.test.mock.mockito.MockBeans",
            "org.springframework.boot.test.mock.mockito.SpyBean",
            "org.springframework.boot.test.mock.mockito.SpyBeans");

    private MockBeanRule() {
    }

    private static ArchCondition<JavaClass> overrideNoBeanWithAMockitoMock() {
        return new ArchCondition<>("override no bean with a Mockito mock") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                report(clazz, clazz.getFullName(), events);
                clazz.getFields().forEach(field -> report(field, field.getFullName(), events));
                clazz.getMethods().forEach(method -> report(method, method.getFullName(), events));
            }
        };
    }

    private static void report(CanBeAnnotated member, String fullName, ConditionEvents events) {
        if (MOCK_OVERRIDES.stream().anyMatch(member::isMetaAnnotatedWith)) {
            events.add(SimpleConditionEvent.violated(member, fullName + " overrides a bean with a Mockito mock"));
        }
    }
}
