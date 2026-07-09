# Implementation Plan: OAuth2 Login Support

## Task Overview

Implement full-stack OAuth2 authentication with Google and Apple Sign-In. Users click user icon in header to see dropdown with welcome message and login buttons. OAuth flow issues JWT tokens stored securely. User accounts saved to MySQL with email-based account linking so same email across providers creates single user record. Backend validates JWT on protected endpoints.

## Steps to Implementation

1. **Add OAuth Dependencies**: Install frontend libraries for Google and Apple OAuth, add Spring Security OAuth2 and JWT dependencies to backend Maven pom.xml.

2. **Create User Entity and Repository**: Build JPA entity with fields for email, name, OAuth provider, provider ID, and timestamps. Create Spring Data JPA repository interface for database operations with custom query for email lookup.

3. **Configure Spring Security**: Set up security configuration class enabling OAuth2 resource server, configuring CORS for frontend origin, disabling CSRF for stateless API, and marking public endpoints for OAuth callbacks.

4. **Implement JWT Utilities**: Build service for generating access tokens and refresh tokens with configurable expiry. Create filter for validating JWT on incoming requests and extracting user info into security context.

5. **Build Backend OAuth Controllers**: Create REST endpoints for Google and Apple OAuth callbacks receiving authorization codes, exchanging for provider tokens, fetching user profile, and issuing application JWT tokens. Include account linking logic for matching emails.

6. **Create Frontend Auth Context**: Build React context managing authentication state with user object and JWT token. Provide login, logout functions and token refresh logic. Persist tokens to localStorage or httpOnly cookies based on security requirements.

7. **Build Login UI Components**: Create dropdown menu under user icon showing welcome guest message for unauthenticated users with Google and Apple login buttons. Style buttons with provider branding and handle OAuth popup or redirect flow initiation.

8. **Integrate OAuth SDKs**: Configure Google OAuth provider with client ID and redirect URI. Set up Apple Sign In button with service ID and callback URL. Handle OAuth responses extracting authorization code and sending to backend.

9. **Add Protected Routes**: Configure frontend router to check authentication state before rendering protected pages. Redirect unauthenticated users to home with login prompt. Add API interceptor attaching JWT to outgoing requests.

10. **Test OAuth Flows**: Manually test Google login with test account ensuring user created in database. Test Apple login with same email verifying account linking creates single user record. Verify JWT validation on protected endpoints and token refresh mechanism.

## Timeline

| Step | Time | Cumulative |
|------|------|------------|
| 1    | 15min | 15min     |
| 2    | 20min | 35min     |
| 3    | 25min | 60min     |
| 4    | 30min | 90min     |
| 5    | 40min | 130min    |
| 6    | 20min | 150min    |
| 7    | 25min | 175min    |
| 8    | 30min | 205min    |
| 9    | 20min | 225min    |
| 10   | 35min | 260min    |
| Total| 260min (4h 20min) |  |

## Success Criteria

- Clicking user icon displays dropdown with welcome message and Google plus Apple login buttons styled appropriately
- Google OAuth flow completes successfully creating user record in MySQL database with email phrimm136@gmail.com
- Apple OAuth flow with same email links to existing Google account resulting in single user record not duplicate
- Backend validates JWT tokens on protected endpoints returning 401 for invalid or expired tokens
- Access token and refresh token issued with appropriate expiry times stored securely on client side
- User remains authenticated across page refreshes and browser sessions until token expires or logout
- CORS configured properly allowing frontend to call backend OAuth endpoints without preflight errors
- JWT payload contains user ID and email enabling backend to identify authenticated user on each request
