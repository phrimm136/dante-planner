# TODO

## Legal Documents Review

**Status:** Adequate for a hobby/fan project, but not legally robust.

### Current Assessment

| Aspect | Assessment |
|--------|------------|
| Completeness | Covers basics (data collection, user conduct, disclaimers) |
| Legal enforceability | Weak - no jurisdiction clause, no dispute resolution |
| GDPR compliance | Partial - missing data retention period, DPO contact, right to portability |
| CCPA compliance | Missing - no "Do Not Sell" mention, no CA-specific rights |
| Cookie policy | Too vague - should list specific cookies used |

### Missing Elements

1. **Age restriction** - No minimum age requirement (COPPA requires 13+)
2. **Jurisdiction** - Which country's law applies?
3. **Liability cap** - No limitation of liability amount
4. **Indemnification** - Users should indemnify you for their content
5. **Data retention** - How long do you keep data after deletion?
6. **Third-party services** - Should list Google OAuth, Cloudflare, hosting provider

### Recommended Additions

- Age requirement: "You must be 13+ to use this service"
- Jurisdiction: "These terms are governed by the laws of [your country]"
- Data retention: "Data is deleted within 30 days of account deletion request"

### When to Prioritize

For a free fan project with no monetization, current state is acceptable. Revisit if:
- Monetizing (ads, donations, premium features)
- Handling EU users seriously
- Scaling to significant traffic

Consider using a legal template generator (Termly, iubenda) or consulting a lawyer.

## Code Quality Integration

### SonarQube Cloud Setup

**Goal:** Integrate SonarQube Cloud with GitHub for automated PR analysis and quality gates.

**Current State:**
- ✅ Local SonarQube 9.9.8 LTS running (Docker)
- ✅ OWASP Dependency-Check 12.2.0 configured
- ✅ JaCoCo code coverage configured
- ✅ Maven integration working
- ❌ Not integrated with GitHub PRs

**Setup Steps:**

1. **Sign up for SonarQube Cloud**
   - URL: https://sonarcloud.io
   - Login with GitHub account
   - Free for public repositories

2. **Import Repository**
   - Connect LimbusPlanner repository
   - Generate project token
   - Note organization key

3. **Configure GitHub Actions**
   - Create `.github/workflows/code-quality.yml`
   - Add SonarQube Cloud token to GitHub Secrets
   - Configure workflow to run on push/PR

4. **Setup Quality Gates**
   - Configure passing thresholds
   - Enable PR decoration
   - Block merges on quality gate failures

5. **Dependency-Check Strategy**

   **Option A: Scheduled Scans (Recommended)**
   - Run nightly via GitHub Actions scheduled workflow
   - Avoids slowing down PR checks (first run: 10-20 min)
   - NVD database cached between runs
   - Results uploaded to GitHub Security tab

   **Option B: On Every PR (Slow)**
   - Adds 10-20 minutes to first PR build
   - Requires NVD cache setup in Actions
   - Better for high-security projects

   **Option C: Local Only**
   - Run manually before releases
   - Fastest CI/CD, but misses early detection

   **Recommendation:** Option A (nightly) balances security and speed

**Benefits:**
- Automatic code quality checks on every PR
- Quality gate status visible in GitHub
- Team-wide visibility of code metrics
- Historical quality trends
- Security vulnerability tracking

**Priority:** Medium - Local setup works, but cloud integration enables team collaboration and automated PR checks.
