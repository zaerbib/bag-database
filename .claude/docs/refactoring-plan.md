# Refactoring Plan — Bag Database

> Derived from [`audit.md`](audit.md). Target order: **(0) safety net → (1) separate front end and back end → (2) upgrade one major version at a time → (3) rewrite hot paths for performance with identical behavior.**

> **Progress tracking:** `[ ]` = not done, `[x]` = done.
> - Check a step only once its *Test* passes.
> - Steps split into separate PRs (4a–4d, 22, 26) have one box per PR; check the parent step when all its boxes are checked.
> - Check a phase when all its steps are checked.

## Ground rules (apply to every step)

- **One step = one PR = one revertable commit.** Each PR must keep `mvn -B package` green.
- **Testable:** each step lists the check that proves it works. Add the test *before* the change when it is a refactoring.
- **Reversible:** each step lists how to roll it back.
  - Prefer additive changes: a new endpoint next to the old one, a flag that selects the old or new path.
  - Remove the old path in a *later* step, once the new one has been verified.
- **Database changes** only go through new Liquibase changelogs (`db.changelog-X.Y.yaml`).
  - Every changelog has a `rollback` block.
  - Existing changelogs are never edited.
- **Git tags:** tag `pre-<step>` before each major upgrade, so that `git revert` or a redeploy of the previous image is always possible.
- **Behavior baseline:** "same behavior" is checked against the API contract tests from step 3, which run in every subsequent step.

---

## [ ] Phase 0 — Safety net (before touching structure)

These steps come first because the plan cannot be validated without tests, and because the audit found exploitable issues that must not survive the refactoring.

- [x] **1. Restore `BagService` search predicates.**
  - Revert the uncommitted `String.valueOf(root.get(...))` change and go back to the metamodel (`Bag_.messageTypes`, `MessageType_.name`, `Tag_.tag`, `Tag_.value`, `Topic_.topicName`).
  - Keep `System.lineSeparator()`.
  - *Test:* new `BagServiceTest` case that searches on `messageType`, `tags` and `topicName`.
  - *Rollback:* `git revert`.

- [x] **2. Measure coverage.**
  - Add `jacoco-maven-plugin` (report only, no threshold yet).
  - Upgrade CI to `actions/checkout@v4` and `actions/setup-java@v4` (Temurin 11).
  - *Test:* CI publishes `target/site/jacoco`.
  - *Rollback:* remove the plugin.

- [x] **3. API contract (characterization) tests.**
  - For every endpoint called by the front end (`bags/*`, `scripts/*`, `status/*`, `admin/*`, `config/get`, `account/current`), add MockMvc tests that pin the JSON shape: `success`, `data`, `totalCount`, field names.
  - *Test:* the tests themselves; they become the regression baseline for all later steps.
  - *Rollback:* not needed (tests only).

- [ ] **4. Close the critical security holes. Each item is a separate PR.**
  - [ ] 4a. Enable method security (`@EnableGlobalMethodSecurity(securedEnabled = true)`). Require `ROLE_ADMIN` on `scripts/save|run|delete`, and authentication on `bags/update|upload|setTag|setTagForBags|removeTags`.
  - [ ] 4b. Stop serializing secrets: `jdbcPassword`, `adminPassword`, `ldapBindPassword`, API keys.
    - `config/get` returns a DTO without them.
    - Saving keeps the existing value when the field is empty.
    - `account/current` returns a DTO, not the `Account` entity.
  - [ ] 4c. Upload path validation: resolve against `basePath`, reject if the result does not `startsWith(basePath)`, and sanitize `getOriginalFilename()`.
  - [ ] 4d. Allow-list the field names accepted by search and `ExtJsFilter` before calling `root.get(field)`.
  - *Test:* `WebSecurityConfigurationAware` tests that check anonymous → 401/403 and admin → 200, plus a traversal test with `../../etc`.
  - *Rollback:* revert the individual PR.

