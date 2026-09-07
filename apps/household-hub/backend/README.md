# Household Hub Backend (FastAPI)

Core backend service for **Household Hub** providing multi-user authentication, decoupled spaces, dynamic agent catalog management with ownership, and isolated conversation sessions.

---

## 🌟 Architecture & Features

* **Multi-User Identity & Closed Household:**
  * **First-Run Onboarding:** Automatically detects an uninitialized database; the first registered user becomes the **Household Admin**.
  * **Admin Member Management:** Subsequent household member accounts are provisioned exclusively by the Admin.
  * **First-Party JWT Auth:** Fast, self-contained JSON login issuing signed JWT tokens with salted `bcrypt` password hashing. Works 100% offline with zero external identity provider dependencies.
* **Strict Zero-Leak Spaces Engine:**
  * **Shared Household Hub (`/spaces/shared`):** Singleton collaborative space containing shared Bento widget configurations (household schedules, AI assistant launchers).
  * **Personal Spaces (`/spaces/personal`):** Strictly private workspaces auto-provisioned for each user.
  * **Zero-Leak Boundary:** Enforced server-side. Even the Household Admin is forbidden (`403 Forbidden`) from viewing or querying another user's personal space.
* **Dynamic Agent Catalog & 7-Day Soft-Delete Grace Period:**
  * **Built-in Baseline Models:** Seeded automatically on startup:
    * `researcher`: Academic & Document Researcher (`qwen3:14b`, Temp: 0.3, tools: `pdf_reader`, `searxng_search`, `document_writer`).
    * `assistant`: Home & Life Coordinator (`qwen3:14b`, Temp: 0.7, tools: `calendar_read`, `calendar_write`, `searxng_search`).
  * **Custom Models:** Any household member can create specialized agents with custom system prompts, temperatures, and tool permissions.
  * **Ownership:** Only the creator can edit or delete their custom model.
  * **7-Day Undo Grace Period:** Deleting a custom model soft-deletes it (`deleted_at`), moving it to `/api/v1/agents/trash`. The owner can restore it within 7 days via `/api/v1/agents/{id}/restore`.
* **Conversation Sessions & Secret Mode:**
  * Multi-agent conversation session threads (`/api/v1/sessions`).
  * **Secret Mode (`is_secret`):** Toggleable session confidentiality flag ensuring sensitive research or surprise planning never leaks to the shared gossip bus or shared memory.
* **Agent Long-Term Memory & User Relationship Engine:**
  * Persistent memory store (`/api/v1/memories`) enabling agents to accumulate personal preferences, dietary habits, and milestones over time.
  * **Two-Tier Scoping:** Personal facts (`scope="personal"`) are strictly isolated to the user under Zero-Leak rules; household facts (`scope="household"`) carry user attribution.
  * **Audit & Revoke:** Users maintain full visibility to view, edit, or delete any memory an agent has formed.
  * **Secret Mode Hard Barrier:** Memories from Secret sessions cannot be published to the household scope.
* **Storage & Reliability:**
  * SQLite database with **WAL (Write-Ahead Logging)** mode enabled and foreign keys strictly enforced (`PRAGMA foreign_keys=ON`).
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
.\.venv\Scripts\python.exe -m pytest tests/ -v --cov=app
```

The test suite covers:
* `test_health.py`: Healthcheck and database connectivity.
* `test_auth.py`: First-run admin onboarding, JWT verification, and closed-household member provisioning.
* `test_spaces.py`: Shared singleton space, Bento widgets layout, and strict Zero-Leak 403 enforcement.
* `test_agents.py`: Builtin models seeding, custom model creation with ownership, soft-delete, trash listing, and 7-day undo grace period.
* `test_sessions.py`: Session thread management, secret mode toggle, and private history isolation.
* `test_memories.py`: Agent memory personal/household scoping, Zero-Leak 403 isolation, edit/delete audit, and secret mode block.

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
