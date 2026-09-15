# Implementation Plan — Stage 5 · Slice 1 (iOS): the native shell on Apple

> Supersedes the slice-0 (iOS) plan of the same name, kept in git history. Slice 0 took the platform
> foundation to Apple; this plan takes **slice 1** — onboarding — there. Written before the work and
> updated with outcomes as each cycle landed, in the shape the slice-0 plan established.

**Status:** ✅ Built, 15 September 2026, on branch `stage5/slice1-ios`.

## 1. Problem

Slice 1's *screens* were already at parity, and that was measured rather than assumed: all four live
in `composeApp/src/commonMain` as Compose Multiplatform, and
`composeApp/build/test-results/iosSimulatorArm64Test/` recorded 22 suites and 0 failures with every
case name carrying an `[iosSimulatorArm64]` suffix — `FirstRunScreenTest`, `LaunchScreenTest`,
`PinEntryScreenTest`, `ProfilePickerScreenTest` among them. CI already ran them: `ios-simulator-tests`
invokes `./gradlew iosSimulatorArm64Test` unqualified, which sweeps in `:composeApp`.

What had no iOS twin was the **native shell around those screens**, and its proofs. Android's slice 1
landed two commits with nothing on the other side:

| Commit | Android artefact | iOS before this slice |
| :--- | :--- | :--- |
| `ada68f3` | `splash_tile.xml`, `Theme.HyggeHub.Starting`, `installSplashScreen()`, `SplashThemeTest` | `UILaunchScreen: {}` — the empty dict that only defeats letterboxing. No asset catalog existed anywhere under `iosApp/` |
| `f9f7fbc` | `ic_launcher_foreground.xml`, the adaptive `ic_launcher`, `launcher_colors.xml`, two `LaunchSmokeTest` assertions | the blank default iOS icon, and no smoke test at all |

A third problem surfaced during planning: **the first-run form's keyboard could not be dismissed on
iOS.** Its PIN field is `KeyboardType.NumberPassword`, which iOS draws as a numeric keypad with no
return key, so `ImeAction.Done` had no button to sit on, and nothing in the repo offered
tap-outside-to-dismiss. It went unnoticed because the Android emulator uses the host hardware
keyboard, so the soft keyboard never opens during a hand-run.

**Done when:** the simulator shows the HyggeHub icon, launches through a Hearth-canvas launch screen
carrying the tile in both appearances, the slice-1 flows have been driven by hand, and the new
XCTests and the drift guard are green in CI.

## 2. What was already ready

| Checked | Result |
| :--- | :--- |
| The four slice-1 screens | 100% `commonMain`; no `expect`/`actual`, no platform branch |
| iOS simulator run | 22 suites / 104 tests / 0 failures, natively |
| Tailscale hand-off | `IosExternalApps` + `LSApplicationQueriesSchemes`, from slice 0 |
| XCTest bundle | app-hosted (`TEST_HOST` = `HouseholdHub.app`), so `Bundle.main` **is** the app — which is all the bundle, icon and plist assertions need |
| CI | `ios-app-tests` already runs `xcodegen generate` + `xcodebuild test -scheme HouseholdHub`, so new `.swift` files in the bundle run with **no workflow change** |

## 3. Architecture

### One source for the glyph and the colours

The plan of record for slice 0 instructed: "export the tile once as SVG, so Android's vector drawable
and the iOS asset come from one source." That was **not** done literally, and the reasoning is in
`STAGE_5_PLAN.md` §3.1: Android cannot consume an SVG, so one source means writing a converter or
making `splash_tile.xml` a build output. Either is a larger program than the two path strings it
deduplicates.

Instead the copies stay hand-typed and **`BrandAssetParityTest` makes divergence impossible to
merge**: `HearthColors.kt` and `HearthIcon.Household` are canonical, and the Android resources, both
launch-tile SVGs, the icon's SVG source, the iOS colour set, the imageset manifest, the appiconset
manifest, the icon PNG and the `UILaunchScreen` declaration all answer to them. It runs on **Linux**
in the existing `jvm-tests` job, which is the only thing that ever reads the iOS catalog on CI.

### The app icon is a raster, from a vector source

Asset catalogs take SVG for image sets but not for `AppIcon`. The icon is therefore a committed
1024² PNG, rasterised from `tools/hearth_app_icon.svg` by `tools/make_app_icon.sh`. **librsvg is a
developer-machine dependency only**; CI never regenerates the PNG.

