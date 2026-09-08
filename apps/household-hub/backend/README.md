# Household Hub Backend (FastAPI)

Core backend service for **Household Hub** providing multi-user authentication, decoupled spaces, dynamic agent catalog management with ownership, and isolated conversation sessions.

---

## 🌟 Architecture & Features

### Clean Architecture Core (`presentation -> domain <- data`)
The backend is structured into decoupled architectural layers strictly enforced by automated AST boundary tests:
* **`app/domain` (Pure Python Core):** Business entities, use cases, repository protocols, and domain exceptions with ZERO framework or ORM dependencies.
* **`app/presentation` (FastAPI Routers & Schemas):** Thin REST controllers and Pydantic DTOs translating HTTP requests to domain use cases via presentation mappers.
* **`app/data` (Persistence & Repositories):** SQLAlchemy ORM models, pluggable SQLite data sources, unit of work, and data mappers implementing domain repository interfaces.
* **`app/bootstrap` (DI Coordinator & App Factory):** Dependency injection container and application lifespan manager. Providers run as native `async` coroutines backed by a cached request-scoped container, preventing threadpool offloading.

### Core Capabilities
* **Multi-User Identity & Closed Household:**
  * **First-Run Onboarding:** Automatically detects an uninitialized database; the first registered user becomes the **Household Admin**.
  * **Distributed Mutex Onboarding:** Atomic `SystemSetting` mutex (`set_if_not_exists`) guarantees that concurrent onboarding requests across multiple workers cannot create duplicate administrators.
  * **Admin Member Management:** Subsequent household member accounts are provisioned exclusively by the Admin.
  * **First-Party JWT Auth:** Fast, self-contained JSON login issuing signed JWT tokens with salted `bcrypt` password hashing. Works 100% offline with zero external identity provider dependencies.
* **Strict Zero-Leak Spaces Engine:**
  * **Shared Household Hub (`/spaces/shared`):** Singleton collaborative space containing shared Bento widget configurations (household schedules, AI assistant launchers).
  * **Personal Spaces (`/spaces/personal`):** Strictly private workspaces auto-provisioned for each user.
  * **Zero-Leak Boundary:** Enforced server-side. Even the Household Admin is forbidden (`403 Forbidden`) from viewing or querying another user's personal space.
* **Dynamic Agent Catalog, Soft-Delete & Lifecycle Management:**
  * **Built-in Baseline Models:** Seeded automatically on startup:
    * `researcher`: Academic & Document Researcher (`qwen3:14b`, Temp: 0.3, tools: `pdf_reader`, `searxng_search`, `document_writer`).
    * `assistant`: Home & Life Coordinator (`qwen3:14b`, Temp: 0.7, tools: `calendar_read`, `calendar_write`, `searxng_search`).
  * **Custom Models:** Any household member can create specialized agents with custom system prompts, temperatures, and tool permissions.
  * **Ownership:** Only the creator can edit or delete their custom model.
  * **7-Day Undo Grace Period:** Deleting a custom model soft-deletes it (`deleted_at`), moving it to `/api/v1/agents/trash`. The owner can restore it within 7 days via `/api/v1/agents/{id}/restore`.
  * **Cascading Trash Purge & Session Preservation:** When an agent is permanently purged (manually or upon expiration), all associated conversation sessions are automatically transitioned to an archived state (`is_archived=True`, `agent_id=None`) *before* agent deletion.
  * **Autonomous Background Purger:** An asynchronous background task runs in the application `lifespan` loop, automatically cleaning up expired trash models on an hourly interval.
  * **Inactive Agent Suspension:** Deactivating an agent (`is_active=False`) suspends it from accepting new sessions or chat messages while preserving full read access to past conversation history.
* **Pluggable Integrations Engine (Stage 2):**
  * **Unified CalDAV Calendar Connector:** Compatible with Apple iCloud, Google Calendar, and self-hosted CalDAV instances. Passwords encrypted with AES-256 (Fernet) at rest. Enforces SSRF blocks and cloud metadata protections.
  * **SearXNG Private Search Client:** Fast HTTP connection pooling (`httpx.AsyncClient`) with bounded LRU caching (max 500 entries) and 15-minute TTL. Role-based profiles (general vs. academic sources).
  * **PyMuPDF Document Reader & Extractor:** Non-blocking PDF parsing with chunked streaming upload validation (64KB chunks), sectioning, citation extraction, and scanned 0-text rejection (`422 Unprocessable Entity`).
  * **Relational Document Store:** SQLite persistence with auto-incrementing version tracking and raw `.md` download endpoint.
