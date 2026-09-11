# Implementation Plan — Stage 5 · Slice 0: Platforms and foundation

> On approval, this plan is saved as `implementation_plan.md` (see Open question 2), and every change below follows it.

## 1. Problem

Stage 5 ships the Household Hub phone app. Slice 0 (`apps/household-hub/docs/STAGE_5_PLAN.md` §3) is the foundation every later slice builds on. Today:

- Every client module builds for the **JVM only**. There is no Android target, UI module or app module.
- `DataModule.kt` (commonMain) hard-codes `HttpClient(CIO)` and `FileTokenStorage()`. `FileTokenStorage` is an `expect` class with only a JVM `actual`.
- The hub address is a literal in `DataModule.kt` **and** a default parameter in 7 constructors: 6 repositories plus `ServerHealthMonitor`.
- `AuthRepositoryImpl.checkStatus()` doesn't turn network failures into `ServerOfflineException` the way `SessionRepositoryImpl` does. The raw Ktor `IOException` reaches the ViewModel, so the launch screen can't tell "hub unreachable" from any other error.
- `.githooks/pre-commit` (the active `core.hooksPath`) runs only the **backend** architecture tests. The client boundary tests never run on commit, which AGENTS.md §3 requires.

**Done when:**
- The app opens on an Android emulator or phone, calls `GET /auth/status` and shows the result: ready / first run / unreachable.
- It follows the system theme.
- SSE is proven token-by-token on Android, including through a long pause.
- Every JVM suite and boundary check passes.

## 2. Architecture

```
:androidApp ──► :composeApp (UI) ──► :core:presentation ──► :core:domain ◄── :core:data
     │                                                                          ▲
     └────────► :shared (DI coordinator = "bootstrap") ─────────────────────────┘
```

- **`:composeApp`** is the UI part of the presentation layer. It depends **only** on `:core:presentation` and `:core:domain`, plus Compose, Nav3 and `koin-compose-viewmodel`, which it uses to obtain ViewModels. It never imports `data`, `di`, `sdk` or `io.ktor`.
- **`:androidApp`** is the app root. It is the only module that calls `HouseholdHubSdk.init`, and it passes `androidContext`.
- **`:shared`** stays the sole place that wires modules, per AGENTS §3.4. New wiring goes here:
  - `expect val platformModule` binds the `HttpClientEngine` and `TokenStorage` per platform.
  - `HubConfig` is provided through `HouseholdHubSdk.init(hubConfig = …)`.
- **`:core:data`** holds the implementations: `FileTokenStorage` (jvmMain), `KeystoreTokenStorage` (androidMain) and `HubConfig`.
- All new rules get automated boundary tests, which run on every `jvmTest` and in the pre-commit hook.

## 3. Versions (checked today on Maven Central / Google Maven)

| Item | Now → Slice 0 | Note |
| :-- | :-- | :-- |
| Compose Multiplatform | — → **1.12.0** | latest stable |
| Kotlin + `plugin.compose` | 2.2.21 → **2.4.20** | latest stable |
| AGP | 9.1.0 → **9.4.0** | latest stable; needs Gradle ≥ 9.6 |
| Gradle wrapper | 9.3.1 → **9.7.1** | latest stable |
| Ktor (+ okhttp) | 3.4.1 → **3.5.2** | |
| Koin (+ android, compose-viewmodel) | 4.0.2 → **4.2.2** | |
| lifecycle-viewmodel (androidx / JetBrains compose) | 2.8.6 → **2.11.0** | the pairing CMP 1.12 declares |
| `org.jetbrains.compose.material3` | — → **1.12.0-alpha03** | ⚠ the stable CMP 1.12.0 pins this pre-release (Open question 1) |
| `org.jetbrains.androidx.navigation3:navigation3-ui` | — → **1.1.1** | stable. Nav2 paired with CMP 1.12 is `2.10.0-alpha02` |
| `androidx.activity:activity-compose` | — → **1.13.0** | |
| compileSdk / targetSdk / minSdk | — → 36 / 36 / **26** | 26 needed for Keystore AES-GCM and variable fonts |

