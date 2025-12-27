# Async Patterns and Error Handling – Enterprise Spring Boot

Production-ready guide to **asynchronous execution**, **@Async configuration**, **CompletableFuture patterns**, **global exception handling**, and **resilience patterns** in Spring Boot applications.

## Table of Contents

* [Async Architecture Overview](#async-architecture-overview)
* [Async Executor Configuration](#async-executor-configuration)
* [CompletableFuture Patterns](#completablefuture-patterns)
* [Event-Driven Async Processing](#event-driven-async-processing)
* [Exception Hierarchy](#exception-hierarchy)
* [Global Exception Handling](#global-exception-handling)
* [Resilience Patterns](#resilience-patterns)
* [Observability and Tracing](#observability-and-tracing)
* [Testing Async Code](#testing-async-code)

---

## Async Architecture Overview

### When to Use Async

| Use Case | Async Appropriate | Reason |
|----------|-------------------|--------|
| Email sending | Yes | Fire-and-forget, no user waiting |
| Report generation | Yes | Long-running, user can poll |
| Database queries | **No** | Already non-blocking with connection pool |
| Simple calculations | **No** | Thread overhead exceeds benefit |
| External API calls | Depends | Consider reactive for high throughput |
| Event publishing | Yes | Decouples sender from listeners |

### Async Decision Flowchart

```
┌─────────────────────────────────────────────┐
│ Does the caller need the result immediately? │
└────────────────────┬────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
       Yes                        No
        │                         │
        ▼                         ▼
  Use synchronous          Is it fire-and-forget?
  or CompletableFuture            │
  with .get()              ┌──────┴──────┐
                           ▼              ▼
                          Yes             No
                           │              │
                           ▼              ▼
                      Use @Async     Use @Async with
                      void method    CompletableFuture
```

---

## Async Executor Configuration

### Production-Ready Async Configuration

```java
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Value("${app.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:500}")
    private int queueCapacity;

    /**
     * Default executor for @Async methods.
     * Uses CallerRunsPolicy for backpressure.
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Async executor configured: core={}, max={}, queue={}",
            corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * Global handler for uncaught async exceptions (void methods).
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * Dedicated executor for email sending.
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("Email task rejected, queue full. Consider increasing capacity.");
            throw new RejectedExecutionException("Email queue full");
        });
        executor.initialize();
        return executor;
    }

    /**
     * Executor for batch processing with larger queue.
     */
    @Bean(name = "batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("batch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### Custom Async Exception Handler

```java
@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final MeterRegistry meterRegistry;

    public CustomAsyncExceptionHandler() {
        this.meterRegistry = null; // For non-Spring managed instantiation
    }

    public CustomAsyncExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        log.error("Uncaught async exception in {}: {} - params: {}",
            methodName,
            ex.getMessage(),
            Arrays.toString(params),
            ex);

        // Track metric
        if (meterRegistry != null) {
            meterRegistry.counter("async.exceptions",
                "method", methodName,
                "exception", ex.getClass().getSimpleName()
            ).increment();
        }

        // Send to error tracking (Sentry, etc.)
        Sentry.captureException(ex, scope -> {
            scope.setTag("async.method", methodName);
            scope.setExtra("params", Arrays.toString(params));
        });
    }
}
```

---

## CompletableFuture Patterns

### Basic Async Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailClient emailClient;
    private final SmsClient smsClient;
    private final PushClient pushClient;

    /**
     * Fire-and-forget notification.
     * Exceptions logged but not propagated.
     */
    @Async
    public void sendNotificationAsync(Long userId, NotificationDto notification) {
        try {
            log.debug("Sending notification to user: {}", userId);
            sendNotification(userId, notification);
            log.info("Notification sent to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", userId, e);
            // Error is logged, not propagated (void method)
        }
    }

    /**
     * Async with result - caller can handle completion.
     */
    @Async
    public CompletableFuture<NotificationResult> sendNotificationWithResult(
            Long userId, NotificationDto notification) {

        try {
            NotificationResult result = sendNotification(userId, notification);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send email using dedicated executor.
     */
    @Async("emailExecutor")
    public CompletableFuture<Void> sendEmailAsync(String to, EmailTemplate template) {
        emailClient.send(to, template);
        return CompletableFuture.completedFuture(null);
    }

    private NotificationResult sendNotification(Long userId, NotificationDto notification) {
        // Implementation
        return new NotificationResult(userId, true);
    }
}
```

### Parallel Execution with CompletableFuture

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UserService userService;
    private final StatsService statsService;
    private final NotificationService notificationService;

    /**
     * Load dashboard data in parallel.
     */
    public DashboardDto loadDashboard(Long userId) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // Launch all async operations
        CompletableFuture<UserProfile> profileFuture =
            userService.getProfileAsync(userId);

        CompletableFuture<UserStats> statsFuture =
            statsService.getUserStatsAsync(userId);

        CompletableFuture<List<Notification>> notificationsFuture =
            notificationService.getRecentNotificationsAsync(userId, 10);

        // Wait for all to complete with timeout
        try {
            CompletableFuture.allOf(profileFuture, statsFuture, notificationsFuture)
                .get(5, TimeUnit.SECONDS);

            DashboardDto dashboard = DashboardDto.builder()
                .profile(profileFuture.join())
                .stats(statsFuture.join())
                .notifications(notificationsFuture.join())
                .build();

            stopWatch.stop();
            log.debug("Dashboard loaded in {}ms", stopWatch.getTotalTimeMillis());

            return dashboard;

        } catch (TimeoutException e) {
            log.warn("Dashboard load timeout for user: {}", userId);
            return buildPartialDashboard(profileFuture, statsFuture, notificationsFuture);

        } catch (ExecutionException e) {
            throw new ServiceException("Failed to load dashboard", e.getCause());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Dashboard loading interrupted");
        }
    }

    /**
     * Build dashboard with available data on timeout.
     */
    private DashboardDto buildPartialDashboard(
            CompletableFuture<UserProfile> profileFuture,
            CompletableFuture<UserStats> statsFuture,
            CompletableFuture<List<Notification>> notificationsFuture) {

        return DashboardDto.builder()
            .profile(getOrDefault(profileFuture, UserProfile.empty()))
            .stats(getOrDefault(statsFuture, UserStats.empty()))
            .notifications(getOrDefault(notificationsFuture, List.of()))
            .partial(true)
            .build();
    }

    private <T> T getOrDefault(CompletableFuture<T> future, T defaultValue) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.join();
        }
        return defaultValue;
    }
}
```

### Error Handling with CompletableFuture

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {

    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;

    /**
     * Process order with proper error handling.
     */
    public CompletableFuture<OrderResult> processOrderAsync(Order order) {
        return CompletableFuture.supplyAsync(() -> validateOrder(order))
            .thenCompose(valid -> reserveInventory(order))
            .thenCompose(reservation -> processPayment(order, reservation))
            .thenCompose(payment -> createShipment(order, payment))
            .thenApply(shipment -> new OrderResult(order.getId(), shipment))
            .exceptionally(ex -> {
                // Unwrap CompletionException
                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;

                log.error("Order processing failed: orderId={}", order.getId(), cause);

                // Compensating transactions
                rollbackOrder(order);

                // Return error result instead of throwing
                return OrderResult.failed(order.getId(), cause.getMessage());
            });
    }

    /**
     * Combining multiple futures with error isolation.
     */
    public CompletableFuture<BatchResult> processBatchOrders(List<Order> orders) {
        List<CompletableFuture<OrderResult>> futures = orders.stream()
            .map(this::processOrderAsync)
            .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> {
                List<OrderResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

                long successCount = results.stream()
                    .filter(OrderResult::isSuccess)
                    .count();

                return new BatchResult(results, successCount, orders.size() - successCount);
            });
    }

    /**
     * Handle with both success and failure.
     */
    public CompletableFuture<OrderResult> processWithFallback(Order order) {
        return processOrderAsync(order)
            .handle((result, ex) -> {
                if (ex != null) {
                    log.warn("Primary processing failed, trying fallback", ex);
                    return processWithFallbackProvider(order);
                }
                return result;
            });
    }

    private void rollbackOrder(Order order) {
        log.info("Rolling back order: {}", order.getId());
        // Compensation logic
    }
}
```

### Batch Processing with Controlled Parallelism

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessingService {

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Value("${app.batch.size:100}")
    private int batchSize;

    /**
     * Process large dataset in parallel batches.
     * Uses virtual threads (Java 21+) or thread pool.
     */
    public BatchResult processInBatches(List<Long> entityIds) {
        List<List<Long>> batches = Lists.partition(entityIds, batchSize);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // Use virtual threads for I/O bound operations (Java 21+)
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(
                    () -> processBatch(batch, successCount, failureCount, errors),
                    executor
                ))
                .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .get(30, TimeUnit.MINUTES);

        } catch (TimeoutException e) {
            log.error("Batch processing timed out after 30 minutes");
            throw new ServiceException("Batch processing timeout");

        } catch (Exception e) {
            log.error("Batch processing failed", e);
            throw new ServiceException("Batch processing failed", e);
        }

        return new BatchResult(
            successCount.get(),
            failureCount.get(),
            errors
        );
    }

    private void processBatch(
            List<Long> ids,
            AtomicInteger successCount,
            AtomicInteger failureCount,
            List<String> errors) {

        transactionTemplate.executeWithoutResult(status -> {
            for (Long id : ids) {
                try {
                    processEntity(id);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    errors.add("ID " + id + ": " + e.getMessage());
                    log.warn("Failed to process entity: {}", id, e);
                }
            }
            entityManager.flush();
            entityManager.clear();
        });
    }

    private void processEntity(Long id) {
        // Entity processing logic
    }
}
```

---

## Event-Driven Async Processing

### Domain Events with @TransactionalEventListener

```java
// Event definition
public record OrderCompletedEvent(
    Long orderId,
    Long customerId,
    BigDecimal totalAmount,
    Instant occurredAt
) {
    public OrderCompletedEvent(Long orderId, Long customerId, BigDecimal totalAmount) {
        this(orderId, customerId, totalAmount, Instant.now());
    }
}

// Event publisher
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order", orderId));

        order.complete();
        Order savedOrder = orderRepository.save(order);

        // Event published after transaction commits
        eventPublisher.publishEvent(new OrderCompletedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotalAmount()
        ));

        return savedOrder;
    }
}

