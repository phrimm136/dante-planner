# Maven Dependency Management: Spring Boot and Version Control

**Context:** Fixed CVE-2025-68161 by upgrading log4j 2.24.3 → 2.25.3 using `dependencyManagement`

---

## The Problem: Transitive Dependencies

When you add Spring Boot starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

You get **hundreds of transitive dependencies** you never declared:

```
spring-boot-starter-data-jpa
  └── spring-boot-starter
      └── spring-boot-starter-logging
          └── log4j-to-slf4j
              └── log4j-api (version controlled by Spring Boot)
```

**Question:** How do you upgrade `log4j-api` when you never declared it?

---

## Spring Boot's Dependency Control Mechanism

### 1. The Parent POM

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.10</version>
</parent>
```

**What this does:**
- Inherits `dependencyManagement` from Spring Boot
- Spring Boot 3.5.10 declares: "log4j-api = 2.24.3"
- All starters use this version automatically

### 2. Spring Boot's Version Strategy

Spring Boot's parent POM contains:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-api</artifactId>
      <version>2.24.3</version>
    </dependency>
    <!-- 300+ other managed dependencies -->
  </dependencies>
</dependencyManagement>
```

**Result:** You never specify versions for Spring-managed dependencies - Spring Boot ensures compatibility.

---

## What is dependencyManagement?

**Purpose:** Centralize version control WITHOUT declaring usage.

### Key Differences:

| `<dependencies>` | `<dependencyManagement>` |
|------------------|--------------------------|
| "I use this library" | "When anyone uses this library, use THIS version" |
| Added to classpath | NOT added to classpath |
| Direct usage | Version control only |
| Required by your code | Optional - only affects IF used |

### Example:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- This gets jackson-databind 2.15.0 (version from above) -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <!-- transitively pulls jackson-databind -->
  </dependency>
</dependencies>
```

**Without dependencyManagement:** Spring Boot decides jackson version
**With dependencyManagement:** YOU decide jackson version

---

## Maven Dependency Resolution Rules

**Priority order (highest to lowest):**

1. **Direct dependency with version** in your `<dependencies>`
2. **dependencyManagement** in your POM
3. **dependencyManagement** in parent POM (Spring Boot)
4. **Nearest in dependency tree** (shortest path wins)
5. **First declaration** if same distance

### Example:

```
Your POM:
  spring-boot-starter-web (no version) → brings log4j-api 2.24.3

Your dependencyManagement:
  log4j-api = 2.25.3

