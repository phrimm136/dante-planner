package org.danteplanner.backend.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link HttpClientConfig#restTemplate()} bean bounds its connect and read
 * timeouts so OAuth calls to Google cannot hang a request thread indefinitely.
 */
class HttpClientConfigTest {

    @Test
    @DisplayName("restTemplate_WhenBuilt_HasBoundedTimeouts")
    void restTemplate_WhenBuilt_HasBoundedTimeouts() {
        RestTemplate restTemplate = new HttpClientConfig().restTemplate();

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        Integer connectTimeout = (Integer) ReflectionTestUtils.getField(factory, "connectTimeout");
        Integer readTimeout = (Integer) ReflectionTestUtils.getField(factory, "readTimeout");

        assertThat(connectTimeout).isEqualTo(3000);
        assertThat(readTimeout).isEqualTo(5000);
    }
}