Having a vector source is the point, not a convenience. The icon was nearly drawn with CoreGraphics
path calls, which would have made it the one copy of the glyph the guard could never check — it
compares path strings and cannot compare one to a sequence of `addArc` calls.

### The smoke test is app-hosted, not XCUITest

An XCUITest twin of `LaunchSmokeTest` would need a `bundle.ui-testing` target that does not exist: a
new target, two scheme entries, a `tests/ci-workflow.test.js` assertion, and a slower job. It would
also not help, because the launch screen is drawn by a system process before app code runs, so no
automated test can see it. Its proof is the plist and asset assertions plus hand verification.

## 4. TDD cycles, and what each found

Each cycle ran red → green → refactor, with the red verified before any implementation. Where a test
pinned already-correct behaviour it was **mutation-checked** instead.

| # | Cycle | Commit | Outcome |
| :-- | :--- | :--- | :--- |
| 0 | Baseline on the branch | — | All four suites green before anything was touched |
| 1 | The catalog and the Hearth canvas | `23caf99` | **Found the `AppIcon` trap** (below) |
| 2 | The launch tile imageset | `e511517` | actool took the SVGs intact, `<g transform>` and strokes included |
| 3–4 | `UILaunchScreen` + `NSLocalNetworkUsageDescription` | `4943723` | Landed as one commit; adjacent lines of one YAML block |
| 5 | The app icon | `5041d47` | **Found a stale-green hole in the guards** (below) |
| 6 | The launch smoke test | `b54dc33` | No natural red; mutation-proved |
| 7 | The first-run keyboard | `165ea7f` | **Half the planned fix was unnecessary** (below) |
| 8 | Docs and this plan | — | |
| 9 | The hand-run | — | See §6 |

### An `AppIcon` set is mandatory the moment any catalog exists

Not once real artwork is ready — the moment a `.xcassets` folder exists at all. XcodeGen's
iOS-application preset injects `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` unconditionally, and
actool hard-fails:

```
error: None of the input catalogs contained a matching stickers icon set,
app icon set, or icon stack named "AppIcon".
** BUILD FAILED **
```

Verified by removing the stub and rebuilding. This broke the planned cycle order — cycles 1–4 would
all have been unbuildable — so an unfilled `AppIcon.appiconset` slot shipped with cycle 1, and cycle
5 filled it rather than creating it. Only building reveals this.

### The guards could report a stale green

`BrandAssetParityTest` reads files off disk rather than through the classpath, which Gradle cannot
infer. A mutated `hearth_app_icon.svg` left `:shared:jvmTest` up to date and the task skipped in
557 ms, reporting green. **The pre-commit hook runs these tests by name**, so a drifted asset could
have been committed on a cached pass — the one failure a drift guard must not have. The files are now
declared task inputs in `shared/build.gradle.kts`.

### `ImeAction.Next` was never broken

The plan asserted the name field's `ImeAction.Next` had no focus target and needed a `keyboardActions`
parameter on `HearthTextField`. It did not: Compose Foundation's `KeyboardActionRunner` falls through
to `defaultKeyboardActionWithResult`, which for `ImeAction.Next` is
`focusManager.moveFocus(FocusDirection.Next)` with no app code required. The test for it passed before
any change, so it was deleted rather than kept as a test that proves nothing, and the parameter was
dropped rather than added as speculative API. Only tap-outside-to-dismiss shipped.

## 5. Files touched

**Created** — `iosApp/HouseholdHub/Assets.xcassets/` (`HearthCanvas.colorset`,
`HearthLaunchTile.imageset` + 2 SVGs, `AppIcon.appiconset` + the 1024 PNG) ·
`iosApp/HouseholdHubTests/{HearthColorAsset,HearthLaunchTile,LaunchScreenPlist,PrivacyDeclaration,AppIcon,LaunchSmoke}Tests.swift`
and `{HearthTraitCollection,PixelSampling}.swift` · `tools/hearth_app_icon.svg` ·
`tools/make_app_icon.sh` · `shared/src/jvmTest/.../architecture/BrandAssetParityTest.kt`

