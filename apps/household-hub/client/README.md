# Household Hub Client (Kotlin Multiplatform + Compose Multiplatform)

Shared Kotlin Multiplatform (KMP) client for **Household Hub**: domain logic, Ktor networking,
reactive ViewModels, dependency injection, and the Compose Multiplatform phone UI.

Targets today: **JVM** (tests) and **Android**. iOS targets are added when the Mac is picked up —
`commonMain` is kept free of JVM- and Android-only APIs, and a test enforces it.

---

## 🏗️ Architecture (`presentation -> domain <- data`)

```
:androidApp ──► :composeApp (UI) ──► :core:presentation ──► :core:domain ◄── :core:data
     │                                                                          ▲
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
  * Token storage: `FileTokenStorage` (JVM) and `KeystoreTokenStorage` (Android, AES-256-GCM key in
    the Android Keystore, file in `noBackupFilesDir`, unreadable data reads as signed out).
* **`:core:presentation` (state & ViewModels):** depends only on `:core:domain`. One package per
  screen (`presentation/launch/`), each holding that screen's `ViewModel`, its `UiState` and its
  `Event` — for example `LaunchViewModel` with `HubStatus`
  (`Checking` / `Ready` / `FirstRun` / `Unreachable` / `Failed`).
* **`:composeApp` (UI):** depends only on `:core:presentation` and `:core:domain`. Hearth theme
  (Copenhagen Day / Midnight Espresso), 30 Hearth icons, shared components, the screens and the
  Nav3 host. Never imports `data`, `di`, `sdk` or Ktor — enforced by a test.
* **`:shared` (DI coordinator):** Koin graph, `platformModule` (`expect`/`actual`: HTTP engine and
  token storage per platform), `HouseholdHubSdk` entry point, and the architecture tests.
* **`:androidApp`:** the Android application — `HouseholdHubApplication` (starts Koin with the
  Android context), `MainActivity` (`setContent { App() }`), and the on-device tests.

---

## 🧭 How a screen is built

`App()` is the theme and nothing else; `AppNavHost` owns the back stack and is the only place that
navigates. Space and size come off the theme like the palette does — `HearthTheme.spacing.lg`,
`HearthTheme.size.iconMd` — and a screen never writes a `dp` of its own; `DesignSystemTokenTest`
fails the build if it does. Every screen follows the same four files:

| File | What it is |
| :--- | :--- |
| `presentation/<screen>/<Screen>ViewModel.kt` | `StateFlow<<Screen>UiState>` for what is drawn, `Channel<<Screen>Event>` for what happens once (navigation, a toast). Never Compose. |
| `app/screens/<screen>/<Screen>Screen.kt` | **No dependencies in its signature** beyond navigation callbacks and a `Modifier`. It resolves its own ViewModel with `koinViewModel()`, collects the state with `collectAsStateWithLifecycle()`, collects the events with `ObserveEvents` and calls the content. |
| `app/screens/<screen>/<Screen>Content.kt` | Stateless: state in, lambdas out. Holds the `@Preview` functions. |
| `app/screens/<screen>/<Screen>UiStateProvider.kt` | A `PreviewParameterProvider` listing every state the screen can be in, so the previews and the screen test cover all of them from one list. |

Events go through a `Channel`, not state: a `Channel` is consumed once, so a recomposition can't
navigate twice, and `ObserveEvents` only collects at `STARTED`, so a backgrounded screen can't
navigate behind the user's back.

Every string the UI writes lives in
`composeApp/src/commonMain/composeResources/values/strings.xml`, read with `stringResource` (or
`pluralStringResource` for counts). A reusable component takes its text as a `String` parameter and
the caller resolves it; only strings a component writes itself become resources. Nothing that
reaches a screen from below is a sentence — `HubStatus.Failed` carries a `HubFailure` and the
screen picks the wording, so the copy is translatable and the data layer stays out of the
language business.

### How a screen is tested

**One test file per screen**, in `composeApp/src/commonTest` so Android and iOS reuse it as they
are added. `<Screen>Test` composes the screen inside `TestApp`, which starts the app's real Koin
graph and overrides only the two seams `platformModule` binds — the HTTP engine and where tokens
are kept. Everything between the screen and the network is the production wiring, so one test
covers the drawing, the ViewModel, the use case, the repository and the error mapping at once.

| Piece | Where | What it is |
| :--- | :--- | :--- |
| `TestApp`, `runScreenTest` | `app/testing/` | Shared, and screen-agnostic. `runScreenTest` installs a Main dispatcher and stops the global Koin context `KoinApplication` leaves behind — per composition, so one test can compose more than once. |
| `Fake<Screen>Hub` | beside the screen's tests | A `MockEngine` speaking only the endpoints that screen calls, with a swappable answer so a test can change the hub's mind halfway through. Named for its screen; when a second screen needs a hub, lift the shared parts out then. |
| `<Screen>Robot` | beside the screen's tests | Every string the screen shows, named once — as the **resource**, resolved with `getString`, never as a second copy of the text. Each assertion **waits** for its text: the hub answers on its own coroutine, so `waitForIdle` — which only waits for Compose — can run first. |

Navigation is tested on its own: `AppNavHostTest` passes `StubScreens` to `AppNavHost` and reads
the hoisted back stack, so it covers where the app goes with no Koin, no hub and no real screen.

Because both the screen and its robot read the same resource, editing `strings.xml` can't fail a
test — that is the point, and it means copy is not what these tests are about. What they do pin is
the wiring, so prove a change by mutating that instead: swap two resource keys, or render the
wrong one, and the tests should fail.

No MockK in `:composeApp` — only JVM artifacts exist, and one mock in `commonTest` would nail the
UI suite to the JVM. `InMemoryTokenStorage` and hand-written fakes do the job and compile for
iOS. MockK stays in the `:core:presentation` ViewModel tests.

---

## 🧰 Toolchain

| | |
| :--- | :--- |
| Kotlin / Compose compiler | 2.4.20 |
| Compose Multiplatform | 1.12.0 (Material3 `1.12.0-alpha03`, the pairing CMP 1.12.0 ships) |
| Navigation 3 | 1.1.1 |
| AGP | 9.4.0 (needs Gradle ≥ 9.6) |
| Gradle | 9.7.1 |
| Ktor | 3.5.2 (OkHttp on Android, CIO on the JVM) |
| Koin | 4.2.2 |
| Lifecycle | 2.11.0 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |

`compileSdk 37` is required by Material3 `1.5.0-alpha22`, which CMP 1.12.0 depends on.

---

## 🧪 Tests

```powershell
cd apps\household-hub\client

