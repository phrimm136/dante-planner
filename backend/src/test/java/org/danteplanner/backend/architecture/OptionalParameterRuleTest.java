package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.danteplanner.fixture.optionalparameter.OptionalParameterFixture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionalParameterRuleTest {

    private static final List<Class<? extends Annotation>> REQUIRED =
            List.of(NotNull.class, NotEmpty.class, NotBlank.class);

    static final ArchRule OPTIONAL_PARAMETERS_ARE_NULLABLE =
            methods()
                    .that().areDeclaredInClassesThat().areMetaAnnotatedWith(Controller.class)
                    .should(declareEveryOptionalParameterNullable())
                    .as("a controller parameter with required = false and no default is @Nullable, and so is a"
                            + " @ModelAttribute record component that no constraint requires");

    @Test
    void mainControllers_WhenScanned_DeclareEveryOptionalParameterNullable() {
        assertThatCode(() -> OPTIONAL_PARAMETERS_ARE_NULLABLE.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixtureController_WhenAnOptionalParameterIsBare_FailsNamingTheMethodAndParameter() {
        assertThatThrownBy(() -> OPTIONAL_PARAMETERS_ARE_NULLABLE.check(
                new ClassFileImporter().importClasses(OptionalParameterFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("bareParam(java.lang.String) parameter 0")
                .hasMessageContaining("bareHeader(java.lang.String) parameter 0")
                .hasMessageContaining("bareCookie(java.lang.String) parameter 0")
                .hasMessageContaining("bareBody(java.lang.String) parameter 0")
                .hasMessageContaining("binds BareModel.q,")
                .hasMessageContaining("binds RequiredModel.ids,")
                .hasMessageNotContaining("BareModel.page")
                .hasMessageNotContaining(".declaredModel(")
                .hasMessageNotContaining(".validatedModel(")
                .hasMessageNotContaining("declared(");
    }

    private static ArchCondition<JavaMethod> declareEveryOptionalParameterNullable() {
        return new ArchCondition<>("declare every optional parameter @Nullable") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Parameter[] parameters = method.reflect().getParameters();
                Arrays.stream(parameters)
                        .flatMap(OptionalParameterRuleTest::bareComponents)
                        .forEach(component -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " binds " + component.getDeclaringRecord().getSimpleName() + "."
                                        + component.getName() + ", which is optional on the wire and not @Nullable")));
                for (int index = 0; index < parameters.length; index++) {
                    if (isOptional(parameters[index]) && !admitsAbsence(parameters[index].getType(), parameters[index].getAnnotatedType())) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName() + " parameter " + index
                                + " is optional on the wire and not @Nullable"));
                    }
                }
            }
        };
    }

    private static Stream<RecordComponent> bareComponents(Parameter parameter) {
        if (!parameter.isAnnotationPresent(ModelAttribute.class) || !parameter.getType().isRecord()) {
            return Stream.empty();
        }
        boolean validated = parameter.isAnnotationPresent(Valid.class);
        return Arrays.stream(parameter.getType().getRecordComponents())
                .filter(component -> !admitsAbsence(component.getType(), component.getAnnotatedType()))
                .filter(component -> !(validated && isRequired(component)));
    }

    private static boolean isRequired(RecordComponent component) {
        return REQUIRED.stream().anyMatch(component.getAnnotatedType()::isAnnotationPresent);
    }

    private static boolean isOptional(Parameter parameter) {
        RequestParam param = parameter.getAnnotation(RequestParam.class);
        RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        CookieValue cookie = parameter.getAnnotation(CookieValue.class);
        RequestBody body = parameter.getAnnotation(RequestBody.class);
        return (param != null && !param.required() && hasNoDefault(param.defaultValue()))
                || (header != null && !header.required() && hasNoDefault(header.defaultValue()))
                || (cookie != null && !cookie.required() && hasNoDefault(cookie.defaultValue()))
                || (body != null && !body.required());
    }

    private static boolean hasNoDefault(String defaultValue) {
        return defaultValue.equals(ValueConstants.DEFAULT_NONE);
    }

    private static boolean admitsAbsence(Class<?> type, AnnotatedType annotatedType) {
        return type.isPrimitive()
                || type == Optional.class
                || annotatedType.isAnnotationPresent(Nullable.class);
    }
}
