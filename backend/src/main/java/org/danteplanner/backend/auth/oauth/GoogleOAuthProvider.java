package org.danteplanner.backend.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google OAuth provider implementation.
 *
 * Handles Google-specific OAuth 2.0 token exchange with PKCE support
 * and user info retrieval via Google's userinfo endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthProvider implements OAuthProvider {

    private static final String PROVIDER_NAME = AuthProviderType.GOOGLE.getValue();
    private static final String SCOPE = "openid email";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthProperties oAuthProperties;
    private final GoogleIdTokenVerifier idTokenVerifier;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String buildAuthorizationUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUriString(oAuthProperties.getGoogle().getAuthorizeUrl())
            .queryParam("client_id", oAuthProperties.getGoogle().getClientId())
            .queryParam("redirect_uri", oAuthProperties.getGoogle().getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", SCOPE)
            .queryParam("state", state)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .build()
            .encode()
            .toUriString();
    }

    @Override
    public OAuthTokens exchangeCodeForTokens(
        String code,
        String redirectUri,
        String codeVerifier
    ) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", oAuthProperties.getGoogle().getClientId());
        params.add(
            "client_secret",
            oAuthProperties.getGoogle().getClientSecret()
        );
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");
        params.add("code_verifier", codeVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(
            params,
            headers
        );

        try {
            log.debug(
                "Exchanging OAuth code with redirect_uri: {}",
                redirectUri
            );
            ResponseEntity<String> response = restTemplate.postForEntity(
                oAuthProperties.getGoogle().getTokenUrl(),
                request,
                String.class
            );
            return parseTokenResponse(response.getBody());
        } catch (HttpStatusCodeException e) {
            // Log Google's actual error response for debugging
            log.error(
                "Google OAuth token exchange failed. Status: {}, Response: {}",
                e.getStatusCode(),
                e.getResponseBodyAsString()
            );
            throw new OAuthException(
                PROVIDER_NAME,
                "token_exchange",
                "Failed to exchange code for tokens: " +
                    e.getResponseBodyAsString(),
                e
            );
        } catch (RestClientException e) {
            log.error(
                "Google OAuth token exchange failed with exception: {}",
                e.getMessage()
            );
            throw new OAuthException(
                PROVIDER_NAME,
                "token_exchange",
                "Failed to exchange code for tokens",
                e
            );
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                oAuthProperties.getGoogle().getUserInfoUrl(),
                HttpMethod.GET,
                request,
                String.class
            );
            return parseUserInfoResponse(response.getBody());
        } catch (RestClientException e) {
            throw new OAuthException(
                PROVIDER_NAME,
                "user_info",
                "Failed to retrieve user info",
                e
            );
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(OAuthTokens tokens) {
        if (tokens.idToken() == null) {
            return getUserInfo(tokens.accessToken());
        }
        Jwt verified = idTokenVerifier.verify(tokens.idToken());
        // Signature validity says nothing about which optional claims the consent screen
        // granted, and both fields are required downstream.
        String subject = requireClaim(verified.getSubject(), "sub");
        String email = requireClaim(verified.getClaimAsString("email"), "email");
        return new OAuthUserInfo(subject, email);
    }

    private String requireClaim(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new OAuthException(
                PROVIDER_NAME,
                "id_token",
                "Missing required field: " + claim
            );
        }
        return value;
    }

    private OAuthTokens parseTokenResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            JsonNode accessTokenNode = json.path("access_token");
            if (accessTokenNode.isMissingNode() || accessTokenNode.isNull()) {
                throw new OAuthException(
                    PROVIDER_NAME,
                    "token_parse",
                    "Missing required field: access_token"
                );
            }

            String accessToken = accessTokenNode.asText();
            String refreshToken = json.path("refresh_token").asText(null);
            String idToken = json.path("id_token").asText(null);

            return new OAuthTokens(
                accessToken,
                refreshToken,
                idToken
            );
        } catch (JsonProcessingException e) {
            throw new OAuthException(
                PROVIDER_NAME,
                "token_parse",
                "Failed to parse token response",
                e
            );
        }
    }

    private OAuthUserInfo parseUserInfoResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            JsonNode idNode = json.path("id");
            JsonNode emailNode = json.path("email");

            if (idNode.isMissingNode() || idNode.isNull()) {
                throw new OAuthException(
                    PROVIDER_NAME,
                    "userinfo_parse",
                    "Missing required field: id"
                );
            }
            if (emailNode.isMissingNode() || emailNode.isNull()) {
                throw new OAuthException(
                    PROVIDER_NAME,
                    "userinfo_parse",
                    "Missing required field: email"
                );
            }

            return new OAuthUserInfo(idNode.asText(), emailNode.asText());
        } catch (JsonProcessingException e) {
            throw new OAuthException(
                PROVIDER_NAME,
                "userinfo_parse",
                "Failed to parse user info response",
                e
            );
        }
    }
}
