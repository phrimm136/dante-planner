# Task: Cloudflare DNS, Pages, and R2 Setup

## Description
Configure Cloudflare as the DNS provider, frontend hosting platform, and object storage for dante-planner.com. The architecture uses a subdomain split:
- `dante-planner.com` → Cloudflare Pages (React frontend)
- `api.dante-planner.com` → AWS EC2 (Spring Boot backend, proxied through Cloudflare)
- `uploads.dante-planner.com` → Cloudflare R2 (user-uploaded images)

Both domains use Cloudflare's proxy mode (orange cloud) for DDoS protection and SSL termination. The setup requires:
- Migrating DNS from Namecheap to Cloudflare nameservers
- Configuring Cloudflare Pages with Vite + Yarn build
- Setting up DNS records for frontend, API subdomain, and uploads subdomain
- Configuring SSL/TLS in Full (Strict) mode
- Setting up R2 bucket for image storage with custom domain
- Updating CORS configuration for cross-subdomain requests

## Research
- Cloudflare Pages build configuration for Vite + Yarn projects
- Origin CA certificate generation for EC2 backend
- CORS requirements for subdomain-based API calls with credentials
- Cloudflare IP ranges for EC2 security group allowlisting
- OAuth callback URL update requirements in Google Cloud Console
- R2 bucket creation and API token configuration
- R2 custom domain setup and CORS policy
- AWS S3 SDK compatibility with R2 endpoint

