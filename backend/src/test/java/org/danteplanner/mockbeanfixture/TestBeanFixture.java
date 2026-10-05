package org.danteplanner.mockbeanfixture;

import org.springframework.test.context.bean.override.convention.TestBean;

public class TestBeanFixture {

    @TestBean
    private Runnable runnable;

    static Runnable runnable() {
        return () -> { };
    }
}
