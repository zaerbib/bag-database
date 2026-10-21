# Audit — Bag Database

> Audit performed on 2026-10-18 on branch `project-refactoring-with-claude` (base `ee3eed9`, version `3.5.3`), without modifying the code.
> Updated on 2026-10-21: §8 (runtime and deployment, toward embedded Tomcat).

## 1. Summary

- Spring MVC application packaged as a **WAR** for **Tomcat 9 / Java 11**, with an **ExtJS 6.2** front end bootstrapped from **Thymeleaf** templates.
- 94 production Java files, **8 test classes, 27 `@Test` methods**, no coverage measurement.
- Dependencies mostly date from 2017–2022, and several are end-of-life (Spring Boot 2.6, Spring Session 1.x, Hibernate 5.4, H2 1.4).
- The application can only run inside an **external Tomcat 9**. It has no Spring Boot launcher and uses no auto-configuration (see §8).
- **Main risks**:
  - In non-LDAP mode, write endpoints are open to anonymous users, including **Docker script execution**.
  - `@Secured` is inactive.
  - Secrets are exposed through `config/get`.
  - The upload endpoint allows path traversal.
  - A vulnerable H2 version ships inside the WAR.

---

## 2. Technologies and versions

| Area | Technology | Version |
|---|---|---|
| Language / build | Java | 11 |
| | Maven (no wrapper) | 3.8.5 (Docker build image) |
| | Packaging | WAR |
| Runtime | Tomcat (external; the embedded Tomcat from `spring-boot-starter-tomcat` ships in the WAR but is unused) | 9 (`tomcat:9-jdk11`) |
| Framework | Spring Boot (parent only, manual configuration) | 2.6.6 → Spring 5.3 |
| | Spring Security (+ LDAP, messaging) | 5.6.x (managed by Boot) |
| | Spring Session (JDBC) | **1.3.5.RELEASE** |
| | Spring WebSocket / STOMP | 5.3.x |
| | Spring REST Docs | 2.0.6 (asciidoctor ext 2.0.4) |
| Persistence | Hibernate (core, spatial, jpamodelgen, entitymanager) | 5.4.24.Final |
| | HikariCP | 3.4.5 |
| | Liquibase | 4.4.0 (13 changelogs, 1.0 → 2.3) |
| | PostgreSQL / PostGIS | production (driver not declared in the pom) |
| | H2 / H2GIS / JTS | 1.4.195 / 1.3.x / 1.18.2 |
| Server-side views | Thymeleaf / layout-dialect / extras-springsecurity5 | 3.0.15 / 3.1.0 / 3.0.4 |
| Integrations | AWS SDK v2 (S3) | 2.17.4 |
| | docker-java-api (amihaiemil) | 0.0.13 |
| | bag-reader-java (hatchbed) | 2.0.0 |
| | OpenCV (openpnp) | 4.5.1-1 |
| | google-maps-services | 2.0.0 |
| | yamlbeans | 1.15 |
| | Guava / commons-io / commons-compress | 32.0.0 / 2.14.0 / 1.26.0 |
| Tests | JUnit 4 (vintage) + Jupiter | 5.8.2 |
| | Mockito / AssertJ / byte-buddy | 4.4.0 / 3.22.0 / 1.10.10 |
| Front end | ExtJS (classic, Neptune theme) | **6.2.0.981** (2016, GPLv3) |
| | OpenLayers | 4.1.1 |
| | sockjs-client | 1.1.4 |
| | stomp-websocket (Jeff Mesnil) | 2.3.x (2013) |
| | Bootstrap | 3.3.5 (present, not loaded) |
| Maven plugins | asciidoctor / war / resources / tomcat7 / yuicompressor | 2.2.2 / 2.1.1 / 2.7 / 2.2 / 1.5.1 |
| CI | GitHub Actions | `checkout@v2`, `setup-java@v1` |
| Docs | Jekyll (GitHub Pages) | `docs/` |

---

## 3. Outdated or abandoned dependencies

