package org.danteplanner.backend.shared.freshness;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("!'true'.equalsIgnoreCase('${datasource.routing.enabled:false}')")
public class DispatchStampConfig {

    @Bean
    public DispatchStamp dispatchStamp() {
        return new NoDispatchStamp();
    }
}
