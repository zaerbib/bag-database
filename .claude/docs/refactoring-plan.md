# Refactoring Plan — Bag Database

> Derived from [`audit.md`](audit.md). Target order: **(0) safety net → (1) separate front end and back end → (2) upgrade one major version at a time → (3) rewrite hot paths for performance with identical behavior.**

> **Step numbering:** steps E1–E4 (embedded Tomcat, auto-configuration) were added after the plan was written. They use an `E` prefix so that the existing step numbers, which skills and `/next-step` refer to, stay stable. Follow the document order and the dependency map, not the numbers.

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

## [x] Phase 0 — Safety net (before touching structure)

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

- [x] **4. Close the critical security holes. Each item is a separate PR.**
  - [x] 4a. Enable method security (`@EnableGlobalMethodSecurity(securedEnabled = true)`). Require `ROLE_ADMIN` on `scripts/save|run|delete`, and authentication on `bags/update|upload|setTag|setTagForBags|removeTags`.
  - [x] 4b. Stop serializing secrets: `jdbcPassword`, `adminPassword`, `ldapBindPassword`, API keys.
    - `config/get` returns a DTO without them.
    - Saving keeps the existing value when the field is empty.
    - `account/current` returns a DTO, not the `Account` entity.
  - [x] 4c. Upload path validation: resolve against `basePath`, reject if the result does not `startsWith(basePath)`, and sanitize `getOriginalFilename()`.
    - Also add a URL-level `authenticated()` rule for `POST /bags/upload` (and the other write endpoints) before the `/bags/**` `permitAll`: the method-level check from 4a only runs after the multipart body has been fully received.
  - [x] 4d. Allow-list the field names accepted by search and `ExtJsFilter` before calling `root.get(field)`.
  - *Test:* `WebSecurityConfigurationAware` tests that check anonymous → 401/403 and admin → 200, plus a traversal test with `../../etc`.
  - *Rollback:* revert the individual PR.

- [x] **5. Dependency hygiene (no version jumps).**
  - Remove `ldapsdk`, `javax.transaction:jta`, `tomcat-el-api` / `tomcat-embed-el` 8.5, and the forced versions of `byte-buddy`, `HikariCP` and `objenesis` (let Boot manage them).
  - Move `h2`, `h2gis-*` and `org.osgi.service.jdbc` to `test` scope.
    - Make `JpaConfig` fail fast instead of silently falling back to H2 in production.
  - Delete the unused `bootstrap.min.js`.
  - *Test:* full test suite, plus a Docker compose smoke test (app starts, bags are listed).
  - *Rollback:* revert the `pom.xml` diff.

---

## [x] Phase 0b — Run on Spring Boot with embedded Tomcat (still on the current stack)

Goal: the application starts with `java -jar bag-database.war` on Spring Boot's embedded Tomcat, and the Docker image no longer needs a Tomcat installation. During the transition, the same WAR can still be deployed to an external Tomcat. See [`audit.md` §8](audit.md#8-runtime-and-deployment).

**Chosen approach: executable WAR, explicit configuration first, auto-configuration later.**
- **Embedded Tomcat now, Boot auto-configuration later (step E4).**
  - The application wires DataSource, JPA, Liquibase, Spring Session 1.3.5, Thymeleaf, MVC (`@EnableWebMvc`) and Security by hand.
  - Enabling `@SpringBootApplication` in one go would make Boot's auto-configurations compete with those beans: a second Liquibase run, a second DataSource or Thymeleaf resolver, and a `SessionAutoConfiguration` written for Spring Session 2.x.
  - So E1 imports **only** the auto-configurations needed to run the embedded server, and behavior stays identical.
  - E4 then hands each subsystem to Boot in its own PR, once its prerequisites (steps 16, 17, 19) are done.
- **Executable WAR, not JAR.**
  - Thymeleaf views and i18n messages are servlet-context resources under `/WEB-INF/`, and static files are served from `/resources/` in `src/main/webapp`. This only works from a WAR.
  - An executable WAR runs with `java -jar` **and** still deploys to an external Tomcat (`docs/installation/tomcat.md`), so every step stays reversible.
  - Moving to a JAR waits until step 13 removes server-side rendering (step 27).