- [ ] **5. Dependency hygiene (no version jumps).**
  - Remove `ldapsdk`, `javax.transaction:jta`, `tomcat-el-api` / `tomcat-embed-el` 8.5, and the forced versions of `byte-buddy`, `HikariCP` and `objenesis` (let Boot manage them).
  - Move `h2`, `h2gis-*` and `org.osgi.service.jdbc` to `test` scope.
    - Make `JpaConfig` fail fast instead of silently falling back to H2 in production.
  - Delete the unused `bootstrap.min.js`.
  - *Test:* full test suite, plus a Docker compose smoke test (app starts, bags are listed).
  - *Rollback:* revert the `pom.xml` diff.

---

## [ ] Phase 1 — Separate front end and back end (still on the current stack)

Goal: the ExtJS application becomes a set of static files that talk to a JSON API under `/api`. It no longer depends on Thymeleaf, and it can be served by any web server.

- [ ] **6. UI configuration endpoint.**
  - Add `GET /api/ui-config` that returns the non-secret UI settings: `useTileMap`, `tileMapUrl`, `tileWidthPx`, `tileHeightPx`, `useBing`, `bingKey`, `openWithUrls`, `debugJavascript`.
  - Add `GET /api/session` that returns `{authenticated, username, roles, ldap}`.
  - *Test:* MockMvc tests for both endpoints, anonymous and admin.
  - *Rollback:* delete the endpoints (nothing uses them yet).

- [ ] **7. Front end reads configuration at startup.**
  - Add `resources/js/Bootstrap.js`. It calls the two endpoints above, fills a `BagDatabase.Config` singleton, and only then creates the viewport.
  - Replace the globals (`useTileMap`, `bingKey`, `openWithUrls`, `isAdmin`…) in `MapWindow`, `ConfigWindow`, `BagGrid`, `BagTreePanel`, `NavigationButton` and `BagDatabaseViewport` with `BagDatabase.Config.*`.
  - Thymeleaf still injects the globals, so the change can be reverted file by file.
  - *Test:* manual smoke test of the map, the "open with" menu and the admin menu, plus the contract tests.
  - *Rollback:* revert the PR; the globals are still present.

