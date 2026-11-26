package org.danteplanner.backend.shared.config;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import com.mysql.cj.jdbc.Driver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;

@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
@EnableConfigurationProperties({ReadSourceProperties.class, HikariTuningProperties.class})
@Import(ReadSourcesConfig.SourcePoolRegistrar.class)
public class ReadSourcesConfig {

    public static final String BREAKER_NAME_PREFIX = "read-source-";
    public static final String BREAKER_TRANSITIONS = "datasource.read.source.breaker.transitions";
    public static final String PROBE_SCHEDULER = "readSourceProbeScheduler";

    private static final String POOL_BEAN_PREFIX = "readSourcePool-";
    private static final String CONNECT_TIMEOUT_PROPERTY = "connectTimeout";
    private static final long START_WITHOUT_CONNECTING = -1L;

    private final DataSourceProperties primaryProperties;
    private final HikariTuningProperties hikariProperties;
    private final ReadSourceProperties readSourceProperties;

    public ReadSourcesConfig(
            DataSourceProperties primaryProperties,
            HikariTuningProperties hikariProperties,
            ReadSourceProperties readSourceProperties) {
        this.primaryProperties = primaryProperties;
        this.hikariProperties = hikariProperties;
        this.readSourceProperties = readSourceProperties;
    }

    public static String breakerName(String sourceName) {
        return BREAKER_NAME_PREFIX + sourceName;
    }

    public static String poolBeanName(String sourceName) {
        return POOL_BEAN_PREFIX + sourceName;
    }

    static List<ReadSourceProperties.Source> boundSources(Environment environment) {
        return Binder.get(environment)
                .bind(ReadSourceProperties.READ_SOURCES_PROPERTY, Bindable.listOf(ReadSourceProperties.Source.class))
                .orElse(List.of());
    }

    public HikariConfig buildSourceHikariConfig(String sourceName) {
        ReadSourceProperties.Source source = source(sourceName);
        HikariConfig config = new HikariConfig();
        config.setPoolName(source.isPrimary() ? PoolLedger.PRIMARY_READ_POOL_NAME : source.name());
        RoutingDataSourceConfig.applyEndpoint(config, credentialsOf(source));
        config.setMaximumPoolSize(poolSizeOf(source));
        config.setConnectionTimeout(hikariProperties.getConnectionTimeout());
        config.setInitializationFailTimeout(START_WITHOUT_CONNECTING);
        return config;
    }

    public SimpleDriverDataSource buildProbeDataSource(String sourceName) {
        ReadSourceProperties.Source source = source(sourceName);
        String timeoutMs = String.valueOf(readSourceProperties.readSourceProbe().timeout().toMillis());
        Properties driverProperties = new Properties();
        driverProperties.setProperty(CONNECT_TIMEOUT_PROPERTY, timeoutMs);
        driverProperties.setProperty(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY, timeoutMs);
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(Driver.class);
        Credentials credentials = credentialsOf(source);
        dataSource.setUrl(credentials.url());
        dataSource.setUsername(credentials.username());
        dataSource.setPassword(credentials.password());
        dataSource.setConnectionProperties(driverProperties);
        return dataSource;
    }

    public HikariDataSource sourcePool(String sourceName) {
        return new HikariDataSource(buildSourceHikariConfig(sourceName));
    }

    @Bean
    public CircuitBreakerRegistry readSourceBreakerRegistry() {
        return CircuitBreakerRegistry.of(readSourceProperties.readSourceBreaker().circuitBreakerConfig());
    }

    @Bean
    public TaggedCircuitBreakerMetrics readSourceBreakerMetrics(CircuitBreakerRegistry readSourceBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(readSourceBreakerRegistry);
    }

    @Bean
    public ReadSourceSelector readSourceSelector(
            @Qualifier("primaryPool") HikariDataSource primaryPool,
            CircuitBreakerRegistry readSourceBreakerRegistry,
            MeterRegistry meterRegistry,
            BeanFactory beanFactory) {
        return new ReadSourceSelector(readSourceProperties.readSources().stream()
                .map(source -> new ReadSourceSelector.Source(
                        source.name(),
                        authorityOf(source.endpoint()),
                        source.isPrimary() && isReadFirst(source)
                                ? primaryPool
                                : beanFactory.getBean(poolBeanName(source.name()), DataSource.class),
                        breaker(readSourceBreakerRegistry, meterRegistry, source.name())))
                .toList());
    }

