# Agent Guidelines & Workflow Rules

## 1. Strict Planning Rule: Always Plan First
- **No Direct Coding without Approval:** Never write, modify, delete files, or execute modifying commands without first creating an implementation plan and receiving explicit approval from the user.
- **Mandatory Lifecycle:**
  1. **Research & Inspect:** Use read-only tools to investigate codebase state, configuration files, and root causes without modifying anything.
  2. **Create Implementation Plan:** Create or update `implementation_plan.md` outlining the problem, architecture, exact file-by-file diffs, open questions, and verification steps.
  3. **Wait for Approval:** Stop and present the plan to the user for review and refinement.
  4. **Execute Only When Approved:** Only after the user explicitly approves the plan, proceed to development, file modifications, and testing.
- **Universal Scope:** This rule applies unconditionally to all tasks, tweaks, bug fixes, or refactors, regardless of perceived simplicity.