// Event listeners
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListeners {

    private final EmailService emailService;
    private final InventoryService inventoryService;
    private final AnalyticsService analyticsService;

    /**
     * Send confirmation email after transaction commits.
     * Async to not block the response.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendConfirmationEmail(OrderCompletedEvent event) {
        log.info("Sending confirmation email for order: {}", event.orderId());
        try {
            emailService.sendOrderConfirmation(event.customerId(), event.orderId());
        } catch (Exception e) {
            log.error("Failed to send order confirmation email", e);
            // Don't rethrow - email failure shouldn't affect order
        }
    }

    /**
     * Update inventory in a new transaction.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInventory(OrderCompletedEvent event) {
        log.info("Updating inventory for order: {}", event.orderId());
        inventoryService.decrementForOrder(event.orderId());
    }

    /**
     * Track analytics asynchronously.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void trackAnalytics(OrderCompletedEvent event) {
        analyticsService.trackOrderComplete(event);
    }

    /**
     * Handle during transaction (for validation).
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void validateBeforeCommit(OrderCompletedEvent event) {
        // Validation that must happen before commit
        if (event.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ORDER_INVALID", "Order total must be positive");
        }
    }
}
```

---

## Exception Hierarchy

### Application Exception Hierarchy

```java
/**
 * Base exception for all application exceptions.
 * Provides error code, HTTP status, and optional details.
 */
@Getter
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, Object> details;
    private final Instant timestamp;

    protected ApplicationException(String errorCode, String message, HttpStatus status) {
        this(errorCode, message, status, Map.of());
    }

    protected ApplicationException(
            String errorCode,
            String message,
            HttpStatus status,
            Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = details;
        this.timestamp = Instant.now();
    }

    protected ApplicationException(
            String errorCode,
            String message,
            HttpStatus status,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.details = Map.of();
        this.timestamp = Instant.now();
    }
}

