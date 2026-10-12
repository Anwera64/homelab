# Household Hub: Stage 5 Plan — Compose Multiplatform UI

**Status:** ✅ Approved — Android first, iOS verified later. **Slice 0 is done on both.**
**Target:** new `:composeApp` and `:androidApp` (Android now, iOS later), `:core:*` and `:shared` (new targets, ViewModel changes), `backend` (the changes the designs depend on)
**Date:** September 2026

---

## 1. Goal and scope

Ship the Household Hub phone app on Android and iOS, built to the Stage 5 designs, with every backend and client-core change those designs depend on.

| | |
| :--- | :--- |
| **Source of truth** | [`STAGE_5_DESIGN_NOTES.md`](STAGE_5_DESIGN_NOTES.md) for screens, rules and the dependency list (§5); [`STAGE_5_MEMORY.md`](STAGE_5_MEMORY.md) for memory; [`STAGE_5_SECRET_SESSION_LOCKING.md`](STAGE_5_SECRET_SESSION_LOCKING.md) for secret chats |
| **In** | Phone portrait on Android and iOS. Every flow on the design canvas. Both themes — the canvas draws the day palette; night comes from the proven tokens |
| **Platform order** | Android first, slice by slice. iOS is compiled and checked later on the owner's Mac; until then `commonMain` must stay free of JVM- and Android-only APIs so it keeps compiling for iOS |
| **Working rhythm** | Each slice is done in a fresh session, starting from this plan and the design notes |
| **Out, later stages** | Tablet split layout · biometric unlock (locking spec §7) · full-text search · one session on several devices · deployment (Stage 6) |
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
- **Session storage.** ✅ Done. The token and the signed-in member, in one item per platform: `FileSessionStorage` (JVM), `KeystoreSessionStorage` (Android), `KeychainSessionStorage` (iOS).
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

**iOS launch screen — built (15 September 2026).** `project.yml`'s `UILaunchScreen` now names real
assets instead of the empty placeholder that only fixed the letterboxing: `UIColorName: HearthCanvas`,
`UIImageName: HearthLaunchTile`, `UIImageRespectsSafeAreaInsets: true`. `NSLocalNetworkUsageDescription`
landed alongside it — the hub's address resolves to a LAN address on home Wi-Fi, and iOS has required
that declaration, shown verbatim in its prompt, since iOS 14. The launch screen is static, as
planned: it disappears on the app's first frame, and the animated waiting, routing and offline
screens needed nothing iOS-specific because they were already Compose in `commonMain`.

The plan said to export the tile once as SVG "so Android's vector drawable and the iOS asset come
from one source". That was not done literally, and deliberately: Android cannot consume an SVG, so
one source would have meant writing an SVG → Android-vector converter — `<g transform>` to
`<group translateX/scaleX>`, stroke mapping, the `@color/` indirection no SVG has — or making
`splash_tile.xml` a build output, which would hide the artwork from review diffs and break Android
Studio's resource preview. That is a larger program than the two path strings it would deduplicate.
The copies stay hand-typed, and each file's comment names the others so whoever edits one knows
where the rest are.

Two things were not obvious going in. First, an asset catalog takes SVG for an ordinary image set —
`HearthLaunchTile.imageset` does, with `preserves-vector-representation` — but not for an app icon,
which actool insists on as a raster; hence a checked-in `AppIcon-1024.png`, rasterised from the
vector source `tools/hearth_app_icon.svg` by `tools/make_app_icon.sh`, rather than the SVG itself.
Second, an `AppIcon` set turns out to be mandatory the moment *any* asset catalog exists at all, not
just once real icon artwork is ready: XcodeGen's iOS-application preset injects
`ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` unconditionally, and actool hard-fails the build —
"None of the input catalogs contained a matching stickers icon set, app icon set, or icon stack
named AppIcon" — the instant a catalog exists without one. A stub `AppIcon.appiconset` (a single
unfilled 1024×1024 slot) had to land with the very first asset catalog, four commits before the
real artwork, for exactly that reason. It was found the only way it can be: by building.

The night launch tile also turned out not to be the day artwork on a darker backdrop. Night's
`onPrimary` is the palette's near-black canvas colour, not white, so the night tile pairs a light
green fill (`NightColors.primary`, `#7FB894`) with a near-black glyph (`NightColors.onPrimary`,
`#100F0E`) — both halves invert together, not just the background.