    @Bean(PROBE_SCHEDULER)
    public ThreadPoolTaskScheduler readSourceProbeScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("read-source-probe-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    @Bean
    public ReadSourceHealthProbe readSourceHealthProbe(ReadSourceSelector readSourceSelector) {
        return new ReadSourceHealthProbe(
                readSourceSelector.sources().stream()
                        .map(source -> new ReadSourceHealthProbe.Target(
                                source.name(), buildProbeDataSource(source.name()), source.breaker()))
                        .toList(),
                readSourceProperties.readSourceProbe());
    }

    private static CircuitBreaker breaker(
            CircuitBreakerRegistry registry, MeterRegistry meterRegistry, String sourceName) {
        CircuitBreaker breaker = registry.circuitBreaker(breakerName(sourceName));
        breaker.getEventPublisher().onStateTransition(event -> meterRegistry.counter(
                BREAKER_TRANSITIONS,
                "source", sourceName,
                "from", event.getStateTransition().getFromState().name().toLowerCase(Locale.ROOT),
                "to", event.getStateTransition().getToState().name().toLowerCase(Locale.ROOT))
                .increment());
        return breaker;
    }

    private static ReadSourceSelector.Authority authorityOf(ReadSourceProperties.Source.Endpoint endpoint) {
        return switch (endpoint) {
            case ReadSourceProperties.Source.Endpoint.Primary primary -> ReadSourceSelector.Authority.PRIMARY;
            case ReadSourceProperties.Source.Endpoint.Replica replica -> ReadSourceSelector.Authority.REPLICA;
        };
    }

    private ReadSourceProperties.Source source(String sourceName) {
        return readSourceProperties.readSources().stream()
                .filter(source -> source.name().equals(sourceName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no read source named " + sourceName));
    }

    private int poolSizeOf(ReadSourceProperties.Source source) {
        if (isReadFirst(source)) {
            return PoolLedger.SEOUL_REPLICA_POOL;
        }
        return source.isPrimary() ? PoolLedger.SEOUL_PRIMARY_READ_POOL : PoolLedger.READ_FALLBACK_POOL;
    }

    private boolean isReadFirst(ReadSourceProperties.Source source) {
        return readSourceProperties.readSources().get(0).equals(source);
    }

    private Credentials credentialsOf(ReadSourceProperties.Source source) {
        Credentials primary = Credentials.of(primaryProperties);
        return switch (source.endpoint()) {
            case ReadSourceProperties.Source.Endpoint.Primary primaryEndpoint -> primary;
            case ReadSourceProperties.Source.Endpoint.Replica replica -> primary.overriddenBy(replica);
        };
    }

    record Credentials(String url, String username, String password) {

        static Credentials of(DataSourceProperties properties) {
            return new Credentials(
                    configured(properties.getUrl(), "url"),
                    configured(properties.getUsername(), "username"),
                    configured(properties.getPassword(), "password"));
        }

        Credentials overriddenBy(ReadSourceProperties.Source.Endpoint.Replica replica) {
            return new Credentials(
                    replica.url(),
                    replica.username().isEmpty() ? username : replica.username(),
                    replica.password().isEmpty() ? password : replica.password());
        }

        private static String configured(@Nullable String value, String key) {
            if (value == null) {
                throw new IllegalStateException("spring.datasource." + key + " is required for routing");
            }
            return value;
        }
    }

    static class SourcePoolRegistrar implements ImportBeanDefinitionRegistrar {

        private final Environment environment;
        private final BeanFactory beanFactory;

        SourcePoolRegistrar(Environment environment, BeanFactory beanFactory) {
            this.environment = environment;
            this.beanFactory = beanFactory;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            List<ReadSourceProperties.Source> sources = boundSources(environment);
            for (ReadSourceProperties.Source source : sources) {
                if (source.name().isEmpty() || (source.isPrimary() && sources.get(0).equals(source))) {
                    continue;
                }
                String name = source.name();
                RootBeanDefinition pool = new RootBeanDefinition(HikariDataSource.class,
                        () -> beanFactory.getBean(ReadSourcesConfig.class).sourcePool(name));
                pool.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
                registry.registerBeanDefinition(poolBeanName(name), pool);
            }
        }
    }
}