## Scope
Files/areas to READ for context:
- `docs/14-deploy/epic.md` - Overall deployment architecture
- `docs/learning/cloudflare-deployment-architecture.md` - Cloudflare concepts reference
- `backend/src/main/java/org/danteplanner/backend/config/CorsConfig.java` - Current CORS setup
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java` - Security headers
- `frontend/vite.config.ts` - Vite build configuration
- `frontend/package.json` - Build scripts

## Target Code Area
Files/folders that will be CREATED or MODIFIED:
- `frontend/.env.production` (create) - Production environment variables
- `backend/src/main/resources/application-prod.properties` (modify) - Add production CORS origin and R2 config
- `backend/pom.xml` (modify) - Add AWS S3 SDK dependency for R2
- `backend/src/main/java/org/danteplanner/backend/config/R2Config.java` (create) - R2 client configuration
- `backend/src/main/java/org/danteplanner/backend/service/StorageService.java` (create) - File upload service
- `.github/workflows/deploy.yml` (create in Phase 3) - CI/CD pipeline
- Cloudflare Dashboard configurations (external)

## System Context (Senior Thinking)
- Feature domain: Infrastructure / Deployment
- Core files in this domain:
  - Frontend: `vite.config.ts`, `lib/api.ts`, `lib/queryClient.ts`
  - Backend: `CorsConfig.java`, `SecurityConfig.java`, `application-*.properties`
- Cross-cutting concerns touched:
  - CORS (backend allows frontend origin)
  - Cookie security (SameSite=Lax works across subdomains)
  - SSL/TLS (Cloudflare manages edge certificates)
  - Rate limiting (X-Forwarded-For preserved through proxy)
  - File storage (R2 for user uploads)

## Impact Analysis
- Files being modified:
  - `application-prod.properties` (Medium) - Production config, affects all deployed behavior
  - `CorsConfig.java` (Medium) - Affects all API cross-origin requests
  - `frontend/.env.production` (Low) - Build-time environment only
  - `pom.xml` (Low) - Adding new dependency
- What depends on these files:
  - All API calls depend on CORS being correctly configured
  - Frontend API_BASE_URL affects every `fetch` call
- Potential ripple effects:
  - OAuth flow will break until Google Console is updated
  - Existing development setup unaffected (uses different origins)
- High-impact files to watch: `CorsConfig.java` (shared by all endpoints)

## Risk Assessment
- Edge cases not yet defined:
  - What happens if Cloudflare proxy is temporarily bypassed?
  - Cookie behavior if user accesses via IP instead of domain
  - R2 upload failures and retry logic
- Performance concerns:
  - Cloudflare adds ~10-50ms latency but provides caching benefits
  - First request after cache expiry is slower
  - R2 has zero egress fees but upload latency varies
- Backward compatibility:
  - Local development unchanged (localhost origins)
  - Docker development may need CORS update for local testing
- Security considerations:
  - EC2 must only accept traffic from Cloudflare IPs
  - Origin CA certificate required for Full (Strict) SSL
  - OAuth callback URL must match production domain exactly
  - R2 API tokens must be scoped to specific bucket
  - File upload validation (type, size) must be enforced

## Testing Guidelines

### Manual UI Testing
1. After DNS propagation, open browser to https://dante-planner.com
2. Verify the React frontend loads without errors
3. Open browser DevTools → Network tab
4. Navigate to a page that makes API calls (e.g., identity browser)
5. Verify API requests go to https://api.dante-planner.com
6. Verify no CORS errors in console
7. Check response headers include `cf-ray` (confirms Cloudflare proxy)
8. Attempt Google OAuth login flow
9. Verify redirect returns to https://dante-planner.com/auth/callback/google
10. Verify authentication cookie is set and works for subsequent requests
11. Navigate to different pages and verify all API calls succeed
12. Open https://dante-planner.com in incognito mode
13. Verify public pages load correctly without authentication
14. Test file upload feature (when implemented)
15. Verify uploaded file is accessible at https://uploads.dante-planner.com/[path]

### Automated Functional Verification
- [ ] DNS resolution: `dig dante-planner.com` returns Cloudflare IPs
- [ ] DNS resolution: `dig api.dante-planner.com` returns Cloudflare IPs (proxied)
- [ ] DNS resolution: `dig uploads.dante-planner.com` returns R2 bucket endpoint
- [ ] SSL certificate: Browser shows valid HTTPS lock icon
- [ ] CORS preflight: OPTIONS requests to API return correct headers
- [ ] Cookie domain: Auth cookie has correct domain attribute
- [ ] Cache headers: Static assets have long cache TTL
- [ ] Cache bypass: API responses are not cached
- [ ] R2 upload: Backend can upload test file to R2 bucket
- [ ] R2 access: Uploaded files are publicly accessible via custom domain

### Edge Cases
- [ ] www redirect: https://www.dante-planner.com redirects to https://dante-planner.com
- [ ] HTTP upgrade: http://dante-planner.com redirects to HTTPS
- [ ] API direct access: https://api.dante-planner.com/api/health returns 200
- [ ] Invalid subdomain: https://invalid.dante-planner.com returns Cloudflare error
- [ ] Rate limiting: Rapid requests still get rate limited (X-Forwarded-For preserved)
- [ ] R2 CORS: Frontend can fetch images from uploads subdomain
- [ ] R2 caching: Images are cached at Cloudflare edge (cf-cache-status: HIT)

### Integration Points
- [ ] Google OAuth: Callback URL works with new domain
- [ ] Sentry: Errors report correct source URLs
- [ ] Cloudflare Analytics: Traffic appears in dashboard
- [ ] Cloudflare Pages: Git push triggers new deployment
- [ ] R2: Backend uploads succeed with S3-compatible SDK

## Implementation Steps

### Step 1: Cloudflare Account Setup
1. Create account at https://dash.cloudflare.com/sign-up
2. Add site: dante-planner.com
3. Select Free plan
4. Note assigned nameservers (e.g., anna.ns.cloudflare.com, bob.ns.cloudflare.com)

### Step 2: Namecheap DNS Migration
1. Log into Namecheap → Domain List → dante-planner.com → Manage
2. Under Nameservers, change from "Namecheap BasicDNS" to "Custom DNS"
3. Enter Cloudflare nameservers
4. Save and wait for propagation (5 min - 48 hours)
5. Verify Cloudflare dashboard shows "Active" status

### Step 3: Cloudflare Pages Setup
1. Dashboard → Pages → Create a project
2. Connect to GitHub → Select LimbusPlanner repository
3. Configure build:
   - Framework preset: None
   - Build command: `cd frontend && yarn install --frozen-lockfile && yarn build`
   - Build output directory: `frontend/dist`
   - Root directory: `/`
4. Add environment variable: `VITE_API_BASE_URL=https://api.dante-planner.com`
5. Deploy

### Step 4: DNS Records Configuration
```
Type   Name      Content                          Proxy Status
CNAME  @         [project-name].pages.dev         Proxied (orange)
CNAME  www       dante-planner.com                Proxied (orange)
A      api       [EC2 Elastic IP - placeholder]   Proxied (orange)
CNAME  uploads   [bucket].r2.cloudflarestorage.com  Proxied (orange)
```

### Step 5: SSL/TLS Configuration
1. Dashboard → SSL/TLS → Overview
2. Set mode to "Full (Strict)"
3. Enable "Always Use HTTPS"
4. Set Minimum TLS Version to 1.2

