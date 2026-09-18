package org.danteplanner.backend.shared.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registered via {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 */
public class MySqlFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry()
                .patternDescriptorBuilder("match_against", "MATCH (?1) AGAINST (?2 IN BOOLEAN MODE)")
                .setInvariantType(functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.DOUBLE))
                .setExactArgumentCount(2)
                .register();
    }
}
