package org.danteplanner.fixturefixture;

import java.util.function.Supplier;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;

public class ConstructorReferenceFixture {

    static Supplier<Planner> planner() {
        return Planner::new;
    }

    static Supplier<User.UserBuilder> user() {
        return User::builder;
    }
}