* **AI Inference, Tool Execution & Attributed Gossip Bus (Stage 3):**
  * **Local Ollama Inference (`qwen3:14b` on RTX 5080):** OpenAI-compatible chat completion and streaming pipelines with persistent connection pooling.
  * **Progressive Token-by-Token SSE Streaming:** Real-time word-by-word streaming via `/sessions/{id}/stream`. Bypasses non-streaming calls for toolless agents and streams synthesized answers post-tool execution.
  * **Prompt Injection Neutralization:** Strips control tokens (`System:`, `<|im_start|>`, `###`, `---`) from user memories and milestones prior to system prompt compilation.
  * **Autonomous Memory Reflection & Deduplication:** Background extraction runs post-turn. Semantic in-place deduplication updates confidence and refreshes timestamps instead of inserting duplicate rows.
  * **Attributed Gossip Bus:** Directional context stream (`/gossip/milestones`) where shared milestones carry explicit provenance (`source_user_id` $\rightarrow$ `reporting_agent_id`).
  * **Durable Session Runners & Concurrency Governance:** Stream execution runs in isolated `AsyncSessionLocal` transactions resistant to client disconnects. `SessionLockRegistry` returns immediate `HTTP 409 Conflict` on concurrent turns with atomic lock pruning.
  * **Security & Network Hardening:** Upstream LLM errors map to `502 Bad Gateway` (and `504 Gateway Timeout` on timeouts). Default CORS locked down to `https://spicy-llama.duckdns.org` with DuckDNS subdomain regex reflection and native mobile socket bypass compatibility.
* **Storage & Reliability:**
  * SQLite database with **WAL (Write-Ahead Logging)** mode enabled and foreign keys strictly enforced (`PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000`).
  * Asynchronous ORM via **SQLAlchemy 2.0** and **aiosqlite**.

---

## 🚀 Running Locally

### 1. Create Virtual Environment & Install Dependencies
```powershell
cd apps\household-hub\backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 2. Start Application Server
```powershell
uvicorn app.main:app --reload --host 0.0.0.0 --port 3050
```

* **Interactive API Documentation:** [http://localhost:3050/docs](http://localhost:3050/docs)
* **OpenAPI Specification:** [http://localhost:3050/api/v1/openapi.json](http://localhost:3050/api/v1/openapi.json)
* **Health Check:** [http://localhost:3050/api/v1/health](http://localhost:3050/api/v1/health)

---

## 🧪 Running Automated Tests (TDD)

```powershell
cd apps\household-hub\backend
.\.venv\Scripts\python.exe -m pytest tests/ -v
```

The 159 automated tests (100% passing) cover:
* `tests/architecture/test_architecture_boundaries.py`: AST static analysis verifying strict layer boundaries and coroutine DI providers.
* `tests/test_auth.py`: First-run admin onboarding, atomic concurrent registration mutex, JWT verification, and member provisioning.
* `tests/test_spaces.py`: Shared singleton space, Bento widgets layout, and strict Zero-Leak 403 enforcement.
* `tests/test_agents.py`: Builtin models seeding, custom model creation, soft-delete, 7-day restore, slug reuse, cascading trash purge, and inactive suspension.
* `tests/test_sessions.py`: Session thread management, tool approval execution (`POST /tools/approve`), cascading session archival (`POST /archive`), secret mode toggle, private history isolation, cursor pagination, 409 stream lock, agent provenance, and LLM 502/504 mapping.
* `tests/test_memories.py`: Agent memory personal/household scoping, Zero-Leak 403 isolation, edit/delete audit, and secret mode block.
* `tests/test_users.py`: User lifecycle, member deletion with knowledge inheritance, and profile updates.
* `tests/test_integrations.py`: CalDAV calendar integration, SearXNG search client with LRU caching, PDF reader with chunked validation, and document store CRUD.
* `tests/test_cors.py`: DuckDNS origin whitelist, subdomain regex matching, and unauthorized origin rejection.
* `tests/test_stage3_e2e.py`: Full SSE streaming chat turns, tool execution loops, memory reflection, and gossip bus milestone lifecycle.
* `tests/domain/`: Pure entity logic and use case isolated unit tests (chat turn progressive streaming, context assembler prompt sanitization, memory reflection deduplication, gossip use cases).
* `tests/data/`: Data source, connector, and repository mapping tests.
* `tests/presentation/`: Presentation mapper, response DTO, session lock registry, and gossip router tests.

---

## 🐳 Docker Deployment

Build the container image:
```powershell
docker build -t household-hub-backend:latest -f Dockerfile .
```

Run with persistent storage volume:
```powershell
docker run -d \
  --name household-hub-backend \
  -p 3050:3050 \
  -v household_hub_data:/data \
  -e SECRET_KEY="your-production-secret-key" \
  household-hub-backend:latest
```
