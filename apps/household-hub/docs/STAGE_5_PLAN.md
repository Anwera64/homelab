# Household Hub: Stage 5 Plan — Compose Multiplatform UI

**Status:** ✅ Approved — Android first, iOS verified later. **Slice 0 is done on both.**
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
4. It has been run by hand on an Android phone or emulator, and on an iPhone simulator.

Rules that apply across slices:

- **One label map** turns tool, action and permission identifiers into words. It lives in one place, keyed by backend names; no screen formats an identifier itself.
- **Primary buttons are never disabled**, errors appear under the field that caused them, and destructive actions list what goes and what stays. Reviews check against design notes §2.
- **Backend contract changes land before the screens that use them**, inside the same slice.

At the end: `STAGE_5_BASELINE.md` in the shape of the earlier baselines, and `SYSTEM_PLAN.md` updated.

---

## 3. Slice 0 — Platforms and foundation

No features; everything after this builds on it.

- **Targets.** ✅ Done. Every client module builds for the JVM, Android, `iosArm64` and `iosSimulatorArm64`.
- **Networking.** ✅ Done. The engine sits behind a platform seam: OkHttp on Android, CIO on the JVM, Darwin on iOS. Token-by-token SSE is proven on both — on iOS by a lock-step test that a buffering engine cannot pass.
- **Token storage.** ✅ Done. `FileTokenStorage` (JVM), `KeystoreTokenStorage` (Android), `KeychainTokenStorage` (iOS).
- **Hub address.** `DEFAULT_BASE_URL` is hard-coded to `https://hub.spicy-llama.duckdns.org` in `DataModule`, and also defaulted inside several repositories. Keep it for now; make it one value.
- **`:composeApp`.** A Kotlin Multiplatform library with the screens, theme and navigation, wired to Koin through `HouseholdHubSdk`.
- **`:androidApp`.** The Android application module: `MainActivity`, SDK initialisation, `setContent { App() }`.
- **Theme.** Copenhagen Day and Midnight Espresso tokens (design notes §3), ghost alphas per palette, Outfit / Inter / JetBrains Mono bundled.
- **Hearth icons** as `ImageVector`s — the sheet now draws **31**, with the PIN pad's `delete` added in slice 1.
- **Shared components.** Bento card, chip, buttons (primary, secondary, destructive), text field with inline error, empty state, tool record line, message composer, bottom navigation with the centre +, and a scaffold that scrolls content under a pinned header and navigation.

**Done when** the app opens on an Android phone or emulator, calls `GET /auth/status`, follows the system theme, and the existing JVM test suites still pass.

### 3.1 Build setup findings (checked 11 September 2026)

**AGP 9 changes the module shape.**
- `com.android.library` can no longer be combined with Kotlin Multiplatform. Library modules use `com.android.kotlin.multiplatform.library`, configured inside `kotlin { android { namespace = …; compileSdk = …; minSdk = … } }`. The older `androidLibrary {}` block is deprecated.
- There is no Kotlin Multiplatform form of `com.android.application`. The app has to be a separate `:androidApp` module that depends on `:composeApp`.
- The catalog's `android-library` plugin alias is declared but unused; replace it.

**Current catalog:** Kotlin 2.2.21, AGP 9.1.0, Ktor 3.4.1, Koin 4.0.2, lifecycle 2.8.6, Gradle 9.3.1.

**Versions chosen and built in slice 0** (September 2026):

| Artifact | Version | Note |
| :--- | :--- | :--- |
| Kotlin + `plugin.compose` | 2.4.20 | latest stable |
| Compose Multiplatform | 1.12.0 | latest stable |
| `org.jetbrains.compose.material3:material3` | 1.12.0-alpha03 | the pairing stable CMP 1.12.0 itself ships; no stable Material3 pairs with it |
| Navigation 3 (`navigation3-ui`) | 1.1.1 | stable. Nav2 paired with CMP 1.12 is `2.10.0-alpha02`, so Nav3 was taken instead |
| `org.jetbrains.androidx.lifecycle:lifecycle-*` | 2.11.0 | |
| Koin | 4.2.2 | |
| Ktor (incl. `ktor-client-okhttp`) | 3.5.2 | |
| AGP | 9.4.0 | **needs Gradle ≥ 9.6** — the wrapper went to 9.7.1 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 | **compileSdk 37** is required by Material3 `1.5.0-alpha22`, which CMP 1.12.0 depends on; AGP downloads the platform itself |

