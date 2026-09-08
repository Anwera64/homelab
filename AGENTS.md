# Agent Guidelines & Workflow Rules

## 1. Strict Planning Rule: Always Plan First
- **No Direct Coding without Approval:** Never write, modify, delete files, or execute modifying commands without first creating an implementation plan and receiving explicit approval from the user.
- **Mandatory Lifecycle:**
  1. **Research & Inspect:** Use read-only tools to investigate codebase state, configuration files, and root causes without modifying anything.
  2. **Create Implementation Plan:** Create or update `implementation_plan.md` outlining the problem, architecture, exact file-by-file diffs, open questions, and verification steps.
  3. **Wait for Approval:** Stop and present the plan to the user for review and refinement.
  4. **Execute Only When Approved:** Only after the user explicitly approves the plan, proceed to development, file modifications, and testing.
- **Universal Scope:** This rule applies unconditionally to all tasks, tweaks, bug fixes, or refactors, regardless of perceived simplicity.

## 2. Mandatory Test-Driven Development (TDD) Rule: Always Test First
- **Strict Red-Green-Refactor Lifecycle:** When designing plans and writing code, always structure work into discrete TDD cycles.
- **Red Phase (Tests First):** Write automated unit or integration tests that assert the desired behavior and verify that they fail before writing any implementation code.
- **Green Phase (Minimal Implementation):** Write the minimal implementation code necessary to make the failing tests pass.
- **Refactor Phase:** Clean up and optimize the implementation while ensuring 100% of the test suite remains passing.
- **Planning Integration:** Every implementation plan must explicitly structure features into sequential TDD cycles, detailing the Red (tests & expected failures), Green (minimal code changes), and Refactor stages for each cycle.

