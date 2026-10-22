---
name: java-upgrade
description: Upgrade the JDK from 11 to 21 LTS in two separate PRs - 11 -> 17 (refactoring-plan step 18) and 17 -> 21 (step 25) - covering pom compiler release, Docker build/runtime images, CI, bytecode-library compatibility (byte-buddy, Mockito, Hibernate, JaCoCo), strong encapsulation (--add-opens), OpenCV native loading and ffmpeg/image endpoints. Use with the generic version-upgrade skill. Not for Spring Boot / Jakarta upgrades (steps 19, 23, 24) or for jumping 11 -> 21 in one PR.
---

# Java upgrade 11 → 17 → 21 (LTS)

The JDK moves in **two hops, one PR each**, in the order set by `.claude/docs/refactoring-plan.md`:

| Hop | Plan step | Runs on | Runtime image |
|---|---|---|---|
| 11 → 17 | **18** | Boot 2.6 / Spring 5.3 / Hibernate 5.4 (after Spring Session 2, step 17) | `eclipse-temurin:17-jre`, embedded Tomcat 9 |
| 17 → 21 | **25** | Boot 3.x / Spring 6 / Hibernate 6 (after steps 23–24) | `eclipse-temurin:21-jre`, embedded Tomcat 10.1 |

Both hops come after plan step E3, which moves the Docker image to a plain JRE running `java -jar` on Spring Boot's embedded Tomcat. A JDK hop therefore changes only the JRE. The Tomcat version comes from Spring Boot, so it never changes in these PRs.

Never jump 11 → 21 directly. On Java 21, the Boot 2.6 / Hibernate 5.4 / byte-buddy 1.10 stack is not expected to work: those libraries predate Java 21 class files. Verify this yourself before relying on it.

This skill adds the JDK-specific checks. The generic upgrade procedure comes from the **version-upgrade** skill: baseline test count, `pre-<step>` tag, one bump, `CHANGELOG.md`, rollback. Contract tests come from **characterization-tests**.

## Where the Java version lives (verified)
| File | Current value | Hop 18 target | Hop 25 target |
|---|---|---|---|
| `pom.xml`, `maven-compiler-plugin` 3.10.1 (`<inherited>false</inherited>`) | `<source>11</source><target>11</target>` | `<release>17</release>` (replaces source/target) | `<release>21</release>` |
| `docker/build/Dockerfile`, build stage | `maven:3.8.5-openjdk-11` | `maven:3.9-eclipse-temurin-17` | `maven:3.9-eclipse-temurin-21` |
| `docker/build/Dockerfile`, runtime stage | `eclipse-temurin:11-jre` (after E3; `tomcat:9-jdk11` before it) | `eclipse-temurin:17-jre` | `eclipse-temurin:21-jre` |
| `.github/workflows/maven.yml` | `setup-java@v1`, `java-version: 1.11` | `setup-java@v4`, `distribution: temurin`, `java-version: 17` | `java-version: 21` |

The image tags come from the plan. **Confirm each tag exists on Docker Hub** before committing.

Also grep for other references and update any that pin the JDK:
- `docker/*/docker-compose.yml`
- `CLAUDE.md` ("Requires JDK 11")
- `docs/` installation pages

Editing `CLAUDE.md` and `docs/` requires the user's approval.

## JDK-sensitive spots in this codebase (verified)
1. **OpenCV native loading.** `BagService` calls `nu.pattern.OpenCV.loadShared()` in a static block and only **logs a warning** on failure. The block is at `src/main/java/com/github/swrirobotics/bags/BagService.java:126-136`; the dependency is `org.openpnp:opencv` 4.5.1-1.
   - openpnp documents `loadShared()` as unsupported on Java 12+, with `loadLocally()` as the replacement. Confirm this against the library's README or source for the version in use.
   - If it is true, image endpoints for Bayer and other OpenCV-decoded formats degrade **silently**.
   - Check the startup log for `Unable to load OpenCV`.
   - Since E1/E3, the library is loaded from a nested JAR inside the executable archive (`WEB-INF/lib` of the WAR, `BOOT-INF/lib` of the JAR after E6). Check the container, not only `mvn test`, because the tests use an exploded classpath.
   - If it fails, switch to `nu.pattern.OpenCV.loadLocally()` in its own small commit, and add a test that asserts OpenCV loaded, e.g. `new org.opencv.core.Mat()` does not throw.
2. **Bytecode libraries pinned too old.** These must support the target class-file version (61 for Java 17, 65 for Java 21):
   - `byte-buddy` is pinned at **1.10.10, compile scope** (pom ~line 325). Hibernate 5.4 proxies and Mockito 4.4 use it at runtime.
     - Plan step 5 removes the pin, so Boot manages it. If step 5 is not merged, do that first. Do not hand-bump the pin inside this PR.
     - Typical symptom: `IllegalArgumentException: Unsupported class file major version`, or Mockito's `Could not modify all classes`.
   - `mockito-core` 4.4.0 and `objenesis` 3.2 are pinned. They are fine for 17. For 21, use what Boot 3.x manages (Mockito 5).
   - JaCoCo (added in plan step 2) needs **≥ 0.8.7 for 17** and **≥ 0.8.11 for 21**. Older agents fail instrumentation, or the report silently comes out empty.
   - `hibernate-jpamodelgen` runs as an annotation processor at `${hibernate.version}`. Confirm the metamodel (`Bag_`, ...) is still generated under `target/generated-sources/annotations`.