**This machine:** JDK 21; Android SDK at `%LOCALAPPDATA%\Android\Sdk` with platforms 33–36.1, build-tools up to 36.1.0, emulator and system images installed. `ANDROID_HOME` isn't set, so the build needs `local.properties` with `sdk.dir` — already gitignored.

**iOS prerequisite:** the owner's Mac with Xcode. An Apple developer account is needed to keep the app on a real iPhone beyond free provisioning's 7-day limit — check when iOS is picked up.

**iOS launch screen — still to do.** The iOS app module exists now (§3.2 below), but `Info.plist`'s
`UILaunchScreen` is still the empty placeholder that fixes the letterboxing (`project.yml`'s
`UILaunchScreen: {}`); the real artwork is unbuilt:
- Declare `UILaunchScreen` in `Info.plist`: `UIColorName` set to a colour asset (e.g. `HearthCanvas` — Any `#F5F2EB`, Dark `#100F0E`), `UIImageName` set to an image asset of the launch tile, and `UIImageRespectsSafeAreaInsets` true.
- The tile asset is a vector (PDF or SVG) with Any and Dark appearances: tile `#3C6E4E` by day, `#7FB894` by night, with the household glyph.
- Export the tile once as SVG, so Android's vector drawable and the iOS asset come from one source.
- App icon: an `AppIcon` asset (1024×1024, no transparency) with the same artwork as Android's launcher icon — full-bleed Hearth green `#3C6E4E` with the white household glyph centred. iOS applies its own corner mask.
- The launch screen is static and disappears when the app draws its first frame. The animated waiting, routing and offline screens are already Compose in `commonMain`, so nothing else is iOS-specific.

### 3.2 iOS findings (done 13 September 2026)

The port cost almost nothing where it was expected to, and a great deal where it was not.

**Environment:** Xcode 26.6, iOS 26.5 simulator, deployment target **15.0** (the simulator SDK's own
`RecommendedDeploymentTarget`), XcodeGen 2.46.0 generating the Xcode project from a checked-in
`project.yml`. The `.xcodeproj` and its `Info.plist` are generated and git-ignored.

**`commonMain` was already clean.** Across 247 Kotlin files the compiler found exactly two
violations: `Charsets.UTF_8` in a test — JVM-only, and default-imported, so no import grep could ever
have found it — and `compose ui-tooling`, which publishes no iOS klib and moved to `androidMain`.
The rule had been held on faith since the Android slice; it held.

**What the port actually cost** was not the targets. It was that all 19 core test files used JUnit 5
and MockK, and that the 36 use cases were final classes. No compile-time mocking library — which is
every library that works on Native — can mock a final class, so the use cases went behind protocols
first and the tests moved to `kotlin.test` + Mokkery. That was two cycles of work before a single
iOS target could be added.

**One production bug that only iOS could expose.** `streamChatTurn` emitted from inside Ktor's
`statement.execute { }`, which switches dispatchers on every non-JVM target, so every `emit` violated
the flow-context invariant — and `DefensiveSseStreamReader`'s catch swallowed the resulting exception
as if it were a malformed line. The stream completed normally having emitted nothing: on an iPhone, a
reply that never arrives and no error to explain it. Android is only *accidentally* safe, because the
JVM flag defaults off; Ktor 4 turns it on everywhere. A `jvmEngineDispatcherTest` task now reproduces
Native's semantics on the JVM in seconds and guards it.

**Risk table, settled.** "SSE buffering on iOS" was a real risk and is now proven absent, by a
lock-step fixture that refuses to send delta N+1 until the client acks delta N — a buffering engine
deadlocks rather than passing by luck. "iOS drifts while Android leads" cost the two violations above
and nothing more.

