# Batch 2 — Database Migrations and User Domain Report

**Scope:** Flyway migration infrastructure and the `User` domain model (entity, enums, repository). No authentication, JWT, login, registration, controllers, services, or other domains.
**Date:** 2026-09-30
**Location:** `C:\Conflux\backend`

Legend: **[Verified]** = implemented and exercised by the passing test suite (H2 in MySQL mode). **[Implemented, not verified on MySQL]** = in code/config, but not run against a real MySQL server in this batch. **[Deferred]** = deliberately not done in this batch.

---

## 1. What was inspected before implementation

- **Batch 1 state.** Confirmed unchanged since the Batch 1 report:
  - `pom.xml`
  - `application.properties`, `application-dev.properties`, `application-prod.properties`
  - `src/test/resources/application-test.properties`
  - `SecurityConfig`, `CorsConfig`, `CorsProperties`, `HealthController`, `ApiPaths`, `GlobalExceptionHandler`
  - the three Batch 1 test classes
- **Spring Boot 4.1.1 BOM** (`spring-boot-dependencies-4.1.1.pom`). It manages `spring-boot-starter-flyway`, `spring-boot-starter-flyway-test`, `spring-boot-flyway`, `flyway-core`, and `flyway-mysql` at `flyway.version = 12.4.0`, and H2 at 2.4.240.
- **Resolved jars**, to use packages that actually exist in Boot 4.1.1 rather than older names:

  | Type | Package / artifact |
  |---|---|
  | `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (`spring-boot-data-jpa-test`) |
  | `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` (`spring-boot-jdbc-test`) |
  | `TestEntityManager` | `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager` (`spring-boot-jpa-test`) |
  | `FlywayAutoConfiguration` | `org.springframework.boot.flyway.autoconfigure` (`spring-boot-flyway`). It is also registered in `AutoConfigureDataSourceInitialization.imports`, which is how it takes part in `@DataJpaTest`. |
  | H2 support in Flyway | built into `flyway-core` 12.4.0 (`org.flywaydb.core.internal.database.h2`); no extra module is needed |
  | Default user auto-config | `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` (`spring-boot-security`) |

- **Boot source: `ImportAutoConfigurationImportSelector.getExclusions`.** It confirms test slices (`@WebMvcTest`, `@DataJpaTest`) honor the `spring.autoconfigure.exclude` property. That drove the choice of how to remove the generated default user (§7).

## 2. Files created / changed

### Changed
| File | Change |
|---|---|
| `backend/pom.xml` | Added `spring-boot-starter-flyway` (compile), `org.flywaydb:flyway-mysql` (compile), and `spring-boot-starter-flyway-test` (test). No versions are declared; all come from the Boot BOM. Nothing else changed. |
| `src/main/resources/application.properties` | Kept `spring.jpa.hibernate.ddl-auto=none` and updated its comment. Added `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` and `spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`. The datasource environment-variable settings are unchanged. |
| `src/test/resources/application-test.properties` | Added `spring.jpa.hibernate.ddl-auto=validate` (test profile only) and a comment explaining that Flyway applies the real migrations. The H2 URL is unchanged. |

### Created
| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V1__create_users_table.sql` | First Flyway migration |
| `src/main/java/com/conflux/user/User.java` | JPA entity |
| `src/main/java/com/conflux/user/UserRole.java` | Enum: `USER`, `ADMIN` |
| `src/main/java/com/conflux/user/UserStatus.java` | Enum: `ACTIVE`, `SUSPENDED` |
| `src/main/java/com/conflux/user/UserRepository.java` | Spring Data repository |
| `src/test/java/com/conflux/user/UserTest.java` | Unit tests for defaults and normalization |
| `src/test/java/com/conflux/user/UserRepositoryTest.java` | `@DataJpaTest` persistence tests against the Flyway-migrated H2 database |
| `docs/batch-2-database-user-domain-report.md` | This report |