## 4. TDD cycles

Every cycle ends with `.\gradlew.bat jvmTest` fully green before the next one starts. Device tests run with `connectedAndroidTest` on the emulator.

### Cycle 0: Toolchain upgrade (refactor only)
- **Guard:** the current suite. Record a green `jvmTest` run **before** any change.
- **Change:**
  - `gradle/libs.versions.toml`: bump versions and add the new libraries. Replace the unused `android-library` alias with `android-kotlin-multiplatform-library`, and add the aliases `android-application`, `compose-multiplatform` and `kotlin-compose`.
  - Wrapper: `gradlew wrapper --gradle-version 9.7.1`.
  - Root `build.gradle.kts`: declare the new plugins `apply false`.
  - Create `local.properties` with `sdk.dir` (gitignored).
- **Refactor check:** `jvmTest` is green again with identical test counts.

### Cycle 1: Android target on the four client modules
- **Red:** `gradlew :core:domain:compileAndroidMain :core:data:compileAndroidMain :core:presentation:compileAndroidMain :shared:compileAndroidMain` fails, because the tasks don't exist.
- **Green:** apply `com.android.kotlin.multiplatform.library` to all four modules, each with `kotlin { android { namespace = "com.homelab.household.<module>"; compileSdk = 36; minSdk = 26 } }`. The `jvm {}` target stays, and host tests stay off because commonTest uses JVM-only JUnit5/MockK/AssertJ.
- **Refactor:** move compileSdk/minSdk into catalog `[versions]` so they're declared once.

### Cycle 2: commonMain free of platform APIs; engine and token-storage seam
- **Red:**
  - `CleanArchitectureBoundaryTest.common_main_has_no_platform_imports`: scans every module's `src/commonMain` and forbids `import java.`, `javax.`, `android.` and `io.ktor.client.engine.cio|okhttp|darwin`. It fails on `DataModule.kt:23` (`io.ktor.client.engine.cio.CIO`).
  - New `shared/src/jvmTest/.../di/PlatformModuleTest`: after `HouseholdHubSdk.init()`, `get<HttpClientEngine>()` is a CIO engine and `get<TokenStorage>()` is a `FileTokenStorage`. It fails because there's no binding.
- **Green:**
  - `shared/src/commonMain/.../di/PlatformModule.kt`: `expect val platformModule: Module`.
  - `shared/src/jvmMain/.../di/PlatformModule.jvm.kt`: `single<HttpClientEngine> { CIO.create() }` and `single<TokenStorage> { FileTokenStorage() }`.
  - `shared/src/androidMain/.../di/PlatformModule.android.kt`: `single<HttpClientEngine> { OkHttp.create() }`. The timeouts are left out on purpose; Cycle 12 adds them test-first. `TokenStorage` gets `InMemoryTokenStorage()` for now; Cycle 5 replaces it.
  - `SharedModule.kt`: `appModules` gets `platformModule`.
  - `DataModule.kt`: `HttpClient(get<HttpClientEngine>()) { …unchanged plugins… }`. Remove `single<TokenStorage> { FileTokenStorage() }` and the CIO import.
  - Delete `core/data/src/commonMain/.../local/FileTokenStorage.kt` (the `expect`). `FileTokenStorage.jvm.kt` becomes a plain `class FileTokenStorage(storageDir: String? = null)`, and `FileTokenStorageTest` moves from commonTest to jvmTest.
  - `core/data/build.gradle.kts`: `ktor-client-cio` moves to `jvmMain`, `ktor-client-okhttp` goes into `androidMain`.
  - `.githooks/pre-commit`: new step 4. If staged files touch `apps/household-hub/client/`, run `gradlew.bat -p apps/household-hub/client :shared:jvmTest --tests "*CleanArchitectureBoundaryTest*" --tests "*KoinDependencyGraphTest*"` and abort the commit on failure.
