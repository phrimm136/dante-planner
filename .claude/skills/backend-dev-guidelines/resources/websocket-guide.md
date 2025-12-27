# WebSocket Guide – Enterprise Spring Boot Patterns

Production-ready **Spring Boot 3.x WebSocket with STOMP** patterns covering configuration, security, messaging, error handling, testing, and scaling.

---

## Table of Contents

* [WebSocket Configuration](#websocket-configuration)
* [STOMP Message Controller](#stomp-message-controller)
* [Security Configuration](#security-configuration)
* [Session Management](#session-management)
* [Broadcasting and User Messaging](#broadcasting-and-user-messaging)
* [Error Handling](#error-handling)
* [Heartbeat and Connection Management](#heartbeat-and-connection-management)
* [Scaling with External Broker](#scaling-with-external-broker)
* [Testing WebSocket Endpoints](#testing-websocket-endpoints)
* [Frontend Integration](#frontend-integration)

---

## WebSocket Configuration

### Basic STOMP Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple in-memory broker for subscriptions
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{10000, 10000})  // Server/client heartbeat
            .setTaskScheduler(heartbeatScheduler());

        // Application destination prefix for @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");

        // User destination prefix for @SendToUser
        registry.setUserDestinationPrefix("/user");

        // Preserve message order
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("https://*.example.com", "http://localhost:*")
            .setHandshakeHandler(customHandshakeHandler())
            .addInterceptors(httpSessionHandshakeInterceptor())
            .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js")
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor());
        registration.taskExecutor()
            .corePoolSize(4)
            .maxPoolSize(10)
            .queueCapacity(100);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
            .corePoolSize(4)
            .maxPoolSize(10);
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    @Bean
    public HandshakeHandler customHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request,
                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
                // Extract user from JWT or session
                return (Principal) attributes.get("user");
            }
        };
    }

    @Bean
    public HttpSessionHandshakeInterceptor httpSessionHandshakeInterceptor() {
        return new HttpSessionHandshakeInterceptor();
    }

    @Bean
    public AuthChannelInterceptor authChannelInterceptor() {
        return new AuthChannelInterceptor();
    }
}
```

### Channel Interceptor for Authentication

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
            .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null) {
                try {
                    UserPrincipal principal = tokenProvider.validateAndGetPrincipal(token);
                    accessor.setUser(principal);
                    log.debug("WebSocket connected for user: {}", principal.getName());
                } catch (Exception e) {
                    log.warn("WebSocket authentication failed: {}", e.getMessage());
                    throw new MessagingException("Authentication failed");
                }
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            String bearerToken = authorization.get(0);
            if (bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }
        return null;
    }
}
```

---

## STOMP Message Controller

### Complete Controller Example

```java
@Controller
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle subscription - return initial data
     * Client subscribes to: /app/notifications
     */
    @SubscribeMapping("/notifications")
    public List<NotificationResponse> handleSubscribe(Principal principal) {
        log.debug("User {} subscribed to notifications", principal.getName());
        return notificationService.getUnreadNotifications(principal.getName())
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }

    /**
     * Handle incoming message
     * Client sends to: /app/notifications/read
     * Response goes to: /user/queue/notifications
     */
    @MessageMapping("/notifications/read")
    @SendToUser("/queue/notifications")
    public NotificationResponse markAsRead(
            @Payload MarkReadRequest request,
            Principal principal
    ) {
        log.debug("User {} marking notification {} as read",
            principal.getName(), request.notificationId());

        Notification notification = notificationService.markAsRead(
            request.notificationId(), principal.getName());

        return NotificationResponse.from(notification);
    }

    /**
     * Handle message with broadcast response
     * Client sends to: /app/chat/room/{roomId}
     * Response goes to: /topic/chat/room/{roomId}
     */
    @MessageMapping("/chat/room/{roomId}")
    @SendTo("/topic/chat/room/{roomId}")
    public ChatMessageResponse sendChatMessage(
            @DestinationVariable String roomId,
            @Payload @Valid ChatMessageRequest request,
            Principal principal,
            @Header("simpSessionId") String sessionId
    ) {
        log.debug("Chat message from {} in room {}", principal.getName(), roomId);

        ChatMessage message = ChatMessage.builder()
            .roomId(roomId)
            .senderId(principal.getName())
            .content(request.content())
            .timestamp(Instant.now())
            .build();

        chatService.saveMessage(message);

        return ChatMessageResponse.from(message);
    }

    /**
     * Handle private message to specific user
     */
    @MessageMapping("/chat/private")
    public void sendPrivateMessage(
            @Payload @Valid PrivateMessageRequest request,
            Principal principal
    ) {
        PrivateMessage message = PrivateMessage.builder()
            .fromUser(principal.getName())
            .toUser(request.recipient())
            .content(request.content())
            .timestamp(Instant.now())
            .build();

        // Send to specific user
        messagingTemplate.convertAndSendToUser(
            request.recipient(),
            "/queue/private-messages",
            PrivateMessageResponse.from(message)
        );

        // Also send back to sender for confirmation
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/private-messages",
            PrivateMessageResponse.from(message)
        );
    }

    /**
     * Exception handler for WebSocket errors
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorResponse handleException(Exception exception, Principal principal) {
        log.error("WebSocket error for user {}: {}",
            principal != null ? principal.getName() : "unknown",
            exception.getMessage());

        return ErrorResponse.of(exception.getMessage());
    }
}

// DTOs
public record MarkReadRequest(Long notificationId) {}

public record ChatMessageRequest(
    @NotBlank @Size(max = 2000) String content
) {}

public record ChatMessageResponse(
    String id,
    String senderId,
    String senderName,
    String content,
    Instant timestamp
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(),
            message.getSenderId(),
            message.getSenderName(),
            message.getContent(),
            message.getTimestamp()
        );
    }
}

public record ErrorResponse(String error, Instant timestamp) {
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, Instant.now());
    }
}
```

---

## Security Configuration

### WebSocket Security (Spring Security 6.x)

```java
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {

        return messages
            // Allow all to connect (auth handled in interceptor)
            .nullDestMatcher().permitAll()

            // Error queue accessible to all authenticated
            .simpSubscribeDestMatchers("/user/queue/errors").authenticated()

            // Topic subscriptions require USER role
            .simpSubscribeDestMatchers("/topic/**").hasRole("USER")

            // User-specific destinations require authentication
            .simpSubscribeDestMatchers("/user/**").authenticated()

            // Application messages require USER role
            .simpDestMatchers("/app/**").hasRole("USER")

            // Admin-only destinations
            .simpDestMatchers("/app/admin/**").hasRole("ADMIN")
            .simpSubscribeDestMatchers("/topic/admin/**").hasRole("ADMIN")

            // Deny everything else
            .anyMessage().denyAll()
            .build();
    }
}
```

### JWT Handshake Interceptor

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {

        // Extract token from query parameter or cookie
        String token = extractToken(request);

        if (token != null) {
            try {
                UserPrincipal principal = tokenProvider.validateAndGetPrincipal(token);
                attributes.put("user", principal);
                attributes.put("userId", principal.getId());
                log.debug("WebSocket handshake authenticated for: {}", principal.getName());
                return true;
            } catch (Exception e) {
                log.warn("WebSocket handshake authentication failed: {}", e.getMessage());
                return false;
            }
        }

        // Allow anonymous connections if needed
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Post-handshake processing
    }

    private String extractToken(ServerHttpRequest request) {
        // Try query parameter first
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "token".equals(pair[0])) {
                    return pair[1];
                }
            }
        }

        // Try cookie
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("access_token".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }

        return null;
    }
}
```

---

## Session Management

### WebSocket Event Listener

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserSessionRegistry sessionRegistry;
    private final UserPresenceService presenceService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();

        if (user != null) {
            sessionRegistry.registerSession(user.getName(), sessionId);
            presenceService.userConnected(user.getName());

            log.info("WebSocket connected: user={}, sessionId={}",
                user.getName(), sessionId);

            // Notify others about user coming online
            messagingTemplate.convertAndSend("/topic/presence",
                new PresenceEvent(user.getName(), PresenceStatus.ONLINE));
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();

        if (user != null) {
            sessionRegistry.removeSession(user.getName(), sessionId);

            // Check if user has no more active sessions
            if (!sessionRegistry.hasActiveSessions(user.getName())) {
                presenceService.userDisconnected(user.getName());

                log.info("WebSocket disconnected: user={}, sessionId={}",
                    user.getName(), sessionId);

                messagingTemplate.convertAndSend("/topic/presence",
                    new PresenceEvent(user.getName(), PresenceStatus.OFFLINE));
            }
        }
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();

        log.debug("User {} subscribed to {}", user.getName(), destination);

        // Track room subscriptions
        if (destination != null && destination.startsWith("/topic/chat/room/")) {
            String roomId = destination.substring("/topic/chat/room/".length());
            presenceService.userJoinedRoom(user.getName(), roomId);
        }
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String subscriptionId = accessor.getSubscriptionId();
        Principal user = accessor.getUser();

        log.debug("User {} unsubscribed from subscription {}",
            user.getName(), subscriptionId);
    }
}

// Session Registry
@Component
public class UserSessionRegistry {

    private final ConcurrentMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    public void registerSession(String username, String sessionId) {
        userSessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet())
            .add(sessionId);
    }

    public void removeSession(String username, String sessionId) {
        Set<String> sessions = userSessions.get(username);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(username);
            }
        }
    }

    public boolean hasActiveSessions(String username) {
        Set<String> sessions = userSessions.get(username);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<String> getSessionIds(String username) {
        return userSessions.getOrDefault(username, Set.of());
    }

    public Set<String> getOnlineUsers() {
        return userSessions.keySet();
    }
}
```

