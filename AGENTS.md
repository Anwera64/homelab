# Agent Guidelines & Workflow Rules

## 1. Plan first
- Investigate read-only, then present a plan through plan mode (problem, architecture, file-by-file changes, open questions, verification) and wait for explicit approval before writing, deleting, or running anything that modifies state.
- This covers every change, including one-line fixes and follow-ups in the same session. Don't write plan files into the repository.

## 2. Test-driven development
- Work in red-green-refactor cycles: write the test and watch it fail before any implementation code, then the minimal code to pass, then refactor with the full suite green.
- Each plan lists its cycles in order, with the Red tests and their expected failures, the Green change, and the Refactor step.
- Client test conventions (names, robots) are in `apps/household-hub/client/AGENTS.md`.

## 3. Clean architecture
Every app keeps three layers with dependencies pointing inward: `presentation -> domain <- data`.
- **domain** is the core: entities, use cases, repository interfaces and domain exceptions. It depends on no framework, database or other layer.
- **presentation** depends only on domain: UI or HTTP entry points, their models, and mappers to domain.
- **data** depends only on domain: it implements the repository interfaces, with data sources, storage models and mappers to domain.
- Models never cross a layer boundary; each boundary has its own mappers, so a model change doesn't leak.
- Wiring happens only in DI code, and only the app's root assembles the graph.
- Boundary tests enforce these rules on every test run and in the pre-commit hook. A new rule gets a test.

Per stack:
- **Hub backend (Python):** `app/domain`, `app/data`, `app/presentation`, and `app/bootstrap` as the DI root. domain imports none of `fastapi`, `sqlalchemy` or `pydantic`. Boundaries are tested in `tests/architecture/`.
- **Client (Kotlin Multiplatform):** Gradle modules `:core:domain`, `:core:data` and `:core:presentation`, each with its own Koin module, assembled by the app. Boundaries are tested in `shared/src/jvmTest/.../architecture/`.

## 4. Sub-Agent Rule: Keep Token Cost Down
- **Delegate what doesn't need the main model's judgement:** **Sonnet** for simple, well-specified tasks, **Haiku** for the most basic ones (lookups, boilerplate, doc checkboxes).
- **The main agent keeps design decisions** and reviews every delegated diff before it is committed.
- **Hand-offs are complete:** a sub-agent starts cold, so give it the tests, specs and file paths it needs.

## 5. Environment and Constraints
- **On-demand model:** The local LLM is loaded and offloaded on demand by design. Don't suggest keep-alive or keeping the model resident.
- **Hub logs live in Grafana:** Query Grafana/Loki for hub logs rather than `docker logs`.
- **Local services:** Services on the local network (e.g. Cleanuparr) need no API key unless stated.
- **Connectivity:** Windows Firewall on the desktop allows Ollama (11434) only from the server; check it early when the hub can't reach the model. For phone connectivity, check the Android local-network permission.
- **Client builds:** Build and test client (Kotlin) code before committing; if the environment can't build it, say so clearly in the commit and PR.