- **Refactor:** `KoinDependencyGraphTest` also asserts the `HttpClientEngine` and `TokenStorage` bindings.

### Cycle 3: The hub address is one value
- **Red:** new `shared/src/jvmTest/.../di/HubAddressWiringTest`:
  - Start Koin with `sdkModules(HubConfig("https://hub.test"))`, plus an override `single<HttpClientEngine> { mockEngine }` that records every request host.
  - Call one method on each of `AuthRepository`, `SessionRepository`, `AgentRepository`, `SpaceRepository`, `MemoryRepository`, `GossipRepository` and `ServerHealthMonitor`, swallowing any errors from the mock responses.
  - Assert that every recorded host is `hub.test`. It fails to compile because `HubConfig` and `sdkModules` don't exist yet.
- **Green:**
  - New `core/data/src/commonMain/.../remote/HubConfig.kt`: `data class HubConfig(val baseUrl: String)`.
  - `DataModule.kt`: every `DEFAULT_BASE_URL` argument becomes `get<HubConfig>().baseUrl`. `DEFAULT_BASE_URL` stays as the single literal.
  - `HouseholdHubSdk.kt`: `fun sdkModules(hubConfig: HubConfig) = listOf(module { single { hubConfig } }) + appModules`, and `init(hubConfig: HubConfig = HubConfig(DEFAULT_BASE_URL), appDeclaration)` loads them.
  - Remove the `baseUrl: String = "https://hub.spicy-llama.duckdns.org"` default from `AuthRepositoryImpl`, `SessionRepositoryImpl`, `AgentRepositoryImpl`, `SpaceRepositoryImpl`, `MemoryRepositoryImpl`, `GossipRepositoryImpl` and `ServerHealthMonitor`.
- **Refactor:** tests that hard-code the URL literal switch to `DEFAULT_BASE_URL`: `SessionRepositoryTest`, `AuthRepositoryTest` and `ServerHealthMonitorTest`.

### Cycle 4: An unreachable hub reaches the launch screen as a typed state
- **Red (data):** `AuthRepositoryTest.check_status_when_hub_unreachable_throws_server_offline`. The MockEngine throws `IOException("Connection refused")` and the test expects `ServerOfflineException`, using the existing `assertThrowsSuspend`. It fails because the raw exception gets through.
- **Green (data):** `AuthRepositoryImpl.checkStatus()` wraps the call and rethrows via `NetworkExceptionHelper`.
- **Refactor (data):** move `SessionRepositoryImpl.handleOfflineOrThrow` (`SessionRepositoryImpl.kt:257`) into `NetworkExceptionHelper.rethrowAsDomain(e): Nothing`, used by both repositories.
- **Red (presentation):** `AuthViewModelTest`:
  - `hub_status_is_checking_before_first_result`
  - `…ready_with_member_count` (initialised, 2 members)
  - `…first_run` (not initialised)
  - `…unreachable` (`ServerOfflineException`)
  - `…failed_keeps_message` (any other exception)

  All fail because there's no `hubStatus` yet.
- **Green (presentation):** `AuthViewModel.kt` gets `sealed interface HubStatus { Checking; Ready(memberCount); FirstRun; Unreachable; Failed(message) }` and `AuthUiState.hubStatus`, set in `checkStatus()`. The existing fields stay unchanged for the current tests.
- **Refactor:** none expected.

### Cycle 5: `KeystoreTokenStorage` on Android
- **Red:** `core/data/src/androidDeviceTest/.../local/KeystoreTokenStorageTest`. Device tests are enabled on this module with `withDeviceTest {}`. The cases:
  - Save, then read from a new instance and get the same tokens.
  - `clear()`, then a new instance reads nulls.
  - The file on disk doesn't contain the plaintext token.
  - The file lives under `noBackupFilesDir`.
  - A corrupted file reads as signed out (nulls) and gets deleted.

  It fails because the class doesn't exist.