---

## Broadcasting and User Messaging

### Notification Service with WebSocket

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserSessionRegistry sessionRegistry;
    private final NotificationRepository notificationRepository;

    /**
     * Send notification to specific user
     */
    public void sendToUser(String username, Notification notification) {
        NotificationResponse response = NotificationResponse.from(notification);

        messagingTemplate.convertAndSendToUser(
            username,
            "/queue/notifications",
            response
        );

        log.debug("Sent notification to user {}: {}", username, notification.getId());
    }

    /**
     * Send to all users subscribed to a topic
     */
    public void broadcastToTopic(String topic, Object payload) {
        messagingTemplate.convertAndSend("/topic/" + topic, payload);
    }

    /**
     * Send to users in a specific room
     */
    public void sendToRoom(String roomId, Object payload) {
        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId, payload);
    }

    /**
     * Send to multiple specific users
     */
    public void sendToUsers(Set<String> usernames, Object payload) {
        for (String username : usernames) {
            if (sessionRegistry.hasActiveSessions(username)) {
                messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/notifications",
                    payload
                );
            }
        }
    }

    /**
     * Send with custom headers
     */
    public void sendWithHeaders(String destination, Object payload, Map<String, Object> headers) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setLeaveMutable(true);
        headers.forEach(accessor::setHeader);

        messagingTemplate.convertAndSend(destination, payload, accessor.getMessageHeaders());
    }

    /**
     * Async notification dispatch
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationCreatedEvent event) {
        Notification notification = event.notification();
        sendToUser(notification.getRecipient(), notification);
    }
}
```

### Scheduled Broadcasts

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final PriceService priceService;

    /**
     * Broadcast price updates every 5 seconds
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastPriceUpdates() {
        List<PriceUpdate> updates = priceService.getLatestPrices();

        if (!updates.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/prices", updates);
            log.trace("Broadcasted {} price updates", updates.size());
        }
    }

    /**
     * Broadcast system status every minute
     */
    @Scheduled(fixedRate = 60000)
    public void broadcastSystemStatus() {
        SystemStatus status = SystemStatus.builder()
            .timestamp(Instant.now())
            .activeConnections(getActiveConnectionCount())
            .serverLoad(getServerLoad())
            .build();

        messagingTemplate.convertAndSend("/topic/system/status", status);
    }
}
```

---

## Error Handling

### Comprehensive Error Handling

```java
@ControllerAdvice
@Slf4j
public class WebSocketExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle validation errors
     */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public WebSocketError handleValidationError(
            MethodArgumentNotValidException ex, Principal principal) {

        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .toList();

        log.warn("WebSocket validation error for {}: {}",
            principal.getName(), errors);

        return WebSocketError.validation(errors);
    }

    /**
     * Handle access denied
     */
    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public WebSocketError handleAccessDenied(
            AccessDeniedException ex, Principal principal) {

        log.warn("WebSocket access denied for {}: {}",
            principal.getName(), ex.getMessage());

        return WebSocketError.forbidden("Access denied");
    }

    /**
     * Handle business exceptions
     */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public WebSocketError handleBusinessError(
            BusinessException ex, Principal principal) {

        log.warn("WebSocket business error for {}: {}",
            principal.getName(), ex.getMessage());

        return WebSocketError.business(ex.getCode(), ex.getMessage());
    }

    /**
     * Handle all other exceptions
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public WebSocketError handleGenericError(Exception ex, Principal principal) {
        log.error("WebSocket error for {}", principal.getName(), ex);

        return WebSocketError.internal("An unexpected error occurred");
    }
}

// Error Response
public record WebSocketError(
    String type,
    String code,
    String message,
    List<String> details,
    Instant timestamp
) {
    public static WebSocketError validation(List<String> errors) {
        return new WebSocketError("VALIDATION_ERROR", "VALIDATION_FAILED",
            "Validation failed", errors, Instant.now());
    }

    public static WebSocketError forbidden(String message) {
        return new WebSocketError("ACCESS_DENIED", "FORBIDDEN",
            message, List.of(), Instant.now());
    }

    public static WebSocketError business(String code, String message) {
        return new WebSocketError("BUSINESS_ERROR", code,
            message, List.of(), Instant.now());
    }

    public static WebSocketError internal(String message) {
        return new WebSocketError("INTERNAL_ERROR", "INTERNAL",
            message, List.of(), Instant.now());
    }
}
```

---

## Heartbeat and Connection Management

### Connection Health Monitoring

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketHealthMonitor {

    private final UserSessionRegistry sessionRegistry;
    private final SimpUserRegistry simpUserRegistry;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        // Register metrics
        Gauge.builder("websocket.connections.active",
            simpUserRegistry, reg -> reg.getUsers().size())
            .description("Active WebSocket connections")
            .register(meterRegistry);

        Gauge.builder("websocket.sessions.total",
            sessionRegistry,
            reg -> reg.getOnlineUsers().stream()
                .mapToInt(u -> reg.getSessionIds(u).size())
                .sum())
            .description("Total WebSocket sessions")
            .register(meterRegistry);
    }

    @Scheduled(fixedRate = 30000)
    public void logConnectionStats() {
        int userCount = simpUserRegistry.getUserCount();
        int sessionCount = simpUserRegistry.getUsers().stream()
            .mapToInt(u -> u.getSessions().size())
            .sum();

        log.info("WebSocket stats: users={}, sessions={}", userCount, sessionCount);
    }

    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
            simpUserRegistry.getUserCount(),
            simpUserRegistry.getUsers().stream()
                .mapToInt(u -> u.getSessions().size())
                .sum(),
            sessionRegistry.getOnlineUsers()
        );
    }
}

public record ConnectionStats(
    int activeUsers,
    int totalSessions,
    Set<String> onlineUsers
) {}
```

---

## Scaling with External Broker

### RabbitMQ STOMP Broker Relay

```java
@Configuration
@EnableWebSocketMessageBroker
@Profile("production")
public class WebSocketBrokerRelayConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${rabbitmq.host}")
    private String rabbitHost;

    @Value("${rabbitmq.port}")
    private int rabbitPort;

    @Value("${rabbitmq.stomp.user}")
    private String stompUser;

    @Value("${rabbitmq.stomp.password}")
    private String stompPassword;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic", "/queue")
            .setRelayHost(rabbitHost)
            .setRelayPort(rabbitPort)
            .setClientLogin(stompUser)
            .setClientPasscode(stompPassword)
            .setSystemLogin(stompUser)
            .setSystemPasscode(stompPassword)
            .setSystemHeartbeatSendInterval(10000)
            .setSystemHeartbeatReceiveInterval(10000);

        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
```

### Redis Session Registry for Clustering

```java
@Component
@RequiredArgsConstructor
@Profile("clustered")
public class RedisUserSessionRegistry {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "ws:session:";
    private static final Duration TTL = Duration.ofHours(24);

    public void registerSession(String username, String sessionId, String serverId) {
        String key = PREFIX + username;
        redisTemplate.opsForHash().put(key, sessionId, serverId);
        redisTemplate.expire(key, TTL);
    }

    public void removeSession(String username, String sessionId) {
        String key = PREFIX + username;
        redisTemplate.opsForHash().delete(key, sessionId);
    }

    public Map<String, String> getSessions(String username) {
        String key = PREFIX + username;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().toString(),
                e -> e.getValue().toString()
            ));
    }

    public boolean hasActiveSessions(String username) {
        String key = PREFIX + username;
        Long size = redisTemplate.opsForHash().size(key);
        return size != null && size > 0;
    }

    public Set<String> getOnlineUsers() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys == null) return Set.of();
        return keys.stream()
            .map(k -> k.substring(PREFIX.length()))
            .collect(Collectors.toSet());
    }
}
```

---

## Testing WebSocket Endpoints

### Standalone Controller Test (No Spring Context)

This pattern uses `SimpAnnotationMethodMessageHandler` directly to test controllers without loading Spring ApplicationContext. Provides fast, isolated unit tests.

```java
/**
 * Standalone test for WebSocket controllers.
 * Tests annotated controller methods without loading Spring configuration.
 *
 * Reference: spring-websocket-portfolio StandalonePortfolioControllerTests
 */
