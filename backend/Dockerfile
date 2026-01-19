# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

# Use BuildKit cache mount for Maven repository - persists across builds
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Use JAVA_OPTS from environment, with fallback defaults for 2GB systems
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
