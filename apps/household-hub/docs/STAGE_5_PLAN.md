# Household Hub: Stage 5 Plan — Compose Multiplatform UI

**Status:** ✅ Approved — Android first, iOS verified later
**Target:** new `:composeApp` and `:androidApp` (Android now, iOS later), `:core:*` and `:shared` (new targets, ViewModel changes), `backend` (the changes the designs depend on)
**Date:** September 2026

---

## 1. Goal and scope

Ship the Household Hub phone app on Android and iOS, built to the Stage 5 designs, with every backend and client-core change those designs depend on.

| | |
| :--- | :--- |
| **Source of truth** | [`STAGE_5_DESIGN_NOTES.md`](STAGE_5_DESIGN_NOTES.md) for screens, rules and the dependency list (§5); [`STAGE_5_SECRET_SESSION_LOCKING.md`](STAGE_5_SECRET_SESSION_LOCKING.md) for secret chats |
| **In** | Phone portrait on Android and iOS. Every flow on the design canvas. Both themes — the canvas draws the day palette; night comes from the proven tokens |
| **Platform order** | Android first, slice by slice. iOS is compiled and checked later on the owner's Mac; until then `commonMain` must stay free of JVM- and Android-only APIs so it keeps compiling for iOS |
| **Working rhythm** | Each slice is done in a fresh session, starting from this plan and the design notes |
| **Out, later stages** | Tablet split layout · biometric unlock (locking spec §7) · full-text search · one session on several devices · Google Calendar via OAuth · deployment (Stage 6) |
| **Designed last, inside this stage** | The morning briefing (slice 11) |

---

## 2. How the work is cut

**Feature by feature.** Each slice carries its backend change, its client-core change and its screens together, and ends with the app usable for that feature. Nothing waits on a big backend phase.

Every slice is done when:

1. Its backend changes have tests, and the full backend suite and AST boundary check pass.
2. Its use cases and ViewModels have KMP tests (Turbine for flows), and the Koin graph check passes.
3. Its screens match the canvas, follow the rules in design notes §2, and have Compose UI tests for the main path and each drawn error state.
4. It has been run by hand on an Android phone or emulator. iOS is checked in batches on the Mac once Android is working.

Rules that apply across slices:

- **One label map** turns tool, action and permission identifiers into words. It lives in one place, keyed by backend names; no screen formats an identifier itself.
- **Primary buttons are never disabled**, errors appear under the field that caused them, and destructive actions list what goes and what stays. Reviews check against design notes §2.
- **Backend contract changes land before the screens that use them**, inside the same slice.

At the end: `STAGE_5_BASELINE.md` in the shape of the earlier baselines, and `SYSTEM_PLAN.md` updated.

---

## 3. Slice 0 — Platforms and foundation

No features; everything after this builds on it.

- **Targets.** Every client module builds for the JVM only today. Add the Android target to `:core:domain`, `:core:data`, `:core:presentation` and `:shared` now, and the iOS targets when iOS is picked up; keep the JVM target for tests.
- **Networking.** `DataModule` hard-codes `HttpClient(CIO)` in `commonMain`. Move the engine behind a platform seam: OkHttp on Android, CIO on the JVM, Darwin on iOS later. Prove token-by-token SSE streaming on Android in this slice, and on iOS first thing when it's picked up — Darwin is the engine most likely to buffer.
- **Token storage.** `FileTokenStorage` is an `expect` class with only a JVM `actual`, created as `FileTokenStorage()` in `DataModule`. Android: Keystore-backed storage in the app's private files. iOS: Keychain, later.
- **Hub address.** `DEFAULT_BASE_URL` is hard-coded to `https://hub.spicy-llama.duckdns.org` in `DataModule`, and also defaulted inside several repositories. Keep it for now; make it one value.
- **`:composeApp`.** A Kotlin Multiplatform library with the screens, theme and navigation, wired to Koin through `HouseholdHubSdk`.
- **`:androidApp`.** The Android application module: `MainActivity`, SDK initialisation, `setContent { App() }`.
- **Theme.** Copenhagen Day and Midnight Espresso tokens (design notes §3), ghost alphas per palette, Outfit / Inter / JetBrains Mono bundled.
- **Hearth icons** as `ImageVector`s, all 27.
- **Shared components.** Bento card, chip, buttons (primary, secondary, destructive), text field with inline error, empty state, tool record line, message composer, bottom navigation with the centre +, and a scaffold that scrolls content under a pinned header and navigation.

