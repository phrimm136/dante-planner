# syntax=docker/dockerfile:1
ARG JAR_STAGE=builder
FROM --platform=$BUILDPLATFORM gradle:9.8.2-jdk21-noble@sha256:7b50a8bc3d5dab9db23053c9fd517f8dfc5166eedad236545eca019fb613ebfd AS builder

WORKDIR /app

COPY backend/build.gradle.kts backend/settings.gradle.kts ./

RUN --mount=type=cache,target=/root/.gradle \
    gradle dependencies --no-daemon

COPY backend/src ./src
COPY testdata/planner-content.schema.json /testdata/planner-content.schema.json

# Declared after the dependency layers so a new commit does not invalidate them.
ARG GIT_SHA=unknown

RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon -PgitSha=$GIT_SHA

FROM scratch AS jar-prebuilt

COPY --from=backend-jar --exclude=*-plain.jar build/libs/*.jar /app/build/libs/

FROM ${JAR_STAGE} AS jar

# Bake the public Amazon RDS CA bundle into a PKCS12 truststore so the backend
# can verify the RDS server certificate (sslMode=VERIFY_CA). The bundle is
# public; the store password guards integrity only, not confidentiality.
# Runs on $BUILDPLATFORM so keytool executes natively (not under QEMU on the
# amd64 runner) and the layer caches independently of app code and game data;
# the resulting .p12 is architecture-independent.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jre-alpine-3.21@sha256:e24e734d8d6bc6372b5b8604aa8f838aae22775b0ec90e7da33e828b94620da9 AS rds-certs

RUN set -eux; \
    apk add --no-cache curl; \
    mkdir -p /app/certs; \
    curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o /tmp/rds-bundle.pem; \
    awk 'BEGIN{c=0} /-----BEGIN CERTIFICATE-----/{c++} {print > ("/tmp/rds-cert-" c ".pem")}' /tmp/rds-bundle.pem; \
    for cert in /tmp/rds-cert-*.pem; do \
      grep -q 'BEGIN CERTIFICATE' "$cert" || continue; \
      keytool -importcert -noprompt -alias "rds-$(basename "$cert" .pem)" \
        -file "$cert" -keystore /app/certs/rds-truststore.p12 \
        -storetype PKCS12 -storepass changeit; \
    done; \
    rm -f /tmp/rds-bundle.pem /tmp/rds-cert-*.pem

FROM eclipse-temurin:21-jre-alpine-3.21@sha256:e24e734d8d6bc6372b5b8604aa8f838aae22775b0ec90e7da33e828b94620da9

WORKDIR /app

RUN apk add --no-cache curl

# CVE-2026-91403: eMudhra roots signed intermediates with weak private keys,
# so anything chained to them is distrusted outright.
RUN set -eux; \
    for alias in \
      "cn_emsign_ecc_root_ca__c3,o_emudhra_inc,ou_emsign_pki,c_us [jdk]" \
      "cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" \
      "cn_emsign_root_ca__c1,o_emudhra_inc,ou_emsign_pki,c_us [jdk]" \
      "cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]"; do \
      if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
        keytool -delete -noprompt -cacerts -storepass changeit -alias "$alias"; \
      fi; \
    done; \
    ! keytool -list -cacerts -storepass changeit | grep -qi emudhra

COPY --from=jar /app/build/libs/*.jar app.jar

# Bake the game domain data so the pod is self-contained; k3s has no host
# bind-mount. The backend reads it via GAME_DATA_PATH (GameDataRegistry).
# Build context must be repo root.
COPY --from=static data /app/data
ENV GAME_DATA_PATH=/app/data

COPY --from=rds-certs /app/certs /app/certs

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
