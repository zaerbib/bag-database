---
name: version-upgrade
description: Bump, remove or replace exactly one dependency, platform or tool version (Maven dependency/plugin, Spring Boot/Session/Security, Hibernate, JUnit, docker-java, JDK, Docker base images, GitHub Actions, vendored front-end libs) with baseline, app-impact checklist, full verification and rollback. Use for refactoring-plan steps 5, 17-26, E3 (Docker runtime moves to embedded Tomcat), E5 (remove external-Tomcat deployment), E6 (WAR to executable JAR) and E4 (Boot auto-configuration). Not for schema-only changes (liquibase-changeset) or tuning (performance-change).
---

# One-at-a-time version upgrade

Phase 2 rule (`.claude/docs/refactoring-plan.md`): each step bumps **one** thing and runs:
- the full suite;
- the contract tests;
- PostGIS tests (after step 16);
- the smoke tests.

## Where versions live
- `pom.xml`:
  - `<parent>` spring-boot-starter-parent, currently 2.6.6;
  - `<hibernate.version>`, currently 5.4.24.Final, also used by the `hibernate-jpamodelgen` annotation processor;
  - `maven-compiler-plugin` `<source>`/`<target>`, currently 11;
  - per-dependency `<version>` pins.
- `docker/build/Dockerfile`: the Maven build image and the runtime image.
  - The runtime image is `eclipse-temurin:<jdk>-jre`, running `java -jar` (since plan step E3; before it was `tomcat:9-jdk11` with `ROOT.war`).
  - There is no Tomcat installation anywhere: Tomcat is embedded and Spring Boot manages its version, so a Tomcat upgrade is a Spring Boot upgrade. External-Tomcat deployment was removed in E5, and the build produces a JAR from E6 on.
- `.github/workflows/maven.yml`: `actions/checkout@v2`, `actions/setup-java@v1`, `java-version: 1.11`.
- Vendored JS: `src/main/webapp/resources/js/external` until E6, then `src/main/resources/static/resources/js/external`. It is loaded from the `fragments/header.html` template; after step 13, from the static `index.html`.

## Procedure
1. **Baseline** on a clean working tree.
   - Run `mvn -B package`.
   - Record the test count from `target/surefire-reports/TEST-*.xml`: sum `tests` minus `skipped`.
   - Save `mvn dependency:tree` output to a scratch file under `target/` (build output, not committed; the IDE guard refuses writes outside the project). Do not run `mvn clean` before the comparison.
2. **Tag the major upgrades** (steps 17, 19, 23). Propose `git tag pre-<step>` and create it only after the user confirms.
3. **Bump exactly one thing.** When the plan says so, remove pins that Boot already manages (step 5: HikariCP, byte-buddy, objenesis).
4. **App-impact checklist.**
   - Copy the step's *App impact* bullets from the plan into your plan and the PR description, then resolve each one.
   - For OpenRewrite steps (20, 23), use the recipe the plan names. The OpenRewrite Maven plugin is not in the pom, so ask before adding it.
   - Confirm that the production bootstrap and the test `WebAppConfigurationAware` still list matching config classes (CLAUDE.md). The bootstrap is `WebAppInitializer` before plan step E1, and `BagDatabaseApplication` after it.
5. **Compile and diff.**
   - Run `mvn -B -DskipTests package` and fix compile errors.
   - Re-run `mvn dependency:tree`. Look for unexpected transitive changes and for mixed `javax.*` and `jakarta.*` artifacts.
6. **Verify.**
   - `mvn -B package`: the test count must be **≥ baseline**. For step 20 it must be exactly equal.
   - Contract tests: see the **characterization-tests** skill.
   - From step 16 on, the PostGIS Testcontainers profile.
   - Smoke test with the relevant `docker/*/docker-compose.yml`, **only with user approval** (Docker pulls and runs). Step-specific checks:
     - image and video endpoints for JDK and OpenCV bumps;
     - LDAP (`test_ldap` profile) for security changes.
   - From step 12 on, the Playwright smoke test.
7. **Vendored front-end libraries** (step 26). Replace the file in `resources/js/external`, update its `<script>` tag, and run the front-end smoke checklist from the **front-back-separation** skill.
8. **Failures.**
   - If the fix needs a second version bump, stop. Propose it as its own PR or plan sub-step.
   - If the test count drops, find the missing tests before going on. For step 20, check that `junit-vintage-engine` removal did not orphan JUnit 4 tests.
   - Rollback is a revert of the single bump, or a redeploy of the `pre-<step>` image.
9. **Record.** Add the old → new version to `CHANGELOG.md`.
10. **Schema changes.** If the upgrade needs one (step 17 session tables), do it through the **liquibase-changeset** skill.

## Safety
- No `git tag`, `git push`, Docker pull or compose run without user confirmation.
- Never combine two major upgrades in one PR.