- **Green:** `core/data/src/androidMain/.../local/KeystoreTokenStorage.kt`:
  - An AES-256-GCM key in `AndroidKeyStore` (alias `household_hub_tokens`).
  - It stores `IV ‖ ciphertext` of the existing JSON payload in `noBackupFilesDir/auth_tokens.bin` and loads lazily.
  - `PlatformModule.android.kt` binds `KeystoreTokenStorage(androidContext())`. `koin-android` goes into `:shared` androidMain.
- **Refactor:** share the `TokenDiskPayload` serialisation between the JVM and Android implementations. It moves to `core/data/src/commonMain/.../local/TokenDiskPayload.kt`.

### Cycle 6: `:composeApp` exists and respects the layer rule
- **Red:** two new tests in `CleanArchitectureBoundaryTest`:
  - `ui_layer_imports_only_presentation_and_domain`: `composeApp/src/commonMain` must exist, and it forbids imports of `com.homelab.household.data`, `.di`, `.sdk` and `io.ktor`.
  - `module_dependencies_point_inward`: it scans each `build.gradle.kts` for `project(":…")`. `domain` has none, `data` and `presentation` only `:core:domain`, and `composeApp` only `:core:presentation` and `:core:domain`.

  It fails because the module is missing.
- **Green:**
  - `settings.gradle.kts` includes `:composeApp`.
  - `composeApp/build.gradle.kts` is a KMP library with an `android` target and a `jvm` target (the JVM one is for UI tests), plus the Compose plugins, material3, Nav3, `koin-compose-viewmodel`, and `compose.components.resources`. Its jvmTest gets `ui-test` and `compose.desktop.currentOs`.
  - A placeholder `App.kt`.
- **Refactor:** none.

### Cycle 7: Theme tokens
- **Red:** `composeApp/src/jvmTest/.../theme/HearthThemeTest`:
  - It asserts every Day and Night value against the palette proof, §04/§05. That includes primary `#3C6E4E` / `#7FB894`, secret `#6B655F` / `#B8B2AC`, error, success, outlineSoft, night `surface2` / `surface3`, and ghost tint/edge/frame of 20/69/66 and 14/55/45.
  - A compose test checks that `HearthTheme(darkTheme = true)` exposes the night set through `LocalHearthColors` and `MaterialTheme.colorScheme.primary`.
- **Green:**
  - `theme/HearthColors.kt` defines `DayColors` and `NightColors`.
  - `theme/HearthTheme.kt` maps them onto the Material3 `ColorScheme` and exposes `LocalHearthColors`. By default it follows `isSystemInDarkTheme()`.
- **Refactor:** none expected.

### Cycle 8: Typography and shapes
- **Red:** `HearthTypographyTest`:
  - Display/headline roles use Outfit 500–700, body/label roles use Inter 400–600, and `HearthTheme.typography.mono` uses JetBrains Mono 500.
  - `HearthShapes.bento` is 24 dp.
- **Green:** static TTFs for those 7 weights, with their OFL licences, in `composeApp/src/commonMain/composeResources/font/`, plus `theme/HearthTypography.kt` and `theme/HearthShapes.kt`.
- **Refactor:** none expected.

### Cycle 9: The 27 Hearth icons
- **Red:** `HearthIconsTest`:
  - `HearthIcons.all` has exactly the 27 token names from the icon sheet, including `attach` and `biometricUnlock`, with no `voice`.
  - Each icon is 24×24.
  - Stroke is 1.5 with round caps and joins, and 1.85 when `active = true`.
  - Every path is non-empty, and `.solid` shapes are filled.
- **Green:** `icons/HearthIcons.kt` builds each icon from the sheet's SVG path data with `addPathNodes`. Circles and rects are converted to path commands. Each variant is cached.
- **Refactor:** move the path data into a table, so the icon builder is a single function.

