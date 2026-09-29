package org.danteplanner.roleclaimfixture;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public class RoleDslFixture {

    public void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/api/fixture/**").hasRole("MODERATOR"));
    }
}