Result: 2.25.3 wins (rule #2 beats rule #3)
```

---

## The BOM Pattern (Bill of Materials)

**Problem:** Libraries like log4j have multiple artifacts that must use the same version:
- log4j-api
- log4j-core
- log4j-to-slf4j
- log4j-slf4j-impl

**Declaring each manually:**
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-api</artifactId>
      <version>2.25.3</version>
    </dependency>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-core</artifactId>
      <version>2.25.3</version>
    </dependency>
    <!-- Repeat for all log4j artifacts... -->
  </dependencies>
</dependencyManagement>
```

**Using BOM (better):**
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-bom</artifactId>
      <version>2.25.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**What `scope=import` does:**
- Imports the log4j-bom's `dependencyManagement` section into yours
- log4j-bom declares versions for ALL log4j artifacts
- You get version consistency with one declaration

**Analogy:** BOM is like importing a configuration file - you get all the version settings at once.

---

## Why Spring Boot Uses This Pattern

### Spring Boot's Parent POM Strategy:

```xml
<dependencyManagement>
  <dependencies>
    <!-- Import multiple BOMs -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>3.5.10</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

The `spring-boot-dependencies` BOM contains 300+ curated, tested dependency versions:
- Jackson 2.18.3
- log4j 2.24.3
- Hibernate 6.6.5
- PostgreSQL driver 42.7.4
- etc.

**Benefit:** Spring Boot team tests these versions together - you get a known-good dependency set.

---

## When to Override Spring Boot Versions

### Scenario 1: Security Vulnerabilities (Your Case)

Spring Boot 3.5.10 ships log4j 2.24.3, but CVE-2025-68161 requires 2.25.3.

**Options:**

**A. Wait for Spring Boot 3.5.11** (includes log4j 2.25.3)
- Pro: Tested by Spring team
- Con: Security window until release

**B. Override immediately** (what we did)
- Pro: Immediate security fix
- Con: Version not tested with Spring Boot 3.5.10

**Decision:** Security wins - override immediately.

### Scenario 2: Feature Requirements

You need a feature only in newer library version:

```xml
<dependencyManagement>
  <dependencies>
    <!-- Need PostgreSQL COPY command support added in 42.8.0 -->
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>42.8.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Scenario 3: Bug Fixes

Spring Boot includes library version with known bug, but patch release available.

---

## How This Solved CVE-2025-68161

### Before (Vulnerable):

```
pom.xml:
  <parent>spring-boot-starter-parent 3.5.10</parent>

Dependency chain:
  spring-boot-starter-web
    → spring-boot-starter-logging
      → log4j-to-slf4j (version from Spring Boot BOM)
        → log4j-api 2.24.3 ❌ (vulnerable)
```

### After (Fixed):

```
pom.xml:
  <parent>spring-boot-starter-parent 3.5.10</parent>

  <dependencyManagement>
    <dependency>log4j-bom 2.25.3</dependency>
  </dependencyManagement>

Dependency chain:
  spring-boot-starter-web
    → spring-boot-starter-logging
      → log4j-to-slf4j (version from YOUR dependencyManagement)
        → log4j-api 2.25.3 ✓ (patched)
```

**Maven resolution:**
1. Sees transitive dependency on `log4j-api`
2. Checks your `dependencyManagement` first → finds 2.25.3
3. Uses 2.25.3 instead of Spring Boot's 2.24.3

---

## Best Practices

### 1. Minimal Overrides
Only override when necessary:
- Security vulnerabilities
- Critical bug fixes
- Required features

**Why:** Spring Boot tests dependency combinations - random upgrades can cause compatibility issues.

### 2. Use BOMs When Available
```xml
<!-- Prefer this -->
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-bom</artifactId>
  <version>2.25.3</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>

<!-- Over this -->
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-api</artifactId>
  <version>2.25.3</version>
</dependency>
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.25.3</version>
</dependency>
<!-- More repetition... -->
```

### 3. Document Why
```xml
<!-- CVE-2025-68161: TLS hostname verification flaw -->
<!-- Fixed in 2.25.3, remove override when Spring Boot updates -->
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-bom</artifactId>
  <version>2.25.3</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

### 4. Monitor Spring Boot Updates
When Spring Boot 3.5.11+ includes log4j 2.25.3+, remove your override:
- Spring Boot's tested version is safer
- Reduces maintenance burden

---

## Common Mistakes

### Mistake 1: Adding Direct Dependency When You Don't Use It

```xml
<!-- BAD: You don't use log4j directly, Spring Boot does -->
<dependencies>
  <dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>2.25.3</version>
  </dependency>
</dependencies>
```

**Problem:** Confuses future maintainers - "Why do we depend on log4j-api? We use SLF4J!"

**Better:**
```xml
<dependencyManagement>
  <dependencies>
    <!-- Override transitive log4j version (we don't use directly) -->
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-bom</artifactId>
      <version>2.25.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Mistake 2: Overriding Without Testing

Upgrading dependencies can break compatibility:
- API changes between versions
- Different behavior
- New bugs

**Always test after overriding:**
```bash
mvn clean verify  # Run all tests
```

### Mistake 3: Mixing Versions

```xml
<!-- BAD: Inconsistent log4j versions -->
<dependencies>
  <dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>2.25.3</version>
  </dependency>
  <dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.24.0</version>  <!-- Different version! -->
  </dependency>
</dependencies>
```

**Solution:** Use BOM to keep versions aligned.

---

## Advanced: Multi-Module Projects

Parent POM can define `dependencyManagement` for all child modules:

```xml
<!-- parent/pom.xml -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.company</groupId>
      <artifactId>shared-library</artifactId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- module-a/pom.xml -->
<dependencies>
  <dependency>
    <groupId>com.company</groupId>
    <artifactId>shared-library</artifactId>
    <!-- Version inherited from parent's dependencyManagement -->
  </dependency>
</dependencies>

<!-- module-b/pom.xml -->
<dependencies>
  <dependency>
    <groupId>com.company</groupId>
    <artifactId>shared-library</artifactId>
    <!-- Same version automatically -->
  </dependency>
</dependencies>
```

**Benefit:** Change version once in parent, all modules inherit it.

---

## Verification Commands

### Check Effective Versions

```bash
# See all log4j versions in your project
mvn dependency:tree | grep log4j

# See where a dependency comes from
mvn dependency:tree -Dverbose | grep -A 5 log4j-api

# Check for version conflicts
mvn dependency:tree -Dverbose | grep "omitted for conflict"

# See effective POM (with all inheritance resolved)
mvn help:effective-pom | grep -A 10 log4j
```

### Analyze Dependencies

```bash
# Find all managed dependencies
mvn dependency:list

# Check for updates
mvn versions:display-dependency-updates

# Check for vulnerabilities
mvn dependency-check:check
```

---

## Real-World Scenario: CVE Response Flow

### Timeline

**Day 0:** CVE-2025-68161 disclosed (log4j ≤ 2.25.2 vulnerable)

**Day 1:** Security team scans dependencies
```bash
mvn dependency-check:check
# Finds: log4j-api 2.24.3 (CRITICAL)
```

**Day 1 (2 hours later):** Emergency patch
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-bom</artifactId>
      <version>2.25.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**Day 1 (4 hours later):** Deploy to production
```bash
mvn clean verify                    # All tests pass
mvn dependency:tree | grep log4j    # Verify 2.25.3
git commit -m "fix: upgrade log4j to 2.25.3 (CVE-2025-68161)"
# Deploy
```

**Week 2:** Spring Boot 3.5.11 released with log4j 2.25.3
```bash
# Remove override, use Spring Boot's tested version
# Delete dependencyManagement override
# Upgrade Spring Boot 3.5.10 → 3.5.11
```

---

## The Intent of dependencyManagement

### Design Goals

1. **Separation of Concerns**
   - Version control ≠ Dependency usage
   - Parent POMs control versions
   - Child modules declare usage (no versions)

2. **Consistency**
   - Entire organization uses same library versions
   - No "works on my machine" due to version mismatch

3. **Override Capability**
   - Child can override parent's version when needed
   - Surgical fixes for security/bugs

4. **Transitive Dependency Control**
   - Control versions you don't directly use
   - Fix vulnerabilities deep in dependency tree

### When Spring Boot Developers Use It

**Spring Boot's parent POM is essentially:**

```xml
<dependencyManagement>
  <dependencies>
    <!-- Import 10+ BOMs -->
    <dependency><artifactId>spring-framework-bom</artifactId></dependency>
    <dependency><artifactId>jackson-bom</artifactId></dependency>
    <dependency><artifactId>junit-bom</artifactId></dependency>
    <dependency><artifactId>netty-bom</artifactId></dependency>

    <!-- Plus 200+ individual library versions -->
    <dependency><artifactId>postgresql</artifactId><version>42.7.4</version></dependency>
    <dependency><artifactId>hibernate-core</artifactId><version>6.6.5</version></dependency>
    <!-- ... -->
  </dependencies>
</dependencyManagement>
```

**Result:** You add `spring-boot-starter-*` without versions - Spring Boot coordinates everything.

---

## Key Takeaways

1. **dependencyManagement = version control without usage declaration**
2. **Your dependencyManagement overrides parent's (Spring Boot's)**
3. **BOM imports multiple artifact versions at once**
4. **Use for security fixes, don't abuse for random upgrades**
5. **Test thoroughly after overriding Spring Boot versions**

---

## Production Pattern

```xml
<project>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.10</version>
  </parent>

  <dependencyManagement>
    <dependencies>
      <!-- Only override when necessary -->
      <!-- Security fixes, critical bugs, required features -->
      <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-bom</artifactId>
        <version>2.25.3</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Declare what you use, not versions -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
      <!-- No version - managed by parent -->
    </dependency>
  </dependencies>
</project>
```

**Philosophy:** Trust Spring Boot's curation, override only when proven necessary.

---

## References

- **CVE Details:** [CVE-2025-68161 - NVD](https://nvd.nist.gov/vuln/detail/CVE-2025-68161)
- **Fixed In:** Apache Log4j 2.25.3
- **Severity:** CRITICAL (TLS hostname verification bypass)
- **Mitigation:** Upgrade to log4j-bom 2.25.3 via dependencyManagement

**Sources:**
- [NVD - CVE-2025-68161](https://nvd.nist.gov/vuln/detail/CVE-2025-68161)
- [Apache Log4j Security Advisory](https://logging.apache.org/security.html)
- [CVE-2025-68161 Details](https://www.cvedetails.com/cve/CVE-2025-68161/)