/**
 * Resource not found (404).
 */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String resourceType, Object resourceId) {
        super(
            "RESOURCE_NOT_FOUND",
            String.format("%s with id '%s' not found", resourceType, resourceId),
            HttpStatus.NOT_FOUND,
            Map.of("resourceType", resourceType, "resourceId", resourceId)
        );
    }

    public NotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}

/**
 * Access denied (403).
 */
public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        super("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String action, String resource) {
        super(
            "ACCESS_DENIED",
            String.format("You don't have permission to %s this %s", action, resource),
            HttpStatus.FORBIDDEN,
            Map.of("action", action, "resource", resource)
        );
    }
}

/**
 * Conflict state (409).
 */
public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        super("CONFLICT", message, HttpStatus.CONFLICT);
    }

    public ConflictException(String resource, String reason) {
        super(
            "CONFLICT",
            String.format("Conflict with %s: %s", resource, reason),
            HttpStatus.CONFLICT,
            Map.of("resource", resource, "reason", reason)
        );
    }
}

/**
 * Business rule violation (422).
 */
public class BusinessException extends ApplicationException {

    public BusinessException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY, details);
    }
}

/**
 * External service failure (502).
 */
public class ExternalServiceException extends ApplicationException {

    public ExternalServiceException(String serviceName, String message) {
        super(
            "EXTERNAL_SERVICE_ERROR",
            String.format("External service '%s' error: %s", serviceName, message),
            HttpStatus.BAD_GATEWAY,
            Map.of("service", serviceName)
        );
    }

