package org.danteplanner.backend.service.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.OAuthProperties;
import org.danteplanner.backend.exception.OAuthException;
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
import org.springframework.web.client.RestTemplate;

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

    private static final String PROVIDER_NAME = "google";
    private static final String TOKEN_URL =
        "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL =
        "https://www.googleapis.com/oauth2/v2/userinfo";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthProperties oAuthProperties;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
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
                TOKEN_URL,
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
                USER_INFO_URL,
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
        if (tokens.idToken() != null) {
            try {
                String payload = tokens.idToken().split("\\.")[1];
                byte[] decoded = Base64.getUrlDecoder().decode(payload);
                JsonNode json = objectMapper.readTree(decoded);
                return new OAuthUserInfo(
                    json.get("sub").asText(),
                    json.get("email").asText()
                );
            } catch (Exception e) {
                log.warn(
                    "Failed to extract user info from id_token, falling back to userinfo endpoint",
                    e
                );
            }
        }
        return getUserInfo(tokens.accessToken());
    }

    private OAuthTokens parseTokenResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            JsonNode accessTokenNode = json.get("access_token");
            if (accessTokenNode == null || accessTokenNode.isNull()) {
                throw new OAuthException(
                    PROVIDER_NAME,
                    "token_parse",
                    "Missing required field: access_token",
                    null
                );
            }

            String accessToken = accessTokenNode.asText();
            String refreshToken = json.has("refresh_token")
                ? json.get("refresh_token").asText()
                : null;
            String idToken = json.has("id_token")
                ? json.get("id_token").asText()
                : null;
            Long expiresIn = json.has("expires_in")
                ? json.get("expires_in").asLong()
                : null;

            return new OAuthTokens(
                accessToken,
                refreshToken,
                idToken,
                expiresIn
            );
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
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

            JsonNode idNode = json.get("id");
            JsonNode emailNode = json.get("email");

            if (idNode == null || idNode.isNull()) {
                throw new OAuthException(
                    PROVIDER_NAME,
                    "userinfo_parse",
                    "Missing required field: id",
                    null
                );
            }
            if (emailNode == null || emailNode.isNull()) {
                throw new OAuthException(
                    PROVIDER_NAME,
                    "userinfo_parse",
                    "Missing required field: email",
                    null
                );
            }

            return new OAuthUserInfo(idNode.asText(), emailNode.asText());
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthException(
                PROVIDER_NAME,
                "userinfo_parse",
                "Failed to parse user info response",
                e
            );
        }
    }
}