class StandaloneNotificationControllerTest {

    private NotificationService notificationService;
    private TestMessageChannel clientOutboundChannel;
    private TestAnnotationMethodHandler annotationMethodHandler;

    @BeforeEach
    void setUp() {
        // Create mock services
        notificationService = mock(NotificationService.class);
        SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(new TestMessageChannel());

        // Create controller
        NotificationController controller = new NotificationController(
            notificationService, messagingTemplate);

        // Setup test channels
        clientOutboundChannel = new TestMessageChannel();

        // Setup annotation method handler (simulates Spring's STOMP handling)
        annotationMethodHandler = new TestAnnotationMethodHandler(
            new TestMessageChannel(),
            clientOutboundChannel,
            new SimpMessagingTemplate(new TestMessageChannel())
        );

        annotationMethodHandler.registerHandler(controller);
        annotationMethodHandler.setDestinationPrefixes(Arrays.asList("/app"));
        annotationMethodHandler.setMessageConverter(new MappingJackson2MessageConverter());
        annotationMethodHandler.setApplicationContext(new StaticApplicationContext());
        annotationMethodHandler.afterPropertiesSet();
    }

    @Test
    void subscribeToNotifications_returnsUnreadNotifications() throws Exception {
        // Given
        when(notificationService.getUnreadNotifications("testuser"))
            .thenReturn(List.of(
                createNotification(1L, "Notification 1"),
                createNotification(2L, "Notification 2")
            ));

        // Create SUBSCRIBE message
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSubscriptionId("sub-0");
        headers.setDestination("/app/notifications");
        headers.setSessionId("session-0");
        headers.setUser(new TestPrincipal("testuser"));
        headers.setSessionAttributes(new HashMap<>());

        Message<byte[]> message = MessageBuilder
            .withPayload(new byte[0])
            .setHeaders(headers)
            .build();

        // When
        annotationMethodHandler.handleMessage(message);

        // Then
        assertThat(clientOutboundChannel.getMessages()).hasSize(1);

        Message<?> reply = clientOutboundChannel.getMessages().get(0);
        StompHeaderAccessor replyHeaders = StompHeaderAccessor.wrap(reply);

        assertThat(replyHeaders.getSessionId()).isEqualTo("session-0");
        assertThat(replyHeaders.getSubscriptionId()).isEqualTo("sub-0");
        assertThat(replyHeaders.getDestination()).isEqualTo("/app/notifications");

        // Verify JSON payload
        String json = new String((byte[]) reply.getPayload(), StandardCharsets.UTF_8);
        assertThat(json).contains("Notification 1");
        assertThat(json).contains("Notification 2");
    }