### Cycle 10: Shared components (one Red→Green→Refactor per component, each with its own jvmTest)

| Component | Red assertions |
| :-- | :-- |
| `PrimaryButton` / `SecondaryButton` / `DestructiveButton` | There's no `enabled` parameter, so a click always reaches `onClick` and the node never has `Disabled` semantics. Destructive is outlined in error red |
| `HearthTextField` | Given `error`, the error text node sits **below** the field and carries the `Error` semantic. The typed value is unchanged after the error appears |
| `BentoCard` | Renders its content. Uses the `bento` shape |
| `HearthChip` | Label shown. The `secret` variant uses the ghost tint and edge of the active palette |
| `EmptyState` | Icon, title and line are shown. The action button appears only when `action` is given |
| `ToolRecordLine` | Shows its text and icon. It's clickable only when `onClick` is provided |
| `MessageComposer` | Stateless (`value`, `onValueChange`, `onSend`). Send is always clickable and passes the current text. The component never clears the text itself |
| `HearthBottomNav` | 4 tabs plus the centre +. The selected tab has `Selected` semantics and the + fires `onNewChat`. Every target is at least 44 dp |
| `HearthScaffold` | After scrolling the content, the header and bottom bar are still displayed |

Sizes and spacing come from the design canvas (artifact `c8d66772…`).

### Cycle 11: Launch screen and navigation
- **Red:** `LaunchScreenTest` renders the stateless `LaunchScreen(status: HubStatus, onRetry)` for each of the five states: Checking shows the "Reaching the hub" copy; Ready shows the member count; FirstRun; Unreachable shows Retry and Retry fires; Failed shows its message.

  `AppNavigationTest` builds an `AuthViewModel` over a fake `AuthRepository` (a domain interface, so no data import) and checks that the app starts on Launch and shows the status once it resolves.
- **Green:**
  - `screens/launch/LaunchScreen.kt` plus `LaunchRoute`, which obtains the ViewModel with `koinViewModel()` and calls `checkStatus()` once.
  - `navigation/AppNavigation.kt` uses a Nav3 `NavDisplay` with `@Serializable` keys `Launch` and `HubStatus`. Slice 1 replaces `HubStatus` with the onboarding branches.
  - `App.kt` is `HearthTheme { AppNavigation() }`.
- **Refactor:** none expected.

### Cycle 12: `:androidApp` shell, and streaming that doesn't buffer or drop on long pauses
- **Red:** tests in `androidApp/src/androidTest`:
  - **`LaunchSmokeTest`:** `MainActivity` starts and the launch screen is displayed. Fails because the module doesn't exist.
  - **`SseStreamingDeviceTest`:**
    - A loopback `ServerSocket` serves `text/event-stream` deltas, one at a time.
    - The server waits for the client to receive each delta before writing the next one, so buffering would deadlock and fail the test on timeout.
    - It includes one **15 s gap**, and it runs through the app's Koin `HttpClient` and `DefensiveSseStreamReader`.
    - The 15 s gap is expected to fail with the default engine config, because OkHttp's read timeout defaults to 10 s.
  - **`SseStreamingJvmTest`** (`:shared` jvmTest): the same scenario on CIO. It shows whether CIO's default request timeout cuts the stream. If it passes as is, it stays as a regression test.
- **Green:**
  - `settings.gradle.kts` includes `:androidApp`. Its `build.gradle.kts` uses `com.android.application` and `plugin.compose` (no `kotlin-android`, because AGP 9 has Kotlin built in).
    - It depends on `:shared` and `:composeApp`.
    - `applicationId "com.homelab.household"`.
  - `HouseholdHubApplication` calls `HouseholdHubSdk.init { androidContext(this@HouseholdHubApplication) }`.
  - `MainActivity` calls `enableEdgeToEdge()` and then `setContent { App() }`.
  - Manifest: `INTERNET` permission, `allowBackup="false"`.
  - Engine timeouts in `PlatformModule.android.kt`: `OkHttp.create { config { connectTimeout(10.seconds); readTimeout(Duration.ZERO); callTimeout(Duration.ZERO) } }`. The JVM actual gets the equivalent only if its test was red.
