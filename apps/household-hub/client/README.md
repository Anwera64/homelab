# Household Hub Client (Kotlin Multiplatform + Compose Multiplatform)

Shared Kotlin Multiplatform (KMP) client for **Household Hub**: domain logic, Ktor networking,
reactive ViewModels, dependency injection, and the Compose Multiplatform phone UI.

Targets: **JVM** (tests), **Android**, and **iOS** (`iosArm64`, `iosSimulatorArm64`). `commonMain`
stays free of platform-only APIs, and a test enforces it.

---

## 🏗️ Architecture (`presentation -> domain <- data`)

```
:androidApp ─┐
             ├─► :composeApp (UI) ──► :core:presentation ──► :core:domain ◄── :core:data
:iosApp ─────┘                                                                  ▲
     │                                                                          │
     └────────► :shared (DI coordinator) ───────────────────────────────────────┘
```

* **`:core:domain` (pure Kotlin core):** entities, repository protocols, use cases. Zero dependencies
  on Android, JVM, Ktor, serialization or UI frameworks.
* **`:core:data` (network & persistence):** depends only on `:core:domain`.
  * Ktor client with `ContentNegotiation`, `Logging` and preemptive Bearer `Auth`.
  * `DefensiveSseStreamReader` — token-by-token SSE with prompt-delimiter sanitisation.
  * `ServerHealthMonitor`, `NetworkExceptionHelper` (`rethrowAsDomain` maps network failures to
    `ServerOfflineException`), repositories and mappers.
  * `HubConfig` — the one hub address, injected; no repository defaults it any more.
  * Token storage, one per platform, all with the same semantics — a `null` refresh token leaves the
    stored one alone, anything unreadable reads as signed out, and nothing throws out of the four
    methods: `FileTokenStorage` (JVM), `KeystoreTokenStorage` (Android, AES-256-GCM key in the
    Android Keystore, file in `noBackupFilesDir`) and `KeychainTokenStorage` (iOS, a generic-password
    item marked `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` so it never syncs to iCloud or
    restores onto another device — the Apple equivalent of `noBackupFilesDir`. It keeps no cache;
    only the read-modify-write in `saveTokens` needs the `NSLock`).
* **`:core:presentation` (state & ViewModels):** depends only on `:core:domain`. One package per
  screen (`presentation/launch/`, `firstrun/`, `profilepicker/`, `pinentry/`), each holding that
  screen's `ViewModel`, its `UiState` and its `Event` — for example `LaunchViewModel` with
  `HubStatus` (`Checking` / `Unavailable`).
* **`:composeApp` (UI):** depends only on `:core:presentation` and `:core:domain`. Hearth theme
  (Copenhagen Day / Midnight Espresso), 31 Hearth icons (the sheet's 30 and the PIN pad's delete),
  shared components, the screens and the Nav3 host. `ExternalApps` is the seam for handing the
  user to another app (Tailscale, from the offline screen). Never imports `data`, `di`, `sdk` or
  Ktor — enforced by a test.
* **`:shared` (DI coordinator):** Koin graph, `platformModule` (`expect`/`actual`: HTTP engine and
  token storage per platform), `HouseholdHubSdk` entry point, and the architecture tests.
* **`:androidApp`:** the Android application — `HouseholdHubApplication` (starts Koin with the
  Android context), `MainActivity` (`installSplashScreen()`, then
  `setContent { App(AndroidExternalApps(this)) }`), the system splash (`Theme.HyggeHub.Starting`:
  the launch tile, `drawable/splash_tile`, on the canvas, day and night), and the on-device tests.
* **`:iosApp`:** the iOS application, mirroring `:androidApp` — the only iOS-side module allowed to
  depend on both `:composeApp` and `:shared`, so `MainViewController` (`ComposeUIViewController { App() }`)
  can start Koin without `:composeApp` ever importing `sdk` or `di`. It produces the
  `HouseholdHubKit` framework, and holds the Swift app and the Keychain XCTest.