    @Test
    void sendMarkAsRead_updatesNotification() throws Exception {
        // Given
        Notification notification = createNotification(1L, "Test");
        notification.setRead(true);
        when(notificationService.markAsRead(1L, "testuser"))
            .thenReturn(notification);

        // Create request payload
        MarkReadRequest request = new MarkReadRequest(1L);
        byte[] payload = new ObjectMapper().writeValueAsBytes(request);

        // Create SEND message
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/app/notifications/read");
        headers.setSessionId("session-0");
        headers.setUser(new TestPrincipal("testuser"));
        headers.setSessionAttributes(new HashMap<>());

        Message<byte[]> message = MessageBuilder
            .withPayload(payload)
            .setHeaders(headers)
            .build();

        // When
        annotationMethodHandler.handleMessage(message);

        // Then
        verify(notificationService).markAsRead(1L, "testuser");
    }

    @Test
    void sendChatMessage_broadcastsToRoom() throws Exception {
        // Given
        ChatMessageRequest request = new ChatMessageRequest("Hello, room!");
        byte[] payload = new ObjectMapper().writeValueAsBytes(request);

        // Create SEND message with destination variable
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/app/chat/room/room123");
        headers.setSessionId("session-0");
        headers.setUser(new TestPrincipal("testuser"));
        headers.setSessionAttributes(new HashMap<>());

        Message<byte[]> message = MessageBuilder
            .withPayload(payload)
            .setHeaders(headers)
            .build();

        // When
        annotationMethodHandler.handleMessage(message);

        // Then
        assertThat(clientOutboundChannel.getMessages()).hasSize(1);

        Message<?> reply = clientOutboundChannel.getMessages().get(0);
        StompHeaderAccessor replyHeaders = StompHeaderAccessor.wrap(reply);

        // @SendTo("/topic/chat/room/{roomId}") destination
        assertThat(replyHeaders.getDestination()).isEqualTo("/topic/chat/room/room123");

        String json = new String((byte[]) reply.getPayload(), StandardCharsets.UTF_8);
        assertThat(json).contains("Hello, room!");
        assertThat(json).contains("testuser");
    }

