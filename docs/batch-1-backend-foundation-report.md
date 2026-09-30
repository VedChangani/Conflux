# Batch 1 — Backend Foundation Report

**Scope:** backend foundation only. No authentication, users, listings, domain entities, migrations, or business logic were implemented.
**Date:** 2026-09-30
**Location:** `C:\Conflux\backend`

Legend: **[Verified]** = implemented and exercised by the passing test suite or build. **[Implemented]** = present in code/config but not exercised at runtime in this batch. **[Recommended]** = future work only; not implemented.

---

## 1. What was inspected

The unmodified Spring Initializr output:

| Item | State before Batch 1 |
|---|---|
| `pom.xml` | Parent `spring-boot-starter-parent:4.1.1`, groupId `in.vedchangani`, artifactId `backend`, `java.version=21`. Starters: `webmvc`, `data-jpa`, `security`, `validation`, `devtools` (runtime, optional), `mysql-connector-j` (runtime). Test starters: `webmvc-test`, `data-jpa-test`, `security-test`, `validation-test`. |
| `src/main/java/in/vedchangani/backend/BackendApplication.java` | Standard `@SpringBootApplication` main class |
| `src/test/java/in/vedchangani/backend/BackendApplicationTests.java` | Empty `@SpringBootTest` `contextLoads()` (would have required a reachable DataSource) |
| `src/main/resources/application.properties` | Only `spring.application.name=backend` |
| `.gitignore`, `.gitattributes`, `HELP.md`, Maven wrapper (Maven 3.9.16, wrapper 3.3.4) | Initializr defaults |
| Environment | JDK 21.0.11. `C:\Conflux` is **not** a git repository. `.idea/` exists at `C:\Conflux`. |

