# Audit and Improvement of Claude Code Skills

## Objective

Analyze the following project documents and existing Claude Code skills to identify recurring procedures in the refactoring plan that should be formalized into reusable skills.

**Source documents:**

* `docs/audit.md`
* `docs/refactoring-plan.md`
* `CLAUDE.md`
* `.claude/skills/`

The goal is to improve the existing skills and propose new ones based on the actual needs identified during the audit.

**Important: Do not create, modify, or delete any files until I explicitly approve the proposals.**

---

## Step 1 — Analyze the Project Documentation

Read and analyze the following files:

1. `docs/audit.md`
2. `docs/refactoring-plan.md`
3. `CLAUDE.md`

Identify:

* The main issues and technical debt identified by the audit.
* The refactoring tasks and their dependencies.
* The procedures that will need to be repeated across multiple steps of the refactoring plan.
* The project's architectural constraints, coding conventions, and development workflows.
* The commands, tools, libraries, and testing procedures relevant to the planned changes.

Base your analysis on the actual contents of these files. Do not assume the project uses tools, libraries, or conventions that are not documented or verifiable in the repository.

---

## Step 2 — Inspect Existing Skills

Explore `.claude/skills/` and read the instructions of every existing skill relevant to the refactoring plan.

For each skill, determine:

* Its purpose and intended use cases.
* The procedures it already covers.
* Whether its instructions are accurate and consistent with the audit findings.
* Whether it is missing project-specific details.
* Whether it overlaps with other skills.
* Whether it should be updated, consolidated, or left unchanged.

Avoid proposing new skills for procedures that are already adequately covered by existing ones.

---

## Step 3 — Identify Recurring Procedures

Cross-reference the refactoring plan with the existing skills.

Identify procedures that:

* Appear multiple times in the refactoring plan.
* Require a consistent sequence of technical steps.
* Involve repeated validation, testing, migration, refactoring, or verification work.
* Would benefit from being documented as a reusable skill.
* Are not already adequately covered by an existing skill.

For each candidate procedure, provide:

* **Procedure:** A concise description of the recurring task.
* **Occurrences:** The specific steps or sections of `docs/refactoring-plan.md` where it appears.
* **Frequency:** The number of identifiable occurrences, where determinable.
* **Current coverage:** The existing skills that partially or fully cover the procedure, if any.
* **Recommendation:** Create a new skill, update an existing skill, or make no changes.
* **Rationale:** Why the proposed approach would improve consistency, reliability, or efficiency.

Do not create skills simply because a procedure is technically complex. Prioritize procedures that recur and can genuinely benefit from reusable instructions.

---

## Step 4 — Propose New Skills

For each procedure that warrants a new skill, provide a complete proposal.

Use the following format:

### Skill: `<skill-name>`

**Location:** `.claude/skills/<skill-name>/SKILL.md`

**Description:** Explain precisely when Claude should use this skill, including the relevant tasks, conditions, and situations in which it should not be used.

**Problem addressed:** Explain which recurring procedure from the refactoring plan the skill will standardize.

**Evidence:** Reference the relevant sections of `docs/audit.md` and `docs/refactoring-plan.md`.

**Prerequisites:**

* Required files or project context.
* Dependencies on other skills or procedures.
* Any conditions that must be satisfied before execution.

**Proposed instructions:**

Provide concrete, sequential steps tailored to this project.

The instructions must:

1. Reference actual module names, libraries, directories, and configuration files found in the repository.
2. Specify the exact commands to execute whenever they can be verified from the project's configuration and documentation.
3. Identify the files or components that need to be inspected or modified.
4. Explain the expected result of each important step.
5. Define the tests, checks, or validation procedures required to verify the changes.
6. Explain how to handle failures, unexpected results, and regressions.
7. Specify which existing skills should be used in conjunction with the proposed skill, when relevant.

Do not use generic placeholders when actual project-specific information can be established.

If a command, module, dependency, or file path cannot be verified, explicitly flag it as unverified instead of inventing it.

**Expected outcome:** Describe the state of the project after the skill has been successfully executed.

**Safety constraints:** Specify any operations that require additional verification or explicit authorization.

**Reason for a separate skill:** Explain why the procedure deserves its own skill rather than being added to an existing one.

---

## Step 5 — Propose Updates to Existing Skills

For each existing skill that should be updated, provide a separate proposal.

Use the following format:

### Existing skill: `<skill-name>`

**Current location:** `.claude/skills/<skill-name>/SKILL.md`

**Issues identified:**

* Instructions that conflict with the audit findings.
* Missing project-specific procedures.
* Incorrect or outdated commands, paths, or conventions.
* Missing validation steps.
* Overlap or integration issues with other skills.

**Evidence:** Reference the relevant findings in the audit and refactoring plan.

**Proposed changes:**

* Instructions to add.
* Instructions to correct.
* Instructions to remove, if necessary.
* Project-specific commands, paths, and technical details to incorporate.
* Integration points with any proposed new skills.

**Expected benefits:** Explain how the changes will improve the existing skill.

Whenever possible, describe the changes precisely enough that they can be implemented without requiring another analysis phase.

Do not rewrite an entire skill if only a few targeted changes are necessary.

---

## Step 6 — Check for Overlaps and Consistency

Before presenting the proposals, verify that:

* No proposed skill duplicates the responsibilities of an existing skill.
* Each new skill has a clearly defined scope.
* The proposed skills complement one another.
* Existing skills are updated where appropriate instead of creating unnecessary new ones.
* All proposed instructions are consistent with `CLAUDE.md`.
* All technical details are supported by the repository.
* The proposed workflows include appropriate testing and validation.
* Dependencies between skills are explicitly documented.
* The proposals address actual recurring procedures in the refactoring plan rather than hypothetical future needs.

If two proposed skills substantially overlap, consider consolidating them into a single skill with clearly defined responsibilities.

---

## Step 7 — Present the Recommendations for Approval

Present the results in the following order.

### A. Executive Summary

Summarize:

* The number of recurring procedures identified.
* The number of new skills recommended.
* The number of existing skills that should be updated.
* The main reasons for these recommendations.

### B. Recurring Procedures

Provide a table with the following columns:

| Procedure | References in Refactoring Plan | Existing Coverage | Recommendation | Priority |
| --------- | ------------------------------ | ----------------- | -------------- | -------- |

### C. Proposed New Skills

Provide the complete proposal for each new skill, following the format defined in Step 4.

### D. Proposed Updates to Existing Skills

Provide the proposed changes for each existing skill, following the format defined in Step 5.

### E. Implementation Order

Recommend an implementation sequence based on:

* Dependencies between skills.
* The order of operations in the refactoring plan.
* The expected benefits of each skill.
* The importance of establishing shared procedures before dependent work begins.

### F. Questions and Uncertainties

List any missing information, ambiguous requirements, or technical details that could not be verified.

Clearly distinguish verified facts from assumptions and recommendations.

---

## Step 8 — Stop and Wait for Approval

**This is a proposal-only task.**

During this analysis:

* Do not create any new skills.
* Do not modify existing skills.
* Do not modify `CLAUDE.md` or any project documentation.
* Do not execute refactoring operations.
* Do not make code changes.
* Do not install dependencies or change configuration.
* Do not create implementation files or temporary files in the repository.

You may read files, inspect the repository structure, and run read-only commands necessary to verify project details.

Once the analysis is complete, present all recommendations and wait for my explicit approval.

**Do not proceed with implementation until I have reviewed and approved the proposals.**

After approval, implement only the changes I explicitly authorize.