- **Refactor:** the device tests and the JVM test share one small SSE test-server helper per source set.

### Cycle 13: Docs
- `client/README.md`: modules, versions, and how to run the app and the device tests.
- `STAGE_5_PLAN.md` §3.1: the chosen versions, the Material3/Nav3 finding, and AGP 9.4 needing Gradle 9.6 or later.

## 5. Files touched (summary)

- **Build:** `gradle/libs.versions.toml`, `gradle/wrapper/*`, root `build.gradle.kts`, `settings.gradle.kts`, the four module `build.gradle.kts` files, and new `composeApp/` and `androidApp/` build files.
- **Existing sources:**
  - `DataModule.kt`, `SharedModule.kt`, `HouseholdHubSdk.kt`.
  - The 6 repository impls and `ServerHealthMonitor.kt` (defaults removed).
  - `AuthRepositoryImpl.kt`, `SessionRepositoryImpl.kt`, `NetworkExceptionHelper.kt`, `AuthViewModel.kt`, `FileTokenStorage.jvm.kt`.
  - `FileTokenStorage.kt` (expect) is deleted.
- **Existing tests:** `CleanArchitectureBoundaryTest.kt`, `KoinDependencyGraphTest.kt`, `AuthRepositoryTest.kt`, `AuthViewModelTest.kt`, `SessionRepositoryTest.kt`, `ServerHealthMonitorTest.kt`; `FileTokenStorageTest.kt` moves to jvmTest.
- **New:**
  - `PlatformModule` (common, jvm, android), `HubConfig`, `TokenDiskPayload`, `KeystoreTokenStorage`.
  - All of `:composeApp` (theme, icons, components, launch, navigation) and `:androidApp`.
  - The tests listed in each cycle.
- **Repo:** `.githooks/pre-commit`.

## 6. Open questions

1. **Material3 alpha:** use `1.12.0-alpha03`, the version the stable CMP 1.12.0 ships with? **Recommended: yes.** The alternative is building on `foundation` only, which means writing our own text fields, ripples and sheets.
2. **Where `implementation_plan.md` lives:** AGENTS.md doesn't say. The proposal is the repo root, next to AGENTS.md, replaced each time a new plan is approved.
3. **Existing wiring outside `:shared`:** `dataModule` (in `:core:data`) and `presentationModule` (in `:core:presentation`) are Stage 4 wiring that sits outside the coordinator AGENTS §3.4 describes. The proposal is to leave them for now (new wiring goes in `:shared`) and move them in a separate refactor.
4. **Pre-commit cost:** the new Gradle step only runs when staged files touch `apps/household-hub/client/`. OK?

## 7. Verification

1. `.\gradlew.bat jvmTest` (from `apps\household-hub\client`): all suites green, including the new boundary, wiring, theme, icon and component tests.
2. `.\gradlew.bat :androidApp:assembleDebug`, then `:core:data:connectedAndroidDeviceTest` and `:androidApp:connectedDebugAndroidTest` on the emulator.
3. A manual run on the emulator, with the PC on home Wi-Fi so the hub domain resolves locally:
   - The launch screen appears, then **Ready** with the real member count.
   - Toggling system dark mode switches palettes.
   - Airplane mode, then Retry, shows **Unreachable**.
4. Commit a test fixture that breaks a boundary rule, and check that the pre-commit hook blocks it.
5. Commit straight to master when everything is green.

## 8. Follow-ups (not in this slice)

- Before the iOS targets are added, commonTest's JUnit5/MockK/AssertJ tests must move to jvmTest or switch to `kotlin.test`.
- Darwin engine and Keychain storage slot into `platformModule` when iOS is picked up.
