# Implementation Plan — Stage 5 · Slice 0 (iOS): the foundation on Apple

> Supersedes the Android slice-0 plan of the same name (kept in git history). Slice 0 was built on
> Windows for Android; this plan takes the **same** slice to iOS. Slice 1 gets its own plan after this
> one lands.

## 1. Problem

`STAGE_5_PLAN.md` §3 says slice 0 ships the platform foundation on Android **and** iOS, with iOS
"compiled and checked later on the owner's Mac". That machine is now the machine we are on. Today:

- Every client module targets **JVM + Android only**. `settings.gradle.kts` has no iOS module.
- `platformModule` (`:shared`) has `actual`s for JVM (CIO + `FileTokenStorage`) and Android (OkHttp +
  `KeystoreTokenStorage`). There is no Darwin engine and no Keychain storage.
- **The blocker nobody has hit yet:** all 19 test files in `core/*/src/commonTest` use JUnit 5, and 10
  of them use MockK. Both are JVM-only. The moment an iOS target is added to those modules,
  `commonTest` is compiled for iOS too and the build stops. This must be fixed *before* any target is
  added, not after.
- **A second blocker behind the first:** the 36 use cases are final classes, so a ViewModel's
  collaborators cannot be mocked by any library that builds its mocks at compile time — which every
  Native-capable one does. MockK hides this today by rewriting bytecode, which is exactly the trick
  that does not exist on iOS.
- There is no iOS entry point: nothing calls `HouseholdHubSdk.init` or hosts `App()` in a
  `UIViewController`, and there is no Xcode project.
- SSE is proven token-by-token on Android (`SseStreamingDeviceTest`). Darwin — the engine the plan
  flags as most likely to buffer — is unproven.

**Done when:** the app opens on an iPhone simulator, calls `GET /auth/status`, shows ready / first
run / unreachable, follows the system theme, keeps its tokens in the Keychain, streams SSE
token-by-token through a long pause — and the Android app and every JVM suite are untouched and green.

## 2. What is already iOS-ready (checked today)

The discipline held; this is why the slice is tractable:

| Checked | Result |
| :--- | :--- |
| `commonMain` imports across all five modules | Zero `java.`/`javax.`/`android.`/engine imports. Only coroutines, serialization, Ktor, Koin, Compose, Nav3, `androidx.lifecycle` — all multiplatform |
| `Dispatchers.IO`, `System.*`, `UUID`, `String.format`, `Locale`, `@Synchronized` in `commonMain` | None |
| Time handling | `kotlin.time.TimeSource` / `Duration` — multiplatform |
| `:composeApp` UI tests | Already MockK-free in `commonTest` by an explicit earlier decision (`client/README.md`), using `runComposeUiTest`, `InMemoryTokenStorage` and hand-written fakes. They compile for iOS as they stand |
| Library versions | Lifecycle 2.11.0, Nav3 1.1.1, Koin 4.2.2, Ktor 3.5.2, CMP 1.12.0 all publish iOS artifacts |

So the work is: the test frameworks, the targets, two platform `actual`s, an app module, and proof.

## 3. Prerequisites on this Mac (re-checked 13 September 2026)

| # | What | Status | Note |
| :-- | :--- | :--- | :--- |
| 1 | **JDK 21** | ✅ Temurin 21.0.12.1, `JAVA_HOME` set | |
| 2 | **Full Xcode active** | ⚠️ `Xcode.app` is installed, but `xcode-select -p` still returns `/Library/Developer/CommandLineTools` | Needs `sudo xcode-select -s /Applications/Xcode.app`, then `sudo xcodebuild -license accept` and `sudo xcodebuild -runFirstLaunch`. Kotlin/Native cannot build for iOS until this points at Xcode |
| 3 | **iOS simulator runtime** | ❓ unverifiable until 2 is done (`simctl` is not on PATH) | Check with `xcrun simctl list runtimes`; install from Xcode → Settings → Components if the list is empty |
| 4 | **Android SDK** | ✅ platforms 36 + 37.0, build-tools 36.0.0, `client/local.properties` written | **Verified**: `./gradlew projects` configures the whole build, AGP KMP plugin and all |
| 5 | **`gradlew` executable** | ✅ `100755` on disk and in the git index | |

First native build also downloads the Kotlin/Native toolchain (~1 GB, once).