**Modified** — `iosApp/project.yml` · `shared/build.gradle.kts` (declared guard inputs) ·
`composeApp/.../screens/firstrun/FirstRunContent.kt` + its tests · the two Android drawables
(comments only) · `.githooks/pre-commit` · `client/README.md` · `docs/STAGE_5_PLAN.md`

**Untouched by design** — `.github/workflows/household-hub-client.yml` and `tests/ci-workflow.test.js`.
New XCTests and catalog files need no workflow change.

## 6. Verification

```bash
cd apps/household-hub/client
./gradlew jvmTest                    # 29 in :shared (incl. 14 BrandAssetParityTest), 104 in :composeApp
./gradlew iosSimulatorArm64Test      # the same 104, natively
./gradlew :androidApp:assembleDebug  # Android unbroken
(cd iosApp && xcodegen generate)
xcodebuild test -project iosApp/HouseholdHub.xcodeproj -scheme HouseholdHub \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'   # 22 XCTests
```

All green as of this commit.

### Hand-verified on the simulator

> **Uninstall first**: `xcrun simctl uninstall booted com.homelab.household`. iOS caches the
> launch-screen snapshot and the home-screen icon per install.

| Check | Result |
| :--- | :--- |
| Launch screen, light | Hearth canvas fills the screen, tile centred, no letterbox |
| Launch screen, dark | Near-black canvas, light-green tile, **near-black glyph** — the inversion is right |
| Hand-off to Compose | No colour jump; the canvas is continuous |
| Home-screen icon | Green, white glyph, iOS's own corner mask, labelled HyggeHub |
| Offline — no route | Title, "No route" chip, hub address, "Retrying in Ns" ticking, both buttons, safe areas respected |

### Not hand-verified, and why

The hub's backend was down throughout (Caddy answered 502; `hub.spicy-llama.duckdns.org` resolves to
`192.168.1.20`). **First run, "Who's here?", the PIN pad, the wrong-PIN lockout backoff, and the
signed-in-opens-on-Home path could not be exercised.** They are covered by the 104 Compose UI tests on
both runtimes, but slice 1's own done-criterion #4 asks for a hand-run, and for those five flows it
remains outstanding until the hub is up.

Note that 502 correctly shows the **no-route** wording, not "HTTP 502":
`AuthRepositoryImpl.ensureJsonSuccess` maps 502–504 to `ServerOfflineException` deliberately — a
proxy answering 502 means the hub behind it is down, which to the user is "can't reach your hub".

## 7. Risks, as they turned out

| Risk | Outcome |
| :--- | :--- |
| actool mis-renders the SVG's `<g transform>` or strokes | Did not happen; accepted intact, vector preserved |
| The launch-screen renderer ignores a vector imageset | Did not happen; verified by eye in both appearances |
| Simulator caches the launch snapshot and icon | Real; the uninstall step is documented in the README |
| `AppIcon` declared `ios-marketing` only | Guarded from Linux before a Mac is involved |
| The keyboard fix regresses Android | Did not; 104 green on both runtimes, Android still assembles |

## 8. Open questions

1. **iOS 18+ dark and tinted app-icon variants** — the twin of Android's `monochrome` layer, which
   already exists. Out of scope here.
2. **`TARGETED_DEVICE_FAMILY "1,2"`** ships an iPad build with no iPad orientations declared, while
   the design is phone portrait. Left alone by decision.
3. **Portrait lock** — neither platform locks it, though the design says phone portrait.

## 9. Follow-ups (explicitly not in this slice)

- **The five hand-run flows above**, once the hub is back up.
- **Signing and a real device** — a `DEVELOPMENT_TEAM`, an Apple developer account, and the one thing
  the simulator cannot answer: whether `openTailscale()` reaches the real app via `tailscale://`. The
  local-network prompt is declared but only appears on hardware.
- **An XCUITest target**, if slice 2 needs cross-screen journeys.
- **iOS has no edge-swipe back** — `ComposeUIViewController` is hosted bare in a SwiftUI `WindowGroup`.
  Only the PIN pad has a back affordance in slice 1 and it is a visible button, so nothing is
  unreachable; revisit when a deeper stack appears.
- **`HearthScaffoldTest` asserts against synthetic insets**, never real device ones. The notch and home
  indicator stay eyeball-verified until there is a UI-test target.
- **The 409 path holds an HTTP response open while it polls** — carried forward from slice 0,
  deferred to slice 3, which owns that code.
