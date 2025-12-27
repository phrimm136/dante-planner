# Validation Patterns – Enterprise Spring Boot Validation

Production-ready guide to input validation using **Jakarta Bean Validation**, **Spring MVC**, custom validators, and validation groups for type-safe, maintainable validation.

## Table of Contents

* [Validation Architecture](#validation-architecture)
* [DTO Validation Patterns](#dto-validation-patterns)
* [Custom Constraint Annotations](#custom-constraint-annotations)
* [Cross-Field Validation](#cross-field-validation)
* [Validation Groups](#validation-groups)
* [Collection and Nested Validation](#collection-and-nested-validation)
* [Programmatic Validation](#programmatic-validation)
* [Error Response Formatting](#error-response-formatting)
* [Internationalization (i18n)](#internationalization-i18n)
* [Testing Validation](#testing-validation)

---

## Validation Architecture

### Validation Layers

```
┌─────────────────────────────────────────────────────────────┐
│  Controller Layer                                            │
│  ├─ @Valid on @RequestBody → MethodArgumentNotValidException │
│  ├─ @Validated on class → ConstraintViolationException      │
│  └─ Path/Query params validation                            │
├─────────────────────────────────────────────────────────────┤
│  Service Layer                                               │
│  ├─ Business rule validation (domain-specific)              │
│  └─ Programmatic validation for external data               │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer                                                │
│  ├─ Entity invariant enforcement                            │
│  └─ Value object validation                                 │
└─────────────────────────────────────────────────────────────┘
```

### Validation Principles

| Layer | Validation Type | Example |
|-------|-----------------|---------|
| **Controller** | Format & structure | `@NotBlank`, `@Email`, `@Size` |
| **Service** | Business rules | "User can only have 5 active posts" |
| **Domain** | Invariants | "Order total must equal sum of items" |

---

## DTO Validation Patterns

### Request DTO with Comprehensive Validation

```java
/**
 * Request DTO for user registration.
 * All validation rules are declarative and self-documenting.
 */
public record CreateUserRequest(
    @NotBlank(message = "{validation.user.email.required}")
    @Email(message = "{validation.user.email.invalid}")
    @Size(max = 255, message = "{validation.user.email.size}")
    String email,

    @NotBlank(message = "{validation.user.password.required}")
    @Size(min = 8, max = 72, message = "{validation.user.password.size}")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "{validation.user.password.pattern}"
    )
    String password,

    @NotBlank(message = "{validation.user.name.required}")
    @Size(min = 2, max = 100, message = "{validation.user.name.size}")
    @Pattern(regexp = "^[\\p{L}\\s'-]+$", message = "{validation.user.name.pattern}")
    String name,

    @Past(message = "{validation.user.birthDate.past}")
    LocalDate birthDate,

    @Valid
    @NotNull(message = "{validation.user.address.required}")
    AddressRequest address
) {
    public CreateUserCommand toCommand() {
        return new CreateUserCommand(
            email.trim().toLowerCase(),
            password,
            name.trim(),
            birthDate,
            address.toValueObject()
        );
    }
}

/**
 * Nested address validation.
 */
public record AddressRequest(
    @NotBlank(message = "{validation.address.street.required}")
    @Size(max = 200)
    String street,

    @NotBlank(message = "{validation.address.city.required}")
    @Size(max = 100)
    String city,

    @NotBlank(message = "{validation.address.postalCode.required}")
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "{validation.address.postalCode.pattern}")
    String postalCode,

    @NotBlank(message = "{validation.address.country.required}")
    @Size(min = 2, max = 2)
    @Pattern(regexp = "^[A-Z]{2}$", message = "{validation.address.country.pattern}")
    String countryCode
) {
    public Address toValueObject() {
        return new Address(street, city, postalCode, countryCode);
    }
}
```

### Update DTO with Optional Fields

```java
/**
 * Request DTO for updating user profile.
 * All fields are optional - only provided fields are updated.
 */
public record UpdateUserRequest(
    @Size(min = 2, max = 100, message = "{validation.user.name.size}")
    @Pattern(regexp = "^[\\p{L}\\s'-]+$", message = "{validation.user.name.pattern}")
    String name,

    @Past(message = "{validation.user.birthDate.past}")
    LocalDate birthDate,

    @Valid
    AddressRequest address,

    @Size(max = 500, message = "{validation.user.bio.size}")
    String bio
) {
    /**
     * Checks if any field is provided for update.
     */
    public boolean hasUpdates() {
        return name != null || birthDate != null || address != null || bio != null;
    }

    public UpdateUserCommand toCommand() {
        return new UpdateUserCommand(
            name != null ? name.trim() : null,
            birthDate,
            address != null ? address.toValueObject() : null,
            bio != null ? bio.trim() : null
        );
    }
}
```

---

## Custom Constraint Annotations

### Simple Custom Validator

```java
/**
 * Validates that a string is not blank after trimming.
 */
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = NotBlankTrimmedValidator.class)
@Documented
public @interface NotBlankTrimmed {
    String message() default "{validation.notBlankTrimmed}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class NotBlankTrimmedValidator implements ConstraintValidator<NotBlankTrimmed, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && !value.trim().isEmpty();
    }
}
```

### Database-Backed Validator

```java
/**
 * Validates that an email is not already registered.
 */
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
@Documented
public @interface UniqueEmail {
    String message() default "{validation.user.email.unique}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
@RequiredArgsConstructor
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    private final UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return true; // Let @NotBlank handle null/blank
        }
        return !userRepository.existsByEmailIgnoreCase(email.trim());
    }
}
```

### Enum Validator

```java
/**
 * Validates that a string is a valid enum value.
 */
@Target({FIELD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = ValidEnumValidator.class)
@Documented
public @interface ValidEnum {
    String message() default "{validation.enum.invalid}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    Class<? extends Enum<?>> enumClass();
    boolean ignoreCase() default true;
}

public class ValidEnumValidator implements ConstraintValidator<ValidEnum, String> {

    private Set<String> validValues;
    private boolean ignoreCase;

    @Override
    public void initialize(ValidEnum annotation) {
        ignoreCase = annotation.ignoreCase();
        validValues = Arrays.stream(annotation.enumClass().getEnumConstants())
            .map(e -> ignoreCase ? e.name().toUpperCase() : e.name())
            .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;

        String compareValue = ignoreCase ? value.toUpperCase() : value;
        return validValues.contains(compareValue);
    }
}

// Usage
public record CreatePostRequest(
    @NotBlank String title,
    @NotBlank String content,

    @ValidEnum(enumClass = PostCategory.class, message = "Invalid category")
    String category
) {}
```

### Conditional Required Validator

```java
/**
 * Validates that a field is required when another field has a specific value.
 */
@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = ConditionalRequiredValidator.class)
@Documented
public @interface ConditionalRequired {
    String message() default "{validation.conditionalRequired}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    String conditionalField();
    String conditionalValue();
    String requiredField();

    @Target({TYPE})
    @Retention(RUNTIME)
    @interface List {
        ConditionalRequired[] value();
    }
}

public class ConditionalRequiredValidator implements ConstraintValidator<ConditionalRequired, Object> {

    private String conditionalField;
    private String conditionalValue;
    private String requiredField;

    @Override
    public void initialize(ConditionalRequired annotation) {
        this.conditionalField = annotation.conditionalField();
        this.conditionalValue = annotation.conditionalValue();
        this.requiredField = annotation.requiredField();
    }

    @Override
    public boolean isValid(Object object, ConstraintValidatorContext context) {
        try {
            Object conditionalFieldValue = BeanUtils.getProperty(object, conditionalField);
            Object requiredFieldValue = BeanUtils.getProperty(object, requiredField);

            if (conditionalValue.equals(String.valueOf(conditionalFieldValue))) {
                if (requiredFieldValue == null ||
                    (requiredFieldValue instanceof String && ((String) requiredFieldValue).isBlank())) {

                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(requiredField)
                        .addConstraintViolation();
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

// Usage
@ConditionalRequired(
    conditionalField = "paymentMethod",
    conditionalValue = "CREDIT_CARD",
    requiredField = "cardNumber",
    message = "Card number is required for credit card payments"
)
@ConditionalRequired(
    conditionalField = "paymentMethod",
    conditionalValue = "BANK_TRANSFER",
    requiredField = "bankAccount",
    message = "Bank account is required for bank transfers"
)
public record PaymentRequest(
    @NotNull PaymentMethod paymentMethod,
    String cardNumber,
    String bankAccount,
    @Positive BigDecimal amount
) {}
```

---

## Cross-Field Validation

### Using @AssertTrue

```java
public record DateRangeRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {
    @AssertTrue(message = "{validation.dateRange.endAfterStart}")
    public boolean isEndDateAfterStartDate() {
        if (startDate == null || endDate == null) return true;
        return endDate.isAfter(startDate) || endDate.isEqual(startDate);
    }

    @AssertTrue(message = "{validation.dateRange.maxRange}")
    public boolean isWithinMaxRange() {
        if (startDate == null || endDate == null) return true;
        return ChronoUnit.DAYS.between(startDate, endDate) <= 365;
    }
}
```

### Class-Level Custom Validator

```java
@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = PasswordMatchValidator.class)
@Documented
public @interface PasswordMatch {
    String message() default "{validation.password.match}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    String passwordField() default "password";
    String confirmPasswordField() default "confirmPassword";
}

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, Object> {

    private String passwordField;
    private String confirmPasswordField;

    @Override
    public void initialize(PasswordMatch annotation) {
        this.passwordField = annotation.passwordField();
        this.confirmPasswordField = annotation.confirmPasswordField();
    }

    @Override
    public boolean isValid(Object object, ConstraintValidatorContext context) {
        try {
            String password = BeanUtils.getProperty(object, passwordField);
            String confirmPassword = BeanUtils.getProperty(object, confirmPasswordField);

            boolean valid = Objects.equals(password, confirmPassword);

            if (!valid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(confirmPasswordField)
                    .addConstraintViolation();
            }

            return valid;
        } catch (Exception e) {
            return false;
        }
    }
}

// Usage
@PasswordMatch
public record ChangePasswordRequest(
    @NotBlank String currentPassword,

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$")
    String password,

    @NotBlank
    String confirmPassword
) {}
```

---

## Validation Groups

### Defining Groups

```java
/**
 * Validation groups for different operations.
 */
public interface ValidationGroups {

    interface Create extends Default {}

    interface Update extends Default {}

    interface Patch {}

    interface Admin {}
}
```

### Using Groups in DTOs

```java
public record UserRequest(
    @Null(groups = Create.class, message = "ID must be null for creation")
    @NotNull(groups = Update.class, message = "ID is required for update")
    Long id,

    @NotBlank(groups = {Create.class, Update.class})
    @Email
    String email,

    @NotBlank(groups = Create.class, message = "Password is required for registration")
    @Size(min = 8, max = 72)
    String password,

    @NotBlank(groups = {Create.class, Update.class})
    @Size(min = 2, max = 100)
    String name
) {}
```

### Controller with Validation Groups

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(
            @Validated(Create.class) @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @Validated(Update.class) @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserDto> patchUser(
            @PathVariable Long id,
            @Validated(Patch.class) @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.patchUser(id, request));
    }
}
```

### Ordered Validation with Group Sequences

```java
/**
 * Validates in order: Basic → Format → Business.
 * Stops at first failing group.
 */
@GroupSequence({Basic.class, Format.class, Business.class})
public interface OrderedValidation {}

public interface Basic {}
public interface Format {}
public interface Business {}

public record RegisterUserRequest(
    // Basic validation first
    @NotBlank(groups = Basic.class, message = "Email is required")
    // Format validation second
    @Email(groups = Format.class, message = "Invalid email format")
    // Business validation third (requires DB check)
    @UniqueEmail(groups = Business.class, message = "Email already registered")
    String email,

    @NotBlank(groups = Basic.class)
    @Size(min = 8, groups = Format.class)
    @StrongPassword(groups = Format.class)
    String password
) {}

// Controller usage
@PostMapping("/register")
public ResponseEntity<UserDto> register(
        @Validated(OrderedValidation.class) @RequestBody RegisterUserRequest request) {
    // ...
}
```

---

## Collection and Nested Validation

### Validating Collections

```java
public record BatchCreateRequest(
    @NotEmpty(message = "At least one item is required")
    @Size(max = 100, message = "Maximum 100 items per batch")
    List<@Valid CreateItemRequest> items
) {}

public record CreateItemRequest(
    @NotBlank String name,
    @Positive BigDecimal price,
    @Min(1) @Max(10000) Integer quantity
) {}
```

### Map Validation

```java
public record ConfigUpdateRequest(
    @NotEmpty
    @Size(max = 50)
    Map<
        @NotBlank @Size(max = 50) String,
        @NotNull @Size(max = 500) String
    > settings
) {}
```

### Deep Nested Validation

```java
public record OrderRequest(
    @NotNull @Valid CustomerInfo customer,
    @NotEmpty @Valid List<OrderLineRequest> lines,
    @Valid ShippingInfo shipping
) {}

public record CustomerInfo(
    @NotBlank String name,
    @Email String email,
    @Valid @NotNull AddressRequest billingAddress
) {}

public record OrderLineRequest(
    @NotBlank String productId,
    @Positive Integer quantity,
    @PositiveOrZero BigDecimal discount
) {}

public record ShippingInfo(
    @Valid AddressRequest address,
    @ValidEnum(enumClass = ShippingMethod.class) String method,
    String instructions
) {}
```

---

## Programmatic Validation

### Validator Service

```java
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final Validator validator;

    /**
     * Validates an object and throws if invalid.
     */
    public <T> void validate(T object) {
        validate(object, Default.class);
    }

    /**
     * Validates an object with specific groups.
     */
    public <T> void validate(T object, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validator.validate(object, groups);

        if (!violations.isEmpty()) {
            throw new ValidationException(formatViolations(violations));
        }
    }

    /**
     * Validates and returns result without throwing.
     */
    public <T> ValidationResult<T> validateSafe(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);

        if (violations.isEmpty()) {
            return ValidationResult.valid(object);
        }
        return ValidationResult.invalid(formatViolations(violations));
    }

    /**
     * Validates a single property.
     */
    public <T> void validateProperty(T object, String propertyName) {
        Set<ConstraintViolation<T>> violations = validator.validateProperty(object, propertyName);

        if (!violations.isEmpty()) {
            throw new ValidationException(formatViolations(violations));
        }
    }

    private <T> Map<String, String> formatViolations(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing
            ));
    }
}

/**
 * Result object for safe validation.
 */
public record ValidationResult<T>(
    boolean valid,
    T value,
    Map<String, String> errors
) {
    public static <T> ValidationResult<T> valid(T value) {
        return new ValidationResult<>(true, value, Map.of());
    }

    public static <T> ValidationResult<T> invalid(Map<String, String> errors) {
        return new ValidationResult<>(false, null, errors);
    }

    public T getOrThrow() {
        if (!valid) {
            throw new ValidationException(errors);
        }
        return value;
    }
}
```

### Service-Level Validation

```java
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ValidationService validationService;
    private final ProductRepository productRepository;

    /**
     * Imports products from external source with validation.
     */
    @Transactional
    public ImportResult importProducts(List<ExternalProductDto> externalProducts) {
        List<ImportError> errors = new ArrayList<>();
        List<Product> imported = new ArrayList<>();

        for (int i = 0; i < externalProducts.size(); i++) {
            ExternalProductDto dto = externalProducts.get(i);

            // Transform to internal DTO
            CreateProductRequest request = mapToRequest(dto);

            // Validate
            ValidationResult<CreateProductRequest> result = validationService.validateSafe(request);

            if (!result.valid()) {
                errors.add(new ImportError(i, dto.externalId(), result.errors()));
                continue;
            }

            // Additional business validation
            if (productRepository.existsBySku(request.sku())) {
                errors.add(new ImportError(i, dto.externalId(),
                    Map.of("sku", "Product with this SKU already exists")));
                continue;
            }

            Product product = productRepository.persist(Product.create(request));
            imported.add(product);
        }

        return new ImportResult(imported.size(), errors);
    }
}
```

---

## Error Response Formatting

### Structured Validation Error Response

```java
@RestControllerAdvice
@Slf4j
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<FieldErrorDto> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorDto(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage(),
                error.getCode()
            ))
            .toList();

        List<GlobalErrorDto> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
            .map(error -> new GlobalErrorDto(
                error.getObjectName(),
                error.getDefaultMessage(),
                error.getCode()
            ))
            .toList();

        ValidationErrorResponse errorResponse = new ValidationErrorResponse(fieldErrors, globalErrors);

        log.debug("Validation failed: {} field errors, {} global errors",
            fieldErrors.size(), globalErrors.size());

        return ResponseEntity.badRequest()
            .body(ApiResponse.error("VALIDATION_ERROR", "Validation failed",
                Map.of("validation", errorResponse)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {

        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
            .map(violation -> new FieldErrorDto(
                extractPropertyName(violation.getPropertyPath()),
                violation.getInvalidValue(),
                violation.getMessage(),
                extractConstraintName(violation)
            ))
            .toList();

        return ResponseEntity.badRequest()
            .body(ApiResponse.error("VALIDATION_ERROR", "Validation failed",
                Map.of("validation", new ValidationErrorResponse(errors, List.of()))));
    }

    private String extractPropertyName(Path propertyPath) {
        String path = propertyPath.toString();
        // Remove method name prefix (e.g., "createUser.arg0.email" -> "email")
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    private String extractConstraintName(ConstraintViolation<?> violation) {
        return violation.getConstraintDescriptor()
            .getAnnotation()
            .annotationType()
            .getSimpleName();
    }
}

public record FieldErrorDto(
    String field,
    Object rejectedValue,
    String message,
    String code
) {}

public record GlobalErrorDto(
    String object,
    String message,
    String code
) {}

public record ValidationErrorResponse(
    List<FieldErrorDto> fieldErrors,
    List<GlobalErrorDto> globalErrors
) {}
```

### Response Example

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": {
      "validation": {
        "fieldErrors": [
          {
            "field": "email",
            "rejectedValue": "invalid-email",
            "message": "Invalid email format",
            "code": "Email"
          },
          {
            "field": "password",
            "rejectedValue": "123",
            "message": "Password must be at least 8 characters",
            "code": "Size"
          }
        ],
        "globalErrors": [
          {
            "object": "changePasswordRequest",
            "message": "Passwords do not match",
            "code": "PasswordMatch"
          }
        ]
      }
    }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Internationalization (i18n)

### Message Properties

```properties
# ValidationMessages.properties (default)
validation.notBlankTrimmed=Must not be blank
validation.user.email.required=Email is required
validation.user.email.invalid=Invalid email format
validation.user.email.unique=This email is already registered
validation.user.email.size=Email must be at most {max} characters
validation.user.password.required=Password is required
validation.user.password.size=Password must be between {min} and {max} characters
validation.user.password.pattern=Password must contain uppercase, lowercase, number, and special character
validation.password.match=Passwords do not match
validation.dateRange.endAfterStart=End date must be after start date
validation.dateRange.maxRange=Date range cannot exceed 365 days
validation.enum.invalid=Invalid value. Allowed values: {validValues}
```

```properties
# ValidationMessages_ko.properties (Korean)
validation.user.email.required=이메일은 필수입니다
validation.user.email.invalid=올바른 이메일 형식이 아닙니다
validation.user.email.unique=이미 등록된 이메일입니다
validation.user.password.required=비밀번호는 필수입니다
validation.user.password.size=비밀번호는 {min}자 이상 {max}자 이하여야 합니다
validation.password.match=비밀번호가 일치하지 않습니다
```

### Configuration

```java
@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource =
            new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(
            "classpath:ValidationMessages",
            "classpath:messages"
        );
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }
}
```

### Dynamic Message Parameters

```java
@Target({FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = AllowedValuesValidator.class)
public @interface AllowedValues {
    String message() default "Invalid value. Allowed: {allowedValues}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    String[] value();
}

public class AllowedValuesValidator implements ConstraintValidator<AllowedValues, String> {

    private Set<String> allowedValues;

    @Override
    public void initialize(AllowedValues annotation) {
        this.allowedValues = Set.of(annotation.value());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;

        boolean valid = allowedValues.contains(value);

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Invalid value. Allowed: " + String.join(", ", allowedValues))
                .addConstraintViolation();
        }

        return valid;
    }
}
```

---

## Testing Validation

### Unit Testing Validators

```java
@ExtendWith(MockitoExtension.class)
class UniqueEmailValidatorTest {

    @Mock UserRepository userRepository;
    @InjectMocks UniqueEmailValidator validator;

    @Test
    void isValid_withNewEmail_returnsTrue() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);

        assertThat(validator.isValid("new@example.com", null)).isTrue();
    }

    @Test
    void isValid_withExistingEmail_returnsFalse() {
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThat(validator.isValid("existing@example.com", null)).isFalse();
    }

    @Test
    void isValid_withNull_returnsTrue() {
        assertThat(validator.isValid(null, null)).isTrue();
        verifyNoInteractions(userRepository);
    }
}
```

### Integration Testing DTOs

```java
@SpringBootTest
class CreateUserRequestValidationTest {

    @Autowired Validator validator;

    @Test
    void validate_withValidRequest_hasNoViolations() {
        CreateUserRequest request = new CreateUserRequest(
            "test@example.com",
            "SecurePass123!",
            "John Doe",
            LocalDate.of(1990, 1, 1),
            new AddressRequest("123 Main St", "City", "12345", "US")
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withInvalidEmail_hasViolation() {
        CreateUserRequest request = new CreateUserRequest(
            "invalid-email",
            "SecurePass123!",
            "John Doe",
            LocalDate.of(1990, 1, 1),
            new AddressRequest("123 Main St", "City", "12345", "US")
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations)
            .hasSize(1)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("email");
    }

    @Test
    void validate_withWeakPassword_hasViolation() {
        CreateUserRequest request = new CreateUserRequest(
            "test@example.com",
            "weak",
            "John Doe",
            LocalDate.of(1990, 1, 1),
            new AddressRequest("123 Main St", "City", "12345", "US")
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .contains("password");
    }

    @Test
    void validate_withFutureBirthDate_hasViolation() {
        CreateUserRequest request = new CreateUserRequest(
            "test@example.com",
            "SecurePass123!",
            "John Doe",
            LocalDate.now().plusDays(1),
            new AddressRequest("123 Main St", "City", "12345", "US")
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("birthDate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "ab@"})
    void validate_withInvalidEmails_hasViolation(String invalidEmail) {
        CreateUserRequest request = new CreateUserRequest(
            invalidEmail,
            "SecurePass123!",
            "John Doe",
            LocalDate.of(1990, 1, 1),
            new AddressRequest("123 Main St", "City", "12345", "US")
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
```

### Testing Controllers with Validation

```java
@WebMvcTest(UserController.class)
class UserControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;

    @Test
    void createUser_withValidRequest_returns201() throws Exception {
        CreateUserRequest request = validRequest();

        when(userService.createUser(any())).thenReturn(new UserDto(1L, "test@example.com", "John"));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void createUser_withMissingEmail_returns400() throws Exception {
        String json = """
            {
              "password": "SecurePass123!",
              "name": "John Doe"
            }
            """;

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details.validation.fieldErrors[0].field").value("email"));
    }

    @Test
    void createUser_withMultipleErrors_returnsAllErrors() throws Exception {
        String json = """
            {
              "email": "invalid",
              "password": "weak",
              "name": ""
            }
            """;

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.validation.fieldErrors").isArray())
            .andExpect(jsonPath("$.error.details.validation.fieldErrors.length()").value(greaterThanOrEqualTo(3)));
    }

    private CreateUserRequest validRequest() {
        return new CreateUserRequest(
            "test@example.com",
            "SecurePass123!",
            "John Doe",
            LocalDate.of(1990, 1, 1),
            new AddressRequest("123 Main St", "City", "12345", "US")
        );
    }
}
```

---

## Summary: Validation Rules

| Rule | Description |
|------|-------------|
| **DTO Validation** | All validation rules on request DTOs, not controllers |
| **Custom Validators** | Create reusable validators for complex rules |
| **Validation Groups** | Use groups for operation-specific validation |
| **Cross-Field** | Use `@AssertTrue` or class-level validators |
| **i18n Messages** | Externalize all messages to properties files |
| **Structured Errors** | Return detailed, machine-readable error responses |
| **Test Coverage** | Unit test validators, integration test DTOs |

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [routing-and-controllers.md](routing-and-controllers.md) - Controller patterns
* [async-and-errors.md](async-and-errors.md) - Exception handling
