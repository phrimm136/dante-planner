import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.2.3.7755"
    id("org.owasp.dependencycheck") version "12.2.0"
    id("de.aaschmid.cpd") version "3.5"
    id("net.ltgt.errorprone") version "4.3.0"
    id("info.solidsoft.pitest") version "1.19.0-rc.1"
    id("org.gradle.test-retry") version "1.6.6"
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

// -PgitSha is supplied by backend/Dockerfile's GIT_SHA build arg.
springBoot {
    buildInfo {
        properties {
            additional.put("commit", providers.gradleProperty("gitSha").orElse("unknown"))
        }
    }
}

repositories {
    mavenCentral()
}

val consistency by sourceSets.creating

sourceSets.test {
    compileClasspath += consistency.output
    runtimeClasspath += consistency.output
}

dependencyManagement {
    imports {
        // Override log4j to fix CVE-2025-68161
        mavenBom("org.apache.logging.log4j:log4j-bom:2.25.4")
        // Override Jackson to fix CVE-2026-54515
        mavenBom("com.fasterxml.jackson:jackson-bom:2.21.5")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("com.mysql:mysql-connector-j")
    implementation("com.github.ben-manes.caffeine:caffeine")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.boot:spring-boot-jackson2")

    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.16.0")

    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.3.0")

    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.bucket4j:bucket4j-redis:8.10.1")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.networknt:json-schema-validator:1.5.1")
    implementation("io.sentry:sentry-spring-boot-4-starter:8.37.1")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.testcontainers:testcontainers:2.0.4")
    testImplementation("org.testcontainers:testcontainers-mysql:2.0.4")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.4")
    testImplementation("org.testcontainers:testcontainers-toxiproxy:2.0.4")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("net.jqwik:jqwik:1.9.3")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")

    errorprone("com.google.errorprone:error_prone_core:2.36.0")

    "consistencyImplementation"("com.fasterxml.jackson.core:jackson-databind")
}

tasks.processResources {
    from(layout.projectDirectory.file("../testdata/planner-content.schema.json")) {
        into("planner")
    }
}