A `BrandAssetParityTest` comparing every copy as text was written during this slice and then
removed before merge, as overkill: 1295 lines guarding 93 lines of artwork that changes almost
never, three times the size of every other architecture guard combined. Most of what it checked the
iOS XCTests already cover on the macOS runners — the colour set resolving in both appearances, the
tile's rendered colours, the launch-screen plist, the icon's corner pixel — so the claim that it was
the only CI coverage of the iOS catalog, made while building it, was wrong. Consistency across the
copies is now a matter of care and the comments in each file.

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

**Still open:** `KeychainSessionStorage` is covered by an XCTest hosted by the app bundle, because a
bare Kotlin/Native test binary cannot reach the Keychain at all (`errSecNotAvailable`). A real device
run, signing, and the local-network permission for the hub's LAN address remain untried.

---

## 4. Slices

Canvas bands are named as on the design canvas; backend and client items refer to design notes §5.

**Slice 1 — built (13 September 2026).** Backend: PIN sign-in with a lockout of five free tries, then 30 s doubling to 15 min; `GET /auth/members`; username, email and password removed; `POST /users` removed until invites. Client: first run, "Who's here?", the PIN pad, and launch; offline counts down to asking again and opens Tailscale. One ViewModel per screen replaced the planned `AuthViewModel`. A follow-up reworked launch: a phone that is signed in opens on Home without calling the hub; signed out, launch is a splash while it checks; every failure uses the offline layout with its own wording and retries by itself; and Android shows a system splash of the launch tile (`core-splashscreen`). Left for slice 2 as designed there: "I have an invite code" and "Forgotten it?". Not drawn: the hub latency pill. The offline screen's Tailscale status line was dropped from the design, because Android can tell a VPN is on but not that it is Tailscale. **Brought to parity on iOS (15 September 2026):** the Compose screens were already shared and already passing on the simulator; what iOS still needed was the native shell around them — the asset catalog, the launch screen and app icon, the LAN-use declaration — and the XCTests proving each (§3.1 above).

**Slice 2 — built (16 September 2026).** Backend: invites (`POST /invites`, `GET /invites/{code}`, `POST /invites/{code}/redeem`), PIN reset (`POST /users/{id}/pin-resets`, `POST /auth/pin-resets/{code}/redeem`) and `hub reset-pin` from the server itself; `token_version` on every token with `POST /auth/refresh` to re-issue one; `POST /users/me/pin`; removal and leaving deactivate rather than delete, so what someone shared keeps their name, and agents know they have gone. Refusals carry a `code`, and 401 now means only that a token isn't accepted — a wrong PIN while signed in answers 403. One hub-wide guard covers guessing invite and reset codes. Client: a 401 forgets the token and returns to "Who's here?"; the app renews its token when it opens; the members screens are built as drawn, with the Profile shell holding only its backed rows until slice 5 fills it in. Codes carry `expires_in_seconds`, because a phone's clock need not agree with the hub's. Still to do by hand: the two-phone run in §8 of the slice plan, and an iPhone pass.

