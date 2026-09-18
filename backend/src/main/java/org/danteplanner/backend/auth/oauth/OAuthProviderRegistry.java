package org.danteplanner.backend.auth.oauth;

import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Locale;

@Service
public class OAuthProviderRegistry {

    private final Map<String, OAuthProvider> providers;

    public OAuthProviderRegistry(List<OAuthProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        OAuthProvider::getProviderName,
                        Function.identity()
                ));
    }

    public OAuthProvider getProvider(String name) {
        OAuthProvider provider = providers.get(name.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new InvalidRequestException("UNKNOWN_OAUTH_PROVIDER", "Unknown OAuth provider: " + name);
        }
        return provider;
    }

    public boolean hasProvider(String name) {
        return providers.containsKey(name.toLowerCase(Locale.ROOT));
    }
}
