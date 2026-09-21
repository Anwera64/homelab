# Slice 2 loading states — handoff

> **Transient. Delete this file before the PR merges.** `AGENTS.md` §1 says plans don't belong in
> the repository; this one is here only because the work has to cross a session boundary, and
> removing it is a checklist item below. It is not a design document — the design notes are.

**Branch:** `claude/slice-2-loading-states-y5yriy` · **Last commit:** `9f01505`
**Design (approved):** [Slice 2 Loading States canvas](https://claude.ai/artifact/38mN2LjFpzpK44XsuA2cPm)
**Delivery:** two PRs, foundation then screens (the author's call). No PR is open yet.

---

## 1. Where this stopped, and why

`9f01505` adds `WaitPhase.kt` and `WaitPhaseTest.kt` — cycle 1 of PR 1. **It has never been
compiled or run.** This container's egress policy denies `dl.google.com:443` (403 on CONNECT), so
Gradle cannot resolve the Android plugin or any AndroidX/Compose artifact, and the Gradle cache
holds only `org.jetbrains.*`. `maven.google.com` is not an alternative: every artifact path 301s
straight back to `dl.google.com`.

**First thing to do in the new session** — confirm the block is gone:

```sh
curl -sS -o /dev/null -w "%{http_code}\n" --max-time 25 \
  https://dl.google.com/dl/android/maven2/androidx/core/core/1.13.1/core-1.13.1.pom
```

`200` means proceed. `000` with `CONNECT tunnel failed, response 403` means the environment's
network policy still has not been updated — stop and say so, do not retry the build or route
around it.

Then, before anything else, actually run the committed cycle:

```sh
cd apps/household-hub/client && ./gradlew :composeApp:jvmTest --tests "*WaitPhaseTest*"
```

If it fails, fix it — it was hand-checked, not proven.

---

## 2. Facts already established — do not re-explore

These cost two full exploration passes. Take them as read.

1. **No ViewModel changes are needed anywhere.** Every one of the eleven ViewModels already sets
   its busy status before the call and guards re-entry (`InviteCreateViewModel.kt:51`,
   `JoinViewModel.kt:74`, `ChangePinViewModel.kt:63`, `PinEntryViewModel.kt:58`, …).
   `:core:presentation` is untouched by this whole change.
2. **First load vs refresh needs no new state.** `MembersViewModel.load()` sets `status = Loading`
   via `copy()`, so `rows` survives a re-fetch: empty rows + `Loading` is pattern 2, non-empty rows
   + `Loading` is pattern 3. Same for `ProfileUiState.member == null` and `PinForgotUiState.others`.
3. **Animation needs no new Gradle dependency.** `HearthSwitch.kt:3` already imports
   `androidx.compose.animation.core.animateDpAsState`, proving `animation-core` is on
   `commonMain`'s classpath. Use **only** `animation-core` — `AnimatedVisibility` lives in the
   separate `animation` package and is *not* proven present.
4. **Every screen test already iterates its preview provider** (`every_previewed_state_draws`), and
   **13 of 15 providers are missing their busy state**. Adding a provider entry extends an existing
   test for free.
5. **The two `LinearProgressIndicator` call sites are byte-identical** (`LaunchContent.kt:151`,
   `ProfilePickerContent.kt:106`) — they become one component, not a third copy.
6. **`stateDescription` and `liveRegion` appear nowhere in the app.** Announcing a busy state is new
   ground here, not a pattern to copy.
7. Screens are stateless: `*Screen.kt` owns the ViewModel, `*Content.kt` takes the state. All UI
   work lands in `*Content.kt`, new components, and `app/theme`.

### The constraint that shapes everything

Compose UI tests synchronise on idleness. **An infinite animation never lets the composition go
idle**, so `waitForIdle`, `waitUntil` and `onNode…` hang until they time out. The moment a busy
state is added to a `*UiStateProvider`, that screen's existing `every_previewed_state_draws` test
would mount an endlessly-animating tree and start timing out. `mainClock` is used nowhere in this
repo and driving it by hand is the brittleness to avoid.

So motion is **switched off in tests by construction**, reusing the scale-swapping mechanism the
theme already has:

- `HearthMotion` carries `animate: Boolean` alongside its durations.
- `DefaultMotion` animates. `StillMotion` does not, and collapses `hold` and `minimumVisible` to
  zero so nothing is time-gated; its `slow` stays 8 s so the slow line never appears by accident.
- Every animated component reads `HearthTheme.motion.animate` and draws its **resting frame** when
  false — a full-opacity skeleton block, a static bar, dots at rest.
- `TestApp` provides `StillMotion`. Previews keep `DefaultMotion`.

**No test asserts on motion, duration, easing or frames.** The author was explicit about this
twice. The only timing test is `waitPhase()`, which is arithmetic with no clock.

### Guard rails that will bite

- `DesignSystemTokenTest` (`shared/src/jvmTest/.../DesignSystemTokenTest.kt`) scans
  `composeApp/src/commonMain/kotlin` **excluding `app/theme`** and fails on raw `.dp`, raw `.sp`,
  `FontWeight.`, and `DefaultSpacing`/`DefaultSizes`. Any new dimension must be a theme token read
  through `HearthTheme`.
- `.githooks/pre-commit` runs `CleanArchitectureBoundaryTest`, `KoinDependencyGraphTest`,
  `DesignSystemTokenTest`, `CoroutineSafetyTest` on every commit touching the client — **but
  `core.hooksPath` is not configured**, so it does not fire unless you set it.
- `TestNameCompatibilityTest` forbids `, . ; [ ] / < > : \` in backticked test names. Existing tests
  sidestep this by using plain `snake_case` identifiers with no backticks — follow that.
- `CoroutineSafetyTest` forbids bare `runCatching` — use `runCatchingSafe`.
- CI (`.github/workflows/household-hub-client.yml`) runs on PRs to `master` only, not on branch
  pushes. It runs `./gradlew jvmTest --continue`, `iosSimulatorArm64Test`, and on-device Android
  tests. `commonTest` runs on iOS too, so tests must stay target-agnostic.

---

## 3. The five patterns

| # | Pattern | Where |
| :-- | :--- | :--- |
| 1 | The tapped button keeps full colour and shadow, its label becomes the verb in progress, a 4dp bar runs along its bottom edge | 8 button actions |
| 2 | Screen arrives empty: real chrome draws at once, breathing blocks where the answer lands | Members, Profile, Forgotten PIN |
| 3 | Screen refreshes: rows stay, 4dp bar full-bleed under the header | Members, coming back |
| 4 | One card reloads in place | Add a member, second code |
| 5 | The PIN pad's six dots wave — it has no submit button to put a bar on | The PIN pad (slice 1) |

Timing, identical everywhere: nothing for 250 ms, then the pattern, held ≥400 ms; at 8 s a quiet
caption; then the Unreachable / Failed wording each screen already has. Nothing is ever dimmed
(design notes §2).

---

## 4. PR 1 — remaining work

Cycle 1 is committed (verify it first, see §1).

### Cycle 2 — motion tokens and the off switch

- **Red:** `composeApp/src/commonTest/.../theme/HearthThemeTest.kt` — `HearthTheme.motion` resolves
  inside `HearthTheme { }`; a subtree given `StillMotion` reports `animate == false`.
- **Green:** `app/theme/HearthMotion.kt`, copying the `HearthSpacing`/`HearthSizes` shape exactly —
  `@Immutable data class HearthMotion`, `DefaultMotion`, `StillMotion`, `internal LocalHearthMotion`
  provided in `HearthTheme`, exposed as `HearthTheme.motion`. Holds `hold` 250 ms,
  `minimumVisible` 400 ms, `slow` 8 s, `barCycle` 1150 ms, `breathe` 1600 ms, `wave` 1400 ms,
  `waveStagger` 90 ms, `waveLift` 4.dp (a `Dp`, so it must live here) and `animate`.
  Put a comment at the top saying a test without `StillMotion` will hang rather than fail clearly.
- **Refactor:** add a fifth rule to `DesignSystemTokenTest` forbidding `durationMillis = <n>` and
  bare `<n>.milliseconds` / `<n>.seconds` outside `app/theme`, mirroring the existing `.dp` rule.
  It passes from the start; it exists so the next screen cannot invent its own timing.
- Then finish cycle 1's refactor: `@Composable fun rememberWaitPhase(busy: Boolean): WaitPhase` in
  `WaitPhase.kt`, driven by `HearthTheme.motion`, keeping the 400 ms anti-flash floor as a
  remembered timestamp (no extra tested API).

### Cycle 3 — the shared bar and busy buttons

- **Red:** `components/ButtonsTest.kt`, all under `StillMotion` — a busy button does not invoke
  `onClick`; it carries a busy state description; a non-busy button still clicks and is never
  dimmed. `components/HearthProgressBarTest.kt` — the resting render is present and announced.
- **Green:** `components/HearthProgressBar.kt` with the config both existing call sites use verbatim
  (160×4, `primary` on `outline`, `StrokeCap.Round`, `gapSize = none`), plus a `fullBleed` variant
  for pattern 3 and an `inset` variant for inside a button; renders **determinate at a fixed
  fraction** when `motion.animate` is false so the composition settles. `components/Buttons.kt`
  gains `busy: Boolean = false` on all three buttons — busy keeps full colour and shadow and
  swallows the tap, never dims, so the file's comment at lines 24–25 stays true. The caller passes
  the working label. All ~24 call sites use named arguments, so the default is source-compatible.
- **Refactor:** point `LaunchContent.kt:151` and `ProfilePickerContent.kt:106` at the new component.
  Their existing tests must stay green **untouched** — that is the proof the refactor is faithful.

### Cycle 4 — skeletons

- **Red:** `components/SkeletonTest.kt` under `StillMotion` — a skeleton exposes no text, is hidden
  from the semantics tree, and its container carries the loading state description.
- **Green:** `components/Skeleton.kt` — `SkeletonBlock` and `SkeletonCircle` in `outlineSoft`,
  breathing 0.5 → 1 → 0.5 over `motion.breathe` via `rememberInfiniteTransition` + `animateFloat` +
  `infiniteRepeatable`, flat at full opacity when `motion.animate` is false. Each is
  `clearAndSetSemantics {}`; the container carries `stateDescription` + `liveRegion = Polite`.

### Also in PR 1

- Provide `StillMotion` from `TestApp` so every future screen test inherits the off switch.
- Two accessibility strings in `composeApp/src/commonMain/composeResources/values/strings.xml`
  (single locale, 219 strings today).
- **Docs** — `STAGE_5_DESIGN_NOTES.md`: a "Waiting has one shape" row in §2, a motion scale in §3
  beside space and type, and a §6.20 recording the five patterns, the four timing beats, why the PIN
  pad needs its own, and the rule that animated components must render a resting frame so tests
  never hang.
- **Delete this handoff file.**

---

## 5. PR 2 — the screens

Needs a **second branch**, and the original session was permitted to push only to
`claude/slice-2-loading-states-y5yriy`. Ask the author before creating it — suggested
`claude/slice-2-loading-states-screens`.

Per screen, across 12 `*Content.kt` files:

1. **Red** — add the missing busy entry to that screen's `*UiStateProvider.kt`; the existing
   `every_previewed_state_draws` test then covers it (safely, because motion is off). Add one
   assertion for the busy affordance — the working label, or the skeleton's state description.
2. **Green** — wire the pattern: pattern 1 for `InviteCode`, `Join`, `InviteCreate`, `PinApprove`,
   `NewPin`, `ChangePin`, `RemoveMember`, `LeaveHousehold`; patterns 2 and 3 for `Members`,
   `Profile`, `PinForgot`; pattern 4 for `InviteCreate` with a code already shown.
3. **Refactor** — push repetition down into the shared components.

Then the PIN pad (pattern 5): the wave in `PinEntryContent.Dots`, one `motion.waveLift` step
staggered by `motion.waveStagger`, flat when `motion.animate` is false; plus **reserving one caption
line of height in `StatusLine`** so the keypad does not jump when the 8-second line arrives.

New strings: the working label per action, and the slow-hub line. The exact labels are column 4 of
the table on the design canvas.

---

## 6. Verification

```sh
cd apps/household-hub/client
./gradlew :shared:jvmTest          # architecture + design-token guards
./gradlew jvmTest --continue       # everything CI's jvm-tests job runs, incl. Compose UI tests
./gradlew iosSimulatorArm64Test    # proves commonTest is target-agnostic
```

Nothing automated watches the motion — that is deliberate. Check by eye once, on the Android app
against a real hub:

- the wave on the PIN pad, and a bar under a working button;
- on a fast LAN, that **no loading state appears at all** for a normal call — the 250 ms hold doing
  its job, and the thing most likely to be wrong;
- one screen-reader pass, since `liveRegion` is new ground here.

## 7. Checklist before the PR

- [ ] `WaitPhaseTest` actually run and green
- [ ] Cycles 2, 3, 4 complete, each Red observed failing before its Green
- [ ] `jvmTest` and `iosSimulatorArm64Test` green
- [ ] Design notes updated (§2, §3, §6.20)
- [ ] **This file deleted**
- [ ] PR opened against `master` (check for a PR template first; there was none as of `3b75f12`)
