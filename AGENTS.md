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

## 3. Mandatory Clean Architecture Rule: Always Follow Clean Architecture
- **Strict Inward Dependency Rule (`presentation -> domain <- data`):**
  1. **`domain` is the Independent Core:** Contains pure Python Entities, Use Cases / Interactors, Repository Interfaces (Protocols), and Domain Exceptions. It must have ZERO dependencies on frameworks, databases, or outside modules (`fastapi`, `sqlalchemy`, `pydantic`, `data`, `presentation`, `bootstrap`).
  2. **`presentation` Depends ONLY on `domain`:** Contains thin FastAPI routers, Pydantic HTTP schemas, Presentation Mappers, and exception handlers. It must have ZERO imports from `data` or persistence engines.
  3. **`data` Depends ONLY on `domain`:** Implements repository protocols defined in `domain`, encapsulates DataSources (abstract protocols + SQLite implementations), ORM models, and Data Mappers. It must have ZERO imports from `presentation` or web frameworks.
  4. **`bootstrap` is the DI Coordinator:** Sits at the application root (equivalent to an Android `:app` module). It is the sole layer permitted to wire DataSources $\rightarrow$ Repositories $\rightarrow$ Use Cases $\rightarrow$ FastAPI dependencies.
- **Inter-Layer Mappers:** Always use dedicated mappers between layers (`presentation.mappers` for HTTP schemas $\leftrightarrow$ domain, and `data.mappers` for ORM models $\leftrightarrow$ domain) to prevent model changes from leaking across boundaries.
- **Automated Boundary Enforcement:** All architecture boundary rules must be covered by automated AST-based tests and executed on every test run and Git pre-commit hook. Cross-module violations must immediately fail the build.