- [ ] **8. CSRF without server-side injection.**
  - Switch to `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
  - Add one global `Ext.Ajax` `beforerequest` hook that copies the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header.
  - Remove the `csrfName` / `csrfToken` / `csrfHeader` parameters from the ~13 JS files. This covers stores, form submits and the upload form (the upload sends the token as a form field).
  - *Test:* run the security tests with CSRF **enabled** (new test profile without the `csrf().disable()` shortcut); manual check of upload, tag editing and admin actions.
  - *Rollback:* revert; the old repository and globals come back.

- [ ] **9. JSON login and logout.**
  - `POST /api/login` returns `200 {username, roles}` or `401`.
  - `POST /api/logout` returns `204`.
  - Configure custom success and failure handlers so that no HTML redirect is involved. LDAP and local accounts use the same endpoint.
  - `LoginWindow` becomes a pure ExtJS component that is shown when `/api/session` reports an unauthenticated session, or when any call returns `401` (global `Ext.Ajax` `requestexception` hook).
  - *Test:* `UserAuthenticationIntegrationTest` is extended to cover JSON login, both success and failure; LDAP is tested with the existing `test_ldap` profile.
  - *Rollback:* keep `/signin` and `/ldap_login` until step 13.

- [ ] **10. Version the API under `/api`.**
  - Map every controller under `/api/...` **in addition to** its current path (both values in `@RequestMapping`).
  - Add a `BagDatabase.Api` JS module holding a configurable `baseUrl`, and replace every hard-coded `'bags/...'`, `'scripts/...'` etc. with `Api.url('bags/search')`.
  - *Test:* contract tests run against both prefixes.
  - *Rollback:* set `baseUrl` back to `''`.

- [ ] **11. WebSocket under `/api/ws`.**
  - Register the STOMP endpoint at `/api/ws` (keep `/register` for now).
  - The front end builds the SockJS URL from `Api.baseUrl` instead of `window.location.pathname`.
  - *Test:* a `StompClient` integration test receives `/topic/status`; manual check that the status bar updates.
  - *Rollback:* point the front end back to `/register`.

- [ ] **12. Static front-end entry point and JS build.**
  - Create `frontend/` with a static `index.html` (no `th:` attributes), `js/`, `css/` and `images/`.
  - Replace `yuicompressor-maven-plugin` with a small Node build (`frontend/package.json`, using `terser` to concatenate and minify).
    - The file order moves from `pom.xml` to `frontend/build-order.json`.
    - Bundle file names are content-hashed.
  - Use `frontend-maven-plugin` so that `mvn package` still builds the front end and copies it into the WAR. Deployment is unchanged.
  - *Test:* the WAR serves `/index.html`; a Playwright smoke test (login, search, open bag details, map) is added to CI.
  - *Rollback:* restore the yuicompressor block and serve `home.html` (still present).

- [ ] **13. Remove server-side rendering.**
  - Delete `HomeController` views, `home.html`, `fragments/header.html`, `signin.html`, `ldap_login.html`, the `spring-boot-starter-thymeleaf` / `thymeleaf-*` / layout-dialect dependencies, and the duplicate non-`/api` routes.
  - `/` forwards to `index.html`. `image.html` becomes an ExtJS window or a plain static page.
  - Keep an `error.html` static page.
  - *Test:* Playwright smoke test and contract tests (now `/api` only).
  - *Rollback:* revert the PR; restoring the files and dependencies is enough.

- [ ] **14. Independent deployment.**
  - Add `docker/split/docker-compose.yml`:
    - an `nginx` container serving `frontend/dist` with long-lived `Cache-Control: immutable` on hashed files and gzip/brotli;
    - a reverse proxy for `/api` and `/api/ws` to Tomcat.
  - The back end gets CORS and cookie `SameSite` settings for a separate origin during development (`npm run dev` against a local API).
  - The existing all-in-one WAR image stays available.
  - *Test:* the Playwright smoke test runs against the split compose.
  - *Rollback:* use the all-in-one image.

- [ ] **15. Document the API contract.**
  - Generate OpenAPI (`springdoc-openapi`, the version compatible with Boot 2.x) next to REST Docs.
  - The `{success, data, totalCount}` ExtJS envelope is documented as part of the contract, so that any future UI can consume it.
  - *Test:* CI publishes `openapi.json`, and a contract test diffs it against the committed version.
  - *Rollback:* remove the dependency.

---

## [ ] Phase 2 — Version upgrades, one major at a time

Each step bumps **one** thing. Each step runs the full suite, the contract tests and the Playwright smoke test against PostgreSQL/PostGIS.
- **Step 16 adds Testcontainers** so that tests no longer rely on H2 only.
- The **"App impact"** line lists the application processes that must be adapted for that upgrade.

- [ ] **16. Testcontainers PostGIS.**
  - Add a `postgis` test profile using Testcontainers. Liquibase runs the full changelog against real PostGIS in CI.
  - *Test:* `BagRepositoryTest`, `SpatialDatabaseTest` and the contract tests run on both H2 and PostGIS.
  - *Rollback:* drop the profile.

- [ ] **17. Spring Session 1.3.5 → 2.6 (Boot 2.6-managed).**
  - *App impact:*
    - `WebSocketConfig` uses `Session` instead of `ExpiringSession`.
    - The session table schema changes (`PRIMARY_ID` column): add a Liquibase changelog that drops and recreates `SPRING_SESSION*`. Existing sessions are lost, so users log in again once.
    - Update or remove the JPA mirrors `persistence/SpringSession` and `SpringSessionAttribute`.
  - *Test:* login, session survives a restart, the WebSocket stays connected.
  - *Rollback:* tag `pre-17`; the changelog rollback restores the 1.x tables.

- [ ] **18. Java 11 → 17.**
  - Compiler `release 17`, Docker build `maven:3.9-eclipse-temurin-17`, runtime `tomcat:9-jdk17-temurin`, CI on Temurin 17.
  - *App impact:* check OpenCV native loading and the `--add-opens` flags needed by older libraries.
  - *Test:* full suite; image and video endpoints (OpenCV/ffmpeg).
  - *Rollback:* revert the images and compiler flag.

- [ ] **19. Spring Boot 2.6 → 2.7.**
  - Remove the explicit `hibernate.version` (Boot 2.7 manages Hibernate 5.6), the Liquibase pin and the Thymeleaf leftovers.
  - *App impact:* rewrite `SecurityConfig` from `WebSecurityConfigurerAdapter` to a `SecurityFilterChain` bean, and switch to `@EnableMethodSecurity`. This is the Security 6 style, but on 5.7.
  - *Test:* the security tests from step 4 plus the LDAP tests.
  - *Rollback:* tag `pre-19`.

- [ ] **20. JUnit 4 → JUnit 5.**
  - Use the OpenRewrite recipe `org.openrewrite.java.testing.junit5.JUnit4to5Migration`.
  - `SpringJUnit4ClassRunner` becomes `@SpringJUnitConfig` / `@ExtendWith`. Remove `junit-vintage-engine`.
  - *Test:* the same number of tests passes (compare the surefire reports).
  - *Rollback:* revert.

- [ ] **21. Replace `docker-java-api` 0.0.13 with `com.github.docker-java:docker-java` (latest 3.x).**
  - *App impact:*
    - Introduce a `ContainerRunner` interface used by `RunnableScript`: pull, create with binds, start, wait, logs, remove.
    - Implement it with docker-java and keep the old implementation behind a setting for one release.
    - Remove `javax.json` and `httpclient` 4 if nothing else uses them.
  - *Test:* a `RunnableScript` test with a fake `ContainerRunner`, plus a Testcontainers-based integration test that runs `alpine echo`.
  - *Rollback:* switch the setting back.

- [ ] **22. Minor upgrades of isolated libraries (one PR each):**
  - [ ] AWS SDK 2.17 → latest 2.x
  - [ ] OpenCV 4.5 → latest 4.x
  - [ ] `google-maps-services` latest
  - [ ] `yamlbeans` latest
  - [ ] `commons-*` latest
  - *Test:* full suite; manual S3 smoke test with the S3 docker-compose example.
  - *Rollback:* revert the single bump.

- [ ] **23. Spring Boot 2.7 → 3.0 (Spring 6, Security 6, Hibernate 6.1, Jakarta EE 9).**
  - Run the OpenRewrite recipe `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0` (it handles `javax.*` → `jakarta.*`).
  - Runtime moves to Tomcat 10.1, and the Docker base image becomes `tomcat:10.1-jdk17-temurin`.
  - *App impact:*
    - Hibernate Spatial 6: dialects are auto-detected, so remove the `System.setProperty("hibernate.dialect", ...)` in `JpaConfig`.
    - Check `BagPosition` geometry mappings and HQL/Criteria queries (the stricter Hibernate 6 query parser).
    - Native query `Bag.countBagPaths` needs a check.
    - H2 → 2.x and H2GIS → 2.x in tests.
    - Spring Session → 3.x.
    - REST Docs → 3.x.
    - `springdoc` → 2.x.
    - `PathMatchConfigurer` trailing-slash matching is off by default, so check the front-end URLs.
  - *Test:* the whole pipeline on PostGIS (step 16); compare the OpenAPI diff; Playwright smoke test.
  - *Rollback:* tag `pre-23`, redeploy the previous image. No schema change is required. If one turns out to be needed, it goes in its own changelog with a rollback.

- [ ] **24. Spring Boot 3.0 → 3.x (latest), one minor at a time if any step fails.**
  - *App impact:* follow the release notes of each minor (for example Hibernate 6.2+ changes to `@Column` defaults on `Instant` or time zones). Add a test on `Bag` dates before each bump.
  - *Rollback:* revert the bump.

- [ ] **25. Java 17 → 21 (LTS).**
  - *App impact:* optionally use virtual threads (`spring.threads.virtual.enabled`) for the request pool and the scanner executor (see step 31).
  - *Test:* full suite plus the load test from step 26.
  - *Rollback:* revert the images.

- [ ] **26. Front-end libraries, one per PR:**
  - [ ] `stomp-websocket` → `@stomp/stompjs` (with the SockJS fallback kept).
  - [ ] `sockjs-client` → latest.
  - [ ] OpenLayers 4 → latest: rewrite `MapWindow` against the current `ol` API; Stamen tiles → a maintained default tile URL.
  - [ ] ExtJS:
    - first make the **decision** (license: GPLv3 compliance or commercial upgrade to ExtJS 7.x, versus a progressive rewrite with a modern framework now that the API is decoupled);
    - then a dedicated sub-plan. This is out of scope for this document's small steps.
  - *Test:* the Playwright smoke test after each library.
  - *Rollback:* restore the vendored file.

- [ ] **27. Optional: executable JAR.**
  - Package as a Spring Boot executable JAR with embedded Tomcat, use real Boot auto-configuration where possible, and remove `WebAppInitializer` and `web.xml`.
  - *Test:* the same smoke tests against `java -jar`.
  - *Rollback:* keep `war` packaging (both can coexist via a Maven profile during the transition).

---

## [ ] Phase 3 — Same behavior, better performance

Every step adds a **measurement first** and keeps the contract tests green. The measurements are:
- a JMH or JUnit timing test,
- a `k6` / Gatling scenario against the split compose,
- or `EXPLAIN ANALYZE` on PostGIS.

The step is accepted only if the measurement improves, and is reverted otherwise. These steps can be interleaved with Phase 2 once step 16 is done.

- [ ] **28. Load baseline.**
  - Add a `k6` scenario: bag search with filters, tree navigation, bag details, tag update, status polling.
  - Record p50/p95 latencies and the number of SQL queries per request (Hibernate statistics or `datasource-proxy` in tests).
  - *Rollback:* not needed.

- [ ] **29. Split `BagService` (1853 lines) without changing logic.**
  - Move code into `BagIndexingService` (parse and persist), `BagSearchService` (criteria, filters, count), `BagTagService`, `BagMaintenanceService` (admin tasks: lat/lon, GPS paths, duplicates, missing bags) and `BagMediaService` (image, video).
  - `BagService` remains a thin facade, so callers and tests stay unchanged.
  - *Test:* the existing tests and contract tests are unchanged.
  - *Rollback:* revert (pure move).

- [ ] **30. Remove the N+1 queries and cartesian products in search.**
  - `Bag` has three `FetchType.EAGER` collections (`messageTypes`, `topics`, `tags`). Each grid page loads all of them, multiplied by every row.
  - Switch them to `LAZY`. For the grid, use a **DTO projection** query with only the displayed columns.
  - For bag details, use an `@EntityGraph` fetch, or `@BatchSize` on the collections.
  - *Test:* a query-count assertion (for example, "search of 100 bags issues ≤ 3 statements"); contract tests prove the JSON is unchanged.
  - *Rollback:* revert the fetch-type change.

- [ ] **31. Database indexes for search.**
  - Add a new Liquibase changelog with:
    - B-tree indexes on the filter and sort columns used by `ExtJsFilter` (`bags.path`, `filename`, `start_time`, `vehicle`, `storage_id`, `md5sum`);
    - indexes on the join tables;
    - on PostGIS, a `pg_trgm` GIN index for `LIKE '%text%'` searches on `filename`, `tags.value`, `topics.topic_name` and `message_types.name` (with a `dbms: postgresql` precondition so H2 skips it).
  - *Test:* `EXPLAIN ANALYZE` before and after on a seeded dataset (for example 50k bags); the k6 p95 improves.
  - *Rollback:* the changelog `rollback` drops the indexes.

- [ ] **32. Faster count and paging.**
  - `filteredcount` and `search` currently build the predicates twice. Build them once.
  - Use a `count(distinct id)` without fetch joins.
  - Optionally use keyset paging for the tree view.
  - *Test:* contract tests plus the timing test.
  - *Rollback:* revert.

- [ ] **33. Batch inserts during indexing.**
  - `Bag`, `Script`, `ScriptResult` and `ScriptCriteria` use `GenerationType.IDENTITY`, which disables JDBC batching even though `STATEMENT_BATCH_SIZE=100` is set.
  - Switch `Bag` (and its children) to `SEQUENCE` with `allocationSize=50`, like `BagPosition` already does. This requires a Liquibase changelog that creates the sequences starting at `max(id)+1`.
  - *Test:* indexing benchmark on a folder of N sample bags (time and statement count).
  - *Rollback:* the changelog rollback plus a code revert. Ids stay compatible.

- [ ] **34. Scanner throughput.**
  - In `BagScanner`, skip unchanged files using `(path, size, lastModified)` before computing the MD5 and parsing.
  - Parse bags in a bounded executor (configurable pool size, or virtual threads after step 25). Persist in batches inside short transactions.
  - Coalesce file-watcher events: debounce bursts of `BagStorageChangeEvent` per path.
  - *Test:* a scan benchmark (cold and warm); a correctness test asserting that the same set of bags and checksums is produced.
  - *Rollback:* `scanner.parallelism=1` falls back to sequential behavior.

- [ ] **35. Event-driven script completion.**
  - Replace `ScriptService`'s `@Scheduled(fixedRate = 500)` polling with a completion callback or `CompletableFuture` from `ContainerRunner` (step 21), which then publishes `/topic/script_finished`.
  - *Test:* a script result is still delivered over STOMP; CPU and DB load at idle drop (no 2 Hz polling query).
  - *Rollback:* re-enable the scheduler through a flag.

- [ ] **36. Throttle status pushes.**
  - `StatusService` sends on every update. Coalesce updates to at most N per second per topic.
  - *Test:* a status-flood unit test (messages sent ≤ limit, last state always delivered).
  - *Rollback:* set the limit to unlimited.

- [ ] **37. Media endpoints.**
  - `bags/image` and `bags/video` should stream with HTTP `Range` support and `StreamingResponseBody`, instead of buffering whole files in memory. Add `ETag` / `Cache-Control` on immutable images.
  - *Test:* a Range-request test (206 with the correct `Content-Range`); memory usage stays flat on a large video in the load test.
  - *Rollback:* revert.

- [ ] **38. Connection pool and Hibernate tuning.**
  - Remove MySQL-only Hikari properties (`cachePrepStmts`, `useServerPrepStmts`), which have no effect on PostgreSQL.
  - Size the pool from the load test. Enable `hibernate.query.in_clause_parameter_padding` and the query plan cache settings.
  - Optionally use a second-level cache for read-mostly reference data (`MessageType`, `Topic` names).
  - *Test:* k6 p95 and pool wait time.
  - *Rollback:* revert the properties.

- [ ] **39. Raise the quality gate.**
  - Once Phases 1–3 are merged, set a JaCoCo minimum (start at the measured value, never lower it) and add OWASP `dependency-check` to CI with a fail threshold on CVSS ≥ 7.
  - *Rollback:* lower or remove the threshold.

---

## Dependency map (what must happen before what)

```
1–5 ──► 6–15 (front/back split) ──► 16 ──► 17 ──► 18 ──► 19 ──► 20 ──► 21–22 ──► 23 ──► 24 ──► 25 ──► 26–27
                                     │
                                     └──► 28 ──► 29 ──► 30–38 (performance, interleavable) ──► 39
```

- Step 4 (security) blocks everything else.
- Step 16 (PostGIS tests) blocks every upgrade and every performance step that touches SQL.
- Step 21 (new Docker client) blocks step 35 (event-driven scripts).