| Dependency | Version | Status | Risk |
|---|---|---|---|
| Spring Boot / Spring Framework | 2.6.6 / 5.3 | OSS support ended (Boot 2.6 since late 2022, Spring 5.3 since 2024) | No more public security fixes |
| Spring Security | 5.6 | EOL; `WebSecurityConfigurerAdapter` removed in 6 | `SecurityConfig` must be rewritten |
| **Spring Session** | **1.3.5** (2017) | Abandoned, not officially compatible with Spring 5.3; the `ExpiringSession` API no longer exists in 2.x | Blocks any version upgrade |
| Hibernate | 5.4.24 | EOL; `hibernate-entitymanager` is obsolete (merged into core) | Hibernate 6 / Jakarta migration required |
| `javax.*` namespace | — | Replaced by `jakarta.*` | Prerequisite for Boot 3 |
| **H2** | **1.4.195** (2017), **compile scope** | Very old, shipped in the production WAR | CVE-2021-42392 and CVE-2022-23221 (critical) |
| H2GIS | 1.3.x | Old, tied to H2 1.4 | Blocks the H2 upgrade |
| docker-java-api (amihaiemil) | 0.0.13 | Abandoned project (~2019) | Does not track the Docker API |
| ldapsdk (Netscape) | 4.1 | Abandoned for decades; apparently unused | Dead code |
| javax.transaction:jta | 1.1 | Obsolete | — |
| tomcat-el-api / tomcat-embed-el | 8.5.15 | Pinned to 8.5 while the runtime is Tomcat 9 | Class conflicts, CVEs |
| byte-buddy | 1.10.10, **compile scope** | Too old for Mockito 4.4; shipped to production | Version conflicts |
| HikariCP | 3.4.5 | Overrides the Boot-managed version (4.x) | — |
| Liquibase | 4.4.0 | Old (2021) | — |
| AWS SDK v2 | 2.17.4 | Old (2021) | — |
| REST Docs | core 2.0.6 / asciidoctor 2.0.4 | Misaligned versions | — |
| maven-war-plugin / resources | 2.1.1 / 2.7 | Very old | — |
| tomcat7-maven-plugin | 2.2 | Abandoned (2013) | — |
| yuicompressor-maven-plugin | 1.5.1 | Abandoned, no ES6 support | Build breaks if the JS uses modern syntax |
| ExtJS | 6.2.0 | Unsupported; GPLv3 or commercial license | Legal risk, no fixes |
| OpenLayers | 4.1.1 | Current version is 10.x | — |
| stomp-websocket | 2.3.x | Abandoned, superseded by `@stomp/stompjs` | — |
| sockjs-client | 1.1.4 | Old | — |
| Bootstrap | 3.3.5 | EOL, XSS CVEs; file present but not loaded | Dead code |
| Stamen tiles (default URL) | — | Service shut down in 2023 | Default map no longer works |
| Java 11 / `openjdk` image | — | `openjdk` images deprecated | — |
| GitHub Actions | checkout@v2, setup-java@v1 | Deprecated (obsolete Node runtime) | CI may break |

> To be confirmed with `mvn versions:display-dependency-updates` and OWASP `dependency-check`.

---

## 4. Existing test coverage

**No coverage measurement**: there is no JaCoCo. There are **no front-end (JS) tests**.

| Test class | `@Test` | Target |
|---|---|---|
| `bags/BagControllerTest` | 12 | `/bags/*` endpoints (+ REST Docs snippets) |
| `scripts/ScriptControllerTest` | 5 | `/scripts/*` endpoints |
| `account/UserServiceTest` | 3 | `UserService` |
| `bags/BagServiceTest` | 3 | A small part of `BagService` (1853 lines) |
| `scripts/ScriptServiceTest` | 1 | `ScriptService` |
| `account/UserAuthenticationIntegrationTest` | 1 | Login |
| `db/SpatialDatabaseTest` | 1 | H2GIS |
| `persistence/BagRepositoryTest` | 1 | Repository |
| **Total** | **27** | |

