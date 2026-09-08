# Household Hub Client (Kotlin Multiplatform Core)

Shared Kotlin Multiplatform (KMP) client core for **Household Hub** providing cross-platform domain logic, Ktor networking, reactive ViewModels, and dependency injection.

---

## 🏗️ Architecture (`presentation -> domain <- data`)

The client adheres strictly to Clean Architecture across discrete Gradle submodules:

* **`:core:domain` (Pure Kotlin Core):**
  * Contains pure domain Entities (`User`, `ConversationSession`, `ChatMessage`, `Space`, `AgentPersonality`, `AgentMemory`, `HouseholdMilestone`, `ServerStatus`).
  * Repository Protocols (Interfaces) defining contracts for data sources.
  * Pure Interactors / Use Cases with constructor injection.
  * Zero dependencies on Android, JVM, Ktor, serialization, or UI frameworks.

* **`:core:data` (Network & Persistence Layer):**
  * Depends **ONLY** on `:core:domain`.
  * **Ktor 3.4.1 Client:** Configured with `ContentNegotiation` (Kotlinx Serialization), `Logging`, and `Auth` (preemptive Bearer token auto-propagation).
  * **`DefensiveSseStreamReader`:** Token-by-token SSE streaming with prompt delimiter sanitization (`<|im_start|>`, `###`, etc.).
  * **`ServerHealthMonitor`:** Probes `/api/v1/health` with exponential backoff on connection drops.
  * **`NetworkExceptionHelper`:** Pure multiplatform network offline detection (zero `java.net.*` in `commonMain`).
  * **`FileTokenStorage`:** Multiplatform disk-backed credential persistence (`expect`/`actual`) surviving process restarts.
  * **Repositories & Mappers:** Bi-directional mapping between API DTOs and domain models.

* **`:core:presentation` (State & ViewModels):**
  * Depends **ONLY** on `:core:domain`. Zero imports from `:core:data`.
  * Multiplatform `androidx.lifecycle.ViewModel` exposing immutable `StateFlow<T>` UI states.
  * `ChatSessionViewModel`: Manages streaming token accumulation, collision-free monotonic nanosecond message IDs, tool approvals, and optimistic status transitions (`SENDING` $\rightarrow$ `SENT` or `FAILED_OFFLINE` / `FAILED_ERROR`).
  * `DashboardViewModel`: Real-time server health observation, member profile, recent sessions, and milestones.
  * `AuthViewModel`: First-run onboarding, login, and auth status verification.
  * `MemoryAuditViewModel`: Personal/household memory audits and one-click revocation.

* **`:shared` (DI Coordinator & SDK Entry Point):**
  * Acts as the application coordinator (equivalent to Android `:app`).
  * Configures **Koin 4.0** dependency injection graphs.
  * Exposes `HouseholdHubSdk` as the public entry point for client targets (Android, iOS, Desktop, Web Wasm).
  * Houses automated AST boundary enforcement tests (`CleanArchitectureBoundaryTest`) and DI graph verification (`KoinDependencyGraphTest`).

---

## 🧪 Running Automated Tests

Run the full KMP test suite across all modules:

```powershell
cd apps\household-hub\client
.\gradlew.bat jvmTest --rerun-tasks
```

Run specific architectural & DI validation tests:

```powershell
# AST Clean Architecture boundary verification (fails if layers breach dependencies)
.\gradlew.bat :shared:jvmTest --tests "*CleanArchitectureBoundaryTest*"

# Koin dependency graph validation (fails if any binding is missing)
.\gradlew.bat :shared:jvmTest --tests "*KoinDependencyGraphTest*"
```

---

## 🌐 Network Ingress

The client connects uniformly to:
* **Base URL:** `https://hub.spicy-llama.duckdns.org`
* **API Prefix:** `/api/v1`
* **Local Ingress:** On home Wi-Fi, local DNS resolves the domain directly to `192.168.1.20` on port 3050 without Hairpin NAT penalties. Away from home, Tailscale MagicDNS and DuckDNS provide encrypted remote access.
