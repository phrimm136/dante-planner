package org.danteplanner.backend.shared.freshness;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.util.Assert;

public sealed interface FreshnessDeclaration {

    default FreshnessDeclaration declare(Set<FreshnessScope> scopes) {
        if (scopes.isEmpty()) {
            return this;
        }
        Set<FreshnessScope> union = new LinkedHashSet<>(scopes());
        union.addAll(scopes);
        return new Scopes(union);
    }

    FreshnessDeclaration declareNone(None none);

    default Set<FreshnessScope> scopes() {
        return Set.of();
    }

    record Undeclared() implements FreshnessDeclaration {

        @Override
        public FreshnessDeclaration declareNone(None none) {
            return none;
        }
    }

    record Scopes(Set<FreshnessScope> scopes) implements FreshnessDeclaration {

        public Scopes {
            Assert.notEmpty(scopes, "a Scopes declaration names at least one scope");
            scopes = Set.copyOf(scopes);
        }

        @Override
        public FreshnessDeclaration declareNone(None none) {
            return this;
        }
    }

    record None(String reason) implements FreshnessDeclaration {

        public None {
            Assert.hasText(reason, "a None declaration needs a reason");
        }

        @Override
        public FreshnessDeclaration declareNone(None none) {
            return this;
        }
    }
}
