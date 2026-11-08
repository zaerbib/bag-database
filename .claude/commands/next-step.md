---
description: Take the next unchecked step of the refactoring plan
---

1. Read `.claude/docs/refactoring-plan.md` and take the FIRST unchecked step `- [ ]`. Only one.
   If that step has sub-boxes (4a–4d, 22, 26, E4), take its first unchecked sub-box.
   Steps E1–E6 are placed by document order, not by number (see the plan's "Step numbering" note).
2. Identify the skill(s) that apply and read them:
   | Steps | Skills |
   |---|---|
   | 1–4, 29 | characterization-tests |
   | 5, 17–26, E3, E4 | version-upgrade |
   | E1, E2 | characterization-tests (bootstrap and server settings, behavior unchanged) |
   | E5, E6 | version-upgrade (scope/packaging change) + characterization-tests (same pages, resources and API) |
   | 18, 25 | + java-upgrade |
   | 6–15 | front-back-separation |
   | 28, 30–38 | performance-change |
   | any schema/data change | + liquibase-changeset |

   `characterization-tests` applies to every step that changes behavior-bearing code.
3. Present your plan for this step: files touched, order of actions, risks, success criterion (the step's *Test*), rollback.
   WAIT for my approval before modifying anything.
4. After approval: carry out the step, make the tests pass, then run the refactoring-reviewer agent on the diff.
5. Fix what the reviewer reports.
   - Check the box only when the step's *Test* passes.
   - Check a parent step only when all its sub-boxes are checked, and a phase only when all its steps are checked.
   - Propose a commit message.
   Do not `git push`.
