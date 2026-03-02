# syntax=docker/dockerfile:1
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