tasks.withType<Test> {
    val excludeTags = (project.findProperty("excludeTags") as String?)
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: emptyList()
    inputs.property("excludeTags", excludeTags)
    useJUnitPlatform {
        if (excludeTags.isNotEmpty()) {
            excludeTags(*excludeTags.toTypedArray())
        }
    }
    // Sized so the cache never evicts. Eviction closes a context while other classes are still
    // running against it, and the default bound of 32 sits below what this suite creates, so the
    // failure lands in an unrelated class at full suite size only. Must stay above the
    // containerized class count; TestIsolationConventionTest budgets the classes that key their own.
    systemProperty("spring.test.context.cache.maxSize", "513")
    systemProperty("spring.test.context.cache.pause", "never")
    systemProperty("org.springframework.boot.logging.LoggingSystem", "none")
    System.getProperties().stringPropertyNames()
        .filter { it.startsWith("consistency.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
    // One JVM, with class concurrency inside it (junit-platform.properties). Every fork repeats the
    // whole fixed cost — a JVM, its Spring contexts and a Testcontainers set — so a fork per core
    // costs memory that a thread per class does not.
    maxParallelForks = 1
    // Measured: at most 989 MiB survives a collecting GC with twelve classes running; 2 GiB is about twice that.
    maxHeapSize = "2g"
    // Test JVMs are short-lived and dominated by Spring context startup, so C1-only JIT favors
    // fast warmup over peak speed the fork never reaches.
    //
    // G1 rather than the throughput collector because it uncommits: a JVM that peaks during one
    // heavy context would otherwise hold that heap for the rest of the run. The free-ratio bounds
    // are what make it give the memory back between contexts.
    //
    // The code cache is sized up because a fork hosting the whole suite loads enough classes to
    // exhaust the adapter space ("Out of space in CodeCache for adapters").
    jvmArgs("-XX:TieredStopAtLevel=1", "-XX:+UseG1GC", "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30",
            "-XX:ReservedCodeCacheSize=256m")
    filter {
        includeTestsMatching("*Test")
        includeTestsMatching("*Tests")
        includeTestsMatching("*IntegrationTest")
        includeTestsMatching("*IT")
    }
}

// Error Prone: bug-pattern axis. Default ERROR-level checks only (no experimental).
// Lombok-generated code carries @lombok.Generated (lombok.config) which Error Prone
// skips, so the generator and the analyzer do not fight. JDK 16+ requires the javac
// internals to be exported to the Error Prone plugin, via a forked compiler.
// Mutation coverage, not line coverage: a surviving mutant is a line no test protects.
// Scoped to the packages carrying the most behavior per line.
pitest {
    targetClasses.set(listOf(
        "org.danteplanner.backend.planner.service.*",
        "org.danteplanner.backend.auth.token.*",
        "org.danteplanner.backend.shared.readpath.*",
        "org.danteplanner.backend.planner.validation.floor.*",
        "org.danteplanner.backend.planner.validation.sinner.*",
        "org.danteplanner.backend.planner.validation.identity.*",
        "org.danteplanner.backend.planner.gamedata.IdentityLevels*",
        "org.danteplanner.backend.planner.validation.ego.*",
        "org.danteplanner.backend.planner.validation.skill.*",
        "org.danteplanner.backend.planner.validation.egogift.*",
        "org.danteplanner.backend.planner.validation.startbuff.*",
        "org.danteplanner.backend.planner.validation.startgift.*",
        "org.danteplanner.backend.planner.validation.Violation*",
        "org.danteplanner.backend.planner.validation.ContentPath*",
        "org.danteplanner.backend.planner.validation.Positions*",
        "org.danteplanner.consistency.*"))
    mainSourceSets.set(setOf(sourceSets.main.get(), consistency))
    // Without this, PIT derives targetTests from targetClasses and misses any test whose package
    // differs from the class under test.
    targetTests.set(listOf("org.danteplanner.backend.*", "org.danteplanner.consistency.*"))
    excludedTestClasses.set(listOf("*IT", "*IntegrationTest", "*ControllerTest"))
    // The minion does not inherit the test task's system properties.
    jvmArgs.set(listOf("-Dorg.springframework.boot.logging.LoggingSystem=none", "-Dspring.test.context.cache.pause=never"))
    junit5PluginVersion.set("1.2.1")
    threads.set(4)
    enableDefaultIncrementalAnalysis.set(true)
    timestampedReports.set(false)
    outputFormats.set(setOf("HTML", "XML"))
    // Pinned to 1.19.0-rc.1: the 1.19.0 release crashes the coverage minion on this project
    // (UNKNOWN_ERROR) across every pitest core from 1.19.6 to 1.22.1.
}

val pitestRatchet by tasks.registering {
    dependsOn(tasks.named("pitest"))
    val report = layout.buildDirectory.file("reports/pitest/mutations.xml")
    val baselineFile = layout.projectDirectory.file("config/pitest-surviving-mutants.json")
    inputs.files(report, baselineFile)
    doLast {
        val xml = report.get().asFile
        if (!xml.isFile) {
            throw GradleException("pitestRatchet: $xml is missing; pitest did not produce its XML report.")
        }
        val mutations = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(xml).getElementsByTagName("mutation")
        val elements = (0 until mutations.length).map { mutations.item(it) as org.w3c.dom.Element }
        fun org.w3c.dom.Element.child(name: String): String = getElementsByTagName(name).item(0).textContent
        val survivors = elements.filter { it.getAttribute("detected") == "false" }
            .groupBy { it.child("mutatedClass").substringBeforeLast('.') }
        val current = survivors.mapValues { it.value.size }.toSortedMap()
        val baseline = (groovy.json.JsonSlurper().parse(baselineFile.asFile) as Map<*, *>)
            .entries.associate { it.key as String to (it.value as Number).toInt() }
        val packages = (current.keys + baseline.keys).toSortedSet()
        val raised = packages.filter { (current[it] ?: 0) > (baseline[it] ?: 0) }
        val lowered = packages.filter { (current[it] ?: 0) < (baseline[it] ?: 0) }
        val baselinePath = "backend/config/pitest-surviving-mutants.json"
        val failures = mutableListOf<String>()
        if (raised.isNotEmpty()) {
            failures += "Surviving mutants rose above $baselinePath in:\n" + raised.joinToString("\n") { pkg ->
                "  $pkg: baseline ${baseline[pkg] ?: 0}, current ${current.getValue(pkg)}\n" +
                    survivors.getValue(pkg)
                        .map { Triple(it.child("mutatedClass"), it.child("lineNumber").toInt(), it.child("mutator")) }
                        .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
                        .take(20)
                        .joinToString("\n") { (cls, line, mutator) ->
                            "    ${cls.substringAfterLast('.')}:$line:${mutator.substringAfterLast('.')}"
                        }
            }
        }
        if (lowered.isNotEmpty()) {
            failures += "Surviving mutants fell below $baselinePath in " +
                lowered.joinToString(", ") { "$it (baseline ${baseline[it]}, current ${current[it] ?: 0})" } +
                "; lower the counts in $baselinePath to:\n" +
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(current))
        }
        if (failures.isNotEmpty()) {
            throw GradleException("pitestRatchet: " + failures.joinToString("\n"))
        }
        logger.lifecycle(
            "pitestRatchet: ${current.values.sum()} surviving of ${elements.size} mutants across " +
                "${current.size} packages, equal to $baselinePath")
    }
}

tasks.check {
    dependsOn(pitestRatchet)
}

// Javadoc references are compiler-checked so a comment can cite an invariant test by name and
// the citation cannot outlive its target. The `missing` group stays off: DTO components and
// trivial getters are deliberately undocumented.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:reference", "-quiet")
        addBooleanOption("Werror", true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Environment-dependent behavior is a build failure, not a warning: each of these compiles
    // clean, passes CI, and then misbehaves under one locale, one charset, or one region's clock.
    options.errorprone {
        error("StringCaseLocaleUsage", "DefaultCharset", "JavaTimeDefaultTimeZone")
    }
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

val checkstyleValidation by tasks.registering(Checkstyle::class) {
    configFile = file("config/checkstyle/checkstyle-validation.xml")
    source("src/main/java")
    include("org/danteplanner/backend/planner/validation/**")
    classpath = files()
    reports {
        html.required = true
        xml.required = true
    }
}

tasks.check {
    dependsOn(checkstyleServiceJavadoc)
}

val astGrepBinary = layout.projectDirectory.file("../frontend/node_modules/.bin/ast-grep").asFile.path

val astGrepInstalled by tasks.registering {
    group = "verification"
    val binary = file(astGrepBinary)
    doLast {
        if (!binary.canExecute()) {
            throw GradleException(
                "astGrepInstalled: $binary is missing; run `yarn --cwd frontend install` from the repo root.")
        }
    }
}

val astGrepRuleTests by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(astGrepInstalled)
    executable = astGrepBinary
    args("test")
}

val astGrepStaleIgnores by tasks.registering {
    group = "verification"
    dependsOn(astGrepRuleTests)
    val binary = astGrepBinary
    val projectDir = layout.projectDirectory.asFile
    val rulePath = "config/ast-grep/rules/no-wall-clock-wait-in-tests.yml"
    val rule = layout.projectDirectory.file(rulePath).asFile
    doLast {
        val lines = rule.readLines()
        val item = Regex("""^\s+-\s+(.+)$""")
        val start = lines.indexOf("ignores:")
        if (start < 0) {
            return@doLast
        }
        val end = (start + 1 until lines.size).firstOrNull { !item.matches(lines[it]) } ?: lines.size
        val frozen = lines.subList(start + 1, end).map { item.find(it)!!.groupValues[1] }
        val unfrozen = (lines.subList(0, start) + lines.subList(end, lines.size)).joinToString("\n")
        val scan = ProcessBuilder(
            listOf(binary, "scan", "--inline-rules", unfrozen, "--files-with-matches") + frozen)
            .directory(projectDir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val matched = scan.inputStream.bufferedReader().readLines().toSet()
        scan.waitFor()
        val stale = frozen.filterNot { it in matched }
        if (stale.isNotEmpty()) {
            throw GradleException(
                "astGrepStaleIgnores: these entries in backend/$rulePath no longer match the rule, so they " +
                    "excuse nothing; delete them from its ignores and let the rule cover them again:\n" +
                    stale.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("astGrepStaleIgnores: all ${frozen.size} ignores in backend/$rulePath still match")
    }
}

val astGrep by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(astGrepRuleTests)
    executable = astGrepBinary
    args("scan", "--error", "src/main/java")
}

val astGrepTestSources by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(astGrepRuleTests)
    executable = astGrepBinary
    args("scan", "--error", "src/test/java")
}

tasks.check {
    dependsOn(astGrep, astGrepTestSources, astGrepStaleIgnores, checkstyleValidation)
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

abstract class RandomOrderSeed : CommandLineArgumentProvider {
    @get:Input
    @get:Optional
    abstract val fixedSeed: Property<String>

    @get:Internal
    var drawnSeed: String? = null

    fun seed(): String = fixedSeed.orNull
        ?: drawnSeed
        ?: kotlin.random.Random.nextLong().toString().also { drawnSeed = it }

    override fun asArguments(): Iterable<String> =
        listOf("-Djunit.jupiter.execution.order.random.seed=${seed()}")
}

val staticLockCheckOff = providers.environmentVariable("STATIC_LOCK_CHECK").map { it == "off" }.orElse(false)

val staticLockCheck by tasks.registering(Exec::class) {
    group = "verification"
    onlyIf("STATIC_LOCK_CHECK is not off") {
        if (staticLockCheckOff.get()) {
            logger.lifecycle("static lock check off")
        }
        !staticLockCheckOff.get()
    }
    executable = layout.projectDirectory.file("../.claude/skills/static-checkout/bin/static").asFile.path
    args("check")
}

tasks.test {
    dependsOn(staticLockCheck)
    inputs.file(layout.projectDirectory.file("config/architecture-rules-without-fixtures.txt"))
    val randomOrderSeed = objects.newInstance<RandomOrderSeed>()
    randomOrderSeed.fixedSeed.set(providers.gradleProperty("testSeed"))
    jvmArgumentProviders.add(randomOrderSeed)
    doFirst {
        logger.quiet("JUnit random order seed: ${randomOrderSeed.seed()}")
    }
    finalizedBy(tasks.jacocoTestReport)
    retry {
        maxRetries = 1
        maxFailures = 5
        failOnPassedAfterRetry = true
        failOnSkippedAfterRetry = true
    }
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