    private Notification createNotification(Long id, String content) {
        return Notification.builder()
            .id(id)
            .content(content)
            .read(false)
            .createdAt(Instant.now())
            .build();
    }
}

/**
 * Custom SimpAnnotationMethodMessageHandler that allows manual controller registration.
 */
class TestAnnotationMethodHandler extends SimpAnnotationMethodMessageHandler {

    public TestAnnotationMethodHandler(
            SubscribableChannel inChannel,
            MessageChannel outChannel,
            SimpMessageSendingOperations brokerTemplate) {
        super(inChannel, outChannel, brokerTemplate);
    }

    /**
     * Register a controller for testing (bypasses auto-discovery).
     */
    public void registerHandler(Object handler) {
        super.detectHandlerMethods(handler);
    }
}
```

### Test Utilities

```java
/**
 * Test message channel that captures sent messages.
 */
class TestMessageChannel implements SubscribableChannel {

    private final List<Message<?>> messages = new ArrayList<>();

    @Override
    public boolean send(Message<?> message) {
        messages.add(message);
        return true;
    }

    @Override
    public boolean send(Message<?> message, long timeout) {
        return send(message);
    }

    @Override
    public boolean subscribe(MessageHandler handler) {
        return true;
    }

    @Override
    public boolean unsubscribe(MessageHandler handler) {
        return true;
    }