## 4. Architecture

```
:androidApp ─┐                                                   Android app (unchanged)
             ├─► :composeApp (UI) ─► :core:presentation ─► :core:domain ◄─ :core:data
:iosApp ─────┘                                                                ▲
     │                                                                        │
     └──────► :shared (DI coordinator) ──────────────────────────────────────┘
```

`:iosApp` mirrors `:androidApp` exactly: it is the only iOS-side module that may see both `:composeApp`
and `:shared`, so `MainViewController` can start Koin and host `App()` without `:composeApp` ever
importing `sdk`/`di`. `CleanArchitectureBoundaryTest.module_dependencies_point_inward` needs no
exception — app roots are not in its map, by the same rule that already exempts `:androidApp`.

**Decisions taken** (from the questions asked before this plan):

1. All 19 core test files are ported to `kotlin.test` + **Mokkery**, so they run on the iOS simulator
   as well as the JVM. MockK leaves the client entirely. No new module and no hand-written fakes.
2. **Use cases move behind protocols** (cycle 1), the way repositories already are. A unit test should
   exercise one class and mock the rest; that is impossible while a ViewModel's dependencies are final
   classes, and it stays impossible under Mokkery, which builds its mocks at compile time. An
   interface per use case fixes the cause rather than working around it, and leaves `:core:presentation`
   depending on domain *protocols* only — which is what the layer rule meant all along.
3. `:iosApp` is a new Kotlin module; the Xcode project sits beside it.
4. Targets `iosArm64` + `iosSimulatorArm64`; verification on the simulator. Real device, signing and
   the local-network permission are a follow-up.

## 5. TDD cycles

Every cycle ends with `./gradlew jvmTest` green and — from cycle 3 on — the iOS compilation green.
Android must stay green throughout: `./gradlew :androidApp:assembleDebug` at the end of each cycle
that touches a shared file.

### Cycle 0 — Baseline on the Mac (no production change)

- **Red/Green:** none. Record a green `./gradlew jvmTest` on macOS *before* anything changes, so a
  later failure is attributable. Fix only what the platform move breaks (path separators, the `gradlew`
  bit).
- **Verify:** `./gradlew jvmTest` and `./gradlew :androidApp:assembleDebug` both green on this machine.

### Cycle 1 — Use cases behind protocols (production refactor)

A unit test should drive one class and mock the rest. `:core:presentation` cannot: a ViewModel's
collaborators are **final classes**, and no compile-time mocking library can stand in for one —
Mokkery's documented workaround is the all-open plugin, which opens those classes in the production
source set. Rather than open production code to suit tests, give a use case the shape a repository
already has: a protocol in the domain, an implementation behind it.

- **Red:** extend `CleanArchitectureBoundaryTest` first with the rule this cycle creates — nothing
  outside `:core:domain` and `DomainModule` may import `com.homelab.household.domain.usecase.impl`.
  It fails while the implementations are still the public names.
- **Green:** for each of the **36** use cases:
  - `usecase/<Name>UseCase.kt` becomes a `fun interface` carrying today's signature:
    `fun interface CheckAuthStatusUseCase { suspend operator fun invoke(): AuthStatus }`. A
    `fun interface` costs nothing over a plain one and lets a test that needs no verification pass a
    lambda instead of a mock.
  - the current class moves to `usecase/impl/<Name>UseCaseImpl.kt` with its body untouched:
    `class CheckAuthStatusUseCaseImpl(private val authRepository: AuthRepository) : CheckAuthStatusUseCase`.
    Protocols stay in `usecase/`, implementations in `usecase/impl/` — the same separation the
    repository protocols have from `…RepositoryImpl`, and it keeps the package everyone imports at 36
    files rather than 72.
  - `DomainModule` binds the protocol:
    `factory<CheckAuthStatusUseCase> { CheckAuthStatusUseCaseImpl(get()) }` — 36 lines, and the only
    place an implementation is named.
  - the 6 domain test files construct `…UseCaseImpl`; they were testing the implementation all along.
- **Verify:** `./gradlew jvmTest` green with every test unchanged apart from the `Impl` suffix, and
  `:shared:jvmTest` for the Koin graph, which resolves by protocol and should not notice.
- **Scope:** all 36, not only the 15 a ViewModel touches today — a half-converted package is worse
  than either shape, and slices 1–11 consume most of the rest. Say the word to cut it to the 15.