**Still open:** `KeychainTokenStorage` is covered by an XCTest hosted by the app bundle, because a
bare Kotlin/Native test binary cannot reach the Keychain at all (`errSecNotAvailable`). A real device
run, signing, and the local-network permission for the hub's LAN address remain untried.

---

## 4. Slices

Canvas bands are named as on the design canvas; backend and client items refer to design notes §5.

**Slice 1 — built (13 September 2026).** Backend: PIN sign-in with a lockout of five free tries, then 30 s doubling to 15 min; `GET /auth/members`; username, email and password removed; `POST /users` removed until invites. Client: first run, "Who's here?", the PIN pad, and launch; offline counts down to asking again and opens Tailscale. One ViewModel per screen replaced the planned `AuthViewModel`. A follow-up reworked launch: a phone that is signed in opens on Home without calling the hub; signed out, launch is a splash while it checks; every failure uses the offline layout with its own wording and retries by itself; and Android shows a system splash of the launch tile (`core-splashscreen`). Left for slice 2 as designed there: "I have an invite code" and "Forgotten it?". Not drawn: the hub latency pill. The offline screen's Tailscale status line was dropped from the design, because Android can tell a VPN is on but not that it is Tailscale.

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

**Carried into slice 3 from slice 0's iOS work:** `streamChatTurn` collects `pollUntilFinished`
inside Ktor's `statement.execute { }`, so a 409 keeps that response and its connection open for the
whole polling recovery (up to 60 s). Restructure it to leave `execute` before polling. Noted during
the iOS port; deferred here deliberately.

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

---

## 7. Carried forward

Picked up in later clean sessions. 7.1 comes before slice 3 starts; 7.2 can follow it.

### 7.1 Rebuild `HearthScaffold` on Material 3's `Scaffold` — done (13 September 2026)

**Built.** `HearthScaffold(header, bottomBar, gutter, contentWindowInsets) { padding -> }` hands the content the bars' and window's padding plus the gutter; `contentWindowInsets` defaults to `safeDrawing`, so the keyboard counts. Bars pad their own insets, as Material 3's do — first run's Create footer does. `HearthTopBar(onBack, backDescription, title = null)` takes an optional title for the screens that need one, and a `backDescription` so each screen names its own arrow. First run and the PIN pad are on it, and neither adds `safeDrawingPadding()` any more. A follow-up moved launch and "Who's here?" onto it too, so every screen takes its canvas and insets from the scaffold — launch had handled none. The offline frame scrolls when it doesn't fit, for short phones and large fonts, and stays centred otherwise.

**Why.** Slice 0 built `HearthScaffold` (`composeApp/.../components/HearthScaffold.kt`) as a hand-made `Column`: header, a `weight(1f)` content region that always scrolls, bottom bar. Material 3's `Scaffold` already does that job, and the `Column` falls short in four ways:
- **Lists crash in it.** The content region always wraps a scrolling `Column`, so a `LazyColumn` inside it gets infinite height. Slice 3's Chats list is a `LazyColumn`.
- **No window insets.** `Scaffold` applies the system bars' insets and hands the result to the content as `PaddingValues`. With the `Column`, first run had to add `safeDrawingPadding()` by hand, and the bottom navigation would need the same.
- **No snackbar slot.** Slice 6's publish notice, with its Undo, is a transient message: that is what a snackbar host is for.
- **Content can't pass under the bars.** `Scaffold` measures the bars and pads the content; the `Column` just stacks them.