**Done when** the app opens on an Android phone or emulator, calls `GET /auth/status`, follows the system theme, and the existing JVM test suites still pass.

### 3.1 Build setup findings (checked 11 September 2026)

**AGP 9 changes the module shape.**
- `com.android.library` can no longer be combined with Kotlin Multiplatform. Library modules use `com.android.kotlin.multiplatform.library`, configured inside `kotlin { android { namespace = …; compileSdk = …; minSdk = … } }`. The older `androidLibrary {}` block is deprecated.
- There is no Kotlin Multiplatform form of `com.android.application`. The app has to be a separate `:androidApp` module that depends on `:composeApp`.
- The catalog's `android-library` plugin alias is declared but unused; replace it.

**Current catalog:** Kotlin 2.2.21, AGP 9.1.0, Ktor 3.4.1, Koin 4.0.2, lifecycle 2.8.6, Gradle 9.3.1.

**Latest published versions** found when this plan was written. Pick the latest *stable* Compose Multiplatform release and align Kotlin to it at the start of slice 0:

| Artifact | Latest seen |
| :--- | :--- |
| Kotlin Gradle plugin | 2.4.20 |
| Compose Multiplatform Gradle plugin | 1.13.0-alpha01 (pre-release — find the latest stable) |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 |
| `org.jetbrains.androidx.navigation:navigation-compose` | 2.10.0-beta01 (pre-release) |
| Koin (`koin-core`, `koin-compose`) | 4.2.2 |
| Ktor (incl. `ktor-client-okhttp`) | 3.5.2 |
| AGP | 9.5.0-alpha05 (pre-release; 9.1.0 is in use) |

**This machine:** JDK 21; Android SDK at `%LOCALAPPDATA%\Android\Sdk` with platforms 33–36.1, build-tools up to 36.1.0, emulator and system images installed. `ANDROID_HOME` isn't set, so the build needs `local.properties` with `sdk.dir` — already gitignored.

**iOS prerequisite:** the owner's Mac with Xcode. An Apple developer account is needed to keep the app on a real iPhone beyond free provisioning's 7-day limit — check when iOS is picked up.

---

## 4. Slices

Canvas bands are named as on the design canvas; backend and client items refer to design notes §5.

