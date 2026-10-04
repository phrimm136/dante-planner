package org.danteplanner.moduleboundaryfixture.backend.planner.service;

import org.danteplanner.moduleboundaryfixture.backend.user.api.FooProvider;

public class FooProviderImplementationFixture implements FooProvider {

    @Override
    public String provide() {
        return "";
    }
}
