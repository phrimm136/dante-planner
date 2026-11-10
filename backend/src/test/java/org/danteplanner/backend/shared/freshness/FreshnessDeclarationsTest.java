package org.danteplanner.backend.shared.freshness;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreshnessDeclarationsTest {

    @AfterEach
    void tearDown() {
        FreshnessDeclarations.clear();
    }

    @Test
    void declare_WhenNoRequestWindowIsOpen_DeclaresNothing() {
        FreshnessDeclarations.declare(new FreshnessScope.Global());

        assertThat(FreshnessDeclarations.declared()).isEmpty();
    }

    @Test
    void declare_WhenCalledTwiceInOneWindow_HoldsEachScopeOnce() {
        FreshnessScope planner = new FreshnessScope.Planner(UUID.randomUUID());
        FreshnessDeclarations.begin();

        FreshnessDeclarations.declare(planner, new FreshnessScope.Global());
        FreshnessDeclarations.declare(new FreshnessScope.Global(), new FreshnessScope.User(7L));

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                planner, new FreshnessScope.Global(), new FreshnessScope.User(7L));
    }

    @Test
    void begin_WhenAPreviousWindowDeclaredScopes_StartsEmpty() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declare(new FreshnessScope.Global());

        FreshnessDeclarations.begin();

        assertThat(FreshnessDeclarations.declared()).isEmpty();
    }

    @Test
    void begin_WhenAWindowOpens_IsUndeclared() {
        FreshnessDeclarations.begin();

        assertThat(FreshnessDeclarations.declaration()).isEqualTo(new FreshnessDeclaration.Undeclared());
    }

    @Test
    void declare_WhenTheWindowIsUndeclared_GivesScopes() {
        FreshnessDeclarations.begin();

        FreshnessDeclarations.declare(new FreshnessScope.User(7L));

        assertThat(FreshnessDeclarations.declaration())
                .isEqualTo(new FreshnessDeclaration.Scopes(Set.of(new FreshnessScope.User(7L))));
    }

    @Test
    void declare_WhenTheWindowHoldsScopes_GivesTheirUnion() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declare(new FreshnessScope.User(7L));

        FreshnessDeclarations.declare(new FreshnessScope.Global(), new FreshnessScope.User(7L));

        assertThat(FreshnessDeclarations.declaration()).isEqualTo(new FreshnessDeclaration.Scopes(
                Set.of(new FreshnessScope.User(7L), new FreshnessScope.Global())));
    }

    @Test
    void declare_WhenTheWindowDeclaredNone_GivesScopes() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declareNone("no read renders it");

        FreshnessDeclarations.declare(new FreshnessScope.Global());

        assertThat(FreshnessDeclarations.declaration())
                .isEqualTo(new FreshnessDeclaration.Scopes(Set.of(new FreshnessScope.Global())));
    }

    @Test
    void declareNone_WhenTheWindowIsUndeclared_GivesNone() {
        FreshnessDeclarations.begin();

        FreshnessDeclarations.declareNone("no read renders a comment report");

        assertThat(FreshnessDeclarations.declaration())
                .isEqualTo(new FreshnessDeclaration.None("no read renders a comment report"));
        assertThat(FreshnessDeclarations.declared()).isEmpty();
    }

    @Test
    void declareNone_WhenTheWindowHoldsScopes_KeepsScopes() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declare(new FreshnessScope.Global());

        FreshnessDeclarations.declareNone("no read renders it");

        assertThat(FreshnessDeclarations.declaration())
                .isEqualTo(new FreshnessDeclaration.Scopes(Set.of(new FreshnessScope.Global())));
    }

    @Test
    void declareNone_WhenTheWindowDeclaredNone_KeepsNone() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declareNone("first reason");

        FreshnessDeclarations.declareNone("second reason");

        assertThat(FreshnessDeclarations.declaration()).isEqualTo(new FreshnessDeclaration.None("first reason"));
    }

    @Test
    void declareNone_WhenTheReasonIsBlank_IsRejected() {
        FreshnessDeclarations.begin();

        assertThatThrownBy(() -> FreshnessDeclarations.declareNone(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(FreshnessDeclarations.declaration()).isEqualTo(new FreshnessDeclaration.Undeclared());
    }

    @Test
    void declareNone_WhenNoRequestWindowIsOpen_DeclaresNothing() {
        FreshnessDeclarations.declareNone("no read renders it");

        assertThat(FreshnessDeclarations.declaration()).isEqualTo(new FreshnessDeclaration.Undeclared());
    }
}
