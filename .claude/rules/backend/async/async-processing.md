---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/async/**/*.java"
---

# Async Processing Patterns

## @Async Configuration

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

## Async Method Pattern

```java
@Service
public class EmailService {

    @Async("taskExecutor")
    public CompletableFuture<Void> sendEmail(String to, String subject, String body) {
        try {
            // Send email (slow operation)
            emailClient.send(to, subject, body);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("taskExecutor")
    public CompletableFuture<ProcessResult> processLargeFile(String fileId) {
        try {
            ProcessResult result = heavyProcessing(fileId);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
```

## Usage in Controller

```java
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final EmailService emailService;

    @PostMapping("/email")
    public ResponseEntity<String> sendEmail(@Valid @RequestBody EmailRequest request) {
        // Fire and forget
        emailService.sendEmail(request.to(), request.subject(), request.body());
        return ResponseEntity.accepted().body("Email queued");
    }

    @PostMapping("/process")
    public ResponseEntity<String> processFile(@RequestParam String fileId) {
        CompletableFuture<ProcessResult> future = emailService.processLargeFile(fileId);

        // Optional: wait for result with timeout
        try {
            ProcessResult result = future.get(5, TimeUnit.SECONDS);
            return ResponseEntity.ok("Processed: " + result.getId());
        } catch (TimeoutException e) {
            return ResponseEntity.accepted().body("Processing in background");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Processing failed");
        }
    }
}
```

## Combining Multiple Async Tasks

```java
public CompletableFuture<CombinedResult> processMultipleTasks() {
    CompletableFuture<Result1> task1 = service1.process();
    CompletableFuture<Result2> task2 = service2.process();
    CompletableFuture<Result3> task3 = service3.process();

    return CompletableFuture.allOf(task1, task2, task3)
        .thenApply(v -> new CombinedResult(
            task1.join(),
            task2.join(),
            task3.join()
        ));
}
```

## Important Rules

| Rule | Reason |
|------|--------|
| **@Async must be on public methods** | Proxy limitation |
| **Don't call @Async from same class** | Proxy won't intercept |
| **No @Transactional with @Async** | Different threads, transaction not propagated |
| **Return CompletableFuture** | Allows result retrieval and error handling |
| **Configure thread pool** | Default pool is unbounded (dangerous) |