    public ExternalServiceException(String serviceName, Throwable cause) {
        super(
            "EXTERNAL_SERVICE_ERROR",
            String.format("External service '%s' error: %s", serviceName, cause.getMessage()),
            HttpStatus.BAD_GATEWAY,
            cause
        );
    }
}

/**
 * Rate limit exceeded (429).
 */
public class RateLimitExceededException extends ApplicationException {

    public RateLimitExceededException(String resource, long retryAfterSeconds) {
        super(
            "RATE_LIMIT_EXCEEDED",
            String.format("Rate limit exceeded for %s. Retry after %d seconds", resource, retryAfterSeconds),
            HttpStatus.TOO_MANY_REQUESTS,
            Map.of("resource", resource, "retryAfterSeconds", retryAfterSeconds)
        );
    }
}
```

---

## Global Exception Handling

### Comprehensive Exception Handler

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;
    private final MessageSource messageSource;

    public GlobalExceptionHandler(
            @Autowired(required = false) MeterRegistry meterRegistry,
            MessageSource messageSource) {
        this.meterRegistry = meterRegistry;
        this.messageSource = messageSource;
    }

    /**
     * Handle application-specific exceptions.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiError> handleApplicationException(
            ApplicationException ex,
            WebRequest request) {

        logException(ex, request, ex.getStatus().is5xxServerError() ? "error" : "warn");
        trackException(ex);

        ApiError error = ApiError.builder()
            .code(ex.getErrorCode())
            .message(ex.getMessage())
            .details(ex.getDetails())
            .path(extractPath(request))
            .timestamp(ex.getTimestamp())
            .build();

        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    /**
     * Handle validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.groupingBy(
                FieldError::getField,
                Collectors.mapping(
                    this::resolveMessage,
                    Collectors.toList()
                )
            ));

        ApiError error = ApiError.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .details(Map.of("fields", fieldErrors))
            .path(extractPath(request))
            .timestamp(Instant.now())
            .build();

        log.debug("Validation error: {}", fieldErrors);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle constraint violations.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {

        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> extractPropertyName(v.getPropertyPath()),
                ConstraintViolation::getMessage,
                (a, b) -> a
            ));

        ApiError error = ApiError.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .details(Map.of("constraints", errors))
            .path(extractPath(request))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle optimistic locking failures.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            OptimisticLockingFailureException ex,
            WebRequest request) {

        log.warn("Optimistic lock failure: {}", ex.getMessage());

        ApiError error = ApiError.builder()
            .code("CONCURRENT_MODIFICATION")
            .message("The resource was modified by another user. Please refresh and try again.")
            .path(extractPath(request))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle data access exceptions.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(
            DataAccessException ex,
            WebRequest request) {

        log.error("Database error", ex);
        trackException(ex);

        // Don't expose internal database details
        ApiError error = ApiError.builder()
            .code("DATABASE_ERROR")
            .message("A database error occurred. Please try again later.")
            .path(extractPath(request))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handle missing request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {

        String message = "Invalid request body";
        if (ex.getCause() instanceof JsonParseException) {
            message = "Malformed JSON";
        }

        ApiError error = ApiError.builder()
            .code("INVALID_REQUEST")
            .message(message)
            .path(extractPath(request))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle authentication failures.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException ex,
            WebRequest request) {

        ApiError error = ApiError.builder()
            .code("AUTHENTICATION_REQUIRED")
            .message("Authentication is required to access this resource")
            .path(extractPath(request))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Fallback for all unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception ex,
            WebRequest request) {

        String traceId = generateTraceId();

        log.error("Unexpected error [traceId={}]", traceId, ex);
        trackException(ex);

        // Report to error tracking
        Sentry.captureException(ex, scope -> {
            scope.setTag("traceId", traceId);
            scope.setExtra("path", extractPath(request));
        });

        ApiError error = ApiError.builder()
            .code("INTERNAL_ERROR")
            .message("An unexpected error occurred. Reference: " + traceId)
            .path(extractPath(request))
            .timestamp(Instant.now())
            .traceId(traceId)
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private void logException(Exception ex, WebRequest request, String level) {
        String path = extractPath(request);
        String message = String.format("Exception on %s: %s", path, ex.getMessage());

        switch (level) {
            case "error" -> log.error(message, ex);
            case "warn" -> log.warn(message);
            default -> log.debug(message);
        }
    }

    private void trackException(Exception ex) {
        if (meterRegistry != null) {
            meterRegistry.counter("exceptions",
                "type", ex.getClass().getSimpleName()
            ).increment();
        }
    }

    private String extractPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return "unknown";
    }

    private String extractPropertyName(Path path) {
        String fullPath = path.toString();
        int lastDot = fullPath.lastIndexOf('.');
        return lastDot >= 0 ? fullPath.substring(lastDot + 1) : fullPath;
    }

    private String resolveMessage(FieldError error) {
        try {
            return messageSource.getMessage(error, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return error.getDefaultMessage();
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
```

