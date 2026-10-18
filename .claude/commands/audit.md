description: Initial project audit (read-only) and preparation of the refactoring plan

Analyze this project WITHOUT MODIFYING the code. You may only write in docs/.

1. Fill in docs/audit.md with:
   - the actual versions read from the files (pom.xml or build.gradle, package.json, angular.json,
     .nvmrc, Dockerfile, CI): Java, Spring Boot, Spring Framework, Angular, TypeScript, Node, Jest,
     Liquibase, PostgreSQL driver, and any important library. Report any inconsistency
     (for example a Spring Framework version that does not match the one managed by Spring Boot).
   - third-party Angular dependencies and their known compatibility with the next Angular versions;
   - existing test coverage, by area (back: unit, integration; front: unit);
   - all front/back coupling points (see step A of the front-back-separation skill);
   - risk areas (untested code, complex logic, security, Liquibase migrations).

2. Complete docs/refactoring-plan.md: keep the phase structure, adjust the steps to what you found,
   add or remove steps if necessary. Each step must be small, testable and reversible.

3. Finish with the list of questions you must ask me before starting (decisions that are mine to make).
