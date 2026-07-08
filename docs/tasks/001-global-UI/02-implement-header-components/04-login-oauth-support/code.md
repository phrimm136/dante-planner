# Implementation Code Documentation: OAuth2 Login Support

## What Was Done

- Added OAuth2 resource server, JWT libraries, and validation dependencies to backend Maven configuration
- Created User entity with provider-based authentication (Google/Apple separate accounts, no email linking)
- Implemented JWT service with 15-minute access tokens and 7-day refresh tokens using JJWT library
- Built Spring Security configuration with stateless sessions, CORS support, and public OAuth endpoints
- Developed Google OAuth flow with authorization code exchange and user profile fetching via RestTemplate
- Created React AuthContext managing authentication state with localStorage token persistence
- Modified Header component to display login dropdown with Google/Apple options and user info when authenticated
- Integrated Google OAuth SDK with authorization code flow and callback route handling
- Added API client with automatic JWT token attachment and 401 unauthorized response handling
- Configured environment variables for JWT secret, OAuth credentials, and CORS allowed origins

## Files Changed

### Backend
- [backend/pom.xml](../../../../backend/pom.xml)
- [backend/src/main/resources/application.properties](../../../../backend/src/main/resources/application.properties)
- [backend/.env](../../../../backend/.env)
- [backend/src/main/java/org/danteplanner/backend/entity/User.java](../../../../backend/src/main/java/org/danteplanner/backend/entity/User.java)
- [backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java](../../../../backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java)
- [backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java](../../../../backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java)
- [backend/src/main/java/org/danteplanner/backend/config/CorsConfig.java](../../../../backend/src/main/java/org/danteplanner/backend/config/CorsConfig.java)
- [backend/src/main/java/org/danteplanner/backend/service/JwtService.java](../../../../backend/src/main/java/org/danteplanner/backend/service/JwtService.java)
- [backend/src/main/java/org/danteplanner/backend/service/GoogleOAuthService.java](../../../../backend/src/main/java/org/danteplanner/backend/service/GoogleOAuthService.java)
- [backend/src/main/java/org/danteplanner/backend/service/UserService.java](../../../../backend/src/main/java/org/danteplanner/backend/service/UserService.java)
- [backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java](../../../../backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java)
- [backend/src/main/java/org/danteplanner/backend/controller/AuthController.java](../../../../backend/src/main/java/org/danteplanner/backend/controller/AuthController.java)
- [backend/src/main/java/org/danteplanner/backend/dto/OAuthCallbackRequest.java](../../../../backend/src/main/java/org/danteplanner/backend/dto/OAuthCallbackRequest.java)
- [backend/src/main/java/org/danteplanner/backend/dto/LoginResponse.java](../../../../backend/src/main/java/org/danteplanner/backend/dto/LoginResponse.java)
- [backend/src/main/java/org/danteplanner/backend/dto/UserDto.java](../../../../backend/src/main/java/org/danteplanner/backend/dto/UserDto.java)

### Frontend
- [frontend/.env](../../../../frontend/.env)
- [frontend/package.json](../../../../frontend/package.json)
- [frontend/src/main.tsx](../../../../frontend/src/main.tsx)
- [frontend/src/types/auth.ts](../../../../frontend/src/types/auth.ts)
- [frontend/src/contexts/AuthContext.tsx](../../../../frontend/src/contexts/AuthContext.tsx)
- [frontend/src/hooks/useAuth.ts](../../../../frontend/src/hooks/useAuth.ts)
- [frontend/src/lib/api.ts](../../../../frontend/src/lib/api.ts)
- [frontend/src/lib/router.tsx](../../../../frontend/src/lib/router.tsx)
- [frontend/src/services/auth.ts](../../../../frontend/src/services/auth.ts)
- [frontend/src/components/Header.tsx](../../../../frontend/src/components/Header.tsx)
- [frontend/src/routes/auth/callback/google.tsx](../../../../frontend/src/routes/auth/callback/google.tsx)
- [static/i18n/EN/common.json](../../../../static/i18n/EN/common.json)

## What Was Skipped

- Apple Sign-In implementation marked as not yet implemented with alert placeholder
- Email-based account linking feature removed after architecture discussion to prevent same-email collision issues
- Manual OAuth flow testing skipped due to Maven not being installed in development environment
- Account merge strategy and refresh token rotation deferred as non-MVP features
- Integration tests and OAuth mock setup deferred to future implementation phase

## Testing Results

- TypeScript compilation passed with no errors in auth-related code
- Frontend build encountered unrelated errors in test utilities and i18n module resolution (OAuth code compiles successfully)
- Backend Maven build: **BUILD SUCCESS** with all 13 source files compiled successfully
- Removed duplicate spring-boot-starter-data-jpa dependency warning
- Manual OAuth flow testing requires running both backend Spring Boot server and MySQL database
- Core OAuth implementation code successfully compiles and follows established patterns

## Issues & Resolutions

- CSRF protection disabled for JWT Bearer token authentication but documented with security comment explaining when to enable
- Changed from email-based account linking to provider-based separate accounts to avoid same-email collision vulnerabilities
- Fixed TypeScript verbatimModuleSyntax errors by converting type imports to use type-only import syntax
- Updated Header component to save current page path in sessionStorage for post-login redirect to original location
- Resolved HeadersInit type error by changing to Record<string, string> for dynamic Authorization header assignment