    public List<Message<?>> getMessages() {
        return messages;
    }

    public void clear() {
        messages.clear();
    }

    public Message<?> getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
}

/**
 * Test principal for simulating authenticated users.
 */
class TestPrincipal implements Principal {

    private final String name;

    TestPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Principal)) return false;
        return name.equals(((Principal) o).getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
```

### Unit Testing Message Controllers (Direct Method Call)

```java
class NotificationControllerTest {

    private NotificationController controller;
    private NotificationService notificationService;
    private SimpMessagingTemplate messagingTemplate;
    private TestMessageChannel clientOutboundChannel;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        clientOutboundChannel = new TestMessageChannel();
        messagingTemplate = new SimpMessagingTemplate(clientOutboundChannel);
        controller = new NotificationController(notificationService, messagingTemplate);
    }

    @Test
    void handleSubscribe_returnsUnreadNotifications() {
        // Given
        Principal principal = new TestPrincipal("testuser");
        List<Notification> notifications = List.of(
            createNotification(1L, "Test notification 1"),
            createNotification(2L, "Test notification 2")
        );
        when(notificationService.getUnreadNotifications("testuser"))
            .thenReturn(notifications);

        // When
        List<NotificationResponse> result = controller.handleSubscribe(principal);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        verify(notificationService).getUnreadNotifications("testuser");
    }

    @Test
    void markAsRead_updatesAndReturnsNotification() {
        // Given
        Principal principal = new TestPrincipal("testuser");
        MarkReadRequest request = new MarkReadRequest(1L);
        Notification notification = createNotification(1L, "Test");
        notification.setRead(true);

        when(notificationService.markAsRead(1L, "testuser"))
            .thenReturn(notification);

        // When
        NotificationResponse result = controller.markAsRead(request, principal);

        // Then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.read()).isTrue();
    }
}
```

### Integration Testing with STOMP Client

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void shouldConnectAndReceiveNotifications() throws Exception {
        // Given
        String token = tokenProvider.generateToken(testUser());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        BlockingQueue<NotificationResponse> notifications = new LinkedBlockingQueue<>();

        // When
        session = stompClient.connectAsync(
            "ws://localhost:" + port + "/ws",
            new WebSocketHttpHeaders(),
            connectHeaders,
            new TestSessionHandler()
        ).get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificationResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                notifications.add((NotificationResponse) payload);
            }
        });

        // Trigger notification
        session.send("/app/notifications/test", new TestRequest("trigger"));

        // Then
        NotificationResponse response = notifications.poll(5, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.message()).isEqualTo("Test notification");
    }

    @Test
    void shouldBroadcastToTopic() throws Exception {
        // Connect two clients
        StompSession session1 = connectWithToken(testUser("user1"));
        StompSession session2 = connectWithToken(testUser("user2"));

        BlockingQueue<ChatMessageResponse> user1Messages = new LinkedBlockingQueue<>();
        BlockingQueue<ChatMessageResponse> user2Messages = new LinkedBlockingQueue<>();

        // Subscribe both to same room
        session1.subscribe("/topic/chat/room/room1", messageHandler(user1Messages));
        session2.subscribe("/topic/chat/room/room1", messageHandler(user2Messages));

        // Wait for subscriptions
        Thread.sleep(500);

        // Send message from user1
        session1.send("/app/chat/room/room1",
            new ChatMessageRequest("Hello from user1"));

        // Both should receive
        ChatMessageResponse msg1 = user1Messages.poll(5, TimeUnit.SECONDS);
        ChatMessageResponse msg2 = user2Messages.poll(5, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();
        assertThat(msg1.content()).isEqualTo("Hello from user1");
        assertThat(msg2.content()).isEqualTo("Hello from user1");
    }

    private StompSession connectWithToken(UserDetails user) throws Exception {
        String token = tokenProvider.generateToken(user);
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);

        return stompClient.connectAsync(
            "ws://localhost:" + port + "/ws",
            new WebSocketHttpHeaders(),
            headers,
            new TestSessionHandler()
        ).get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler messageHandler(BlockingQueue<ChatMessageResponse> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((ChatMessageResponse) payload);
            }
        };
    }

    private static class TestSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void handleException(StompSession session, StompCommand command,
                StompHeaders headers, byte[] payload, Throwable exception) {
            throw new RuntimeException(exception);
        }
    }
}
```