### Cycle 2 — The core tests leave JUnit 5 and MockK (tests only, no production change)

The largest cycle (~1,900 lines touched) and pure refactor: **the assertions must not change**, only
the framework under them.

**Mokkery 3.5.0** (published 7 September 2026) replaces MockK. It supports Kotlin 2.4.0–2.4.20 — our
exact compiler — and is compiler-plugin driven rather than reflective, which is why it works on
Native. Its vocabulary is MockK's: `mock<T>()`, `every { }`, `everySuspend { }`,
`verifySuspend(exactly(1)) { }`.

- **Red:** none — this cycle rewrites tests, so its own proof is the mutation check below.
- **Green:** port file by file, running `jvmTest` after each:
  - Framework swap everywhere: `org.junit.jupiter.api.Test` → `kotlin.test.Test`;
    `@BeforeEach`/`@AfterEach` → `@BeforeTest`/`@AfterTest`; `Assertions.assertX` →
    `kotlin.test.assertX`.
  - `:core:data` (7 files) — JUnit only, no mocks at all: just the swap. They keep `MockEngine`, which
    is multiplatform.
  - `:core:domain` (6 files) — these mock repository **protocols**, which Mokkery handles directly:
    `mockk<AuthRepository>()` → `mock<AuthRepository>()`, `coEvery { } returns` →
    `everySuspend { } returns`, `coVerify(exactly = 1) { }` → `verifySuspend(exactly(1)) { }`. Close
    to a find-and-replace.
  - `:core:presentation` (4 files) — cycle 1 turned the 15 use cases they mock into protocols, so
    these are the same find-and-replace as the domain ones: `mockk<CheckAuthStatusUseCase>()` →
    `mock<CheckAuthStatusUseCase>()`. Each ViewModel test still drives one class and mocks exactly
    the collaborators that class declares; nothing else about them changes.
  - Gradle: `dev.mokkery` 3.5.0 as a catalog plugin alias, applied to `:core:domain` and
    `:core:presentation` (the plugin wires its own test dependencies). `junit-jupiter` +
    `junit-platform-launcher` move from `commonTest` to `jvmTest` in all three modules; `mockk` and
    `assertj` leave the catalog (`assertj` is declared but imported nowhere). `kotlin-test` joins every
    `commonTest`; the Kotlin plugin resolves it to the JUnit 5 variant because `useJUnitPlatform()` is
    configured — the pattern `:composeApp` already uses.
- **Refactor:** lift the two duplicate `assertThrowsSuspend` helpers onto `kotlin.test.assertFailsWith`
  and delete them.
- **Prove the port didn't hollow the tests out:** mutate one production line per module (flip a
  comparison in `LoginUseCaseImpl`, drop the `ServerOfflineException` mapping in `NetworkExceptionHelper`,
  return `HubStatus.Ready` unconditionally in `LaunchViewModel`) and confirm the suite fails each time,
  then revert.
- **Verify:** `./gradlew jvmTest` — same test count, all green, and `grep -r "io.mockk\|org.junit" core/`
  returns nothing outside `jvmTest`/`androidDeviceTest`.
- **Note:** `:composeApp`'s UI tests keep using no mocking library at all. `client/README.md` explains
  why (a mock in `commonTest` would have nailed the UI suite to the JVM); Mokkery would lift that
  constraint, but `MockEngine` plus hand-written fakes is the better test for a full-stack screen
  test, so that decision stands and only its *reason* needs rewording in cycle 9.

### Cycle 3 — iOS targets on the five modules

- **Red:** `./gradlew compileKotlinIosSimulatorArm64` — no such task.
- **Green:** add `iosArm64()` and `iosSimulatorArm64()` to `:core:domain`, `:core:testing`,
  `:core:data`, `:core:presentation` and `:composeApp`. `:shared` comes last because it needs the
  `actual` from cycles 4–5. Move `compose-ui-tooling` out of `composeApp`'s `commonMain` and into
  `androidMain` if its iOS klib turns out not to be published — `ui-tooling-preview`, which
  `DayNightPreviews` actually imports, is multiplatform and stays.
- **Verify:** `./gradlew compileKotlinIosSimulatorArm64 compileTestKotlinIosSimulatorArm64` green for
  every module except `:shared`; `jvmTest` still green.