**Not covered:**
- Storage: `FilesystemBagStorageImpl`, `RecursiveWatcher`, `S3BagStorageImpl`, `S3BagWrapperImpl`
- `BagScanner` (indexing)
- `RunnableScript` (Docker execution)
- `AdminController`, `ConfigController`, `ConfigService`
- `StatusService` and WebSocket push
- `GeocodingService`
- Real LDAP authentication
- Liquibase migrations on PostgreSQL/PostGIS (tests run on H2 only)

**Test biases:**
- **CSRF is disabled** in the `test` profile, so production behavior is not validated.
- Tests are written in JUnit 4 even though Jupiter is on the classpath.

---

## 5. Front-end / back-end coupling

### Server-side templates
The ExtJS application can only start through Thymeleaf (`src/main/webapp/WEB-INF/views`). Templates involved:
- `home/home.html`: application entry point
- `fragments/header.html`: loads all JS and CSS libraries and picks compressed vs. uncompressed JS
- `signin/signin.html`, `ldap/ldap_login.html`: login pages
- `image/image.html`, `error/general.html`

### Injected variables (`th:inline="javascript"` → JS globals)
| Variable | Source | Consumed by |
|---|---|---|
| `useTileMap`, `tileMapUrl`, `tileWidthPx`, `tileHeightPx`, `useBing`, `bingKey` | `Configuration` | `MapWindow.js`, `ConfigWindow.js` |
| `openWithUrls` | `Configuration` | `BagGrid.js`, `BagTreePanel.js`, `ConfigWindow.js` |
| `csrfName`, `csrfToken`, `csrfHeader` | Spring Security `_csrf` | ~13 files (`AdminWindow`, `BagGrid`, `BagStore`, `ScriptStore`, `ScriptWindow`, `LoginWindow`…) |
| `isAdmin` | Thymeleaf `#authorization` | `BagDatabaseViewport.js`, `NavigationButton.js` |
| `loadCompressed` | `config.debugJavascript` | `header.html` |
| `error`, `logout` | request parameters | `signin.html` |

`HomeController` puts **the entire `Configuration` object** into the model.

### Session
- The HTTP session is persisted in the database via JDBC (`@EnableJdbcHttpSession` in `JpaConfig`), with `SessionCreationPolicy.ALWAYS`.
- Login goes through an ExtJS form (`LoginWindow` → `POST signin`) and then `AjaxAuthenticationSuccessHandler`.
- The WebSocket is bound to the HTTP session (`AbstractSessionWebSocketMessageBrokerConfigurer<ExpiringSession>`).

### Shared routes
- URLs are **relative and hard-coded** in the JS: `bags/*` (search, get, update, upload, setTag…), `scripts/*`, `admin/*`, `status/*`, `config/get`, `signin`, `logout`.
- SockJS connects to `window.location.pathname + 'register'`.
- STOMP topics: `/topic/status` (`StatusService`) and `/topic/script_finished` (`ScriptService` → `ScriptResultGrid`).
- The front end and the API must therefore be served **from the same origin and the same path**.

### ExtJS-specific data contract
- Responses follow the `{success, data, totalCount}` format.
- `ExtJsFilter` parses ExtJS grid filters on the server side.
- The DTOs in `support/web` are shaped for ExtJS stores.

### Build
The **ordered** list of JS files for `yuicompressor-maven-plugin` in `pom.xml` must be updated whenever a front-end file is added.

### Client-side authorization
`isAdmin` only shows or hides menu entries. The comment in `header.html` acknowledges this.

---

## 6. Highest-risk areas (by severity)

1. **Unauthenticated remote code execution (non-LDAP mode).**
   - `SecurityConfig` sets `/bags/**` and `/scripts/**` to `permitAll`.
   - An anonymous user can therefore call `scripts/save` then `scripts/run`, which launches **an arbitrary Docker image** with an arbitrary command on the configured `dockerHost` (`RunnableScript`).
   - Only the CSRF token is required, and it is served by the home page.
