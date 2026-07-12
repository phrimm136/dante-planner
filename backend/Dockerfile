# syntax=docker/dockerfile:1
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

COPY backend/build.gradle.kts backend/settings.gradle.kts ./

RUN --mount=type=cache,target=/root/.gradle \
    gradle dependencies --no-daemon

COPY backend/src ./src

RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/build/libs/*.jar app.jar

# Bake the game domain data so the pod is self-contained; k3s has no host
# bind-mount. The backend reads it via GAME_DATA_PATH (GameDataRegistry).
# Build context must be repo root.
COPY static/data /app/data
ENV GAME_DATA_PATH=/app/data

# Bake the public Amazon RDS CA bundle into a PKCS12 truststore so the backend
# can verify the RDS server certificate (sslMode=VERIFY_CA). The bundle is
# public; the store password guards integrity only, not confidentiality.
RUN set -eux; \
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

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