---

## Frontend Integration

### TypeScript STOMP Client

```typescript
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  private client: Client;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  constructor(private token: string) {
    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => console.log('STOMP:', str),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.client.onConnect = this.onConnect.bind(this);
    this.client.onDisconnect = this.onDisconnect.bind(this);
    this.client.onStompError = this.onError.bind(this);
  }

  connect(): void {
    this.client.activate();
  }

  disconnect(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();
    this.client.deactivate();
  }

  subscribe<T>(
    destination: string,
    callback: (message: T) => void
  ): () => void {
    const subscription = this.client.subscribe(destination, (msg: IMessage) => {
      const parsed = JSON.parse(msg.body) as T;
      callback(parsed);
    });

    this.subscriptions.set(destination, subscription);

    return () => {
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
    };
  }

  send(destination: string, body: unknown): void {
    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
  }

  private onConnect(): void {
    console.log('WebSocket connected');
    this.reconnectAttempts = 0;

    // Subscribe to error channel
    this.subscribe<WebSocketError>('/user/queue/errors', (error) => {
      console.error('WebSocket error:', error);
      // Handle error (show toast, etc.)
    });
  }

  private onDisconnect(): void {
    console.log('WebSocket disconnected');
  }

  private onError(frame: IMessage): void {
    console.error('STOMP error:', frame.headers['message']);
  }
}

// React Hook
function useWebSocket() {
  const [isConnected, setIsConnected] = useState(false);
  const wsRef = useRef<WebSocketService | null>(null);
  const { token } = useAuth();

  useEffect(() => {
    if (token) {
      wsRef.current = new WebSocketService(token);
      wsRef.current.connect();
      setIsConnected(true);

      return () => {
        wsRef.current?.disconnect();
        setIsConnected(false);
      };
    }
  }, [token]);

  const subscribe = useCallback(
    <T>(destination: string, callback: (msg: T) => void) => {
      return wsRef.current?.subscribe(destination, callback) ?? (() => {});
    },
    []
  );

  const send = useCallback((destination: string, body: unknown) => {
    wsRef.current?.send(destination, body);
  }, []);

  return { isConnected, subscribe, send };
}

// Usage in component
function ChatRoom({ roomId }: { roomId: string }) {
  const { subscribe, send } = useWebSocket();
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  useEffect(() => {
    const unsubscribe = subscribe<ChatMessage>(
      `/topic/chat/room/${roomId}`,
      (message) => {
        setMessages((prev) => [...prev, message]);
      }
    );

    return unsubscribe;
  }, [roomId, subscribe]);

  const sendMessage = (content: string) => {
    send(`/app/chat/room/${roomId}`, { content });
  };

  return (
    <div>
      {messages.map((msg) => (
        <div key={msg.id}>{msg.content}</div>
      ))}
      <MessageInput onSend={sendMessage} />
    </div>
  );
}
```

---

### Related Documentation

* `async-and-errors.md` - Async patterns and error handling
* `testing-guide.md` - Testing strategies
* `complete-examples.md` - Full implementation examples
* `services-and-repositories.md` - Service layer patterns
