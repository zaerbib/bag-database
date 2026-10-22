---
description: Run every unchecked step of the next refactoring-plan phase, then check the phase
argument-hint: "[phase, e.g. 0b | 1 | 2 | 3] (default: first unchecked phase)"
---

Run a whole phase of `.claude/docs/refactoring-plan.md` in one go. The plan's ground rules still apply to every step: **one step = one revertable commit**, `mvn -B package` green after each step, test before the change for refactorings, Liquibase-only schema changes.

## 1. Select the phase
- If `$ARGUMENTS` names a phase (`0b`, `1`, `2`, `3`), take that `## [ ] Phase …` section. Otherwise take the FIRST `## [ ] Phase` heading in document order.
- If it is already `[x]`, say so and stop.
- List its unchecked steps `- [ ] **…**` in **document order** (E-steps are placed by position, not by number; see the plan's "Step numbering" note). For steps with sub-boxes (4a–4d, 22, 26, E4), each unchecked sub-box is its own unit of work and its own commit.
- Check the plan's **Dependency map**. If a step depends on an unchecked step outside this phase (e.g. Phase 3 SQL steps need 16, step 35 needs 21, step 27 needs 13 and E4), mark it **blocked** and leave it out of this run.

## 2. Read the context
- `.claude/docs/audit.md`: the sections the phase's steps refer to.
- The phase intro text in the plan (e.g. Phase 0b "Chosen approach", Phase 2 "one major at a time", Phase 3 "measure first").
- Every skill that applies to at least one step:
  | Steps | Skills |
  |---|---|
  | 1–4, 29 | characterization-tests |
  | 5, 17–27, E3, E4 | version-upgrade |
  | E1, E2 | characterization-tests (bootstrap and server settings, behavior unchanged) |
  | 18, 25 | + java-upgrade |
  | 6–15 | front-back-separation (if `.claude/skills/front-back-separation` does not exist, say so and use characterization-tests for the contract checks) |
  | 28, 30–38 | performance-change |
  | any schema/data change | + liquibase-changeset |

  `characterization-tests` applies to every step that changes behavior-bearing code.

## 3. Present the phase plan, then WAIT
One section per step, in execution order:
- files touched, order of actions, risks, success criterion (the step's *Test*), rollback;
- which skill(s) drive it;
- **gates**, i.e. actions that need my explicit confirmation during the run: Docker pulls / compose or container smoke tests, `git tag pre-<step>` (steps 17, 19, 23), adding the OpenRewrite plugin (steps 20, 23), anything outside the project directory.

Also list blocked steps and why, and the baseline you will record (test count, `dependency:tree` when versions change).

Ask for approval **once for the whole phase**, and ask all gate questions up front in the same message so the run does not stall. Do not modify anything before I answer.

## 4. Run each step (after approval), in order
For each step or sub-box:
1. Follow its skill procedure (baseline, characterization test before the change, measurement before the change for Phase 3).
2. Make the change and get `mvn -B package` green; the test count must be ≥ the previous step's count.
3. Review the step's diff with the `refactoring-reviewer` agent. If no such agent exists in `.claude/agents/`, use a `general-purpose` agent with a strict reviewer brief (scope = this step only, correctness, conventions from CLAUDE.md, leftovers) and say that you did.
4. Fix what the review reports, or explain why a finding is deferred.
5. Run the step's *Test*. Check the step's box **only** if it passes; check a parent step when all its sub-boxes are checked.
6. Add the CHANGELOG entry the skill asks for, then **commit the step locally** (one commit per step / sub-box, message `step <n>: …`, naming any intended contract change). Never `git push`.

Phase 3 steps: if the measurement does not improve, revert that step's change (keep the measurement test if useful), leave its box unchecked, record the numbers, and continue with the next step.

## 5. Stop conditions
Stop the run and report (steps already committed stay committed) when:
- a contract test that passed before fails and the step does not intend that contract change;
- a fix would need a second version bump or a change belonging to another step;
- a step's *Test* cannot pass, or a gate was refused;
- you find a regression from an earlier step: propose a separate fix commit and ask before making it.

## 6. Finish
- Check the phase heading `## [x] Phase …` **only** when every step in it is checked. If steps remain (blocked, failed, reverted), leave it unchecked.
- Report per step: status (done / reverted / blocked / stopped), test count, commit hash, deferred review findings, and anything left running (containers, images) with clean-up commands.