**Use cases are protocols.** `usecase/<Name>UseCase.kt` is the interface, `usecase/impl/<Name>UseCaseImpl.kt`
the implementation, and `DomainModule` is the only place an implementation is ever named —
`use_case_implementations_are_named_only_by_the_di_module` enforces that. It is what makes a
ViewModel's collaborators mockable at all: a final class cannot be mocked by any library that builds
its mocks at compile time, which every Native-capable one does. 8 of the 36 are plain `interface`
rather than `fun interface`, because Kotlin forbids a default parameter value on a functional
interface's abstract method.

---

## 🧭 How a screen is built

`App()` is the theme and the platform's `ExternalApps`, and nothing else; `AppNavHost` owns the back
stack and is the only place that navigates. It wraps every destination in `WithEntryViewModels`, so
a screen's ViewModels go when it leaves the stack and a screen opened again starts fresh. Space
and size come off the theme like the palette does — `HearthTheme.spacing.lg`,
`HearthTheme.size.iconMd` — and a screen never writes a `dp` of its own; `DesignSystemTokenTest`
fails the build if it does. Every screen follows the same four files:

| File | What it is |
| :--- | :--- |
| `presentation/<screen>/<Screen>ViewModel.kt` | `StateFlow<<Screen>UiState>` for what is drawn, `Channel<<Screen>Event>` for what happens once (navigation, a toast). Never Compose. |
| `app/screens/<screen>/<Screen>Screen.kt` | **No dependencies in its signature** beyond navigation callbacks and a `Modifier`. It resolves its own ViewModel with `koinViewModel()`, collects the state with `collectAsStateWithLifecycle()`, collects the events with `ObserveEvents` and calls the content. |
| `app/screens/<screen>/<Screen>Content.kt` | Stateless: state in, lambdas out. Holds the `@Preview` functions. |
| `app/screens/<screen>/<Screen>UiStateProvider.kt` | A `PreviewParameterProvider` listing every state the screen can be in, so the previews and the screen test cover all of them from one list. |

Shared components in `app/components/` keep their previews beside them, in `<Component>Preview.kt`,
drawn inside `ComponentPreview` so they sit on the canvas; the icon set's grid is
`icons/HearthIconPreview.kt`. A new component gets one.

Events go through a `Channel`, not state: a `Channel` is consumed once, so a recomposition can't
navigate twice, and `ObserveEvents` only collects at `STARTED`, so a backgrounded screen can't
navigate behind the user's back.

Every string the UI writes lives in
`composeApp/src/commonMain/composeResources/values/strings.xml`, read with `stringResource` (or
`pluralStringResource` for counts). A reusable component takes its text as a `String` parameter and
the caller resolves it; only strings a component writes itself become resources. Nothing that
reaches a screen from below is a sentence — `HubStatus.Unavailable` carries a `HubFailure` and the
screen picks the wording, so the copy is translatable and the data layer stays out of the
language business.

### How a screen is tested

**One test file per screen**, in `composeApp/src/commonTest` so Android and iOS reuse it as they
are added. `<Screen>Test` composes the screen inside `TestApp`, which starts the app's real Koin
graph and overrides only the two seams `platformModule` binds — the HTTP engine and where tokens
are kept — plus a counting `FakeExternalApps`. Everything between the screen and the network is
the production wiring, so one test
covers the drawing, the ViewModel, the use case, the repository and the error mapping at once.

| Piece | Where | What it is |
| :--- | :--- | :--- |
| `TestApp`, `runScreenTest` | `app/testing/` | Shared, and screen-agnostic. `runScreenTest` installs a Main dispatcher and stops the global Koin context `KoinApplication` leaves behind — per composition, so one test can compose more than once. |
| `Fake<Screen>Hub` | beside the screen's tests | A `MockEngine` speaking only the endpoints that screen calls, with a swappable answer so a test can change the hub's mind halfway through. Named for its screen; a hub two screens share moves to `app/testing/` — `FakeSignInHub` serves both "Who's here?" and the PIN pad. |
| `<Screen>Robot` | beside the screen's tests | Every string the screen shows, named once — as the **resource**, resolved with `getString`, never as a second copy of the text. Each assertion **waits** for its text: the hub answers on its own coroutine, so `waitForIdle` — which only waits for Compose — can run first. |

