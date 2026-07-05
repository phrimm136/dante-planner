plugins {
    java
    id("org.springframework.boot") version "3.5.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.2.3.7755"
    id("org.owasp.dependencycheck") version "12.2.0"
    id("de.aaschmid.cpd") version "3.5"
    id("net.ltgt.errorprone") version "4.3.0"
    checkstyle
    jacoco
}

group = "org.danteplanner"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Override tomcat to fix CLIENT_CERT auth bypass CVE
extra["tomcat.version"] = "10.1.53"

dependencyManagement {
    imports {
        // Override log4j to fix CVE-2025-68161
        mavenBom("org.apache.logging.log4j:log4j-bom:2.25.4")
        // Override Spring Security to fix CVE-2026-22732
        mavenBom("org.springframework.security:spring-security-bom:6.5.9")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    runtimeOnly("com.mysql:mysql-connector-j")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.37.1")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.testcontainers:testcontainers:2.0.4")
    testImplementation("org.testcontainers:testcontainers-mysql:2.0.4")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.4")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    errorprone("com.google.errorprone:error_prone_core:2.36.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

// Error Prone: bug-pattern axis. Default ERROR-level checks only (no experimental).
// Lombok-generated code carries @lombok.Generated (lombok.config) which Error Prone
// skips, so the generator and the analyzer do not fight. JDK 16+ requires the javac
// internals to be exported to the Error Prone plugin, via a forked compiler.
tasks.withType<JavaCompile>().configureEach {
    options.forkOptions.jvmArgs!!.addAll(
        listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        )
    )
}

checkstyle {
    toolVersion = "10.21.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

// Public-type Javadoc scoped to service classes only; kept out of the main
// config so DTOs/entities/controllers are not pulled into a Javadoc baseline.
val checkstyleServiceJavadoc by tasks.registering(Checkstyle::class) {
    configFile = file("config/checkstyle/checkstyle-service-javadoc.xml")
    source("src/main/java")
    include("**/service/**Service.java")
    classpath = files()
    reports {
        html.required = true
        xml.required = true
    }
}

tasks.check {
    dependsOn(checkstyleServiceJavadoc)
}

// Copy-paste detection as a regression ratchet: catch NEW production duplication.
// Test sources are excluded — assertion/arrange scaffolding legitimately repeats
// and would force a threshold high enough to blind the main-source check.
// Threshold set just above the two pre-existing blocks the consolidation phases
// deliberately kept (structure-varying / rule-of-three exempt, docs/32 Decisions):
// PlannerIndexService's twin equipment-iteration extractors (112 tokens) and the
// PlannerBookmarkId/SubscriptionId/VoteId composite-key boilerplate (101 tokens).
cpd {
    language = "java"
    toolVersion = "7.7.0"
    minimumTokenCount = 120
}

tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
    source = files("src/main/java").asFileTree
    reports {
        text.required = true
        xml.required = false
    }
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        xml.outputLocation = layout.buildDirectory.file("reports/jacoco/test/jacoco.xml")
        html.required = true
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.57".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

sonarqube {
    properties {
        property("sonar.organization", "danteplanner")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.projectKey", "danteplanner_backend")
        property("sonar.projectName", "DantePlanner Backend")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.java.binaries", "build/classes/java/main")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacoco.xml")
        property("sonar.dependencyCheck.htmlReportPath", "build/reports/dependency-check-report.html")
        property("sonar.dependencyCheck.jsonReportPath", "build/reports/dependency-check-report.json")
    }
}

dependencyCheck {
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
    formats = listOf("HTML", "JSON")
}