Spring Boot 4 splits test auto-configuration into per-technology modules. I confirmed from the resolved jar that `@WebMvcTest` now lives at `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (artifact `spring-boot-webmvc-test:4.1.1`). The test code uses that package.

## 2. What was changed

### Modified
- `backend/pom.xml`
  - groupId `in.vedchangani` → `com.conflux`. artifactId `backend`, version, and `<name/>` are unchanged.
  - Added `com.h2database:h2` with `<scope>test</scope>`. The version comes from the Boot BOM and resolved to 2.4.240.
  - No other dependency or build setting was changed.
- `backend/src/main/resources/application.properties`: rewritten (see §4).
- `backend/.gitignore`: appended `.env`.

### Deleted
- `src/main/java/in/vedchangani/backend/BackendApplication.java` and its package directories
- `src/test/java/in/vedchangani/backend/BackendApplicationTests.java` and its package directories

### Created

| File | Purpose |
|---|---|
| `src/main/java/com/conflux/ConfluxApplication.java` | Main class, replacing `BackendApplication` |
| `src/main/java/com/conflux/{config,common,auth,user,listing,connection,message,saved,report,admin}/package-info.java` | One-line Javadoc marking each module boundary. These are not classes. |
| `src/main/java/com/conflux/common/web/ApiPaths.java` | `API_V1 = "/api/v1"` constant |
| `src/main/java/com/conflux/common/web/HealthController.java` | `GET /api/v1/health` with a nested `HealthResponse` record |
| `src/main/java/com/conflux/common/web/GlobalExceptionHandler.java` | ProblemDetail-based `@RestControllerAdvice` |
| `src/main/java/com/conflux/config/CorsProperties.java` | `@ConfigurationProperties("conflux.cors")` record |
| `src/main/java/com/conflux/config/CorsConfig.java` | `CorsConfigurationSource` bean for `/api/**` |
| `src/main/java/com/conflux/config/SecurityConfig.java` | Stateless `SecurityFilterChain` |
| `src/main/resources/application-dev.properties` | Local development profile |
| `src/main/resources/application-prod.properties` | Production profile |
| `src/test/resources/application-test.properties` | H2 test profile |
| `src/test/java/com/conflux/ConfluxApplicationTests.java` | Context-load test |
| `src/test/java/com/conflux/common/web/HealthControllerTest.java` | Health, security, and CORS web-slice tests |
| `src/test/java/com/conflux/common/web/GlobalExceptionHandlerTest.java` | Exception handler test |
| `backend/.env.example` | Template listing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS` |
| `docs/batch-1-backend-foundation-report.md` | This report |

## 3. Final package structure

```
com.conflux
├── ConfluxApplication
├── config
│   ├── package-info
│   ├── CorsConfig
│   ├── CorsProperties
│   └── SecurityConfig
├── common
│   ├── package-info
│   └── web
│       ├── ApiPaths
│       ├── GlobalExceptionHandler
│       └── HealthController (+ nested record HealthResponse)
├── auth        (package-info only)
├── user        (package-info only)
├── listing     (package-info only)
├── connection  (package-info only)
├── message     (package-info only)
├── saved       (package-info only)
├── report      (package-info only)
└── admin       (package-info only)
```

`ConfluxApplication` sits at the root package, so component scanning covers every feature package. No entities, repositories, services, or DTOs exist.

## 4. Configuration decisions

### Profiles
| File | Active when | Contents |
|---|---|---|
| `application.properties` | always | `spring.application.name=conflux-backend`, `spring.profiles.default=dev`, `server.port=${PORT:8080}`, `spring.datasource.url=${DB_URL}`, `spring.datasource.username=${DB_USERNAME}`, `spring.datasource.password=${DB_PASSWORD}`, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=none` |
| `application-dev.properties` | no profile set (default), or `dev` | `spring.config.import=optional:file:.env[.properties]`, `spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/conflux}`, `conflux.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}`, `logging.level.com.conflux=DEBUG` |
| `application-prod.properties` | `SPRING_PROFILES_ACTIVE=prod` | `conflux.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}`, with no fallback |
| `application-test.properties` (test classpath) | `@ActiveProfiles("test")` | H2 `jdbc:h2:mem:conflux;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`, user `sa`, empty password, driver `org.h2.Driver`, CORS origin `http://localhost:5173` |

### Rationale
- **No credentials in source.** Username and password come only from environment variables, or from a local `.env` in dev. There is no default for either.
- **`spring.profiles.default=dev` instead of `spring.profiles.active=dev`.** Dev applies only when nothing else is selected, so setting `SPRING_PROFILES_ACTIVE=prod` fully replaces it. Tests select `test` explicitly and never load the dev profile or `.env`.
- **Prod fails fast.** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `CORS_ALLOWED_ORIGINS` have no production defaults, so a missing variable is an unresolvable placeholder at startup rather than a silently wrong value. *[Implemented, not exercised: the prod profile was not started in this batch.]*
- **`.env` loading uses Spring Boot's own `spring.config.import`** with the `[.properties]` format hint. No dotenv library was added. OS environment variables take precedence over values in `.env`.
- **`open-in-view=false`** keeps JPA sessions out of the web layer and removes Boot's startup warning.
- **`ddl-auto=none`** leaves the schema alone until a strategy is chosen (see §10).
- **CORS origins** are bound to `List<String>`, so `CORS_ALLOWED_ORIGINS` accepts a comma-separated list.

### Security (`SecurityConfig`) [Verified]
- CORS via `http.cors(Customizer.withDefaults())`, which uses the `CorsConfigurationSource` bean
- CSRF disabled; session policy `STATELESS`
- `httpBasic`, `formLogin`, and `logout` disabled
- Unauthenticated requests get **401** via `HttpStatusEntryPoint(UNAUTHORIZED)`, not a login redirect
- `GET /api/v1/health` and `/error` are `permitAll`; everything else is `authenticated()`
- No authentication mechanism exists, so every non-public endpoint returns 401.

### CORS (`CorsConfig`) [Verified]
- Registered for `/api/**` only
- Origins from `conflux.cors.allowed-origins`
- Methods GET, POST, PUT, PATCH, DELETE, OPTIONS
- All request headers allowed (`*`)
- `maxAge` 3600 s
- `allowCredentials` **not** enabled

### Error handling (`GlobalExceptionHandler`) [Partially verified]
- `@RestControllerAdvice` extending Spring's `ResponseEntityExceptionHandler`. It returns RFC 9457 `ProblemDetail` (`application/problem+json`) for standard MVC exceptions, including:
  - `MethodArgumentNotValidException` / `HandlerMethodValidationException` (400)
  - `HttpMessageNotReadableException` for malformed JSON (400)
  - `HttpRequestMethodNotSupportedException` (405)
  - `NoResourceFoundException` / `NoHandlerFoundException` (404)
  - `HttpMediaTypeNotSupportedException` (415)
  - `ErrorResponseException` / `ResponseStatusException`
- One added handler, `@ExceptionHandler(Exception.class)`, logs the exception at ERROR on the server and returns a 500 `ProblemDetail` with the fixed detail `"An unexpected error occurred."`. The exception message and stack trace are not included.
- `spring.mvc.problemdetails.enabled` is **not** set. Boot's own ProblemDetail handler backs off when a `ResponseEntityExceptionHandler` bean exists, so setting it would be redundant.
- *Verified by test:* the generic 500 path. *Not individually tested:* the inherited 400/404/405 mappings. Those come from Spring Framework itself.
- Note: in the running app, requests to unknown paths under `/api/v1` are stopped by Spring Security (401) before MVC's 404 handling, because no authentication exists yet.

## 5. Endpoints introduced

| Method | Path | Auth | Response |
|---|---|---|---|
| GET | `/api/v1/health` | public | `200 OK`, `{"status":"UP"}` |

It is a liveness check only: it does not touch the database, and Actuator was not added. **[Verified]** in the MockMvc web slice. It was not called against a running server; see §8.

## 6. Dependencies actually present after changes

Direct dependencies in `pom.xml`. The only one added is **H2 (test)**.

| Dependency | Scope | Used in Batch 1 by |
|---|---|---|
| `spring-boot-starter-webmvc` | compile | `HealthController`, `GlobalExceptionHandler`, CORS |
| `spring-boot-starter-security` | compile | `SecurityConfig` |
| `spring-boot-starter-data-jpa` | compile | DataSource / EntityManagerFactory auto-configured; no entities yet |
| `spring-boot-starter-validation` | compile | Not used directly yet. Validation errors are covered by the exception handler base class. |
| `spring-boot-devtools` | runtime, optional | Dev convenience only |
| `mysql-connector-j` | runtime | Runtime database driver (resolved 9.7.0) |
| `com.h2database:h2` **(new)** | test | In-memory DB for the context test (resolved 2.4.240) |
| `spring-boot-starter-webmvc-test` | test | `@WebMvcTest`, MockMvc |
| `spring-boot-starter-security-test` | test | On classpath; no security-test utilities used yet |
| `spring-boot-starter-data-jpa-test` | test | On classpath; not used yet |
| `spring-boot-starter-validation-test` | test | On classpath; not used yet |

Key resolved versions:
- Spring Framework 7.0.9
- Spring Security 7.1.1
- Spring Data JPA 4.1.1
- Hibernate ORM 7.4.5.Final
- Hibernate Validator 9.1.3.Final
- Jackson 3.1.5 (`tools.jackson`)
- Tomcat 11.0.24

## 7. Tests added

| Test class | Type | Test | Checks |
|---|---|---|---|
| `ConfluxApplicationTests` | `@SpringBootTest` + `@ActiveProfiles("test")` | `contextLoads` | The full context starts with H2. The log confirms `jdbc:h2:mem:conflux`, H2Dialect, and the `test` profile. |
| `HealthControllerTest` | `@WebMvcTest(HealthController.class)` + `@Import({SecurityConfig, CorsConfig})` + `@ActiveProfiles("test")` | `healthReturnsUpWithoutCredentials` | 200 and `$.status == "UP"` with no credentials |
| | | `unknownApiEndpointIsNotPublic` | `GET /api/v1/does-not-exist` returns 401 |
| | | `corsPreflightFromReactDevServerIsAccepted` | `OPTIONS` with `Origin: http://localhost:5173` returns 200 and `Access-Control-Allow-Origin: http://localhost:5173` |
| | | `corsPreflightFromUnapprovedOriginIsRejected` | `OPTIONS` with `Origin: http://evil.example.com` returns 403 with no `Access-Control-Allow-Origin` |
| `GlobalExceptionHandlerTest` | Plain JUnit, `MockMvcBuilders.standaloneSetup` with a test-only controller | `unexpectedExceptionReturnsGeneric500ProblemDetail` | A `RuntimeException("secret internal failure detail")` becomes 500 `application/problem+json` with `status=500` and `detail="An unexpected error occurred."`. The body does **not** contain the original message. |

The test-only `FailingController` is a nested class in the test source set. It is not part of the application.

## 8. Build / test result

Command, run from `C:\Conflux\backend`:

```
.\mvnw.cmd -B clean verify
```

Result: **exit code 0**. Relevant output:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.786 s -- in com.conflux.common.web.GlobalExceptionHandlerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.969 s -- in com.conflux.common.web.HealthControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.214 s -- in com.conflux.ConfluxApplicationTests
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: C:\Conflux\backend\target\backend-0.0.1-SNAPSHOT.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  11.567 s
```

The build passed on the first run; no fixes were needed.

Expected or harmless log output during the build:
- An `ERROR ... GlobalExceptionHandler -- Unexpected error while processing request` with a stack trace, emitted by `GlobalExceptionHandlerTest`. The test is meant to trigger it, and it shows server-side logging works.
- `Using generated security password: ...` from Boot's `UserDetailsServiceAutoConfiguration`. See §10.
- A JDK/Mockito warning about Mockito self-attaching as a Java agent. It comes from the test starter, not from this batch.

**Runtime run not performed.** A MySQL service (`MySQL267`) is listening on port 3306 on this machine. No credentials were provided and no `.env` exists, so the application was **not** started against MySQL and `/api/v1/health` was **not** called on a live server. To check manually:

```
# in C:\Conflux\backend, with .env filled in (or env vars set) and a `conflux` database created
.\mvnw.cmd spring-boot:run
curl http://localhost:8080/api/v1/health     # expected: {"status":"UP"}
```

## 9. Assumptions

1. The report lives at `C:\Conflux\docs\`, the project root next to `backend/`, on the assumption that the React frontend will be a sibling of `backend/`.
2. The `package-info.java` one-line descriptions for `saved`, `report`, `connection`, `message`, and `admin` are working descriptions drawn from the product flow in the brief. They are not domain definitions and can be reworded freely.
3. The dev `.env` path is relative to the process working directory. It works for `mvnw spring-boot:run` from `backend/`, and from an IDE run configuration whose working directory is `backend/`.
4. The `conflux` MySQL database must already exist; `createDatabaseIfNotExist` was deliberately not added to the dev URL.
5. The pom's `<name/>` element was left empty, as generated. "conflux-backend" is applied as `spring.application.name`.
6. `HealthController` exposes a `HEALTH_PATH` constant (`ApiPaths.API_V1 + "/health"`), so `SecurityConfig`'s public matcher doesn't repeat the path string.

## 10. Concerns / decisions to review before Batch 2

All items below are **[Recommended] / open decisions**. None are implemented.

1. **Schema management.** `ddl-auto=none` is a placeholder. Before the first entity, choose between Flyway migrations with `ddl-auto=validate` (recommended for a MySQL production target) and Hibernate-managed DDL in dev only.
2. **H2 vs MySQL drift.** H2 in MySQL mode is not MySQL. Once entities and queries exist, some MySQL-specific SQL, types, or constraints will behave differently in tests. Docker/Testcontainers is out of scope for now. Keep repository tests simple, or revisit this later.
3. **Authentication design drives CORS/CSRF.** CSRF is disabled and `allowCredentials` is off. That's correct for header-based bearer tokens. If Batch 2 chooses **cookie-based sessions or cookie-held JWTs**, then:
   - CSRF protection must be re-enabled or otherwise addressed
   - `allowCredentials(true)` must be set, which requires explicit origins, not wildcards
4. **Generated default user.** Boot's `UserDetailsServiceAutoConfiguration` still creates an in-memory user and logs a generated password at startup. It can't be used to log in, because no authentication mechanism is enabled. It disappears once Batch 2 defines its own `UserDetailsService` / `AuthenticationManager`.
5. **Catch-all handler and Spring Security.** `@ExceptionHandler(Exception.class)` will also catch `AccessDeniedException` / `AuthenticationException` thrown from inside controllers, e.g. by future `@PreAuthorize` method security, and turn them into 500s. It also overrides `@ResponseStatus`-annotated custom exceptions. If Batch 2 adds method security or `@ResponseStatus` exceptions, add explicit handlers or rethrow those types.
6. **`ConstraintViolationException` is not mapped.** Violations from `@Validated` service or path-parameter validation outside Spring MVC's method validation would currently return 500. Add a 400 mapping when validation is first used.
7. **Security errors are not ProblemDetail.** 401 responses from the security filter chain have an empty body. CORS rejections return 403 with Spring's plain-text `Invalid CORS request`. Neither is `application/problem+json`. Decide whether the frontend needs a consistent error body.
8. **`.env.example` copied verbatim.** An empty `DB_URL=` line in `.env` resolves to an empty string and **overrides** the dev default `jdbc:mysql://localhost:3306/conflux`. The same applies to `CORS_ALLOWED_ORIGINS=`. Developers should delete unused lines or fill them in.
9. **Local runtime requires MySQL.** Hibernate reads JDBC metadata at startup, so the app does not start in dev without a reachable database. That's acceptable for now, but worth knowing for frontend-only work.
10. **No readiness/metrics endpoint.** `/api/v1/health` is liveness only. If deployment later needs DB-aware readiness or metrics, Spring Boot Actuator is the standard option. It was deliberately not added.
11. **API versioning.** `/api/v1` is a plain path prefix held in `ApiPaths`. Spring Framework 7 has first-class API versioning support; that is not needed now, but consider it before a `v2` exists.
12. **Version control.** `C:\Conflux` is not a git repository. Initialize one before Batch 2 so each batch can be reviewed as a diff.