### API Error Response

```java
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    String code;
    String message;
    Map<String, Object> details;
    String path;
    Instant timestamp;
    String traceId;
}
```

---

## Resilience Patterns

### Retry with Exponential Backoff

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientExternalService {

    private final RestTemplate restTemplate;
    private final RetryTemplate retryTemplate;

    @PostConstruct
    void init() {
        // Configure retry template
    }

    /**
     * Call with automatic retry.
     */
    public ExternalResponse callWithRetry(ExternalRequest request) {
        return retryTemplate.execute(context -> {
            int attempt = context.getRetryCount() + 1;
            log.debug("Calling external service, attempt {}", attempt);

            try {
                return restTemplate.postForObject(
                    "/api/external",
                    request,
                    ExternalResponse.class
                );
            } catch (RestClientException e) {
                log.warn("External call failed, attempt {}: {}", attempt, e.getMessage());
                throw e;
            }
        }, context -> {
            log.error("All retry attempts exhausted for external service");
            throw new ExternalServiceException("external-api",
                "Service unavailable after " + context.getRetryCount() + " attempts");
        });
    }
}

@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // Exponential backoff: 100ms, 200ms, 400ms, 800ms...
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(100);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000);
        template.setBackOffPolicy(backOff);

        // Retry on specific exceptions
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(RestClientException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        template.setRetryPolicy(retryPolicy);

        return template;
    }
}
```

### Circuit Breaker with Resilience4j

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ExternalServiceClient client;

    public ExternalResponse callWithCircuitBreaker(ExternalRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-service");

        return circuitBreaker.executeSupplier(() -> {
            log.debug("Calling external service through circuit breaker");
            return client.call(request);
        });
    }

    public ExternalResponse callWithFallback(ExternalRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-service");

        return Try.ofSupplier(
            CircuitBreaker.decorateSupplier(circuitBreaker, () -> client.call(request))
        ).recover(throwable -> {
            log.warn("Circuit breaker fallback triggered: {}", throwable.getMessage());
            return ExternalResponse.fallback();
        }).get();
    }
}

// Configuration
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .recordExceptions(IOException.class, TimeoutException.class)
            .ignoreExceptions(BusinessException.class)
            .build();

        return CircuitBreakerRegistry.of(config);
    }
}
```

