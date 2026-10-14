package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.support.CronExpression;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledJobsTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 1, 15, 12, 0);

    private static final Map<String, String> SCHEDULES = new TreeMap<>(Map.ofEntries(
            Map.entry("DomainEventRelay.dispatchPendingEvents",
                    "initialDelay 60000ms, fixedDelay 60000ms"),
            Map.entry("NotificationCleanupScheduler.cleanupOldNotifications",
                    "cron '0 0 2 * * *' next 2026-01-16T02:00, 2026-01-17T02:00"),
            Map.entry("PlannerCatalogSyncJob.sync",
                    "initialDelay 1000ms, fixedDelay 1000ms"),
            Map.entry("PlannerCommentSseService.cleanupZombieConnections",
                    "initialDelay 30000ms, fixedRate 60000ms"),
            Map.entry("PlannerCommentSseService.sweepSseHeartbeats",
                    "initialDelay 5000ms, fixedRate 15000ms"),
            Map.entry("PlannerDriftReconciler.reconcile",
                    "cron '0 0 4 * * *' next 2026-01-16T04:00, 2026-01-17T04:00"),
            Map.entry("PlannerKeywordBackfill.backfill",
                    "initialDelay 60000ms"),
            Map.entry("PlannerViewRecorder.flush",
                    "fixedDelay 500ms on viewFlushScheduler"),
            Map.entry("ReadSourceHealthProbe.probe",
                    "fixedDelay 1000ms on readSourceProbeScheduler"),
            Map.entry("SseService.cleanupZombieConnections",
                    "fixedRate 60000ms"),
            Map.entry("SseService.sweepSseHeartbeats",
                    "fixedRate 10000ms"),
            Map.entry("UserCleanupScheduler.cleanupExpiredUsers",
                    "cron '0 0 3 * * *' next 2026-01-16T03:00, 2026-01-17T03:00"),
            Map.entry("ViewCountFlushJob.flush",
                    "initialDelay 10000ms, fixedDelay 10000ms")));

    @Test
    void scheduledMethods_WhenScannedInProduction_AreExactlyTheTable() {
        assertThat(scan().keySet())
                .as("every @Scheduled method needs a row in SCHEDULES")
                .containsExactlyElementsOf(SCHEDULES.keySet());
    }

    @Test
    void schedules_WhenResolvedFromBaseAndProdProperties_MatchTheTable() throws IOException {
        PropertyResolver application = baseAndProdProperties();

        Map<String, String> resolved = new TreeMap<>();
        scan().forEach((name, scheduled) -> resolved.put(name, String.join(" | ",
                scheduled.stream().map(each -> describe(each, application)).toList())));

        assertThat(resolved).containsExactlyEntriesOf(SCHEDULES);
    }

    private static Map<String, List<Scheduled>> scan() {
        Map<String, List<Scheduled>> scheduled = new TreeMap<>();
        ImportedClasses.production().stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isMetaAnnotatedWith(Scheduled.class)
                        || method.isMetaAnnotatedWith(Schedules.class))
                .forEach(method -> scheduled.put(name(method), List.copyOf(
                        AnnotatedElementUtils.getMergedRepeatableAnnotations(
                                method.reflect(), Scheduled.class, Schedules.class))));
        return scheduled;
    }

    private static String name(JavaMethod method) {
        return method.getOwner().getSimpleName() + "." + method.getName();
    }

    private static PropertyResolver baseAndProdProperties() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(propertiesFile("application-prod.properties"));
        sources.addLast(propertiesFile("application.properties"));
        return new PropertySourcesPropertyResolver(sources);
    }

    private static PropertiesPropertySource propertiesFile(String name) throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource(name));
        return new PropertiesPropertySource(name, properties);
    }

    private static String describe(Scheduled scheduled, PropertyResolver application) {
        List<String> parts = new ArrayList<>();
        if (!scheduled.cron().isEmpty()) {
            parts.add(cron(application.resolveRequiredPlaceholders(scheduled.cron())));
        }
        interval(scheduled.initialDelay(), scheduled.initialDelayString(), scheduled.timeUnit(), application)
                .ifPresent(delay -> parts.add("initialDelay " + delay));
        interval(scheduled.fixedDelay(), scheduled.fixedDelayString(), scheduled.timeUnit(), application)
                .ifPresent(delay -> parts.add("fixedDelay " + delay));
        interval(scheduled.fixedRate(), scheduled.fixedRateString(), scheduled.timeUnit(), application)
                .ifPresent(rate -> parts.add("fixedRate " + rate));
        String description = String.join(", ", parts);
        if (!scheduled.zone().isEmpty()) {
            description += " zone " + scheduled.zone();
        }
        if (!scheduled.scheduler().isEmpty()) {
            description += " on " + scheduled.scheduler();
        }
        return description;
    }

    private static String cron(String expression) {
        if (expression.equals(Scheduled.CRON_DISABLED)) {
            return "cron disabled";
        }
        CronExpression cron = CronExpression.parse(expression);
        LocalDateTime first = cron.next(FROM);
        LocalDateTime second = cron.next(first);
        return "cron '" + expression + "' next " + first + ", " + second;
    }

    private static Optional<String> interval(
            long value, String text, TimeUnit unit, PropertyResolver application) {
        Duration duration;
        if (!text.isEmpty()) {
            duration = DurationFormatterUtils.detectAndParse(application.resolveRequiredPlaceholders(text),
                    DurationFormat.Unit.fromChronoUnit(unit.toChronoUnit()));
        } else if (value >= 0) {
            duration = Duration.of(value, unit.toChronoUnit());
        } else {
            return Optional.empty();
        }
        return Optional.of(duration.toMillis() + "ms");
    }
}
