# Research: OAuth2 Login Implementation

## Overview of Codebase

**Backend Technology Stack:**
- Spring Boot 4.0.0 with Spring Security starter dependency already added but not configured
- Java 17 with Maven build tool and Lombok for boilerplate reduction
- MySQL 8.0.33 database with Hibernate JPA for ORM using update DDL mode
- WebMVC and WebSocket starters available for REST API and real-time communication
- Minimal backend with only entry point class, no controllers, services, repositories or entities exist

**Frontend State Management:**
- TanStack Query v5 for data fetching with one minute stale time and five minute garbage collection
- TanStack Router with five routes and GlobalLayout wrapper for consistent UI structure
- Theme context using localStorage for persistence pattern established for cross-tab sync
- No authentication context or protected route handling currently implemented
- No API client wrapper configured, only mocked fetch in test setup

**Header Component Structure:**
- User icon button exists with TODO comment for OAuth implementation at line 107-113
- Three section layout with logo, navigation, and settings buttons using shadcn components
- DropdownMenu pattern established for language selector can be replicated for auth menu
- Translation keys available via react-i18next for welcome message and button labels

**Database Configuration:**
- MySQL connection via application.properties with environment variable overrides from .env file
- Database name danteplanner with user danteplanner and password from environment
- Hibernate configured with show-sql enabled and MySQL dialect for debugging queries
- No migration tools like Flyway or Liquibase configured, relies on Hibernate DDL auto-update

## Codebase Structure

**Backend Directory Layout:**
- Backend root contains pom.xml with Spring dependencies and application.properties for config
- Source structure follows standard Maven layout with main and test directories
- Only BackendApplication.java exists as Spring Boot entry point with main method
- No package structure for controllers, services, repositories, entities, security, or utilities
- Test directory has basic context load test but no integration or unit tests

**Frontend Authentication Touch Points:**
- Header component at frontend/src/components/Header.tsx contains user button integration point
- Theme context pattern at frontend/src/contexts/ThemeContext.tsx provides template for auth context
- Query client at frontend/src/lib/queryClient.ts ready for API mutations and queries
- Router at frontend/src/lib/router.tsx needs protected route configuration for authenticated pages
- No hooks directory entry for useAuth similar to existing useTheme pattern

**API Communication Gap:**
- No base URL configuration for backend API endpoints in environment variables
- No interceptor setup for attaching JWT tokens to outgoing requests automatically
- No error handling for 401 unauthorized responses triggering logout or token refresh
- React Query mutations not set up for login, logout, or token refresh operations

**Testing Infrastructure:**
- Frontend vitest setup mocks window.matchMedia, IntersectionObserver, and global fetch
- Backend JUnit Jupiter via Spring Boot Test starter but only context load test exists
- No OAuth flow testing, JWT validation tests, or integration tests for auth endpoints

## Gotchas and Pitfalls

**Spring Security Default Behavior:**
- Adding Spring Security dependency enables default authentication requiring all requests be authenticated
- Default form login redirects block API endpoints unless explicitly configured with stateless policy
- CSRF protection enabled by default will block POST requests from frontend without token exchange
- Must explicitly configure public endpoints for OAuth callback URLs and health checks

**OAuth2 Provider Credentials:**
- Google OAuth requires client ID and secret from Google Cloud Console with redirect URI whitelist
- Apple Sign In requires service ID, team ID, key ID, and private key file from Apple Developer portal
- Redirect URIs must exactly match including protocol, domain, port, and path or OAuth fails
- Localhost testing requires separate OAuth app registrations from production deployments

**JWT Token Security:**
- JWT secret must be cryptographically strong random string not hardcoded in source code
- Access tokens in localStorage vulnerable to XSS attacks, consider httpOnly cookies for refresh tokens
- Token expiry must be validated on every request to prevent expired token usage
- Refresh token rotation recommended to limit damage from stolen refresh tokens

**Email-Based Account Linking:**
- Same email from Google and Apple must create single user record not duplicate accounts
- Provider ID uniqueness constraint prevents same provider account from creating multiple users
- Email verification status differs between providers, Google emails verified but Apple allows relay emails
- Account merge strategy needed when user logs in with different providers using same email

**CORS Configuration Complexity:**
- Must allow credentials true for cookies to work with OAuth callback flow
- Allowed origins must be specific not wildcard when credentials enabled for security
- Preflight OPTIONS requests need separate handling before authentication filters
- Different CORS rules may be needed for OAuth endpoints versus regular API endpoints

**State Parameter CSRF Protection:**
- OAuth state parameter must be cryptographically random and stored server-side or in session
- State verification prevents authorization code interception attacks during OAuth flow
- State must expire after short time window to limit replay attack surface
- Client-side state storage in sessionStorage vulnerable to XSS requires server validation

**Database Schema Design:**
- Composite unique constraint on provider and providerId prevents duplicate OAuth accounts
- Email uniqueness constraint enables account linking across providers for same email
- Nullable fields for provider-specific data like refresh tokens that may not be returned
- Timestamps for createdAt and updatedAt needed for audit trail and token expiry calculations

**Frontend OAuth Library Selection:**
- Google Sign-In library deprecated, must use @react-oauth/google newer package instead
- Apple Sign In button requires Apple JS SDK loaded via script tag not npm package
- OAuth popup flow requires handling popup blockers and user closing popup before completion
- Redirect flow simpler but loses application state unless preserved in URL parameters

**JWT Implementation Challenges:**
- JJWT library version compatibility with Java 17 and Spring Boot 4 requires specific versions
- JWT parsing exceptions need graceful handling returning 401 not 500 server errors
- Clock skew allowance needed for token validation across distributed systems
- Token payload size affects cookie storage limits when using httpOnly cookies

**Testing OAuth Flow:**
- OAuth providers require HTTPS for redirect URIs in production making local testing difficult
- Mocking OAuth provider responses for unit tests requires detailed knowledge of token structure
- Integration tests need test OAuth apps with separate credentials from production
- Manual testing requires real Google and Apple accounts with specific test email address