2. **`@Secured` is inactive.**
   - There is no `@EnableGlobalMethodSecurity`: the annotations on `AdminController`, `ConfigController`, `AccountController` and `ConfigService.setConfiguration` are ignored.
   - Only the URL rule `anyRequest().authenticated()` applies.
   - In LDAP mode, **every authenticated LDAP user is effectively an admin**.
3. **Secret leakage.**
   - `POST config/get` returns the full `Configuration`: `jdbcPassword`, `adminPassword`, `ldapBindPassword`, `googleApiKey`, `bingKey`.
   - These secrets are stored in plain text in `settings.yml`, generated by `entrypoint.sh`.
   - The JDBC URL and username are written to the logs (`JpaConfig`, `ConfigController`).
4. **Path traversal on upload.**
   - In `FilesystemBagStorageImpl.uploadBag`, `Paths.get(targetDirectory).normalize()` keeps leading `..` segments.
   - `file.getOriginalFilename()` is not sanitized.
   - Files can thus be written outside `basePath`, and `/bags/upload` is reachable by anonymous users.
5. **Broken uncommitted local change in `BagService.java` (~line 1120).**
   - `root.join(String.valueOf(root.get("messageTypes")))` and the neighboring lines pass the `toString()` of a `Path` object instead of the attribute name.
   - Example: `tagJoin.get(String.valueOf(root.get("tag")))`, whereas `Bag` has no `tag` attribute.
   - Full-text search on messageType, tags and topicName **will fail at runtime**.
   - Revert to the JPA metamodel (`Bag_.messageTypes`, `MessageType_.name`…) or to string literals.
6. **Blocking upgrade debt.**
   - Spring Session 1.x, Spring 5, Security 5 (`WebSecurityConfigurerAdapter`), Hibernate 5 and `javax.*` are intertwined.
   - Any security fix requires a combined migration to Boot 3 / Jakarta / Security 6.
7. **Monolithic `BagService`.**
   - 1853 lines and 3 tests, at the core of indexing.
   - JPA predicates are built from client-supplied field names (`root.get(field)`, `path.get(filter.getProperty())`) without an allow-list.
8. **Vulnerable H2 in the WAR and silent fallback.**
   - If the configured driver is not `org.postgresql.Driver`, `JpaConfig` silently switches to an embedded H2GIS database.
9. **Frozen front end.**
   - ExtJS 6.2 is unsupported and its license needs to be checked.
   - There is no JS build, and minification relies on an abandoned tool.
   - Third-party libraries are manually vendored in `resources/js/external`.
10. **File watcher and scanner.**
    - `RecursiveWatcher`, `BagScanner` and S3 storage are untested.
    - A regression can trigger massive re-indexing: changing a `storageId` re-indexes the entire backend.

---

## 7. Priority recommendations

1. Restrict `/scripts/**` and `POST /bags/**` (update, upload, setTag, removeTags) to admins, and enable `@EnableGlobalMethodSecurity(securedEnabled = true)`.
2. Stop returning secrets from `config/get` (dedicated DTO, `@JsonIgnore`).
3. Validate the upload path: resolve it, then check `startsWith(basePath)`, and sanitize the file name.
4. Revert or fix the in-progress change to `BagService.java`.
5. Move H2/H2GIS and byte-buddy to `test` scope, and remove `ldapsdk`, `tomcat-el` 8.5 and Bootstrap if unused.
6. Add JaCoCo, then tests for route security, upload, scripts and storage.
7. Plan the migration to Spring Boot 3 / Jakarta / Spring Session 3 / Hibernate 6, then the front-end strategy (decouple from Thymeleaf via an API and configuration exposed as JSON).
8. Update CI (`actions/checkout@v4`, `setup-java@v4`) and Docker images (`eclipse-temurin`).
9. Run on Spring Boot's embedded Tomcat:
   - first as an executable WAR that keeps the explicit configuration;
   - then hand each subsystem over to Boot auto-configuration;
   - switch to a JAR only once server-side rendering is gone.

   See §8 and plan steps E1–E4 and 27.

---