- **Why before Phase 1:**
  - The Phase 1 deliverables (the front-end build in step 12, the split compose in step 14, the Playwright smoke tests) should target the final runtime.
  - The Boot 3 upgrade (step 23) then brings Tomcat 10.1 automatically, with no base-image migration.
- **Why after Phase 0:**
  - E1 re-registers the security, session and multipart filters, so the access-control tests from step 4 (including 4c's upload rule) must already exist.
  - Step 5 removes the `tomcat-el` 8.5 pins, which would clash with the embedded Tomcat 9.

- [x] **E1. One Spring Boot bootstrap, two launch modes.** *(Checked by decision on 2026-10-22 with one open item: the external-Tomcat smoke test, see Results.)*
  - Add `BagDatabaseApplication`:
    - annotated `@SpringBootConfiguration`, with `@Import` of the six config classes;
    - `@ImportAutoConfiguration` limited to `ServletWebServerFactoryAutoConfiguration`, `DispatcherServletAutoConfiguration` and `WebSocketServletAutoConfiguration`;
    - extends `SpringBootServletInitializer` and has a `main` method.
  - Replace the three `WebApplicationInitializer`s (`WebAppInitializer`, `SecurityInitializer`, `SpringSessionInitializer`), which Boot does **not** call in embedded mode, with registration beans.
    - Keep today's filter order: `CharacterEncodingFilter` (UTF-8, forced) → `springSessionRepositoryFilter` → `MultipartFilter` → `springSecurityFilterChain` → `DispatcherServlet`.
    - The `DispatcherServlet` keeps its mapping `/`, `defaultHtmlEscape=true` and the 50 GB `MultipartConfigElement`.
    - Boot registers every `Filter` bean on `/*` automatically. List them, and disable (`FilterRegistrationBean.setEnabled(false)`) any that must not be mapped twice.
  - Replace the `<error-page>` in `web.xml` with an `ErrorPageRegistrar` (→ `/generalError`), then delete `web.xml`.
  - Replace the `spring.profiles.active=default` init parameter with `spring.profiles.default=default`.
  - Merge the root and servlet contexts into one.
    - Today `WebMvcConfig` is loaded in **both**, so controllers and `@PostConstruct` hooks such as `AdminController.checkAdminAccount` run twice.
    - After the merge they run once. Name this behavior change in the PR.
  - `pom.xml`:
    - add `spring-boot-maven-plugin` (`repackage`);
    - change `spring-boot-starter-tomcat` to `provided`, so it moves to `WEB-INF/lib-provided` and an external Tomcat does not load a second Tomcat;
    - remove `tomcat7-maven-plugin`.
  - `WebAppConfigurationAware` lists the new application class instead of the six config classes.
  - The Docker image is unchanged in this step (still `tomcat:9` + `ROOT.war`). Only the bootstrap changes.
  - *Test:*
    - The full suite, the contract tests and the access-control tests pass unchanged.
    - A new `@SpringBootTest(webEnvironment = RANDOM_PORT)` smoke test on the `test` profile checks that:
      - `/` and a `/resources/**` file are served;
      - `/register/info` (SockJS) answers;
      - an unknown URL renders the general error page;
      - the session is created by Spring Session (JDBC);
      - an anonymous `POST /bags/upload` is refused.
    - A filter-order assertion: session, then multipart, then security.
    - A Docker compose smoke test on the unchanged `tomcat:9` image proves the external-Tomcat mode still works.
  - *Results (2026-10-22):*
    - [x] Full suite, contract and access-control tests unchanged; `EmbeddedServerTest` (embedded smoke test and filter order) passes.
    - [ ] External-Tomcat smoke test on `tomcat:9`: declined, not run. Since E3 the Docker image no longer uses an external Tomcat, so this needs a WAR deployed to a standalone Tomcat 9.
  - *Rollback:* revert the PR; the three initializers and `web.xml` come back.

- [x] **E2. Tomcat settings as Spring Boot properties.**
  - Move what `src/main/docker/server.xml` configures into `application.properties`:
    - `server.port=8080`;
    - `server.tomcat.threads.max=150`, `server.tomcat.threads.min-spare=25`;
    - `server.tomcat.connection-timeout=20s`;
    - `server.compression.enabled=true`, `min-response-size=2KB` and the same MIME types;
    - `server.tomcat.accesslog.*` with the same pattern, `%h %l %u %t "%r" %s %b`.
  - Large uploads: set `server.tomcat.max-swallow-size=-1` and check `max-http-form-post-size`, so that 50 GB multipart uploads behave as they do today.
  - Add `server.shutdown=graceful`, so that `docker stop` lets running requests finish.
  - `LockOutRealm` / `UserDatabaseRealm` and the shutdown port are not carried over; the application does its own authentication.
  - *Test:*
    - An integration test on the embedded server checks that:
      - a JSON response larger than 2 KB is gzip-encoded;
      - the access log is written;
      - a multipart upload larger than Tomcat's 2 MB form limit succeeds.
    - Manual run of `java -jar target/bag-database-<version>.war` against a local PostGIS.
  - *Rollback:* remove the properties. The embedded defaults apply again, and an external Tomcat is not affected.

- [x] **E3. The Docker image runs the embedded server.** *(Checked by decision on 2026-10-22 with two open items: the S3 and webviz smoke tests, see Results.)*
  - Tag `pre-E3` first.
  - `docker/build/Dockerfile`:
    - the build stage becomes `maven:3.9-eclipse-temurin-11`;
    - the runtime image becomes `eclipse-temurin:11-jre` plus `ffmpeg` and `perl`;
    - the WAR is copied to `/app/bag-database.war`.
  - `entrypoint.sh` ends with `exec java $JAVA_OPTS -jar /app/bag-database.war`. The `exec` lets the JVM receive `SIGTERM`.
    - `BAGDB_PATH`, which today renames `ROOT.war`, becomes `--server.servlet.context-path=/${BAGDB_PATH}`.
    - Keep `/usr/local/tomcat/logs` as the access-log directory, or document the new path, so that existing volumes keep working.
  - Delete `src/main/docker/server.xml`.
  - Update every `docker/*/docker-compose.yml`, `docs/installation/docker/*` and the README. `docs/installation/tomcat.md` stays valid, because the WAR still deploys to an external Tomcat.
  - *Test:*
    - A compose smoke test for each example (no auth, LDAP, S3, reverse proxy with a context path, webviz) checks that:
      - the application starts and bags are listed;
      - upload works;
      - the status WebSocket connects;
      - image and video endpoints work (OpenCV native loading from the executable WAR).
    - `docker stop` shuts down gracefully.
    - Compare the image size with the old one.
  - *Results (2026-10-22, examples reproduced with `docker run` against the locally built image):*
    - [x] No auth: starts, OpenCV loads, bags listed, SockJS info and WebSocket upgrade (101), logs in `/usr/local/tomcat/logs`.
    - [x] LDAP: anonymous requests redirected to `/ldap_login`, wrong password refused, LDAP user `jdoe` logs in (form + CSRF), bags listed.
    - [x] Reverse proxy (Traefik, `BAGDB_PATH=bagdb`): `/bagdb/` served through the proxy, bags listed, WebSocket upgrade (101) through the proxy, `/` is 404. `BAGDB_PATH=a#b/` serves `/a/b`.
    - [ ] S3: not run. Its `settings.yml` (with credentials) must be written by hand, which the project's credential-file hook forbids.
    - [ ] Webviz: not run. The IDE guard refused the Docker socket mount for Traefik and reading `docker/webviz/*.env` (credentials). Its TLS/ACME setup also needs a public host name.
    - [x] Upload, image and video endpoints: skipped by decision (no sample `.bag` in the repository).
    - [x] `docker stop`: graceful shutdown completed in 1 s.
    - [x] Image size: 1.86 GB (was 2.16 GB on `tomcat:9-jdk11`).
  - *Rollback:* redeploy the `pre-E3` image; reverting the Dockerfile brings back `tomcat:9`.

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
    - a reverse proxy for `/api` and `/api/ws` to the back end (embedded Tomcat since E3).
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
  - Compiler `release 17`, Docker build `maven:3.9-eclipse-temurin-17`, runtime `eclipse-temurin:17-jre` (embedded Tomcat since E3), CI on Temurin 17.
  - *App impact:* check OpenCV native loading and the `--add-opens` flags needed by older libraries.
  - *Test:* full suite; image and video endpoints (OpenCV/ffmpeg).
  - *Rollback:* revert the images and compiler flag.

- [ ] **19. Spring Boot 2.6 → 2.7.**
  - Remove the explicit `hibernate.version` (Boot 2.7 manages Hibernate 5.6), the Liquibase pin and the Thymeleaf leftovers.
  - *App impact:* rewrite `SecurityConfig` from `WebSecurityConfigurerAdapter` to a `SecurityFilterChain` bean, and switch to `@EnableMethodSecurity`. This is the Security 6 style, but on 5.7.
  - *Test:* the security tests from step 4 plus the LDAP tests.
  - *Rollback:* tag `pre-19`.

- [ ] **E4. Hand the infrastructure over to Spring Boot auto-configuration (one PR per subsystem).**
  - Goal: replace the hand-built infrastructure beans with Boot auto-configuration and `spring.*` properties. `settings.yml` stays the user-facing configuration file.
  - Prerequisites: E1 (Boot bootstrap), 16 (PostGIS tests), 17 (Spring Session 2.x) and 19 (`SecurityFilterChain`).
  - [ ] DataSource, Hikari and JPA.
    - An `EnvironmentPostProcessor` maps `settings.yml` (`driver`, `jdbcUrl`, `jdbcUsername`, `jdbcPassword`) to `spring.datasource.*`.
    - The hand-built DataSource and `EntityManagerFactory` in `JpaConfig` give way to `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` (`spring.jpa.*`, Hibernate Spatial dialect).
  - [ ] Liquibase.
    - Use `spring.liquibase.change-log`, and delete `LiquibaseConfig`.
    - The changelog path and ids must stay identical, so that `DATABASECHANGELOG` is not re-run.
  - [ ] Spring Session: `spring.session.store-type=jdbc`; remove `@EnableJdbcHttpSession` and the manual filter registration from E1.
  - [ ] Executors and scheduling.
    - Use `spring.task.execution.*` / `spring.task.scheduling.*` instead of `mvcTaskExecutor`.
    - Keep the 1 h async timeout (`spring.mvc.async.request-timeout`).
  - [ ] Switch the application class to `@SpringBootApplication`.
    - What stays hand-configured remains explicit beans: Security (`SecurityFilterChain` from step 19), the WebSocket broker, and MVC (`@EnableWebMvc` makes `WebMvcAutoConfiguration` back off until the front end no longer depends on it).
    - Every auto-configuration that would still conflict is listed in `exclude`, with a comment saying why.
  - Thymeleaf is not migrated, because step 13 removes it.
  - *Test:*
    - Each sub-step runs the full suite on H2 and PostGIS, the contract tests and the E1 smoke test.
    - The `--debug` condition report is reviewed for unexpected matches.
    - Startup logs no bean-definition overriding.
  - *Rollback:* revert the single sub-step PR.

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
  - The embedded Tomcat moves to 10.1 with Boot 3. After E3 there is no base-image change. Installations on an external Tomcat must move to Tomcat 10.1 (update `docs/installation/tomcat.md`).
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

- [ ] **27. Optional: executable JAR (ends external-Tomcat support).**
  - The embedded Tomcat, the removal of `WebAppInitializer` and `web.xml`, and the auto-configuration are done in E1–E4. This step only changes the packaging.
  - Prerequisites: step 13 (no Thymeleaf views left under `/WEB-INF`) and E4.
  - Move what remains in `src/main/webapp` (static front end, error page, i18n messages) to `classpath:/static` and `classpath:/`.
  - Switch the packaging to `jar`, and drop `SpringBootServletInitializer`.
  - Deprecate `docs/installation/tomcat.md` for one release before deleting it.
  - *Test:* the E1 smoke test and the Playwright smoke test against `java -jar bag-database.jar` and the Docker image.
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
1–5 ──► E1 ──► E2 ──► E3 ──► 6–15 (front/back split) ──► 16 ──► 17 ──► 18 ──► 19 ──► E4 ──► 20 ──► 21–22 ──► 23 ──► 24 ──► 25 ──► 26–27
                                                          │
                                                          └──► 28 ──► 29 ──► 30–38 (performance, interleavable) ──► 39
```

- Step 4 (security) blocks everything else.
- E1 needs steps 4 and 5: it re-registers the security, session and multipart filters, and step 5 removes the `tomcat-el` 8.5 pins.
- E4 needs steps 16, 17 and 19. Step 27 (JAR packaging) needs steps 13 and E4.
- Step 16 (PostGIS tests) blocks every upgrade and every performance step that touches SQL.
- Step 21 (new Docker client) blocks step 35 (event-driven scripts).