Unchanged:
- `dev` and `prod` profiles
- `SecurityConfig`, `CorsConfig`, `HealthController`, `GlobalExceptionHandler`
- all Batch 1 tests
- `com/conflux/user/package-info.java`

## 3. Final User model

`com.conflux.user.User`: `@Entity`, `@Table(name = "users")`. Plain JPA with explicit getters and setters. No Lombok, no base class, no auditing framework.

| Field | Java type | Column | Required | Meaning / notes |
|---|---|---|---|---|
| `id` | `Long` | `id` | yes (DB-generated) | `@GeneratedValue(strategy = IDENTITY)`, backed by `AUTO_INCREMENT` |
| `email` | `String` | `email` | yes, unique | Login identifier; always stored normalized (§8) |
| `passwordHash` | `String` | `password_hash` | yes | Hash only; hashing is not implemented yet. Not exposed by any API; no API exists. |
| `username` | `String` | `username` | yes, unique | Public handle; always stored normalized (§8) |
| `displayName` | `String` | `display_name` | yes | Human-readable name, stored as given, casing preserved |
| `bio` | `String` | `bio` | no | Free-text profile description |
| `location` | `String` | `location` | no | Free-text location |
| `websiteUrl` | `String` | `website_url` | no | Personal website |
| `githubUrl` | `String` | `github_url` | no | GitHub profile |
| `linkedinUrl` | `String` | `linkedin_url` | no | LinkedIn profile |
| `role` | `UserRole` | `role` | yes | `@Enumerated(EnumType.STRING)`; defaults to `USER` |
| `status` | `UserStatus` | `status` | yes | `@Enumerated(EnumType.STRING)`; defaults to `ACTIVE` |
| `createdAt` | `Instant` | `created_at` | yes | Set once in `@PrePersist`; `updatable = false` |
| `updatedAt` | `Instant` | `updated_at` | yes | Set in `@PrePersist`, refreshed in `@PreUpdate` |

Construction and invariants:
- The public constructor is `User(email, passwordHash, username, displayName)` and yields an `ACTIVE` user with role `USER`. A `protected` no-arg constructor exists for JPA only.
- Required fields reject `null` in the constructor and setters (`Objects.requireNonNull`). Email and username also reject blank values (§8).
- `id`, `createdAt`, and `updatedAt` have no setters.
- Every column name is explicit in `@Column(name = ...)`, so the mapping doesn't depend on Hibernate's naming strategy.

**Timestamp approach** [Verified on H2]:
- JPA lifecycle callbacks (`@PrePersist` / `@PreUpdate`) set `Instant.now()`, truncated to microseconds.
- The truncation matches `DATETIME(6)` precision, so the in-memory value equals the stored value.
- `hibernate.jdbc.time_zone=UTC` makes Hibernate read and write timestamps in UTC regardless of the JVM or database time zone.
- I chose this over Hibernate's `@CreationTimestamp` / `@UpdateTimestamp` and Spring Data JPA auditing because it's standard JPA, visible in the entity, and needs no extra configuration.
- Limitation: it applies only to changes made through JPA. Bulk JPQL or native updates bypass `@PreUpdate`.

**Deliberately omitted:**
- `equals` / `hashCode`: default object identity. This avoids the usual pitfalls with generated IDs and proxies.
- `toString`: the default `Object.toString()` cannot leak `passwordHash`.
- Bean Validation annotations on the entity: request validation belongs to future DTOs.

## 4. Role and status enums

| Enum | Values | Notes |
|---|---|---|
| `UserRole` | `USER`, `ADMIN` | Security roles only. No marketplace personas (buyer, seller, founder, …). |
| `UserStatus` | `ACTIVE`, `SUSPENDED` | No email-verification or approval states. |

Both are stored as their `name()` in `VARCHAR(20)` columns, not native MySQL `ENUM`. No `CHECK` constraint limits the allowed values, so adding a value needs only a Java change and no migration. The trade-off is that the database won't reject an unknown string written outside JPA (§11).