**Agreed shape.**
- Keep the name `HearthScaffold` as the design-system wrapper, built on `Scaffold`: canvas `containerColor`, header as `topBar`, `bottomBar`, and the screen gutter.
- The content receives the scaffold's `PaddingValues` and chooses how it scrolls: a `Column` with `verticalScroll` for forms (first run), `LazyColumn(contentPadding = …)` for lists.
- Add the snackbar slot when slice 6 needs it, not before.
- Only caller today: `FirstRunContent` — move it over, and drop its hand-added `safeDrawingPadding()` if the scaffold's insets cover it.
- **Move the PIN pad onto it too.** `PinEntryContent` draws its own back button: a padded `Box` holding a 48dp circular, clickable `Box` with the `Back` icon and a content description. That is a top app bar with an empty title. Give `HearthScaffold` a header built on Material 3's `TopAppBar` (empty title, `navigationIcon` = an `IconButton` with `HearthIcon.Back` tinted `textMuted`, container colour the canvas) — as a small reusable `HearthTopBar(onBack)` so later screens with a back arrow (invite, members, profile) share it. The PIN pad keeps its fixed, non-scrolling content; drop its own `safeDrawingPadding()` once the scaffold handles insets. `PinEntryRobot.tapsBack()` finds the button by its content description, so keep `pin_back` on the icon.

**Tests (first).** The existing `HearthScaffoldTest` (header and bottom bar stay put while content scrolls) keeps passing; add one proving a `LazyColumn` works inside, and one proving the bars' insets reach the content padding. `FirstRunScreenTest` must stay green.

**Commit.** On its own, before slice 3 starts.

### 7.2 Previews for the reusable components — done (13 September 2026)

**Built.** Every component in `app/components/` has a `<Component>Preview.kt` beside it, one day/night preview per state, and `icons/HearthIconPreview.kt` draws the set in a grid, resting and active. `HearthTopBar`, added by 7.1 after this list was written, has one too. They share `ComponentPreview`, which puts the component on the theme's canvas — the tooling's background is white in both modes — except the scaffold and the bars, which draw edge to edge. Sample copy is written in the previews, not `strings.xml`: it never reaches the app.

**Why.** Every screen's `Content` file has day/night previews driven by its `UiStateProvider`, but none of the shared components in `composeApp/.../app/components/` has one, nor does the icon set. Someone maintaining the app later should be able to open a component and see what it looks like.

**Scope.** `@DayNightPreviews` beside each component, in its own file, showing the states that matter:
- `Buttons` — primary, secondary, destructive; with and without an icon
- `HearthChip` — one per `ChipVariant`
- `HearthTextField` — empty with placeholder, with helper, with error, filled
- `BentoCard` — with and without its label
- `EmptyState` — with and without its action
- `MemberAvatar` — the sizes in use (Who's here, PIN pad)
- `HearthBottomNav` — a tab selected
- `MessageComposer` — empty and with text
- `ToolRecordLine` — a record
- `HearthScaffold` — header, scrolling content and bottom nav; do it after (or with) 7.1 so it previews the `Scaffold`-based version
- `HearthIcon` — the whole set in a grid, resting and active

`ChipColors`, `ChipVariant` and `IconShape` are not composables and need none. No guard test: previews only.

**Commit.** On its own.

### 7.3 Smaller items

- ~~**A token the hub no longer accepts.**~~ Done in slice 2: a 401 to any call that carries a token forgets the token and sends the phone back to "Who's here?", and the app renews its token when it opens. Sign-in's own 401 is a wrong PIN, and a wrong PIN while signed in answers 403, so neither signs anyone out.
- ~~**No `/auth/refresh` on the backend.**~~ Decided in slice 2: the endpoint was added. It re-issues a token that the hub still accepts — no separate refresh token — and the app renews once per start, so a phone in use never reaches the end of its 30 days.
- **Dead backend code.** `backend/app/api`, `app/models` and `app/schemas` are a legacy layer nothing mounts; they still speak username and password. Delete or migrate.
- **Icon sheet leftovers** (Hearth icon set artefact): section 04's intro still says "the twenty-six above", and section 02's "Shared set — chosen" option shows a stray `biometricUnlock` cell beside `memory`.
- **First-run swatches.** Two of the five match other meanings: `#6B655F` is the Secret Mode ghost and `#A33B2A` is the error red. Built as drawn; revisit on the canvas if they read wrong.
- **System splash tile.** On the canvas, "0 · System splash" draws the tile without the drop shadow launch's tile has.
