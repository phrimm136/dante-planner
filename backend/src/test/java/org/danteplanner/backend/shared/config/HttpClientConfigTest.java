package org.danteplanner.backend.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link HttpClientConfig#restTemplate()} bean bounds its connect and read
 * timeouts so OAuth calls to Google cannot hang a request thread indefinitely.
 */
class HttpClientConfigTest {

    @Test
    void restTemplate_WhenBuilt_HasBoundedTimeouts() {
        RestTemplate restTemplate = new HttpClientConfig().restTemplate();

        // With interceptors registered, getRequestFactory() returns the intercepting wrapper;
        // the timeouts live on its delegate.
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        if (factory instanceof InterceptingClientHttpRequestFactory) {
            factory = (ClientHttpRequestFactory) ReflectionTestUtils.getField(factory, "requestFactory");
        }
        Integer connectTimeout = (Integer) ReflectionTestUtils.getField(factory, "connectTimeout");
        Integer readTimeout = (Integer) ReflectionTestUtils.getField(factory, "readTimeout");

        assertThat(connectTimeout).isEqualTo(3000);
        assertThat(readTimeout).isEqualTo(5000);
    }

    @Test
    void restTemplate_WhenBuilt_SendsProductUserAgent() {
        RestTemplate restTemplate = new HttpClientConfig().restTemplate();

        assertThat(restTemplate.getInterceptors()).isNotEmpty();
    }
}
