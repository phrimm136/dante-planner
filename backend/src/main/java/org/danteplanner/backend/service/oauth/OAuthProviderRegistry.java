package org.danteplanner.backend.service.oauth;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for OAuth provider lookup.
 *
 * Automatically discovers all {@link OAuthProvider} beans and builds
 * a name-to-provider map for runtime lookup.
 *
 * Usage:
 * <pre>
 * OAuthProvider provider = registry.getProvider("google");
 * OAuthTokens tokens = provider.exchangeCodeForTokens(code, redirectUri, verifier);
 * </pre>
 */
@Service
public class OAuthProviderRegistry {

    private final Map<String, OAuthProvider> providers;

    /**
     * Constructs registry from all discovered OAuthProvider beans.
     *
     * @param providerList All OAuthProvider implementations (auto-injected by Spring)
     */
    public OAuthProviderRegistry(List<OAuthProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        OAuthProvider::getProviderName,
                        Function.identity()
                ));
    }

    /**
     * Get provider by name.
     *
     * @param name Provider name in lowercase (e.g., "google", "apple")
     * @return The OAuth provider implementation
     * @throws IllegalArgumentException if provider not found
     */
    public OAuthProvider getProvider(String name) {
        OAuthProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Unknown OAuth provider: " + name);
        }
        return provider;
    }

    /**
     * Check if a provider is registered.
     *
     * @param name Provider name to check
     * @return true if provider exists
     */
    public boolean hasProvider(String name) {
        return providers.containsKey(name.toLowerCase());
    }
}