- **Correction, found while doing it:** `:composeApp`'s *test* compilation cannot be part of this
  cycle. Its `commonTest` depends on `:shared` by design — the UI tests wire the real Koin graph — so
  it cannot compile for iOS until `:shared` has iOS targets in cycle 5. This cycle takes
  `:composeApp`'s `commonMain` only; its tests are verified in cycle 8, where they were going to be
  run anyway.
- **What actually broke** (both invisible to an import grep, which is why the compiler had to be the
  judge): `Charsets.UTF_8` in `DefensiveSseStreamReaderTest` — JVM-only and default-imported, so it
  needs no `import` line to find — replaced with `encodeToByteArray()`; and
  `org.jetbrains.compose.ui:ui-tooling`, which has no iOS klib and moved to `androidMain` as
  predicted.

### Cycle 4 — `KeychainTokenStorage` (iOS)

- **Red:** `core/data/src/iosTest/…/KeychainTokenStorageTest.kt`, the Android
  `KeystoreTokenStorageTest` said in Apple terms: saving then reading returns the tokens; a second
  instance reads what the first wrote; `clear()` empties it; a corrupt item reads as signed out rather
  than throwing; a refresh token of `null` leaves the stored one alone.
- **Green:** `core/data/src/iosMain/…/KeychainTokenStorage.kt` — `kSecClassGenericPassword`, service
  `com.homelab.household.tokens`, account `auth_tokens`, holding the same `TokenDiskPayload` JSON as
  the other two. `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: not synced to iCloud and not
  restored onto a new device, which is what `noBackupFilesDir` buys on Android. `SecItemCopyMatching`
  → `SecItemUpdate`/`SecItemAdd` → `SecItemDelete`; anything unreadable is deleted and reads as signed
  out. The Keychain is itself thread-safe, so there is no lock and no cached copy to go stale.
- **Verify:** ~~`./gradlew :core:data:iosSimulatorArm64Test`~~ — **not possible, and this is the
  cycle's real finding.** Every `SecItem*` call from a Kotlin/Native test binary returns
  `errSecNotAvailable` (-25291): Gradle spawns the binary with `simctl spawn --standalone`, outside
  the booted simulator's daemon environment, so `securityd` is unreachable. It is not the
  `errSecMissingEntitlement` (-34018) the plan half-expected, but the same family of problem.
  `kSecUseDataProtectionKeychain` changes nothing (it is a macOS-side switch), and running
  non-standalone against a booted device hangs with no output. Both knobs are one-liners if anyone
  wants to revisit.
- **Decided:** the tests that cannot run are deleted rather than left red or faked against a seam.
  `KeychainTokenStorage` is proven at the app level in cycle 7, where the real app bundle has the
  entitlements a bare test binary lacks. Until then the implementation is **unverified by any
  automated test** — reviewed by eye only, and that is the trade this decision accepts.
- The class therefore ships without the `accessibility()` helper and the accessibility constant
  accessor, which existed only for the deleted tests.

### Cycle 4b — The chat stream survives a dispatcher switch (production bug, found in cycle 4)

**Not in the original plan.** Three `SessionRepositoryTest` cases pass on the JVM and fail on iOS.
The diagnosis is a production defect, not a test artifact, and it is the exact class of bug this
stage's iOS work exists to find.

**What is wrong.** `SessionRepositoryImpl.streamChatTurn` calls `emit()` from inside
`statement.execute { }`. Ktor 3.5.2 switches dispatchers there on Native — `useEngineDispatcher` is
unconditionally `true` off the JVM — so the block runs on the engine's `Dispatchers.IO` while the
`flow { }` collector runs on the caller's dispatcher, and every `emit` violates the flow-context
invariant. On the JVM the flag is off, which is why it has never shown: the Android path is
*accidentally* safe, and Ktor 4.0 turns the switch on everywhere.

Proved by setting `-Dio.ktor.client.statement.useEngineDispatcher=true` on the JVM test task, which
reproduces all three failures on the JVM with the same MockEngine and the same `runTest`.

**Why it is silent.** `DefensiveSseStreamReader.readEvents` holds its `emit` calls inside a
`catch (_: Exception)` meant for malformed JSON. The invariant `IllegalStateException` is an
`Exception`, so every event is swallowed as a "malformed line" and the flow completes normally with
nothing emitted. On the 409 path the same violation is discarded by `catch (e: Throwable)`, so
polling never completes and burns its whole 60 s budget.

**What a user would see on iOS:** an assistant reply that never arrives, with no error — and a 409
recovery that always times out even when the server answered immediately.

- **Red:** a JVM test task running with `useEngineDispatcher=true`, reproducing the three failures in
  seconds without a simulator. It stays as a permanent guard, and it pre-qualifies the codebase for
  Ktor 4.0.
- **Green:**
  1. Hoist emission out of `execute`: `channelFlow { … send(event) … }`, whose `send` is safe from
     any context — in `streamChatTurn` and in `pollUntilFinished`. **Not** `flowOn`, which would move
     the whole upstream instead of fixing the boundary.
  2. Narrow `DefensiveSseStreamReader`'s `catch (_: Exception)` to the JSON parse it was written for,
     so a collector error can never again be mistaken for a malformed line.
- **Refactor:** `SessionRepositoryImpl.pollDelayMs` is declared, passed by the test as `10`, and never
  read — `pollUntilFinished` hardcodes `500L`. Wire it up; it is why the 409 test spends real seconds.
- **Verify:** `jvmTest` green both with and without the flag; `:core:data:iosSimulatorArm64Test` green.
- **Note for cycle 6:** `androidApp`'s `SseStreamingDeviceTest` collects inside `execute` with a plain
  lambda rather than emitting through a `flow {}`, so it could never have caught this. Cycle 6's
  Darwin proof must drive the real `Flow<ChatStreamEvent>` boundary the app uses, and assert on event
  count and content — "no exception thrown" would have passed throughout this bug.

### Cycle 5 — The iOS `platformModule`

- **Red:** `shared/src/iosTest/…/IosPlatformModuleTest.kt` — `HouseholdHubSdk.init()` binds a Darwin
  engine and a `KeychainTokenStorage`; plus an iOS twin of `KoinDependencyGraphTest` resolving every
  repository, use case and ViewModel. (The JVM graph test stays where it is: `:shared`'s `commonTest`
  can't hold it, because the Android unit-test compilation would try to run it without a context.)
- **Green:** `ktor-client-darwin` in the catalog and in `shared`'s `iosMain`; `platformModule.ios.kt`
  binding `Darwin.create { … }` and `KeychainTokenStorage()`. NSURLSession's default 60-second
  `timeoutIntervalForRequest` is the Apple equivalent of the OkHttp read timeout the Android module
  had to zero out — a long answer pausing between tokens would trip it — so
  `timeoutIntervalForRequest = 3600.0` and `timeoutIntervalForResource = 86_400.0`, with the comment
  explaining why, as on Android.
- **Verify:** `./gradlew :shared:iosSimulatorArm64Test :shared:jvmTest`.

### Cycle 6 — Proof that Darwin doesn't buffer

The claim to prove is the Android one: deltas arrive **one at a time**, and the stream survives a pause
longer than the engine's default timeout.

- **Red:** `core/data/src/iosTest/…/DarwinSseStreamingTest.kt` against a fixture SSE server on
  `127.0.0.1` (loopback is exempt from ATS) that refuses to write delta *n+1* until delta *n* has been
  read, and inserts one 15-second gap. A buffering engine deadlocks; a timing-out engine drops. The
  fixture is a small Python script under `client/tools/`, started and stopped by the Gradle test task
  on a fixed port — the same trick the Android test plays with an in-process `ServerSocket`, which
  Kotlin/Native has no equivalent of.
- **Green:** whatever the Darwin config needs — expected to be the timeouts from cycle 5 and nothing
  more.
- **Fallback if the fixture proves flaky in CI-less local runs:** keep the test, and additionally prove
  it by hand against the real hub with a long answer, recorded in the baseline document. The proof is
  not optional; only its harness is negotiable.
- **Verify:** `./gradlew :core:data:iosSimulatorArm64Test`.

### Cycle 7 — `:iosApp`, and the app on a simulator

- **Red:** nothing automated yet — the gate is the app launching.
- **Green:**
  - `client/iosApp/build.gradle.kts`: `kotlin-multiplatform` + `compose-multiplatform` +
    `kotlin-compose`, `iosArm64`/`iosSimulatorArm64`, `binaries.framework { baseName =
    "HouseholdHubKit"; isStatic = true }`, depending on `:composeApp` and `:shared`.
  - `src/iosMain/…/MainViewController.kt`: `fun MainViewController() = ComposeUIViewController { App() }`,
    and a `startHouseholdHub()` that calls `HouseholdHubSdk.init()` once — the iOS twin of
    `HouseholdHubApplication`.
  - `client/iosApp/project.yml` — the XcodeGen spec, checked in; `xcodegen generate` produces
    `HouseholdHub.xcodeproj`, which is git-ignored. Swift sources under `client/iosApp/HouseholdHub/`:
    a SwiftUI `App` and a `UIViewControllerRepresentable` wrapping `MainViewController()`, with
    `.ignoresSafeArea(.keyboard)` so the composer behaves the way `adjustResize` does on Android.
    Bundle id `com.homelab.household`, display name **HyggeHub**, deployment target **iOS 15**. The
    spec declares a pre-build script phase running
    `./gradlew :iosApp:embedAndSignAppleFrameworkForXcode`, plus `FRAMEWORK_SEARCH_PATHS` and
    `LD_RUNPATH_SEARCH_PATHS` for the built framework.
  - `.gitignore`: `*.xcodeproj/`, `xcuserdata/`, `*.xcworkspace/xcuserdata/`, `DerivedData/`.
  - An **XCUITest target** in `project.yml`, carrying cycle 4's debt: it drives the real app bundle,
    which has the entitlements a bare Kotlin/Native test binary lacks, and proves
    `KeychainTokenStorage` end to end — sign in, terminate the app, relaunch, still signed in; sign
    out, relaunch, signed out. This is the only automated cover `KeychainTokenStorage` gets.
- **Verify:** build and run on an iPhone 17 simulator; the launch screen names the hub, calls
  `GET /auth/status` and shows ready / first run; airplane-mode the hub (point `HubConfig` at an unused
  host) and the offline state appears; toggle the simulator to dark and the theme follows; and the
  Keychain XCUITest passes.

### Cycle 8 — The UI tests on the simulator

- **Red:** `./gradlew :composeApp:iosSimulatorArm64Test` — the existing `LaunchScreenTest`,
  `AppNavHostTest` and the component tests, unchanged, on Apple.
- **Green:** expected to be free: they are already MockK-free and engine-agnostic. The one known risk
  is Compose resources (`strings.xml`, the fonts) inside a Native test binary; if `getString` can't find
  the bundle, `LaunchRobot` is the only thing that needs adapting.
- **Fallback:** if resource loading in Native test binaries is broken in CMP 1.12, the tests keep
  compiling for iOS (which is the regression guard that matters) and only their *execution* stays on
  the JVM. Write down which it was.

### Cycle 9 — Guards and documentation

- **Red:** extend `CleanArchitectureBoundaryTest`: `platform.` (the Kotlin/Native Apple frameworks)
  joins `java.`/`javax.`/`android.` in the `commonMain` forbidden list, and `io.ktor.client.engine.darwin`
  is already there. Add `:iosApp` to nothing — app roots stay out of the dependency map, as
  `:androidApp` does. Prove the new rule by importing `platform.Foundation.NSDate` in a `commonMain`
  file and watching it fail.
- **Green:** the rule passes.
- **Docs:** `client/README.md` — targets line, the Ktor engine table (Darwin), Keychain beside
  Keystore, macOS commands beside the PowerShell ones, how a screen is tested on iOS, the use-case
  protocol convention (`usecase/` vs `usecase/impl/`, bound by protocol in `DomainModule`), and
  Mokkery in place of MockK.
  `STAGE_5_PLAN.md` §3 — iOS is no longer "later"; record the versions and the Apple-side findings the
  way §3.1 records the Android ones. `.githooks/pre-commit` stays JVM-only: simulator tests are far too
  slow for a commit hook, and the boundary test already catches the mistake that would break iOS.

## 6. Files touched

| Area | Files |
| :--- | :--- |
| New module | `client/iosApp/` (build file, `MainViewController.kt`, Swift app, `project.yml`) |
| Gradle | `settings.gradle.kts`, `gradle/libs.versions.toml` (add `ktor-client-darwin` and the `dev.mokkery` plugin; drop `mockk`, `assertj`), the 5 module build files (+ iOS targets, Mokkery, test-dep moves) |
| New iOS source | `core/data/src/iosMain/…/KeychainTokenStorage.kt`, `shared/src/iosMain/…/PlatformModule.ios.kt` |
| New iOS tests | `core/data/src/iosTest/` (Keychain, Darwin SSE), `shared/src/iosTest/` (platform module, Koin graph) |
| Domain refactor | 36 use-case protocols in `usecase/`, 36 implementations moved to `usecase/impl/`, `DomainModule` rebound to the protocols |
| Ported tests | 19 files under `core/*/src/commonTest` |
| Guards | `shared/src/jvmTest/…/CleanArchitectureBoundaryTest.kt` |
| Docs | `client/README.md`, `apps/household-hub/docs/STAGE_5_PLAN.md`, `.gitignore` |
| Untouched | Every `commonMain` production file, `:androidApp`, the backend |

## 7. Verification (the whole slice)

```bash
cd apps/household-hub/client
./gradlew jvmTest                                   # every JVM suite, as before
./gradlew :shared:jvmTest --tests "*CleanArchitectureBoundaryTest*" \
    --tests "*KoinDependencyGraphTest*" --tests "*DesignSystemTokenTest*"
./gradlew iosSimulatorArm64Test                     # core, shared and the UI tests on Apple
./gradlew :androidApp:assembleDebug                 # Android unbroken
```
Then by hand: the app on an iPhone simulator against the real hub — ready / first run / unreachable,
light and dark, tokens surviving a relaunch.

## 8. Risks

| Risk | Mitigation |
| :--- | :--- |
| `KeychainTokenStorage` is unverified until cycle 7 | Accepted deliberately (cycle 4). The blast radius is "the app forgets you are signed in", which the first manual run on a simulator would expose immediately; the XCUITest then holds the line |
| The 36-use-case split is a wide diff mid-stage | It is mechanical and guarded: every domain test and the Koin graph test must pass unchanged, and it lands as its own commit before any iOS work, so a bisect separates it cleanly |
| The test port quietly weakens assertions | Every ported file is mutation-checked against a deliberate production bug before the cycle closes |
| Mokkery is a compiler plugin, so it is pinned to the Kotlin version | 3.5.0 covers 2.4.0–2.4.20 and shipped six days after we picked 2.4.20, so the project is tracking it closely. A future Kotlin bump waits for a Mokkery release — the same coupling the Compose compiler plugin already imposes |
| `compose-ui-tooling` has no iOS klib | Caught at cycle 3's first compile; it moves to `androidMain`, where it was only ever used from |
| Compose resources unavailable in Native test binaries | Cycle 8's fallback: compile on iOS, execute on the JVM, and say so in the docs |
| Darwin buffers SSE despite the timeouts | Cycle 6 is deliberately before the app module, so this is known before any chat screen depends on it |
| Xcode project drift (a hand-edited `.pbxproj` is unreviewable) | See open question 1 |
| The Mac can't build Android at all without an SDK | Prerequisite 4; nothing else can be verified until it's done |

## 9. Open questions

1. ~~How the Xcode project gets created~~ — **decided: XcodeGen** (2.46.0, installed 13 September
   2026). `client/iosApp/project.yml` is checked in and generates `HouseholdHub.xcodeproj`, which is
   **git-ignored** — the YAML is the reviewable source of truth, and `xcodegen generate` is a
   documented step in `client/README.md` for anyone cloning the repo.
2. **Bundle identifier.** `com.homelab.household` matches Android. Confirm — it is awkward to change
   once anything is installed on a device.
3. **Where the port stops.** `:core:data`'s `FileTokenStorageTest` (jvmTest) and the Android
   `KeystoreTokenStorageTest` stay on JUnit — they are platform tests in platform source sets. Only
   `commonTest` is ported. Say if you want those moved too, for one framework across the repo.

## 10. Follow-ups (explicitly not in this slice)

Real-device run: an Apple developer account (or free provisioning's 7-day limit),
`NSLocalNetworkUsageDescription` in `Info.plist` for the hub's `192.168.1.20` address on home Wi-Fi and
the permission prompt that comes with it, and a signing team in the Xcode project. Also: an iOS
smoke test equivalent to `LaunchSmokeTest`, and iOS in CI (GitHub's macOS runners are billable —
worth a decision when Stage 6 touches deployment).