Navigation is tested on its own: `AppNavHostTest` passes `StubScreens` to `AppNavHost` and reads
the hoisted back stack, so it covers where the app goes with no Koin, no hub and no real screen.
Where it *opens* is `AppStartTest`: the host's own back stack reads the stored session through
Koin, so that one runs the stubs inside `TestApp` — home with a token kept on the phone, launch
without, and the hub never asked.

Because both the screen and its robot read the same resource, editing `strings.xml` can't fail a
test — that is the point, and it means copy is not what these tests are about. What they do pin is
the wiring, so prove a change by mutating that instead: swap two resource keys, or render the
wrong one, and the tests should fail.

These tests run on **both** the JVM and the iOS simulator — the same 64, name for name — which is
why `commonTest` holds no JVM-only library. The core modules mock with **Mokkery**, a compiler
plugin rather than a bytecode rewriter, which is why it works on Native; MockK is gone from the
client entirely. `:composeApp` still uses no mocking library at all, but the reason has changed:
not that a mock would nail the suite to the JVM, but that `MockEngine` plus hand-written fakes is
simply the better test for a screen wired to the real graph.

---

## 🧰 Toolchain

| | |
| :--- | :--- |
| Kotlin / Compose compiler | 2.4.20 |
| Compose Multiplatform | 1.12.0 (Material3 `1.12.0-alpha03`, the pairing CMP 1.12.0 ships) |
| Navigation 3 | 1.1.1 |
| AGP | 9.4.0 (needs Gradle ≥ 9.6) |
| Gradle | 9.7.1 |
| Ktor | 3.5.2 (OkHttp on Android, CIO on the JVM, Darwin on iOS) |
| Koin | 4.2.2 |
| Lifecycle | 2.11.0 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| iOS deployment target | 15.0 (the simulator SDK's own `RecommendedDeploymentTarget`) |
| Xcode / XcodeGen | 26.6 / 2.46.0 |
| Mocking | Mokkery 3.5.0 (compiler plugin; works on Native) |

`compileSdk 37` is required by Material3 `1.5.0-alpha22`, which CMP 1.12.0 depends on.

---

## 🧪 Tests

```powershell
cd apps\household-hub\client

# Everything that runs on the JVM (domain, data, presentation, shared, Compose UI tests)
.\gradlew.bat jvmTest

# Architecture and DI guards on their own (also run by the pre-commit hook)
.\gradlew.bat :shared:jvmTest --tests "*CleanArchitectureBoundaryTest*" --tests "*KoinDependencyGraphTest*"

# The same JVM tests with Ktor's dispatcher switch forced on, reproducing Native's semantics in
# seconds. Emitting inside `statement.execute { }` silently dropped every SSE event on iOS; Ktor 4
# makes that switch the default everywhere, so this is how Android stays fixed too.
.\gradlew.bat :core:data:jvmEngineDispatcherTest

# On-device tests (needs a running emulator or a phone)
.\gradlew.bat :core:data:connectedAndroidTest      # Keystore token storage
.\gradlew.bat :androidApp:connectedDebugAndroidTest # launch smoke test + SSE streaming proof
```

```bash
# iOS simulator (macOS only; needs python3 for the SSE fixture server in tools/sse_fixture.py,
# which Gradle starts and stops around these tasks on the fixed port 8749)
cd apps/household-hub/client

./gradlew iosSimulatorArm64Test           # everything on the simulator: core, shared, and the
                                          # 64 Compose UI tests, which run here as well as on the JVM
./gradlew :shared:iosSimulatorArm64Test   # ~10s, includes the Darwin SSE lock-step proof

# SLOW, >75s: the only thing guarding timeoutIntervalForRequest in PlatformModule.ios.kt.
# Opt-in — `check` and `build` skip it, it runs only when named.
./gradlew :shared:iosSimulatorArm64SlowSseLongPauseTest
```

The Keychain is the one thing Gradle cannot test. A bare Kotlin/Native test binary is spawned
outside the simulator's daemon environment, so every `SecItem*` call returns `errSecNotAvailable`
(-25291). `KeychainTokenStorage` is covered instead by an XCTest bundle **hosted by the app**,
which does have the entitlements — run it from Xcode, or:

```bash
cd apps/household-hub/client/iosApp
xcodegen generate
xcodebuild test -project HouseholdHub.xcodeproj -scheme HouseholdHub \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

The two iOS SSE proofs drive the real `Flow<ChatStreamEvent>` the app collects — the Koin Darwin
engine, the real `SessionRepositoryImpl.streamChatTurn`, collected off the engine's dispatcher.
`DarwinSseLockstepStreamingTest` proves incremental delivery causally: the fixture refuses to write
delta N+1 until the client has acked delta N over a second connection, so a buffering engine
deadlocks rather than passing by luck. `DarwinSseLongPauseTest` outlasts NSURLSession's 60-second
default `timeoutIntervalForRequest`, which is why it cannot be made cheap.

The architecture tests enforce: domain has no outward dependencies; presentation never imports data;
data never imports presentation; `commonMain` has no `java.`/`javax.`/`android.`/`platform.`/engine
imports (though for `platform.` the compiler rejects it first — the rule is there to state intent); no
module outside `:core:domain` names a use-case implementation; the
UI module imports only presentation and domain; and every module's **production** Gradle
dependencies point inward. Test source sets are exempt from that last one — the full-stack UI
tests in `:composeApp` wire the real graph from `:shared`, while `composeApp/commonMain` still
can't reach past `:core:presentation`.

`DesignSystemTokenTest` sits beside them and guards the design system instead of the layers: no raw
`dp` in `composeApp/commonMain` outside `app/theme`, which is the only place space and size are
decided, and no reading `DefaultSpacing` / `DefaultSizes` around the theme — `HearthIcon`, whose
vectors are built outside composition, is the one listed exception. Test sources are exempt — an
assertion is free to name a real number.

## ▶️ Running the app

```powershell
.\gradlew.bat :androidApp:installDebug
adb shell am start -n com.homelab.household/.MainActivity
```

On iOS, generate the Xcode project first — `project.yml` is the source of truth, and both the
`.xcodeproj` and the `Info.plist` it writes are generated and git-ignored:

```bash
cd apps/household-hub/client/iosApp
xcodegen generate
open HouseholdHub.xcodeproj   # then run on an iPhone simulator
```

Three things that wiring needs, none of which produce a useful error if missing:

* **`CADisableMinimumFrameDurationOnPhone`** in the plist — Compose Multiplatform 1.12 throws from
  `PlistSanityCheck` before drawing its first frame without it.
* **The plist is written explicitly** by `project.yml`, because Xcode silently drops
  `INFOPLIST_KEY_*` settings it does not recognise, and that key is one of them.
* **`ENABLE_USER_SCRIPT_SANDBOXING: NO`** — on by default since Xcode 15, and it denies Gradle every
  write outside DerivedData, so the framework build phase fails.

---

## 🌐 Network ingress

* **Base URL:** `https://hub.spicy-llama.duckdns.org`, set once as `hub.baseUrl` in `gradle.properties`.
  `:core:data` generates it into `BuildConfig`, exposed as `DEFAULT_BASE_URL` and injected as `HubConfig`.
  Override it per build with `-Phub.baseUrl=...`.
* **API prefix:** `/api/v1`
* **Local ingress:** on home Wi-Fi, local DNS resolves the domain to `192.168.1.20:3050`. Away from
  home, Tailscale MagicDNS and DuckDNS provide encrypted remote access.
* The Android engine runs with **no read or call timeout**: a long answer pauses between tokens, and
  OkHttp's 10-second default would cut the stream. An on-device test holds that line.
* The iOS engine needs the same fix in Apple's terms: NSURLSession's default
  `timeoutIntervalForRequest` is 60 seconds and measures the gap *between bytes*, so it would cut a
  pausing answer just the same. `PlatformModule.ios.kt` sets it to 3600 s, and
  `timeoutIntervalForResource` to 86400 s. Unlike OkHttp, `0` does **not** mean "no timeout" there —
  hence large finite values. `DarwinSseLongPauseTest` holds that line.
