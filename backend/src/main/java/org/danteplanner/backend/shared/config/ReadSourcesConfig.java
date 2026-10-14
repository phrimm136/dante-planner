package org.danteplanner.backend.shared.config;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

import com.mysql.cj.jdbc.Driver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;

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
        config.setPoolName(source.isPrimary() ? PoolLedger.PRIMARY_READ_POOL_NAME : source.getName());
        RoutingDataSourceConfig.applyEndpoint(config, urlOf(source), usernameOf(source), passwordOf(source));
        config.setMaximumPoolSize(poolSizeOf(source));
        config.setConnectionTimeout(hikariProperties.getConnectionTimeout());
        config.setInitializationFailTimeout(START_WITHOUT_CONNECTING);
        return config;
    }

    public SimpleDriverDataSource buildProbeDataSource(String sourceName) {
        ReadSourceProperties.Source source = source(sourceName);
        String timeoutMs = String.valueOf(readSourceProperties.getReadSourceProbe().getTimeout().toMillis());
        Properties driverProperties = new Properties();
        driverProperties.setProperty(CONNECT_TIMEOUT_PROPERTY, timeoutMs);
        driverProperties.setProperty(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY, timeoutMs);
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(Driver.class);
        dataSource.setUrl(urlOf(source));
        dataSource.setUsername(usernameOf(source));
        dataSource.setPassword(passwordOf(source));
        dataSource.setConnectionProperties(driverProperties);
        return dataSource;
    }

    public HikariDataSource sourcePool(String sourceName) {
        return new HikariDataSource(buildSourceHikariConfig(sourceName));
    }

    @Bean
    public CircuitBreakerRegistry readSourceBreakerRegistry() {
        return CircuitBreakerRegistry.of(readSourceProperties.getReadSourceBreaker().circuitBreakerConfig());
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
        return new ReadSourceSelector(readSourceProperties.getReadSources().stream()
                .map(source -> new ReadSourceSelector.Source(
                        source.getName(),
                        source.isPrimary(),
                        source.isPrimary() && isReadFirst(source)
                                ? primaryPool
                                : beanFactory.getBean(poolBeanName(source.getName()), DataSource.class),
                        breaker(readSourceBreakerRegistry, meterRegistry, source.getName())))
                .toList());
    }

    @Bean(PROBE_SCHEDULER)
    public ThreadPoolTaskScheduler readSourceProbeScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("read-source-probe-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    public ReadSourceHealthProbe readSourceHealthProbe(ReadSourceSelector readSourceSelector) {
        return new ReadSourceHealthProbe(
                readSourceSelector.sources().stream()
                        .map(source -> new ReadSourceHealthProbe.Target(
                                source.name(), buildProbeDataSource(source.name()), source.breaker()))
                        .toList(),
                readSourceProperties.getReadSourceProbe());
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

    private ReadSourceProperties.Source source(String sourceName) {
        return readSourceProperties.getReadSources().stream()
                .filter(source -> source.getName().equals(sourceName))
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
        return readSourceProperties.getReadSources().get(0) == source;
    }

    private String urlOf(ReadSourceProperties.Source source) {
        return source.isPrimary() ? primaryProperties.getUrl() : source.getUrl();
    }

    private String usernameOf(ReadSourceProperties.Source source) {
        return source.getUsername() != null ? source.getUsername() : primaryProperties.getUsername();
    }

    private String passwordOf(ReadSourceProperties.Source source) {
        return source.getPassword() != null ? source.getPassword() : primaryProperties.getPassword();
    }

    static class SourcePoolRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware {

        private Environment environment;
        private BeanFactory beanFactory;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            List<ReadSourceProperties.Source> sources = boundSources(environment);
            for (ReadSourceProperties.Source source : sources) {
                if (source.getName() == null || (source.isPrimary() && sources.get(0) == source)) {
                    continue;
                }
                String name = source.getName();
                RootBeanDefinition pool = new RootBeanDefinition(HikariDataSource.class,
                        () -> beanFactory.getBean(ReadSourcesConfig.class).sourcePool(name));
                pool.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
                registry.registerBeanDefinition(poolBeanName(name), pool);
            }
        }
    }
}