---

## Observability and Tracing

### MDC Context Propagation

```java
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}

// Apply to executor
@Bean
public Executor asyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // ... other config
    executor.setTaskDecorator(new MdcTaskDecorator());
    executor.initialize();
    return executor;
}
```

---

## Testing Async Code

### Testing @Async Methods

```java
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private EmailClient emailClient;

    @Test
    void sendNotificationAsync_shouldCompleteSuccessfully() throws Exception {
        // Given
        Long userId = 1L;
        NotificationDto notification = new NotificationDto("Test", "Body");

        // When
        CompletableFuture<NotificationResult> future =
            notificationService.sendNotificationWithResult(userId, notification);

        // Then - wait for async completion
        NotificationResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.isSuccess()).isTrue();

        verify(emailClient).send(any(), any());
    }

    @Test
    void sendNotificationAsync_withFailure_shouldHandleGracefully() throws Exception {
        // Given
        when(emailClient.send(any(), any()))
            .thenThrow(new RuntimeException("SMTP error"));

        // When
        CompletableFuture<NotificationResult> future =
            notificationService.sendNotificationWithResult(1L, new NotificationDto("Test", "Body"));

        // Then
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class);
    }
}
```

### Testing Event Listeners

```java
@SpringBootTest
@Transactional
class OrderEventListenersTest {

    @Autowired
    private OrderService orderService;

    @MockBean
    private EmailService emailService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void completeOrder_shouldTriggerEmailEvent() {
        // Given
        Order order = createTestOrder();
        entityManager.persist(order);
        entityManager.flush();

        // When
        orderService.completeOrder(order.getId());

        // Then - verify async event was processed
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(emailService).sendOrderConfirmation(any(), eq(order.getId()))
            );
    }
}
```

---

## Summary: Async and Error Handling Rules

| Rule | Description |
|------|-------------|
| **Default to Sync** | Use async only when justified (I/O bound, fire-and-forget) |
| **Configure Executors** | Custom thread pools with proper sizing and rejection policy |
| **Handle Exceptions** | AsyncUncaughtExceptionHandler for void methods |
| **Propagate Context** | Use MDC decorators for logging context |
| **Exception Hierarchy** | Typed exceptions with error codes and HTTP status |
| **Global Handler** | Centralized exception handling in @RestControllerAdvice |
| **Never Swallow** | Always log, track, and handle exceptions |
| **Resilience** | Retry with backoff, circuit breakers for external calls |

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [services-and-repositories.md](services-and-repositories.md) - Service patterns
* [testing-guide.md](testing-guide.md) - Testing patterns
