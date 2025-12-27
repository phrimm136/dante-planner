# Complete Examples – Enterprise Spring Boot Patterns

Production-ready, **end-to-end Spring Boot 3.x (Java 17+)** examples showcasing clean architecture, advanced JPA patterns, specifications, auditing, caching, and comprehensive error handling.

---

## Table of Contents

* [Enterprise Entity Design](#enterprise-entity-design)
* [Complete Controller with HATEOAS](#complete-controller-with-hateoas)
* [Service Layer Patterns](#service-layer-patterns)
* [Advanced Repository Patterns](#advanced-repository-patterns)
* [Specification Pattern for Dynamic Queries](#specification-pattern-for-dynamic-queries)
* [Pagination and Cursor-Based Paging](#pagination-and-cursor-based-paging)
* [MapStruct DTO Mapping](#mapstruct-dto-mapping)
* [Caching Patterns](#caching-patterns)
* [End-to-End Feature Example](#end-to-end-feature-example)
* [Multi-Tenancy Pattern](#multi-tenancy-pattern)

---

## Enterprise Entity Design

### Base Entity with Auditing

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @CreatedBy
    @Column(updatable = false)
    protected String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    protected Instant updatedAt;

    @LastModifiedBy
    protected String updatedBy;

    @Version
    protected Long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

### Auditor Configuration

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName);
    }
}
```

### Rich Domain Entity

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_customer", columnList = "customer_id"),
    @Index(name = "idx_order_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<OrderLine> lines = new ArrayList<>();

    @Embedded
    private Money totalAmount;

    @Column(nullable = false)
    private boolean deleted = false;

    // Factory method
    public static Order create(Customer customer, String orderNumber) {
        Order order = new Order();
        order.customer = customer;
        order.orderNumber = orderNumber;
        order.totalAmount = Money.ZERO;
        return order;
    }

    // Domain behavior
    public void addLine(Product product, int quantity) {
        if (status != OrderStatus.PENDING) {
            throw new OrderModificationException("Cannot modify non-pending order");
        }
        int nextLineNumber = lines.size() + 1;
        OrderLine line = OrderLine.create(this, product, quantity, nextLineNumber);
        lines.add(line);
        recalculateTotal();
    }

    public void submit() {
        if (lines.isEmpty()) {
            throw new OrderValidationException("Order must have at least one line");
        }
        if (status != OrderStatus.PENDING) {
            throw new OrderStateException("Order already submitted");
        }
        this.status = OrderStatus.SUBMITTED;
        registerEvent(new OrderSubmittedEvent(this));
    }

    public void cancel(String reason) {
        if (!status.isCancellable()) {
            throw new OrderStateException("Order cannot be cancelled in state: " + status);
        }
        this.status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(this, reason));
    }

    public void softDelete() {
        this.deleted = true;
    }

    private void recalculateTotal() {
        this.totalAmount = lines.stream()
            .map(OrderLine::getLineTotal)
            .reduce(Money.ZERO, Money::add);
    }
}
```

### Value Object (Embeddable)

```java
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO, "USD");

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    private void validateSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount);
    }
}
```

---

## Complete Controller with HATEOAS

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<EntityModel<OrderResponse>> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Order order = orderService.findByIdAndUser(id, principal.getId());
        OrderResponse response = orderMapper.toResponse(order);

        EntityModel<OrderResponse> model = EntityModel.of(response,
            linkTo(methodOn(OrderController.class).getOrder(id, principal)).withSelfRel(),
            linkTo(methodOn(OrderController.class).getOrderLines(id, principal)).withRel("lines"),
            linkTo(methodOn(OrderController.class).listOrders(null, null, principal)).withRel("orders")
        );

        return ResponseEntity.ok(model);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "List orders with filtering and pagination")
    public ResponseEntity<PagedModel<EntityModel<OrderSummaryResponse>>> listOrders(
            @ParameterObject OrderSearchCriteria criteria,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<Order> orders = orderService.search(criteria, pageable, principal.getId());
        Page<OrderSummaryResponse> responsePage = orders.map(orderMapper::toSummaryResponse);

        PagedModel<EntityModel<OrderSummaryResponse>> pagedModel =
            pagedResourcesAssembler.toModel(responsePage);

        return ResponseEntity.ok(pagedModel);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create new order")
    public ResponseEntity<EntityModel<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Order order = orderService.create(request, principal.getId());
        OrderResponse response = orderMapper.toResponse(order);

        EntityModel<OrderResponse> model = EntityModel.of(response,
            linkTo(methodOn(OrderController.class).getOrder(order.getId(), principal)).withSelfRel()
        );

        URI location = linkTo(methodOn(OrderController.class)
            .getOrder(order.getId(), principal)).toUri();

        return ResponseEntity.created(location).body(model);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Submit order for processing")
    public ResponseEntity<OrderResponse> submitOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Order order = orderService.submit(id, principal.getId());
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel order")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        orderService.cancel(id, request.reason(), principal.getId());
        return ResponseEntity.noContent().build();
    }
}
```

---

## Service Layer Patterns

### Command/Query Separation

```java
// Query Service - Read-only operations
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderSpecification orderSpecification;

    public Order findById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order findByIdAndUser(Long id, Long userId) {
        return orderRepository.findByIdAndCustomerId(id, userId)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Page<Order> search(OrderSearchCriteria criteria, Pageable pageable, Long userId) {
        Specification<Order> spec = orderSpecification.build(criteria)
            .and(orderSpecification.belongsToUser(userId))
            .and(orderSpecification.notDeleted());

        return orderRepository.findAll(spec, pageable);
    }

    public OrderStatistics getStatistics(Long userId, LocalDate from, LocalDate to) {
        return orderRepository.calculateStatistics(userId, from, to);
    }
}

// Command Service - Write operations
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Order create(CreateOrderRequest request, Long userId) {
        Customer customer = customerRepository.findById(userId)
            .orElseThrow(() -> new CustomerNotFoundException(userId));

        String orderNumber = generateOrderNumber();
        Order order = Order.create(customer, orderNumber);

        for (OrderLineRequest lineRequest : request.lines()) {
            Product product = productRepository.findById(lineRequest.productId())
                .orElseThrow(() -> new ProductNotFoundException(lineRequest.productId()));
            order.addLine(product, lineRequest.quantity());
        }

        Order savedOrder = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder));

        return savedOrder;
    }

    public Order submit(Long orderId, Long userId) {
        Order order = findAndValidateOwnership(orderId, userId);
        order.submit();
        return order;
    }

    public void cancel(Long orderId, String reason, Long userId) {
        Order order = findAndValidateOwnership(orderId, userId);
        order.cancel(reason);
    }

    @Retryable(
        retryFor = OptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public Order updateWithRetry(Long orderId, UpdateOrderRequest request, Long userId) {
        Order order = findAndValidateOwnership(orderId, userId);
        order.update(request);
        return order;
    }

    private Order findAndValidateOwnership(Long orderId, Long userId) {
        return orderRepository.findByIdAndCustomerId(orderId, userId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private String generateOrderNumber() {
        return "ORD-" + Instant.now().toEpochMilli();
    }
}
```

### Facade Service

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderQueryService queryService;
    private final OrderCommandService commandService;

    // Query delegation
    public Order findById(Long id) {
        return queryService.findById(id);
    }

    public Order findByIdAndUser(Long id, Long userId) {
        return queryService.findByIdAndUser(id, userId);
    }

    public Page<Order> search(OrderSearchCriteria criteria, Pageable pageable, Long userId) {
        return queryService.search(criteria, pageable, userId);
    }

    // Command delegation
    public Order create(CreateOrderRequest request, Long userId) {
        return commandService.create(request, userId);
    }

    public Order submit(Long orderId, Long userId) {
        return commandService.submit(orderId, userId);
    }

    public void cancel(Long orderId, String reason, Long userId) {
        commandService.cancel(orderId, reason, userId);
    }
}
```

---

## Advanced Repository Patterns

### Repository with Custom Queries

```java
public interface OrderRepository extends
        JpaRepository<Order, Long>,
        JpaSpecificationExecutor<Order>,
        OrderRepositoryCustom {

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    @Query("SELECT o FROM Order o JOIN FETCH o.lines WHERE o.id = :id")
    Optional<Order> findByIdWithLines(@Param("id") Long id);

    @Query("""
        SELECT o FROM Order o
        WHERE o.customer.id = :customerId
        AND o.status = :status
        AND o.deleted = false
        ORDER BY o.createdAt DESC
        """)
    List<Order> findByCustomerIdAndStatus(
        @Param("customerId") Long customerId,
        @Param("status") OrderStatus status
    );

    @Query("""
        SELECT new com.example.dto.OrderStatistics(
            COUNT(o),
            SUM(o.totalAmount.amount),
            AVG(o.totalAmount.amount)
        )
        FROM Order o
        WHERE o.customer.id = :customerId
        AND o.createdAt BETWEEN :from AND :to
        AND o.deleted = false
        """)
    OrderStatistics calculateStatistics(
        @Param("customerId") Long customerId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Modifying
    @Query("UPDATE Order o SET o.deleted = true WHERE o.id = :id")
    void softDelete(@Param("id") Long id);

    @EntityGraph(attributePaths = {"lines", "lines.product", "customer"})
    List<Order> findByStatusIn(List<OrderStatus> statuses);
}

// Custom implementation for complex queries
public interface OrderRepositoryCustom {
    List<Order> findOrdersForReport(ReportCriteria criteria);
    void batchUpdateStatus(List<Long> orderIds, OrderStatus status);
}

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<Order> findOrdersForReport(ReportCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);

        List<Predicate> predicates = new ArrayList<>();

        if (criteria.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
        }
        if (criteria.getFromDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get("createdAt"), criteria.getFromDate()));
        }
        if (criteria.getMinAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get("totalAmount").get("amount"), criteria.getMinAmount()));
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("createdAt")));

        return entityManager.createQuery(query)
            .setHint(QueryHints.HINT_FETCH_SIZE, 50)
            .getResultList();
    }

    @Override
    @Transactional
    public void batchUpdateStatus(List<Long> orderIds, OrderStatus status) {
        String jpql = "UPDATE Order o SET o.status = :status, o.updatedAt = :now WHERE o.id IN :ids";
        entityManager.createQuery(jpql)
            .setParameter("status", status)
            .setParameter("now", Instant.now())
            .setParameter("ids", orderIds)
            .executeUpdate();
    }
}
```

---

## Specification Pattern for Dynamic Queries

```java
@Component
public class OrderSpecification {

    public Specification<Order> build(OrderSearchCriteria criteria) {
        return Specification.where(hasStatus(criteria.status()))
            .and(hasOrderNumberLike(criteria.orderNumber()))
            .and(createdBetween(criteria.fromDate(), criteria.toDate()))
            .and(totalAmountBetween(criteria.minAmount(), criteria.maxAmount()));
    }

    public Specification<Order> belongsToUser(Long userId) {
        return (root, query, cb) ->
            cb.equal(root.get("customer").get("id"), userId);
    }

    public Specification<Order> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    private Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    private Specification<Order> hasOrderNumberLike(String orderNumber) {
        return (root, query, cb) -> {
            if (orderNumber == null || orderNumber.isBlank()) return null;
            return cb.like(cb.lower(root.get("orderNumber")),
                "%" + orderNumber.toLowerCase() + "%");
        };
    }

    private Specification<Order> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"),
                    from.atStartOfDay().toInstant(ZoneOffset.UTC),
                    to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"),
                    from.atStartOfDay().toInstant(ZoneOffset.UTC));
            }
            return cb.lessThan(root.get("createdAt"),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        };
    }

    private Specification<Order> totalAmountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            Path<BigDecimal> amountPath = root.get("totalAmount").get("amount");
            if (min != null && max != null) {
                return cb.between(amountPath, min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(amountPath, min);
            }
            return cb.lessThanOrEqualTo(amountPath, max);
        };
    }
}

// Search Criteria DTO
public record OrderSearchCriteria(
    OrderStatus status,
    String orderNumber,
    LocalDate fromDate,
    LocalDate toDate,
    BigDecimal minAmount,
    BigDecimal maxAmount
) {}
```

---

## Pagination and Cursor-Based Paging

### Offset Pagination

```java
@GetMapping
public ResponseEntity<Page<OrderSummaryResponse>> listOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String[] sort
) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100),
        Sort.by(parseSort(sort)));

    Page<Order> orders = orderRepository.findByDeletedFalse(pageable);
    return ResponseEntity.ok(orders.map(orderMapper::toSummaryResponse));
}

private Sort.Order[] parseSort(String[] sort) {
    return Arrays.stream(sort)
        .map(s -> {
            String[] parts = s.split(",");
            return parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Order.desc(parts[0])
                : Sort.Order.asc(parts[0]);
        })
        .toArray(Sort.Order[]::new);
}
```

### Cursor-Based (Keyset) Pagination

```java
public record CursorPageRequest(
    String cursor,
    int limit,
    Direction direction
) {
    public enum Direction { NEXT, PREV }
}

public record CursorPage<T>(
    List<T> content,
    String nextCursor,
    String prevCursor,
    boolean hasNext,
    boolean hasPrev
) {}

@Service
@RequiredArgsConstructor
public class OrderCursorPaginationService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public CursorPage<OrderSummaryResponse> findWithCursor(
            Long userId, CursorPageRequest request) {

        int fetchSize = request.limit() + 1;
        List<Order> orders;

        if (request.cursor() == null) {
            orders = orderRepository.findTopNByCustomerIdOrderByCreatedAtDesc(
                userId, fetchSize);
        } else {
            Cursor cursor = decodeCursor(request.cursor());
            if (request.direction() == Direction.NEXT) {
                orders = orderRepository.findByCustomerIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                    userId, cursor.createdAt(), cursor.id(), fetchSize);
            } else {
                orders = orderRepository.findByCustomerIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                    userId, cursor.createdAt(), cursor.id(), fetchSize);
                Collections.reverse(orders);
            }
        }

        boolean hasMore = orders.size() > request.limit();
        if (hasMore) {
            orders = orders.subList(0, request.limit());
        }

        List<OrderSummaryResponse> content = orders.stream()
            .map(orderMapper::toSummaryResponse)
            .toList();

        String nextCursor = hasMore && !orders.isEmpty()
            ? encodeCursor(orders.get(orders.size() - 1)) : null;
        String prevCursor = !orders.isEmpty()
            ? encodeCursor(orders.get(0)) : null;

        return new CursorPage<>(content, nextCursor, prevCursor, hasMore, request.cursor() != null);
    }

    private String encodeCursor(Order order) {
        Cursor cursor = new Cursor(order.getId(), order.getCreatedAt());
        return Base64.getEncoder().encodeToString(
            objectMapper.writeValueAsBytes(cursor));
    }

    private Cursor decodeCursor(String encoded) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        return objectMapper.readValue(decoded, Cursor.class);
    }

    private record Cursor(Long id, Instant createdAt) {}
}
```

---

## MapStruct DTO Mapping

```java
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {MoneyMapper.class, OrderLineMapper.class})
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.fullName")
    @Mapping(target = "lineCount", expression = "java(order.getLines().size())")
    OrderResponse toResponse(Order order);

    @Mapping(target = "customerId", source = "customer.id")
    OrderSummaryResponse toSummaryResponse(Order order);

    List<OrderSummaryResponse> toSummaryResponseList(List<Order> orders);

    @AfterMapping
    default void addLinks(@MappingTarget OrderResponse response, Order order) {
        response.setLinks(Map.of(
            "self", "/api/v1/orders/" + order.getId(),
            "lines", "/api/v1/orders/" + order.getId() + "/lines"
        ));
    }
}

@Mapper(componentModel = "spring")
public interface MoneyMapper {

    default MoneyDto toDto(Money money) {
        if (money == null) return null;
        return new MoneyDto(money.getAmount(), money.getCurrency());
    }

    default Money toEntity(MoneyDto dto) {
        if (dto == null) return null;
        return new Money(dto.amount(), dto.currency());
    }
}

// Response DTOs
public record OrderResponse(
    Long id,
    String orderNumber,
    OrderStatus status,
    Long customerId,
    String customerName,
    MoneyDto totalAmount,
    List<OrderLineResponse> lines,
    int lineCount,
    Instant createdAt,
    Instant updatedAt,
    Map<String, String> links
) {
    // Allow link injection
    private Map<String, String> links;
    public void setLinks(Map<String, String> links) { this.links = links; }
}

public record OrderSummaryResponse(
    Long id,
    String orderNumber,
    OrderStatus status,
    Long customerId,
    MoneyDto totalAmount,
    Instant createdAt
) {}
```

---

## Caching Patterns

### Service-Level Caching

```java
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "orders")
public class OrderCacheService {

    private final OrderRepository orderRepository;
    private final CacheManager cacheManager;

    @Cacheable(key = "#id", unless = "#result == null")
    public Order findById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }

    @Cacheable(key = "'user:' + #userId + ':page:' + #pageable.pageNumber",
               condition = "#pageable.pageNumber < 5")
    public Page<Order> findByUserId(Long userId, Pageable pageable) {
        return orderRepository.findByCustomerId(userId, pageable);
    }

    @CachePut(key = "#result.id")
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @CacheEvict(key = "#id")
    public void deleteFromCache(Long id) {
        // Just evicts from cache
    }

    @CacheEvict(allEntries = true)
    @Scheduled(fixedRateString = "${cache.orders.ttl:3600000}")
    public void evictAllOrders() {
        log.info("Evicting all orders from cache");
    }

    // Manual cache operations
    public void evictUserOrdersCache(Long userId) {
        Cache cache = cacheManager.getCache("orders");
        if (cache != null) {
            // Evict user-specific entries
            for (int page = 0; page < 5; page++) {
                cache.evict("user:" + userId + ":page:" + page);
            }
        }
    }
}

// Cache Configuration
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats());
        return cacheManager;
    }
}
```

---

## End-to-End Feature Example

### Complete Order Management Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Request Flow                                 │
├─────────────────────────────────────────────────────────────────────┤
│  POST /api/v1/orders                                                 │
│       ↓                                                              │
│  SecurityFilter (JWT validation)                                     │
│       ↓                                                              │
│  RateLimitFilter (100 req/min)                                       │
│       ↓                                                              │
│  OrderController.createOrder()                                       │
│       ↓                                                              │
│  @Valid CreateOrderRequest (Bean Validation)                         │
│       ↓                                                              │
│  OrderService.create()                                               │
│       ↓                                                              │
│  OrderRepository.save() (JPA)                                        │
│       ↓                                                              │
│  ApplicationEventPublisher.publishEvent(OrderCreatedEvent)           │
│       ↓                                                              │
│  OrderMapper.toResponse() (MapStruct)                                │
│       ↓                                                              │
│  201 CREATED + Location header                                       │
└─────────────────────────────────────────────────────────────────────┘
```

### Request/Response DTOs

```java
// Request
public record CreateOrderRequest(
    @NotEmpty(message = "Order must have at least one line")
    @Size(max = 100, message = "Order cannot have more than 100 lines")
    List<@Valid OrderLineRequest> lines,

    @Size(max = 500)
    String notes
) {}

public record OrderLineRequest(
    @NotNull Long productId,
    @Min(1) @Max(1000) int quantity
) {}

// Response with validation summary
public record ApiResponse<T>(
    boolean success,
    T data,
    List<String> errors,
    Map<String, Object> metadata
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, List.of(), Map.of());
    }

    public static <T> ApiResponse<T> error(List<String> errors) {
        return new ApiResponse<>(false, null, errors, Map.of());
    }
}
```

### Event-Driven Processing

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventHandler {

    private final NotificationService notificationService;
    private final InventoryService inventoryService;
    private final AnalyticsService analyticsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("orderEventExecutor")
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = event.order();
        log.info("Processing OrderCreatedEvent for order: {}", order.getOrderNumber());

        CompletableFuture.allOf(
            CompletableFuture.runAsync(() ->
                notificationService.sendOrderConfirmation(order)),
            CompletableFuture.runAsync(() ->
                inventoryService.reserveStock(order)),
            CompletableFuture.runAsync(() ->
                analyticsService.trackOrderCreation(order))
        ).exceptionally(ex -> {
            log.error("Error processing order event: {}", ex.getMessage());
            return null;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderSubmitted(OrderSubmittedEvent event) {
        // Process synchronously - payment processing
        log.info("Order submitted: {}", event.order().getOrderNumber());
    }
}
```

---

## Multi-Tenancy Pattern

### Tenant Context

```java
public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    public static void setCurrentTenant(String tenant) {
        currentTenant.set(tenant);
    }

    public static void clear() {
        currentTenant.remove();
    }
}

@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null) {
            tenantId = extractTenantFromSubdomain(request);
        }

        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantFromSubdomain(HttpServletRequest request) {
        String host = request.getServerName();
        if (host.contains(".")) {
            return host.split("\\.")[0];
        }
        return null;
    }
}
```

### Tenant-Aware Entity

```java
@Entity
@Table(name = "orders")
@Where(clause = "tenant_id = :tenantId")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "string"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Order extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @PrePersist
    public void prePersist() {
        this.tenantId = TenantContext.getCurrentTenant();
    }
}

// Aspect to enable tenant filter
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Before("execution(* com.example.repository.*Repository.*(..))")
    public void enableTenantFilter() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        }
    }
}
```

---

## Complete Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Enterprise Request Flow                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Client Request                                                              │
│       ↓                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Spring Security Filter Chain                                         │    │
│  │  • TenantFilter (extract tenant)                                     │    │
│  │  • JwtAuthenticationFilter (validate token)                          │    │
│  │  • RateLimitFilter (check limits)                                    │    │
│  │  • RequestLoggingFilter (MDC context)                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│       ↓                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Controller Layer                                                      │    │
│  │  • @PreAuthorize (method security)                                   │    │
│  │  • @Valid (request validation)                                       │    │
│  │  • Response mapping                                                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│       ↓                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Service Layer                                                         │    │
│  │  • @Transactional (transaction management)                           │    │
│  │  • Business validation                                                │    │
│  │  • Domain logic orchestration                                        │    │
│  │  • Event publishing                                                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│       ↓                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Repository Layer                                                      │    │
│  │  • Spring Data JPA                                                    │    │
│  │  • Specifications (dynamic queries)                                   │    │
│  │  • @EntityGraph (fetch optimization)                                 │    │
│  │  • Auditing (created/updated tracking)                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│       ↓                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Database                                                              │    │
│  │  • Connection pooling (HikariCP)                                     │    │
│  │  • Optimistic locking (@Version)                                     │    │
│  │  • Soft deletes                                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│       ↓                                                                      │
│  Response (DTO with HATEOAS links)                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Related Documentation

* `async-and-errors.md` - Async patterns and error handling
* `testing-guide.md` - Testing strategies
* `services-and-repositories.md` - Service layer patterns
* `validation-patterns.md` - Validation approaches