## 5. Database schema

`V1__create_users_table.sql`:

```sql
CREATE TABLE users (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    username      VARCHAR(50)   NOT NULL,
    display_name  VARCHAR(100)  NOT NULL,
    bio           VARCHAR(1000),
    location      VARCHAR(100),
    website_url   VARCHAR(500),
    github_url    VARCHAR(500),
    linkedin_url  VARCHAR(500),
    role          VARCHAR(20)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);
```

The file also has a leading comment explaining normalization, string enums, and UTC.

Size choices:
- **email 254:** the practical maximum length of an email address.
- **password_hash 255:** fits bcrypt (60) and Argon2 encodings with room to spare.
- **username 50, display_name 100, location 100:** generous and simple.
- **bio `VARCHAR(1000)` instead of `TEXT`:** keeps it an ordinary string column on both MySQL and H2. H2 maps `TEXT` to CLOB, which fails Hibernate schema validation for a `String` field.
- **URLs 500:** long enough for real profile links.

Sizing constraints:
- Worst-case utf8mb4 row size is about 13 KB, under MySQL's 64 KB row limit.
- The unique index on `email` (254 × 4 bytes = 1016 bytes) is under InnoDB's 3072-byte index key limit.

**Deliberately not in the schema:**
- `ENGINE` / `CHARSET` / `COLLATE` table options. The server defaults apply; on MySQL 8 that is InnoDB and `utf8mb4` / `utf8mb4_0900_ai_ci` (§11).
- Foreign keys or relationships: `users` is independent.
- Columns for future features.
- Functional or case-insensitive indexes. They aren't needed, because values are normalized before they are stored (§8).

## 6. Migration and Flyway configuration

| | |
|---|---|
| Migration | `V1__create_users_table.sql`: version 1, description "create users table" |
| Purpose | Create the `users` table and its PK/unique constraints. This is the first schema object Conflux owns. |
| Location | `src/main/resources/db/migration`, which is Boot's default `spring.flyway.locations=classpath:db/migration` |
| Dependencies | `spring-boot-starter-flyway` 4.1.1 → `spring-boot-flyway` 4.1.1 + `flyway-core` 12.4.0; `flyway-mysql` 12.4.0 for MySQL 8; `spring-boot-starter-flyway-test` 4.1.1 (test) |
| Configuration | **Boot defaults only; no `spring.flyway.*` properties set.** Flyway migrates the configured `spring.datasource` on startup, before the JPA `EntityManagerFactory` is created. `clean` is disabled by default. There's no baseline-on-migrate, since the database is new. |
| Schema ownership | Flyway only. `spring.jpa.hibernate.ddl-auto=none` stays in `application.properties`, so Hibernate never creates or alters tables. |
| Test profile | `spring.jpa.hibernate.ddl-auto=validate`. Hibernate checks the entity mappings against the Flyway-created schema and fails the context if they diverge. It never modifies the schema; it's a check, not a second schema-management system. |

What was verified [Verified on H2]:
- Build log, first context: `Migrating schema "public" to version "1 - create users table"` and `Successfully applied 1 migration to schema "public", now at version v1`.
- A later context found `Schema "public" is up to date. No migration necessary.` All contexts share the named in-memory database.
- Hibernate `validate` passed in both JPA-backed contexts.

Not verified: running V1 against a real MySQL server [Implemented, not verified on MySQL], see §10.

## 7. Existing security (unchanged behavior) and the default user

