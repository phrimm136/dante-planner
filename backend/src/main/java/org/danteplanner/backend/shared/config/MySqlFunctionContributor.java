package org.danteplanner.backend.shared.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Exposes MySQL's FULLTEXT relevance operator to HQL/Criteria as
 * {@code match_against(column, query)} so Specifications can compose the ngram
 * title search with ordinary predicates.
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
