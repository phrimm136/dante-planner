package org.danteplanner.backend.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public final class GoogleStub {

    public static final String CLIENT_ID = "test-client-id";

    private static final String TOKEN_PATH = "/token";
    private static final String USER_INFO_PATH = "/userinfo";
    private static final String JWKS_PATH = "/jwks";
    private static final String ACCESS_TOKEN_PREFIX = "stub-access-";
    private static final Duration ID_TOKEN_LIFETIME = Duration.ofMinutes(10);
    private static final int RSA_KEY_BITS = 2048;

    private final WireMockServer server;
    private final RSAKey signingKey;

    private GoogleStub() {
        try {
            signingKey = new RSAKeyGenerator(RSA_KEY_BITS).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("could not generate the stub's signing key", e);
        }
        server = new WireMockServer(options().dynamicPort());
        server.start();
        server.stubFor(get(urlPathEqualTo(JWKS_PATH))
                .willReturn(okJson(new JWKSet(signingKey.toPublicJWK()).toString())));
        server.stubFor(get(urlPathEqualTo(USER_INFO_PATH))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_token\"}")));
    }

    private static final class Holder {
        private static final GoogleStub INSTANCE = new GoogleStub();
    }

    private static GoogleStub instance() {
        return Holder.INSTANCE;
    }

    public static String issuer() {
        return instance().server.baseUrl();
    }

    public static String tokenUrl() {
        return instance().server.baseUrl() + TOKEN_PATH;
    }

    public static String userInfoUrl() {
        return instance().server.baseUrl() + USER_INFO_PATH;
    }

    public static String jwksUri() {
        return instance().server.baseUrl() + JWKS_PATH;
    }

    public static Script script(String code) {
        return new Script(instance(), code);
    }

    public static int tokenCalls(String code) {
        return instance().server.findAll(postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withFormParam("code", equalTo(code))).size();
    }

    public static final class Script {

        private final GoogleStub stub;
        private final String code;

        private Script(GoogleStub stub, String code) {
            this.stub = stub;
            this.code = code;
        }

        public Script user(String subject, String email) {
            stub.server.stubFor(tokenRequest().willReturn(okJson(
                    "{\"access_token\":\"" + ACCESS_TOKEN_PREFIX + code + "\","
                            + "\"token_type\":\"Bearer\",\"expires_in\":3600,"
                            + "\"id_token\":\"" + stub.idToken(subject, email) + "\"}")));
            return this;
        }

        public Script tokenEndpointFails() {
            stub.server.stubFor(tokenRequest().willReturn(aResponse().withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"internal_failure\"}")));
            return this;
        }

        private MappingBuilder tokenRequest() {
            return post(urlPathEqualTo(TOKEN_PATH)).withFormParam("code", equalTo(code));
        }
    }

    private String idToken(String subject, String email) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(server.baseUrl())
                .audience(CLIENT_ID)
                .subject(subject)
                .claim("email", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ID_TOKEN_LIFETIME)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign the stub id_token", e);
        }
        return jwt.serialize();
    }
}
