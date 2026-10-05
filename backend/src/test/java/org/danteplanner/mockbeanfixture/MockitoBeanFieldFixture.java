package org.danteplanner.mockbeanfixture;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@MockitoBean(types = Runnable.class)
@MockitoBean(types = Comparable.class)
public class MockitoBeanFieldFixture {

    @MockitoSpyBean
    private Runnable spied;

    @SuppressWarnings("removal")
    @MockBean
    private Runnable bootMocked;

    @MockedBean
    private Runnable metaMocked;
}
