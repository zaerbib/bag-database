---
name: performance-change
description: Make a measured performance change with identical behavior - measure first, change, re-measure, keep only if it improves, otherwise revert. Use for refactoring-plan Phase 3 steps 28 and 30-38 (query counts, indexes, paging, batch inserts, scanner throughput, script polling, status throttling, media streaming, pool tuning). Not for step 29 (pure code move) or functional changes.
---

# Performance change (measure → change → measure)

Phase 3 rule (`.claude/docs/refactoring-plan.md`): every step adds a **measurement first**, and keeps the contract tests green. The change is accepted only if the measurement improves; otherwise it is reverted.

## Prerequisites
- Step 16: the PostGIS Testcontainers profile, for any SQL-related step.
- Step 28: the load baseline (k6 scenario, SQL statement counting).
- Contract tests green (**characterization-tests**).

**Not yet in the repo:** k6, JMH, `datasource-proxy`, Hibernate statistics wiring, a seeded dataset. Step 28 introduces them. When they exist, record their paths and commands here.

## Procedure
1. **Pick the measurement** the step names:

   | Steps | Measurement |
   |---|---|
   | 30 | Query-count assertion (e.g. "search of 100 bags issues ≤ 3 statements") |
   | 31 | `EXPLAIN ANALYZE` on PostGIS before and after |
   | 32–34 | JUnit/JMH timing or indexing/scan benchmark |
   | 36 | Status-flood unit test |
   | 37 | Range request, plus memory under load |
   | 31, 38 | k6 p95 |

2. **Measure before.** Commit the measurement test or script first, unchanged code. Record the numbers: at least 3 runs, median and p95.
3. **Change.**
   - Schema, index or sequence work goes through **liquibase-changeset**.
   - Fetch-type or projection changes must leave JSON identical. Run the contract tests.
   - Keep the switches the plan names: `scanner.parallelism=1`, the scheduler flag (step 35), the "unlimited" throttle (step 36).
4. **Measure after** with the same data, environment and number of runs.
5. **Decide.**
   - If the result improves and `mvn -B package` and the contract tests are green, keep it. Put before and after numbers in the PR description.
   - Otherwise, revert the change and note the negative result under the plan step.
6. **Verify:** `mvn -B package`, plus the PostGIS profile for SQL steps.

## Safety
- Run load tests and seeding (e.g. 50k bags) only against local, disposable environments.
- Ask before starting Docker or Testcontainers-heavy runs.
