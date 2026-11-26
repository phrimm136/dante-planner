package org.danteplanner.backend.architecture;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.backend.shared.security.SpringContextAdapter;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringContextAdapterRuleTest {

    private static final String SECURITY_CONTEXT = "org.springframework.security.core.context";

    private static final String REQUEST_CONTEXT = "org.springframework.web.context.request";

    private static final String HOLDER_SUFFIX = "ContextHolder";

    @Test
    void production_WhenScanned_NamesTheContextHoldersOnlyInTheAdapter() {
        assertThatCode(() -> onlyAllowedNameTheContextHolders(Set.of(SpringContextAdapter.class))
                .check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void adapter_WhenTheAllowedSetIsEmpty_FailsNamingIt() {
        JavaClasses adapter = new ClassFileImporter().importClasses(SpringContextAdapter.class);

        assertThatThrownBy(() -> onlyAllowedNameTheContextHolders(Set.of()).check(adapter))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("<" + SpringContextAdapter.class.getName() + ".viewer()> calls method <"
                        + SECURITY_CONTEXT + ".Security" + HOLDER_SUFFIX + ".getContext()>");
    }

    static ArchRule onlyAllowedNameTheContextHolders(Set<Class<?>> allowed) {
        return noClasses()
                .that(not(belongToAnyOf(allowed.toArray(Class<?>[]::new))))
                .should().dependOnClassesThat(resideInAnyPackage(SECURITY_CONTEXT, REQUEST_CONTEXT)
                        .and(simpleNameEndingWith(HOLDER_SUFFIX)))
                .as("only SpringContextAdapter reads or writes Spring's thread-bound security and request context");
    }
}