### Step 6: R2 Bucket Setup
1. Dashboard → R2 → Create bucket
2. Bucket name: `dante-planner-uploads`
3. Location: Auto (or closest to your EC2 region)
4. Create bucket

### Step 7: R2 Custom Domain
1. R2 → dante-planner-uploads → Settings → Custom Domains
2. Add domain: `uploads.dante-planner.com`
3. Cloudflare automatically creates CNAME record
4. Wait for SSL certificate provisioning

### Step 8: R2 CORS Policy
1. R2 → dante-planner-uploads → Settings → CORS Policy
2. Add rule:
```json
[
  {
    "AllowedOrigins": ["https://dante-planner.com"],
    "AllowedMethods": ["GET", "PUT", "POST", "DELETE"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag", "Content-Length"],
    "MaxAgeSeconds": 3600
  }
]
```

### Step 9: R2 API Token
1. Dashboard → R2 → Manage R2 API Tokens
2. Create API Token:
   - Permission: Object Read & Write
   - Specify bucket: dante-planner-uploads
3. Save credentials:
   - Access Key ID
   - Secret Access Key
   - Endpoint URL (https://[account-id].r2.cloudflarestorage.com)

### Step 10: Code Changes
1. Create `frontend/.env.production`:
   ```
   VITE_API_BASE_URL=https://api.dante-planner.com
   VITE_UPLOADS_URL=https://uploads.dante-planner.com
   ```

2. Update `application-prod.properties`:
   ```properties
   cors.allowed-origins=https://dante-planner.com

   # R2 Configuration
   r2.endpoint=https://[account-id].r2.cloudflarestorage.com
   r2.bucket-name=dante-planner-uploads
   r2.access-key-id=${R2_ACCESS_KEY_ID}
   r2.secret-access-key=${R2_SECRET_ACCESS_KEY}
   r2.public-url=https://uploads.dante-planner.com
   ```

3. Add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>software.amazon.awssdk</groupId>
       <artifactId>s3</artifactId>
       <version>2.25.0</version>
   </dependency>
   ```

### Step 11: Google OAuth Update
1. Go to Google Cloud Console → APIs & Services → Credentials
2. Edit OAuth 2.0 Client ID
3. Add to Authorized redirect URIs:
   - `https://dante-planner.com/auth/callback/google`
4. Save

## Upload Specifications

| Aspect | Value |
|--------|-------|
| **File types** | Images only (JPEG, PNG, WebP, GIF) |
| **Max size** | 5MB |
| **Upload method** | Backend-proxied (not presigned URLs) |
| **Flow** | Browser → Backend → R2 |

**Validation requirements:**
- MIME type must be image/* (validate both Content-Type header and magic bytes)
- File size ≤ 5MB (reject before reading full body if possible)
- Generate unique filename (UUID + original extension)
- Return public URL after successful upload

## R2 Backend Integration (Future Implementation)

### R2Config.java
```java
@Configuration
public class R2Config {

    @Value("${r2.endpoint}")
    private String endpoint;

    @Value("${r2.access-key-id}")
    private String accessKeyId;

    @Value("${r2.secret-access-key}")
    private String secretAccessKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .region(Region.of("auto"))
            .build();
    }
}
```

### StorageService.java
```java
@Service
public class StorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    public String uploadImage(MultipartFile file) {
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Allowed: JPEG, PNG, WebP, GIF");
        }

        // Generate unique key
        String extension = getExtension(file.getOriginalFilename());
        String key = UUID.randomUUID() + extension;

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            return publicUrl + "/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }
}
```

## Dependencies
- Phase 1 (Docker) must be complete and validated
- Domain dante-planner.com must be registered and accessible
- Cloudflare account created
- GitHub repository accessible for Pages integration

## Cost Summary

| Service | Free Tier | Notes |
|---------|-----------|-------|
| Cloudflare DNS | Unlimited | Always free |
| Cloudflare Pages | 500 builds/month | Unlimited sites, bandwidth |
| Cloudflare R2 | 10GB storage, 1M Class A ops, 10M Class B ops | Zero egress fees |
| SSL Certificates | Unlimited | Auto-provisioned |

Estimated monthly cost for 10k MAU: **$0**

## Resolved Decisions

| Question | Decision |
|----------|----------|
| File types for R2 | Images only (JPEG, PNG, WebP, GIF) |
| Max file size | 5MB |
| Upload method | Backend-proxied (simpler, sufficient for scale) |
