package org.danteplanner.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class GoogleOAuthService {

    @Value("${oauth.google.client-id}")
    private String clientId;

    @Value("${oauth.google.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> exchangeCodeForToken(String code, String redirectUri) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

        try {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            Map<String, String> tokens = new HashMap<>();
            tokens.put("access_token", jsonNode.get("access_token").asText());
            if (jsonNode.has("refresh_token")) {
                tokens.put("refresh_token", jsonNode.get("refresh_token").asText());
            }
            return tokens;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response", e);
        }
    }

    public Map<String, String> getUserInfo(String accessToken) {
        String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, String.class);

        try {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("id", jsonNode.get("id").asText());
            userInfo.put("email", jsonNode.get("email").asText());
            userInfo.put("name", jsonNode.get("name").asText());
            if (jsonNode.has("picture")) {
                userInfo.put("picture", jsonNode.get("picture").asText());
            }
            return userInfo;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse user info response", e);
        }
    }
}