- `SecurityConfig` was not modified. The health endpoint is public, `/error` is public, and everything else returns 401. All 4 `HealthControllerTest` tests still pass.
- **The Boot-generated default user is removed.** Batch 1 logged `Using generated security password: …` because `UserDetailsServiceAutoConfiguration` created an `InMemoryUserDetailsManager`. It is now excluded through `spring.autoconfigure.exclude` in `application.properties`, which applies to the app and to every test slice.
- Verified: the Batch 2 build log has no `generated security password`, `UserDetails`, `inMemory`, or `AuthenticationManager` lines.
- No authentication mechanism, `UserDetailsService`, `PasswordEncoder`, or users of any kind were added.

## 8. Normalization rules

| Field | Rule | Where |
|---|---|---|
| `email` | `strip()`, then `toLowerCase(Locale.ROOT)`; `null` → `NullPointerException`; blank after stripping → `IllegalArgumentException` | `User.normalizeEmail(String)`, applied by the constructor and `setEmail` |
| `username` | Same: `strip()`, then `toLowerCase(Locale.ROOT)`, same null/blank rejection | `User.normalizeUsername(String)`, applied by the constructor and `setUsername` |
| `displayName` | None; stored as given | — |

Why it's done this way:
- **Normalize in the domain model, not the database.** Every value that reaches the `email` / `username` columns has already gone through `normalize…`, because the fields have no other write path. The plain `UNIQUE` constraints therefore enforce case-insensitive uniqueness, and the same constraint works identically on MySQL and H2, with no functional index and no reliance on a particular collation.
- **Lower-casing the whole email, including the local part.** In practice mailbox providers treat the local part case-insensitively, and case-distinct duplicate accounts are a real source of confusion and takeover risk. The cost is that an address whose provider really is case-sensitive is stored lower-cased; that is accepted for the MVP.
- **Why `Locale.ROOT`.** Without it, a server running under a Turkish locale would lower-case `I` to the dotless `ı`. The unit test checks this under a `tr-TR` default locale.
- **Scope of the username rule.** Trimming and lower-casing only. No allowed-character, length, or reserved-name rules, per the batch scope. `displayName` carries the user's preferred casing.
- **Lookups.** The repository does not normalize its arguments, so callers must pass `User.normalizeEmail(...)` / `User.normalizeUsername(...)`. This is documented on `UserRepository` and exercised in the tests.

## 9. Repository methods

`UserRepository extends JpaRepository<User, Long>`:

| Method | Purpose |
|---|---|
| `Optional<User> findByEmail(String email)` | Future login lookup |
| `boolean existsByEmail(String email)` | Future registration duplicate check |
| `boolean existsByUsername(String username)` | Future registration duplicate check |

No other query methods were added. Hibernate generated `select … where u1_0.email=?` and `select u1_0.id from users u1_0 where … fetch first ? rows only` for the `exists` methods.

## 10. Tests added and results

### New tests

`UserTest`: plain JUnit, no Spring. 4 tests:

| Test | Checks |
|---|---|
| `newUserIsActiveWithUserRole` | Defaults are `USER` / `ACTIVE` |
| `emailAndUsernameAreNormalized` | `"  Alice@Example.COM "` → `alice@example.com`; `" Alice_Dev "` → `alice_dev`; the display name is untouched |
| `normalizationIsLocaleIndependent` | Under a `tr-TR` default locale, `IVAN` → `ivan` and `INFO@EXAMPLE.COM` → `info@example.com` |
| `blankOrNullIdentifiersAreRejected` | Blank → `IllegalArgumentException`; `null` → `NullPointerException` |

`UserRepositoryTest` uses:
- `@DataJpaTest`
- `@AutoConfigureTestDatabase(replace = NONE)`, which deliberately keeps the H2 MySQL-mode database from the `test` profile instead of Boot's auto-replaced embedded database
- `@ActiveProfiles("test")`

Flyway runs the real V1 migration, and Hibernate `validate` runs. 8 tests:

