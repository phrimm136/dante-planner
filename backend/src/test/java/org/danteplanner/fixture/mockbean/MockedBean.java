package org.danteplanner.fixture.mockbean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean
public @interface MockedBean {
}