**Slice 4 — built (12 October 2026).** Every tool step says what that action did: the labels are keyed by tool and action, so removing an event reads "Remove from your calendar", and the permission words match the settings screen (#51). A write now pauses the turn on an approval card with Approve and Decline, the `tool_call_id` travels with the proposal, and the hub saves the paused turn, so the model unloading or the hub restarting loses nothing; several writes in one step give one card each and the turn resumes after the last; an unanswered card never expires; Send keeps the text and says "Answer the card above first" (#58). The card's Edit view changes a write before it runs, with day and time pickers (#60) and Repeat and Ends (#90). Repeating events: a change to one asks "Only this date" or "This and following" (#62). Auto-approve is per person and per tool and action, set from the card's "from now on" box (#61) or from Profile › Agents › "When agents act" (#147); removing events and replacing notes always ask, and an automatic write still leaves a record. A calendar step that fails says why and offers "Reconnect calendar" (#59). Approval cards follow AGENTS.md's layer rules: the phone maps the hub's arguments into typed `ProposalDetails` (#66). **Calendar connection was pulled forward from slice 8** so the calendar tools could be tested: Apple with an app password, a rejected password and an unreachable server told apart (#52), and Google by signing in with Google (#54, built in #56 and #57). The Calendar tab, Schedule and events stay in slice 8.

Fixed during the slice: web search shows its query and sources and says why it failed (#40), the model chooses general or science search (#38), throttled engines no longer look like "no results" (#42); remove and change cards show the event's title (#63); the agent no longer invents an event id to delete (#65); a second message right after an answer is no longer refused while the hub reflects (#53); the clock in the system prompt no longer breaks the prompt cache every minute (#79); signing out revokes that device's token (#94); on iOS, the safe area is no longer applied twice and the keyboard can be dismissed (#45, #46); lint and test rules for composables and screen robots (#149, #150). Carried forward: the agent can say it added an event without calling the tool (#64), calendar events get saved as memories (#133), events have no time zone of their own (#84), `read_page` can't pass bot challenges (#37), and answers cut off by the context window aren't marked (#41). Run by hand on Android and the iPhone simulator (12 October 2026).

| # | Slice | Screens | Backend | Client core | Done when |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Sign in** | Onboarding (launch, first run, profile picker, PIN, signed-out offline) | PIN replaces password, attempt counter with backoff, public profile list, Hygge avatar default (§5.1) | `LoginUseCase` takes a member and PIN instead of username and password; `AuthViewModel` follows | First run creates the admin with a PIN; the picker lists members; repeated wrong PINs back off; an unreachable hub shows the offline screen |
| 2 | **Members** | Invite; Members & account; PIN forgot / approve | Invites, PIN reset, `token_version`, deactivation, delete account, sole-admin wording (§5.1) | Invite, reset and member use cases | A second member joins by code; the other member can approve a PIN reset; changing a PIN signs out the other device; removing someone deactivates them |
| 3 | **Chatting** | Daily loop (new chat, conversation, chats); Message states; title search; empty chats. Household and My Space shells with only their backed parts | `last_message_preview` (§5.3) | Stream failures classified by when they happen; the 60 s limit ends the wait, not the turn; on-device title search (§5.3) | All four message states reproduce against a real hub; the partial answer survives a dropped stream |
| 4 | **Tools** | Tools; Auto-approve; Web search; connecting a calendar (moved here from slice 8) | `tool_call_id` on the approval event; per-person, per-action approval settings (§5.3–5.4) | Handle `ToolExecuting` and `ToolResult`; the label map | Reads leave records; approve, edit and decline work; auto-approve adds events without asking and still leaves a record; search sources open; Apple and Google calendars connect, and a bad password shows the drawn failure |
| 5 | **Memory** | Profile and the memory screens. To be designed when the slice is planned: the Profile & memory boards on the canvas describe the system this slice replaces | [`STAGE_5_MEMORY.md`](STAGE_5_MEMORY.md) in full: `remember` and `forget`, reflection after a chat goes idle in place of one per turn, one store without gossip milestones, vectors, the fixed block and recall, proposals, calendar events as linked memories, logging (§5.6) | What those screens need, decided with their design | An event added in a chat is remembered once, with its expiry; removing or moving it, in the hub or outside, retires or updates that memory; an explicit "remember" is stored through its card; an idle chat is reflected once and shows when; a changed fact appears as a proposal; nothing from a secret turn is stored; no model call follows a turn; each of those can be read back from the logs |
| 6 | **Secret chats** | Secret Mode; Search & secret titles; Privacy phrases | The locking spec §4, except §4.4 (no memories from secret turns), which slice 5 builds: unlock endpoint, 423 reads, scoped tokens, list redaction (§5.2) | Replace the unlock stub; lock state and idle timer; unlock bar and Lock now; `suggestSecretMode` | The locking spec's §8 tests pass; a stolen access token gets 423; titles appear only after the PIN |
| 7 | **Agents** | Agents; Agent form errors; Agent unavailable; empty trash | 409 handle conflicts with codes; minimum lengths (§5.5) | Agent create, edit, suspend, trash, restore, purge | Every drawn form error reproduces; trashed and suspended agents make their chats read-only; purged ones archive |
| 8 | **Calendar** | Calendar; Schedule; calendar parts of Household and My Space (connecting a calendar moved to slice 4) | — (endpoints exist) | `CalendarViewModel` and repository; Google URL built on the phone (§5.7) | Schedule shows both members |
| 9 | **PDFs and notes** | PDFs; Notes; empty notes | Session documents, PDF tool reading from the store, attached-document context, removal, no page limit, password detection, pages without text; note attribution and the create-overwrite bug (§5.8–5.9) | File picker (Android and iOS), share sheet for notes | Every drawn PDF state reproduces with a real file; a note shows its source chat and shares as Markdown |
| 10 | **Offline reading** | Offline | — | A multiplatform client database (e.g. SQLDelight) caching sessions, messages, members, spaces and events, with a written-at time per record; secret content never cached (§5.10) | Signed in with the hub off, the app opens on cached data with an "as of" banner; every write is disabled with a reason |
| 11 | **Morning briefing** | To design first; then Household and "Your day" in My Space | A briefing endpoint (to be designed) | — | Designed on the canvas, then built |

**Carried into slice 3 from slice 0's iOS work:** `streamChatTurn` collects `pollUntilFinished`
inside Ktor's `statement.execute { }`, so a 409 keeps that response and its connection open for the
whole polling recovery (up to 60 s). Restructure it to leave `execute` before polling. Noted during
the iOS port; deferred here deliberately.

**Why this order.** Sign-in gates everything. Chat is the core loop and the first thing worth using daily. Tools come before memory and secret chats because both rely on the tool events being handled: `remember` is a write tool with an approval card, and secret chats lock write tools. Memory comes before secret chats because it is used every day and is wrong today (#133): it saves stale and repeated facts, makes the next message wait, and undoes the prompt cache. The two were first planned the other way round, because the memory screen promises "Nothing said in Secret Mode is ever written down"; that promise rests on one backend rule (locking spec §4.4), which slice 5's own reflection now carries. They were swapped and renumbered on 12 October 2026. Calendar comes before the briefing, which is built on events. Offline reading is last before the briefing because it caches what the other slices produce.

---

## 5. Risks

| Risk | Mitigation |
| :--- | :--- |
| iOS drifts while Android leads | `commonMain` stays free of JVM- and Android-only APIs; iOS targets are compiled on the Mac as soon as Android slice 0 works, then checked in batches |
| SSE buffering on iOS | Prove streaming first thing when iOS is picked up, before relying on any chat screen there |
| Re-locking on background behaves differently on iOS and Android | Slice 6 tests both lifecycles by hand; the idle timer counts only user input (locking spec §3) |
| Stage size — twelve slices | Slices 10 and 11 can move to the next stage without breaking anything before them |
| PDFs larger than the model's context | The PDF tool reads a few pages at a time (slice 9) |
| Google narrowing app passwords | Resolved in slice 4: Google connects by signing in with Google (#54) |
| Privacy phrases are English only | Known and not planned. Spanish or Catalan phrases can be added to the list if you want them |

---

## 6. Decisions still needed

1. ~~The iOS build machine~~ — decided: the owner's Mac, after Android.
2. **Offline reading** — in this stage as slice 10, or moved to the next. Needed before slice 10 starts.
3. **Memory** — the screens (where proposals live, how the last reflection shows in a chat, what becomes of "Shared with the household") and a few behaviours and defaults. Listed in [`STAGE_5_MEMORY.md`](STAGE_5_MEMORY.md) §12; settled when slice 5 is planned.

---

## 7. Carried forward

Picked up in later clean sessions. 7.1 comes before slice 3 starts; 7.2 can follow it.

### 7.1 Rebuild `HearthScaffold` on Material 3's `Scaffold` — done (13 September 2026)

**Built.** `HearthScaffold(header, bottomBar, gutter, contentWindowInsets) { padding -> }` hands the content the bars' and window's padding plus the gutter; `contentWindowInsets` defaults to `safeDrawing`, so the keyboard counts. Bars pad their own insets, as Material 3's do — first run's Create footer does. `HearthTopBar(onBack, backDescription, title = null)` takes an optional title for the screens that need one, and a `backDescription` so each screen names its own arrow. First run and the PIN pad are on it, and neither adds `safeDrawingPadding()` any more. A follow-up moved launch and "Who's here?" onto it too, so every screen takes its canvas and insets from the scaffold — launch had handled none. The offline frame scrolls when it doesn't fit, for short phones and large fonts, and stays centred otherwise.

**Why.** Slice 0 built `HearthScaffold` (`composeApp/.../components/HearthScaffold.kt`) as a hand-made `Column`: header, a `weight(1f)` content region that always scrolls, bottom bar. Material 3's `Scaffold` already does that job, and the `Column` falls short in four ways:
- **Lists crash in it.** The content region always wraps a scrolling `Column`, so a `LazyColumn` inside it gets infinite height. Slice 3's Chats list is a `LazyColumn`.
- **No window insets.** `Scaffold` applies the system bars' insets and hands the result to the content as `PaddingValues`. With the `Column`, first run had to add `safeDrawingPadding()` by hand, and the bottom navigation would need the same.
- **No snackbar slot.** The publish notice then planned, with its Undo, is a transient message: that is what a snackbar host is for. *The notice was dropped with the memory rework (12 October 2026): no turn publishes anything any more.*
- **Content can't pass under the bars.** `Scaffold` measures the bars and pads the content; the `Column` just stacks them.

**Agreed shape.**
- Keep the name `HearthScaffold` as the design-system wrapper, built on `Scaffold`: canvas `containerColor`, header as `topBar`, `bottomBar`, and the screen gutter.
- The content receives the scaffold's `PaddingValues` and chooses how it scrolls: a `Column` with `verticalScroll` for forms (first run), `LazyColumn(contentPadding = …)` for lists.
- Add the snackbar slot when a screen needs it, not before.
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