| Requirement | Test | Checks |
|---|---|---|
| Migration creates the users table | `flywayMigrationCreatesUsersTable` | Flyway's current migration is version `1`, script `V1__create_users_table.sql`, state `SUCCESS`. `information_schema.columns` for `users` in the current schema lists exactly the 14 expected columns, in order. |
| A valid user persists and is retrieved | `persistsAndRetrievesUser` | Every field round-trips after `flush` + `clear`, so it's read from the database, not the persistence context |
| Optional fields | `optionalProfileFieldsMayBeNull` | All five optional fields persist as `null` |
| Email uniqueness | `emailIsUniqueIgnoringCase` | A second user with `"  Alice@Example.COM "` → `DataIntegrityViolationException` from `uk_users_email` |
| Username uniqueness | `usernameIsUniqueIgnoringCase` | A second user with `"ALICE"` → `DataIntegrityViolationException` from `uk_users_username` |
| Enums persist correctly | `enumsArePersistedAsStrings` | A native SQL read returns the literal strings `"ADMIN"` / `"SUSPENDED"`, and a JPA read maps them back to the enums |
| Timestamps | `timestampsAreSetOnCreateAndUpdatedOnChange` | On insert, `createdAt` is non-null and `updatedAt == createdAt`, and the stored values equal the in-memory values. After a 5 ms pause and an update, `createdAt` is unchanged and `updatedAt` is strictly after it. |
| Repository methods | `findsAndChecksExistenceByNormalizedEmailAndUsername` | `findByEmail`, `existsByEmail`, and `existsByUsername` return true/present for normalized input and false/empty for unknown values |

Existing Batch 1 tests were kept unmodified. `ConfluxApplicationTests` now also runs Flyway and Hibernate `validate` against H2.

### Issues found while writing the tests
Both were in my own test code and were fixed without loosening any assertion:
1. A compile error: `List<?>` couldn't be used with AssertJ `containsExactly`. Fixed by typing the native query as `createNativeQuery(sql, String.class)`.
2. `flywayMigrationCreatesUsersTable` first failed because H2's own `INFORMATION_SCHEMA.USERS` table, with columns `user_name`, `is_admin`, and `remarks`, also matched `table_name = 'users'`. Fixed by adding `table_schema = SCHEMA()` to the query. The exact 14-column assertion is unchanged.

## 11. Exact Maven build result