3. **Strong encapsulation.** `--illegal-access` was removed in 17, so reflective access to JDK internals now throws `InaccessibleObjectException`.
   - Candidates are older reflective libraries: yamlbeans 1.15 (`ConfigService` reading `settings.yml`), Hibernate 5.4, H2GIS 1.3 in tests, docker-java-api 0.0.13, and google-maps-services.
   - Do **not** add blanket `--add-opens`. Add one only for a stack trace you actually observed, and scope it to the module and package named in the error.
   - Put it in the surefire `argLine` for tests.
   - For the container, put it in `JAVA_OPTS`, which `src/main/docker/entrypoint.sh` passes to `exec java $JAVA_OPTS -jar` since E3. Set the default with `ENV JAVA_OPTS` in the Dockerfile.
   - For installations without Docker, document the same flag in `docs/installation/standalone.md` (added by E5; there is no external-Tomcat installation, so no `CATALINA_OPTS` / `setenv.sh`).
   - `ConfigService` is skipped under the `test` profile, so yamlbeans is **not exercised by tests**. Check it on a running container.
4. **`javax.annotation.PostConstruct`** is used in `AdminController`, `BagScanner`, `ScriptService` and `StatusService`. It left the JDK in Java 11 and comes from a dependency.
   - Hop 18: nothing to change. Confirm it still resolves with `mvn dependency:tree -Dincludes=*:*annotation*`.
   - Hop 25: it is already `jakarta.annotation` after step 23.
5. **ffmpeg / `ProcessBuilder`** video streaming in `BagService` (~lines 392-770). These are JDK-independent APIs, but the runtime image changes, so ffmpeg must still be installed. The `apt-get install -y ffmpeg perl` line must keep working on the new Debian/Ubuntu base. Temurin images are Ubuntu-based, so check the package names.

## Procedure (each hop)
1. **Run the version-upgrade baseline.** On a clean tree, run `mvn -B package`, and record the surefire test count and the `dependency:tree`. Tag `pre-18` or `pre-25` after the user confirms.
2. **Check the prerequisites:**
   - Hop 18: steps 5, E3 and 17 are checked in the plan, and the `byte-buddy` pin is gone.
   - Hop 25: steps 23 and 24 are checked.
   - If any is missing, stop and tell the user.
3. **Run the local JDK.** Both `java -version` and `mvn -v` must report the target JDK. If not, ask the user to install or select it; do not install it yourself.
4. **Bump the compiler.** Replace `<source>`/`<target>` with `<release>NN</release>`. Run `mvn -B -DskipTests package` and fix compile errors only, such as deprecated-for-removal APIs and new compiler errors. Do not refactor to new language features in this PR.
5. **Run the tests.** Run `mvn -B package`.
   - The test count must equal the baseline.
   - Grep the surefire output for these errors, and handle each one per the spots above:
     - `InaccessibleObjectException`
     - `Unsupported class file major version`
     - `Could not initialize plugin: interface org.mockito`
     - `Unable to load OpenCV`
6. **Bump the images and CI.** Update both Dockerfile stages and `.github/workflows/maven.yml` in the same PR. The PR must keep build and runtime on the same JDK.
7. **Run the container smoke test**, after the user approves Docker use:
   - Build with `docker build -f docker/build/Dockerfile .`, then run one of the `docker/*/docker-compose.yml` examples.
   - Check that the app starts, settings.yml is read and bags are listed, which exercises yamlbeans.
   - Check bag details.
   - Check an image topic, especially a Bayer or OpenCV-decoded one.
   - Check video streaming (ffmpeg).
   - Check the WebSocket status bar.
   - Scan the application log (container stdout, which includes the embedded Tomcat) for `WARNING: Illegal reflective access` / `InaccessibleObjectException` / OpenCV warnings.
8. **Hop 25 only, optional:** virtual threads (`spring.threads.virtual.enabled`, Boot ≥ 3.2) belong to plan step 34 (scanner executor) or a separate PR, **not this one**. The upgrade PR must not change behavior.
9. **Record:**
   - Add `CHANGELOG.md` entries for the JDK, base images and CI.
   - Note any `--add-opens` added, with the library that needed it.
   - Tick step 18 or 25 only when its *Test* passes: the full suite plus the image and video endpoints for 18; the full suite plus the step-26 load test, when it exists, for 25.

## Failures and rollback
- **A library fails on the new JDK and only a version bump fixes it.** Stop. That bump becomes its own PR via **version-upgrade**, merged on the old JDK first. Then resume.
- **OpenCV cannot load and `loadLocally()` doesn't help.** Report it to the user before shipping, because image formats would break silently.
- **Rollback:** revert the PR, which covers the compiler release, images and CI together, or redeploy the `pre-<step>` image. No schema change is involved.

## Safety
- No Docker builds or runs, image pulls, `git tag` or `git push` without user confirmation.
- Never install or switch the system JDK yourself; ask the user.
- Do not combine the JDK bump with Spring, Hibernate or Tomcat major upgrades. After E3, Tomcat is embedded and versioned by Spring Boot, so the runtime image change in a hop is only the JRE (`eclipse-temurin:NN-jre`).
- If E3 is not checked when hop 18 starts, stop and tell the user. Do not move to a `tomcat:*-jdk17` image as a workaround.