| # | Slice | Screens | Backend | Client core | Done when |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Sign in** | Onboarding (launch, first run, profile picker, PIN, signed-out offline) | PIN replaces password, attempt counter with backoff, public profile list, Hygge avatar default (§5.1) | `LoginUseCase` takes a member and PIN instead of username and password; `AuthViewModel` follows | First run creates the admin with a PIN; the picker lists members; repeated wrong PINs back off; an unreachable hub shows the offline screen |
| 2 | **Members** | Invite; Members & account; PIN forgot / approve | Invites, PIN reset, `token_version`, deactivation, delete account, sole-admin wording (§5.1) | Invite, reset and member use cases | A second member joins by code; the other member can approve a PIN reset; changing a PIN signs out the other device; removing someone deactivates them |
| 3 | **Chatting** | Daily loop (new chat, conversation, chats); Message states; title search; empty chats. Household and My Space shells with only their backed parts | `last_message_preview` (§5.3) | Stream failures classified by when they happen; the 60 s limit ends the wait, not the turn; on-device title search (§5.3) | All four message states reproduce against a real hub; the partial answer survives a dropped stream |
| 4 | **Tools** | Tools; Auto-approve; Web search | `tool_call_id` on the approval event; per-person, per-action approval settings (§5.3–5.4) | Handle `ToolExecuting` and `ToolResult`; the label map | Reads leave records; approve, edit and decline work; auto-approve adds events without asking and still leaves a record; search sources open |
| 5 | **Secret chats** | Secret Mode; Search & secret titles; Privacy phrases | The locking spec §4 in full: unlock endpoint, 423 reads, scoped tokens, list redaction, no memories from secret turns, cleanup (§5.2) | Replace the unlock stub; lock state and idle timer; unlock bar and Lock now; `suggestSecretMode` | The locking spec's §8 tests pass; a stolen access token gets 423; titles appear only after the PIN |
| 6 | **Profile, memory and sharing** | Profile; memory audit; shared context; publish notice; their empty states | Milestone ids on the stream's done event (§5.3) | `MemoryAuditViewModel` loads milestones (§5.6) | Memories and shared facts list and revoke; the publish notice's Undo takes a fact back |
| 7 | **Agents** | Agents; Agent form errors; Agent unavailable; empty trash | 409 handle conflicts with codes; minimum lengths (§5.5) | Agent create, edit, suspend, trash, restore, purge | Every drawn form error reproduces; trashed and suspended agents make their chats read-only; purged ones archive |
| 8 | **Calendar** | Calendar; Schedule; calendar parts of Household and My Space | — (endpoints exist) | `CalendarViewModel` and repository; Google URL built on the phone (§5.7) | Apple and Google connect with app passwords; a bad password shows the drawn failure; Schedule shows both members |
| 9 | **PDFs and notes** | PDFs; Notes; empty notes | Session documents, PDF tool reading from the store, attached-document context, removal, no page limit, password detection, pages without text; note attribution and the create-overwrite bug (§5.8–5.9) | File picker (Android and iOS), share sheet for notes | Every drawn PDF state reproduces with a real file; a note shows its source chat and shares as Markdown |
| 10 | **Offline reading** | Offline | — | A multiplatform client database (e.g. SQLDelight) caching sessions, messages, members, spaces and events, with a written-at time per record; secret content never cached (§5.10) | Signed in with the hub off, the app opens on cached data with an "as of" banner; every write is disabled with a reason |
| 11 | **Morning briefing** | To design first; then Household and "Your day" in My Space | A briefing endpoint (to be designed) | — | Designed on the canvas, then built |

**Why this order.** Sign-in gates everything. Chat is the core loop and the first thing worth using daily. Tools come before secret chats because secret chats lock write tools and rely on the tool events being handled. Profile follows secret chats because the memory screen's Secret Mode promise depends on slice 5. Calendar comes before the briefing, which is built on events. Offline reading is last before the briefing because it caches what the other slices produce.

---

## 5. Risks

| Risk | Mitigation |
| :--- | :--- |
| iOS drifts while Android leads | `commonMain` stays free of JVM- and Android-only APIs; iOS targets are compiled on the Mac as soon as Android slice 0 works, then checked in batches |
| SSE buffering on iOS | Prove streaming first thing when iOS is picked up, before relying on any chat screen there |
| Re-locking on background behaves differently on iOS and Android | Slice 5 tests both lifecycles by hand; the idle timer counts only user input (locking spec §3) |
| Stage size — twelve slices | Slices 10 and 11 can move to the next stage without breaking anything before them |
| PDFs larger than the model's context | The PDF tool reads a few pages at a time (slice 9) |
| Google narrowing app passwords | Accepted for now; OAuth is listed for a later stage |
| Privacy phrases are English only | Known and not planned. Spanish or Catalan phrases can be added to the list if you want them |

---

## 6. Decisions still needed

1. ~~The iOS build machine~~ — decided: the owner's Mac, after Android.
2. **Offline reading** — in this stage as slice 10, or moved to the next. Needed before slice 10 starts.