Command, run from `C:\Conflux\backend`:
```
.\mvnw.cmd -B clean verify
```
Final run: **exit code 0**.

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.710 s -- in com.conflux.common.web.GlobalExceptionHandlerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.638 s -- in com.conflux.common.web.HealthControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.525 s -- in com.conflux.ConfluxApplicationTests
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.047 s -- in com.conflux.user.UserRepositoryTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in com.conflux.user.UserTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: C:\Conflux\backend\target\backend-0.0.1-SNAPSHOT.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  10.135 s
```

Expected log noise:
- The `GlobalExceptionHandler` ERROR stack trace comes from its own test.
- `HHH000247 … Unique index or primary key violation` WARN lines come from the two uniqueness tests.
- A Mockito self-attach JDK warning.

**Not verified at runtime against MySQL.** A MySQL service is running locally, but no credentials or `.env` were provided. The application was not started against MySQL, and V1 has not been applied to a MySQL database. To verify:
```
# backend/.env with DB_USERNAME / DB_PASSWORD (and DB_URL if not the default), database `conflux` created
.\mvnw.cmd spring-boot:run
# expect: "Successfully applied 1 migration ... now at version v1" and a users table in MySQL
```

## 12. Compatibility issues encountered

1. **H2's `INFORMATION_SCHEMA.USERS` name clash.** This affects only schema-introspection queries in tests, not the app. Always filter by `table_schema` (see §10).
2. **Flyway / H2 version warning.** `Using H2 2.4.240 which is newer than the version Flyway has been verified with. The latest verified version of H2 is 2.3.232.` Both versions come from the Boot 4.1.1 BOM. The migration and history table work correctly, so this is only a warning. I did not override the BOM.
3. **`TEXT` vs `VARCHAR` for `bio`.** H2 maps `TEXT` to CLOB, which would fail Hibernate validation for a `String`. `VARCHAR(1000)` avoids this on both databases. It was a design-time choice, not a failure.
4. No incompatibility was found with `AUTO_INCREMENT`, `DATETIME(6)`, named `UNIQUE` constraints, `Instant` + UTC, or `EnumType.STRING` on H2 in MySQL mode. Hibernate 7.4.5 `validate` accepted all columns.

## 13. Implemented and verified vs intentionally deferred

**Implemented and verified (H2, MySQL mode):**
- Flyway dependencies and auto-configuration
- V1 migration applied, recorded in `flyway_schema_history`, and idempotent across contexts
- `ddl-auto=none` preserved at runtime; mappings validated against the migrated schema in tests
- `User`, `UserRole`, `UserStatus`, `UserRepository` with the three methods
- DB-enforced PK, `NOT NULL`, and both unique constraints, including case-insensitive uniqueness through normalization
- `EnumType.STRING` persistence
- `createdAt` / `updatedAt` behavior
- Boot default user removed; Batch 1 security, CORS, health, and error behavior unchanged; all Batch 1 tests passing

**Implemented, not verified on MySQL:** V1 execution on MySQL 8; `Instant` / UTC round-trip on MySQL; MySQL unique-index behavior under the server's default collation.

**Intentionally deferred:**
- Authentication and password hashing (`PasswordEncoder`)
- `UserService`
- User API / DTOs, and request validation (email format, username character rules, lengths at the API level)
- Email verification, password reset, OAuth
- `CHECK` constraints on `role` / `status`
- Any other domain tables
- Testcontainers / Docker
- `ddl-auto=validate` outside tests

## 14. Decisions to review before Batch 3

1. **MySQL collation.** No collation is pinned, so MySQL 8's default `utf8mb4_0900_ai_ci` applies. It is case- *and accent*-insensitive, so on MySQL `josé` and `jose` would conflict as usernames, while H2 treats them as distinct. Decide whether that's desired (anti-impersonation) or whether to pin the columns to a specific collation in a V2 migration. Either way, H2 tests can't catch collation differences.
2. **Callers must normalize before querying.** `findByEmail` / `existsBy…` take already-normalized values. Batch 3's auth service should route every lookup through `User.normalizeEmail` / `normalizeUsername`, and a unit test there should prove it. An alternative is wrapping the repository calls in the service so raw input is never passed.
3. **Never serialize the entity.** `User` has a public `getPasswordHash()` for the future authentication code. Controllers in Batch 3+ should return DTOs, never `User`, or the hash will leak into JSON.
4. **`ddl-auto=validate` in production?** It is currently only in the test profile. Enabling it in `prod` would catch mapping/schema drift at startup, at the cost of a small startup check. This is your decision; it wasn't changed, per the batch rules.
5. **Default-user exclusion.** Once Batch 3 defines its own `UserDetailsService` / `AuthenticationManager`, Boot's auto-config would back off anyway. The `spring.autoconfigure.exclude` line can then stay (harmless) or be removed.
6. **Timestamps depend on JPA callbacks.** Bulk or native updates won't touch `updated_at`. If Batch 3+ uses bulk JPQL updates on `users`, they must set `updated_at` explicitly.
7. **Unique-violation translation.** A duplicate registration currently surfaces as `DataIntegrityViolationException`, which the Batch 1 catch-all handler turns into a generic 500. Batch 3 should check `existsByEmail` / `existsByUsername` first and still map the constraint violation for the race condition to a 409 response.
8. **H2 is not MySQL.** The migration uses only syntax that both accept. Future migrations using MySQL-only features (functional indexes, `JSON` columns, full-text) may not run on H2 and would force a decision about Testcontainers.
9. **Version control.** `C:\Conflux` is still not a git repository. Initializing one would let Batch 2 be reviewed as a diff against Batch 1.
