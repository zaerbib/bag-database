---
name: characterization-tests
description: Pin existing API behavior with MockMvc contract tests before a change and prove it is unchanged after; also access-control (anonymous/user/admin, CSRF) tests. Use for refactoring-plan steps that mention "contract tests", "add the test before the change", security fixes, or "JSON unchanged" (steps 1, 3, 4a-d, E1, E2, E5, E6, 6, 9, 10, 13, 15, 29, 30, 32, 37). Not for performance measurements (performance-change) or schema migrations (liquibase-changeset).
---

# Characterization / contract tests

The contract tests from plan step 3 are the regression oracle for every later step
(`.claude/docs/refactoring-plan.md`, ground rule "Behavior baseline"). Write and run them the same way every time.

## Project facts
- Test base classes, in `src/test/java/com/github/swrirobotics/config/`:
  - `WebAppConfigurationAware`: JUnit 4, `@ActiveProfiles("test")`, MockMvc and REST Docs snippets written to `target/generated-snippets`.
  - `WebSecurityConfigurationAware`: extends `WebAppConfigurationAware` and adds Spring Security.
- Examples to copy:
  - `bags/BagControllerTest`: services mocked with `@MockBean`; POSTs send `.with(csrf())`; snippets via `document("bags/{method-name}", ...)`.
  - `scripts/ScriptControllerTest`.
- The `test` profile **disables CSRF** (`SecurityConfig.configure`, `if (profileSet.contains("test")) http.csrf().disable()`). The `test_ldap` profile enables LDAP mode (`UserAuthenticationIntegrationTest`).
- ExtJS response envelope: `{success, data, totalCount}` (DTOs in `support/web`).

## Procedure
1. **Inventory the surface the step touches.**
   - Grep `@RequestMapping|@GetMapping|@PostMapping` in `src/main/java/com/github/swrirobotics/**/*Controller.java`.
   - Grep the JS callers in `src/main/webapp/resources/js`: look for `url:` and literals like `'bags/` or `'scripts/`.
   - Produce a list of (HTTP method, path, params, JS caller).
2. **Locate or create the test class** in the controller's package under `src/test/java/com/github/swrirobotics/<pkg>/`.
   - Extend `WebAppConfigurationAware`, or `WebSecurityConfigurationAware` for auth.
   - Use `org.junit.Test` until plan step 20 migrates to JUnit 5.
   - Use the SwRI BSD license header (copy it from a neighbouring file) and `my`-prefixed fields.
3. **Pin the contract** with `jsonPath`:
   - Assert the status, the content type, `$.success` and `$.totalCount`.
   - Assert the **exact field names** under `$.data`. For lists, assert the full key set of one element, so added or removed fields are caught.
   - Keep `document(...)` ids stable. `src/main/asciidoc/*.adoc` includes them, and a renamed snippet breaks `asciidoctor` during `prepare-package`.
4. **Characterize before changing.** Run against the *unmodified* production code:
   ```
   mvn test -Dtest=<ClassName>
   ```
   The tests must pass. If a test fails, the test is wrong: fix the test. Never touch production code in this pass.
5. **Access matrix** (security steps). For every endpoint, cover:
   - Anonymous: 401/403, or record the current redirect.
   - Authenticated user: record the current status.
   - `ROLE_ADMIN`: 200.
   - Upload traversal (step 4c): a filename such as `../../etc/x` and a target directory starting with `..`.
   - CSRF is **not** proven by `test`-profile tests. State this in the PR until step 8 adds a CSRF-enabled test profile.
6. **Apply the step's change**, then run:
   ```
   mvn test -Dtest=<ClassName>
   mvn -B package
   ```
   `mvn -B package` runs the whole suite, generates REST Docs and builds the WAR.
7. **Failures after the change.**
   - A contract test that passed before and fails now is a behavior change. **Stop and report it.**
   - Change an assertion only when the plan step intends the contract change (e.g. 4b removes secrets from `config/get`; 13 drops non-`/api` routes). Name that change in the commit message.
8. **Prefixes and databases:**
   - From step 10, run contract tests against both the `""` and `/api` prefixes (parameterize the base path).
   - From step 13, run them against `/api` only.
   - From step 16, they must also pass under the PostGIS Testcontainers profile. Read its name from the pom or test config once step 16 is merged.

## Safety
- Never weaken or delete an assertion to make a refactor pass without explicit user approval.
- Do not lower the total test count.