# Everything that runs on the JVM (domain, data, presentation, shared, Compose UI tests)
.\gradlew.bat jvmTest

# Architecture and DI guards on their own (also run by the pre-commit hook)
.\gradlew.bat :shared:jvmTest --tests "*CleanArchitectureBoundaryTest*" --tests "*KoinDependencyGraphTest*"

# On-device tests (needs a running emulator or a phone)
.\gradlew.bat :core:data:connectedAndroidTest      # Keystore token storage
.\gradlew.bat :androidApp:connectedDebugAndroidTest # launch smoke test + SSE streaming proof
```

The architecture tests enforce: domain has no outward dependencies; presentation never imports data;
data never imports presentation; `commonMain` has no `java.`/`javax.`/`android.`/engine imports; the
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

---

## 🌐 Network ingress

* **Base URL:** `https://hub.spicy-llama.duckdns.org` (one value, `DEFAULT_BASE_URL`, injected as `HubConfig`)
* **API prefix:** `/api/v1`
* **Local ingress:** on home Wi-Fi, local DNS resolves the domain to `192.168.1.20:3050`. Away from
  home, Tailscale MagicDNS and DuckDNS provide encrypted remote access.
* The Android engine runs with **no read or call timeout**: a long answer pauses between tokens, and
  OkHttp's 10-second default would cut the stream. An on-device test holds that line.