## 8. Runtime and deployment

The application is a classic Servlet 3.0 WAR started by an **external Tomcat 9**. Spring Boot is only used as the dependency-management parent.

| Aspect | Today | Impact of moving to embedded Tomcat |
|---|---|---|
| Bootstrap | `WebAppInitializer` (`AbstractAnnotationConfigDispatcherServletInitializer`) builds a root context and a servlet context | Boot does **not** call `WebApplicationInitializer`s in embedded mode |
| Filters | `CharacterEncodingFilter` (UTF-8, forced); `SpringSessionInitializer` registers the session repository filter; `SecurityInitializer` registers the security chain and inserts `MultipartFilter` before it | Each one must become a registration bean, in the same order. Boot also maps every `Filter` bean to `/*` automatically |
| Contexts | `WebMvcConfig` is listed in **both** contexts. Controllers, `@EnableAsync` / `@EnableScheduling` and `@PostConstruct` hooks such as `AdminController.checkAdminAccount` are therefore created twice | A single Boot context creates them once. This is a visible behavior change and must be named |
| Error page | `<error-page>` in `WEB-INF/web.xml` → `/generalError` | Embedded Tomcat ignores `web.xml`; an `ErrorPageRegistrar` is needed |
| Profile | `spring.profiles.active=default` is set as a `DispatcherServlet` init parameter | Becomes a property |
| Uploads | A 50 GB `MultipartConfigElement` on the `DispatcherServlet` | The same bean is picked up by `DispatcherServletAutoConfiguration`. Tomcat's swallow and form-post limits must be checked |
| Views and resources | Thymeleaf templates and i18n messages under `/WEB-INF/`, static files under `/resources/` (servlet-context resources in `src/main/webapp`) | Works from an executable **WAR**. A JAR needs them moved to the classpath, which is easiest after the front-end split |
| Server settings | `src/main/docker/server.xml`: port 8080, 150 threads, 20 s timeout, gzip compression, access log, `LockOutRealm` | Become `server.*` / `server.tomcat.*` properties |
| Context path | `entrypoint.sh` renames `ROOT.war` after `BAGDB_PATH` | `server.servlet.context-path` |
| Docker | `tomcat:9-jdk11` with `catalina.sh run`; logs in `/usr/local/tomcat/logs` | A JRE image with `exec java -jar` |
| Build | `spring-boot-starter-tomcat` in compile scope (embedded Tomcat jars ship unused); `tomcat-el-api` / `tomcat-embed-el` pinned to 8.5.15; `tomcat7-maven-plugin` 2.2; no `spring-boot-maven-plugin` | `provided` scope plus `spring-boot-maven-plugin` `repackage`; remove the 8.5 pins and the tomcat7 plugin |
| Auto-configuration | None. DataSource, JPA, Liquibase, Spring Session 1.3.5 (`@EnableJdbcHttpSession`), Thymeleaf, MVC (`@EnableWebMvc`) and Security are wired by hand | Turning on `@SpringBootApplication` in one go would conflict with the hand-wired beans: a second Liquibase run, a second DataSource or Thymeleaf resolver, and a session auto-configuration written for Spring Session 2.x. Migrate one subsystem at a time |
| Native code | OpenCV is loaded with `nu.pattern.OpenCV.loadShared()` | Must be checked when loading from a nested JAR inside the executable WAR |

**Options compared:**
- **Embedded Tomcat with the explicit configuration (executable WAR).**
  - Small, behavior-preserving change.
  - The WAR still deploys to an external Tomcat, so it is reversible.
  - It does not depend on the Spring Session or Security upgrades.
- **Full Boot auto-configuration in one step.**
  - Collides with every hand-wired subsystem.
  - Needs Spring Session 2.x first.
  - Cannot be reverted piece by piece.
- **Executable JAR right away.**
  - Blocked by the `/WEB-INF/` views until server-side rendering is removed.

**Recommendation:** the first option now, then progressive auto-configuration, then optionally a JAR. These are plan steps E1–E3, then E4, then 27.
