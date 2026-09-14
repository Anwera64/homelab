# Implementation Plan — CI for the Household Hub client (GitHub Actions)

> Approved 2026-09-14. Delivered as a pull request from `ci/household-hub-client-actions`; the owner merges it once every job is green and stable.

## 1. Problem

The repo is public and its only workflow, `.github/workflows/test.yml`, runs `node --test` and validates `docker-compose.yml`. Nothing builds or tests the Kotlin Multiplatform client in `apps/household-hub/client`:

- The unit tests (domain, data, presentation, shared — including the architecture, Koin and design-token guards) only run on a developer machine.
- The Android app is never compiled in CI.
- The on-device tests (`:androidApp` launch smoke, splash theme, SSE streaming; `:core:data` Keystore storage) only run against a local emulator or phone.
- `gradlew` is committed as `100644`, so it cannot execute on a Linux runner.

**Scope:** the app targets Android and iOS. The desktop (JVM) Compose UI tests in `:composeApp` are **not** run in CI; the JVM target is used only to run the unit tests.

**Done when:** a pull request touching the client runs the unit tests, compiles the Android app and its test APKs, and runs the on-device tests on an emulator — all green, repeatably.

## 2. Design

New workflow `.github/workflows/household-hub-client.yml`:

- **Triggers:** `push` to `master`, `pull_request` to `master`, `workflow_dispatch`; path-filtered to `apps/household-hub/client/**`, the workflow and the shared setup action. `concurrency` cancels superseded pull-request runs.
- **Public-repo hardening:** `permissions: contents: read`; third-party actions pinned to commit SHAs; `pull_request` only (never `pull_request_target`); Gradle cache written only from `master`; Gradle wrapper validated by `setup-gradle`.
- **Shared setup** in a composite action, `.github/actions/setup-client` (Temurin 21 — the daemon JVM `gradle-daemon-jvm.properties` asks for — and `setup-gradle`), so a later macOS job reuses it unchanged.
- **Jobs (Linux, in parallel):**

| Job | Runs |
|---|---|
| `unit-tests` | `./gradlew jvmTest -x :composeApp:jvmTest` |
| `compile` | `:androidApp:assembleDebug`, `:androidApp:assembleDebugAndroidTest`, `:core:data:assembleAndroidDeviceTest` |
| `android-ui-tests` | `reactivecircus/android-emulator-runner` (KVM, API 36, `google_apis`, x86_64, cached AVD snapshot): `:androidApp:connectedDebugAndroidTest` and `:core:data:connectedAndroidDeviceTest` |

  Test reports are uploaded as artifacts on failure.

- **Linux / macOS split:** GitHub's macOS runners are Apple Silicon and cannot start the Android emulator (`HV_UNSUPPORTED`), so every Android job stays on Linux. iOS is **not** added here; the iOS pull request adds a `macos-latest` job that uses the same setup action.

## 3. TDD cycles

1. **Red** — `tests/ci-workflow.test.js`, in the style of `config-integrity.test.js`, asserts: the workflow exists and is path-filtered to the client; it runs `jvmTest` without `:composeApp:jvmTest`, the Android assemble and both connected-test tasks; permissions are read-only; every third-party `uses:` is pinned to a 40-character SHA; the workflow uses the shared setup action; `gradlew` is executable in the git index. `node --test` fails.
2. **Green** — `git update-index --chmod=+x apps/household-hub/client/gradlew`, the setup action and the workflow. `node --test` passes; `actionlint` is clean.
3. **Refactor** — tidy the workflow; open the pull request and iterate on real runs until every job is green, then re-run to confirm it is stable.

## 4. Verification

- `node --test` (also runs in the existing workflow and the pre-commit hook).
- `actionlint` via Docker.
- Local baseline: `./gradlew jvmTest`, `:androidApp:assembleDebug :androidApp:assembleDebugAndroidTest` — green.
- The pull request's own runs: all three jobs green, and green again on a re-run.

## 5. Risks

- Emulator jobs are slow (~10–15 min) and can flake → AVD snapshot cache; one retry at most, never masking a real failure.
- The API 36 `google_apis` x86_64 image availability is confirmed only by a third-party mirror → fall back to API 35 if the image can't be installed.
