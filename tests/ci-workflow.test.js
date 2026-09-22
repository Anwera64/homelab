const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const ROOT_DIR = path.resolve(__dirname, '..');
const WORKFLOW_PATH = path.join(ROOT_DIR, '.github/workflows/household-hub-client.yml');
const SETUP_ACTION_PATH = path.join(ROOT_DIR, '.github/actions/setup-client/action.yml');
const GRADLEW_PATH = 'apps/household-hub/client/gradlew';
const GRADLE_PROPERTIES_PATH = path.join(ROOT_DIR, 'apps/household-hub/client/gradle.properties');
const PRE_COMMIT_HOOK_PATH = path.join(ROOT_DIR, '.githooks/pre-commit');

// Missing files read as empty so each check fails with its own message instead of aborting the suite.
// Line endings are normalised: a Windows checkout with core.autocrlf hands the YAML over as CRLF, and
// the block regexes below end a block at the first blank line that isn't a bare "\n".
function readIfExists(filePath) {
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, 'utf8').replace(/\r\n/g, '\n') : '';
}

// Text of a top-level YAML key's block, up to the next top-level key.
function topLevelBlock(content, key) {
  const match = content.match(new RegExp(`^${key}:[^\\n]*\\n((?:[ \\t]+[^\\n]*\\n|[ \\t]*\\n)*)`, 'm'));
  return match ? match[1] : '';
}

// Text of a key's block nested at the given indent inside a parent block.
function nestedBlock(block, key, indent) {
  const pad = ' '.repeat(indent);
  const match = block.match(new RegExp(`^${pad}${key}:[^\\n]*\\n((?:${pad}[ \\t]+[^\\n]*\\n|[ \\t]*\\n)*)`, 'm'));
  return match ? match[1] : '';
}

test('Household Hub client CI workflow', async (t) => {
  const workflowContent = readIfExists(WORKFLOW_PATH);
  const setupActionContent = readIfExists(SETUP_ACTION_PATH);

  await t.test('Workflow file exists', () => {
    assert.ok(fs.existsSync(WORKFLOW_PATH), '.github/workflows/household-hub-client.yml must exist');
  });

  await t.test('Workflow runs on push and pull_request to master, path-filtered to the client', () => {
    const onBlock = topLevelBlock(workflowContent, 'on');
    for (const event of ['push', 'pull_request']) {
      const eventBlock = nestedBlock(onBlock, event, 2);
      assert.ok(eventBlock, `Workflow must trigger on ${event}`);
      assert.ok(/branches:[^\n]*\bmaster\b|branches:\s*\n\s+-\s*["']?master\b/.test(eventBlock), `${event} trigger must target master`);
      assert.ok(eventBlock.includes('apps/household-hub/client/**'), `${event} trigger must be path-filtered to apps/household-hub/client/**`);
    }
  });

  await t.test('Workflow is read-only and never uses pull_request_target', () => {
    const permissionsBlock = topLevelBlock(workflowContent, 'permissions');
    assert.ok(/^\s+contents:\s*read\s*$/m.test(permissionsBlock), 'Workflow must declare top-level permissions: contents: read');
    assert.ok(!/write/.test(permissionsBlock), 'Top-level permissions must not grant write access');
    // pull_request_target runs fork code with a write token and secrets; fatal on a public repo.
    assert.ok(!workflowContent.includes('pull_request_target'), 'Workflow must not use pull_request_target');
  });

  await t.test('Workflow runs the JVM tests, the Android build and the on-device tests', () => {
    const requiredTasks = ['jvmTest', ':androidApp:assembleDebug', ':androidApp:connectedDebugAndroidTest', ':core:data:connectedAndroidDeviceTest'];
    for (const task of requiredTasks) {
      const escaped = task.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      assert.ok(
        new RegExp(`\\./gradlew\\b[^\\n]*\\s${escaped}(\\s|$)`, 'm').test(workflowContent),
        `Workflow must run ./gradlew ${task}`
      );
    }
    // :composeApp:jvmTest is how the commonTest screen tests run; skipping it drops them from CI.
    assert.ok(
      !/-x\s+:composeApp:jvmTest\b/.test(workflowContent),
      'Workflow must not exclude :composeApp:jvmTest'
    );
  });

  await t.test('Jobs run compile -> JVM tests -> on-device tests, and the emulator reuses the build', () => {
    const jobsBlock = topLevelBlock(workflowContent, 'jobs');
    const compileJob = nestedBlock(jobsBlock, 'compile', 2);
    const uiTestsJob = nestedBlock(jobsBlock, 'android-ui-tests', 2);
    const needsOf = (job) => {
      const match = job.match(/^\s+needs:\s*(?:\[([^\]]*)\]|([\w-]+))/m) || [];
      return (match[1] ?? match[2] ?? '').split(',').map((n) => n.trim()).filter(Boolean);
    };
    // Its own job, so a compile failure reads as one instead of hiding in a test log.
    assert.ok(compileJob.includes(':androidApp:assembleDebug'), 'A separate compile job must run :androidApp:assembleDebug');
    assert.ok(needsOf(nestedBlock(jobsBlock, 'jvm-tests', 2)).includes('compile'), 'jvm-tests must need compile');
    assert.ok(needsOf(uiTestsJob).includes('jvm-tests'), 'android-ui-tests must need jvm-tests');
    // The compile job hands its Gradle build cache to the emulator job, keyed on the commit.
    assert.ok(
      /uses:\s*actions\/cache\/save@[\s\S]*?key:[^\n]*github\.sha/.test(compileJob),
      'compile must save the Gradle build cache keyed on github.sha'
    );
    assert.ok(
      /uses:\s*actions\/cache\/restore@[\s\S]*?key:[^\n]*github\.sha/.test(uiTestsJob),
      'android-ui-tests must restore the build cache compile saved'
    );
  });

  // The linter is only worth having if it cannot be quietly dropped. Two places enforce it: the
  // hook, so a violation never reaches a commit, and CI, so it never reaches master from a machine
  // whose hooks were skipped.
  await t.test('A lint job runs ktlintCheck, beside the compile chain rather than behind it', () => {
    const jobsBlock = topLevelBlock(workflowContent, 'jobs');
    const lintJob = nestedBlock(jobsBlock, 'lint', 2);
    assert.ok(lintJob, 'Workflow must define a lint job');
    assert.ok(/\.\/gradlew\b[^\n]*\sktlintCheck(\s|$)/m.test(lintJob), 'lint must run ./gradlew ktlintCheck');
    // Nothing to wait for: no Android SDK, no emulator, so it answers in about a minute.
    assert.ok(!/^\s+needs:/m.test(lintJob), 'lint must not depend on another job');
    assert.ok(
      /uses:\s*["']?\.\/\.github\/actions\/setup-client\b/.test(lintJob),
      'lint must use the shared ./.github/actions/setup-client'
    );
  });

  await t.test('The pre-commit hook runs ktlintCheck on client commits', () => {
    const hook = readIfExists(PRE_COMMIT_HOOK_PATH);
    assert.ok(hook, '.githooks/pre-commit must exist');
    assert.ok(/\bktlintCheck\b/.test(hook), 'The pre-commit hook must run ktlintCheck');
    // Formatting is a command, not a puzzle; the hook has to name it.
    assert.ok(/\bktlintFormat\b/.test(hook), 'A failing lint must tell the committer to run ktlintFormat');
    // Only client commits pay for it.
    assert.ok(
      hook.includes('^apps/household-hub/client/'),
      'The ktlint step must stay behind the staged-client-files guard'
    );
  });

  await t.test('Gradle build cache is on', () => {
    assert.ok(
      /^org\.gradle\.caching=true\s*$/m.test(readIfExists(GRADLE_PROPERTIES_PATH)),
      'apps/household-hub/client/gradle.properties must set org.gradle.caching=true'
    );
  });

  await t.test('Every third-party action is pinned to a full commit SHA', () => {
    const sources = [
      ['household-hub-client.yml', workflowContent],
      ['setup-client/action.yml', setupActionContent],
    ];
    for (const [name, content] of sources) {
      const refs = [...content.matchAll(/^\s*(?:-\s*)?uses:\s*["']?([^\s"'#]+)/gm)].map((m) => m[1]);
      assert.ok(refs.length > 0, `${name} must use at least one action`);
      for (const ref of refs.filter((r) => !r.startsWith('./'))) {
        // Tags are mutable; only a SHA guarantees the code that was reviewed is the code that runs.
        assert.ok(/@[0-9a-f]{40}$/.test(ref), `${name}: "${ref}" must be pinned to a 40-character commit SHA`);
      }
    }
  });

  await t.test('Shared setup lives in the setup-client composite action and the workflow uses it', () => {
    assert.ok(fs.existsSync(SETUP_ACTION_PATH), '.github/actions/setup-client/action.yml must exist');
    assert.ok(/using:\s*["']?composite/.test(setupActionContent), 'setup-client must be a composite action');
    assert.ok(
      /uses:\s*["']?\.\/\.github\/actions\/setup-client\b/.test(workflowContent),
      'Workflow must use ./.github/actions/setup-client'
    );
  });

  // The iOS lane. Every iOS test here is Kotlin/Native or Swift and the only targets declared are
  // iosArm64 + iosSimulatorArm64, so none of it can run on the Linux runner the other jobs share.
  await t.test('The iOS jobs run on macOS, beside the Android chain rather than after it', () => {
    const jobsBlock = topLevelBlock(workflowContent, 'jobs');
    for (const job of ['ios-simulator-tests', 'ios-app-tests']) {
      const jobBlock = nestedBlock(jobsBlock, job, 2);
      assert.ok(jobBlock, `Workflow must define a ${job} job`);
      // Linux cannot build iOS at all, and an Intel mac cannot build iosSimulatorArm64.
      assert.ok(/^\s+runs-on:\s*macos-/m.test(jobBlock), `${job} must run on a macOS runner`);
      // No needs: iOS starts when compile does. Chaining it behind the Android jobs would serialise
      // the two platforms and defeat the point of the lane.
      assert.ok(
        !/^\s+needs:/m.test(jobBlock),
        `${job} must not depend on another job, so it runs in parallel with the Android chain`
      );
      // The same JDK and Gradle setup as every other job, so the platforms cannot drift apart.
      assert.ok(
        /uses:\s*["']?\.\/\.github\/actions\/setup-client\b/.test(jobBlock),
        `${job} must use ./.github/actions/setup-client`
      );
      // ~/.konan holds the Kotlin/Native toolchain and the Apple platform libs: a large download
      // that every macOS job would otherwise repeat on every run.
      assert.ok(/~\/\.konan/.test(jobBlock), `${job} must cache ~/.konan`);
    }
  });

  await t.test('The simulator job runs the Kotlin/Native tests, including the long-pause SSE proof', () => {
    const jobBlock = nestedBlock(topLevelBlock(workflowContent, 'jobs'), 'ios-simulator-tests', 2);
    // Core, shared, and the commonTest Compose screen tests — the same ones jvmTest runs, executed
    // natively this time.
    assert.ok(
      /\.\/gradlew\b[^\n]*\siosSimulatorArm64Test(\s|$)/m.test(jobBlock),
      'ios-simulator-tests must run ./gradlew iosSimulatorArm64Test'
    );
    // Opt-in locally because it costs over a minute of wall clock; CI is exactly where that cost
    // belongs, because it is the only guard on timeoutIntervalForRequest in PlatformModule.ios.kt.
    assert.ok(
      /\.\/gradlew\b[^\n]*\s:shared:iosSimulatorArm64SlowSseLongPauseTest(\s|$)/m.test(jobBlock),
      'ios-simulator-tests must run the opt-in :shared:iosSimulatorArm64SlowSseLongPauseTest proof'
    );
  });

  await t.test('The app job regenerates the Xcode project and runs the Keychain XCTest bundle', () => {
    const jobBlock = nestedBlock(topLevelBlock(workflowContent, 'jobs'), 'ios-app-tests', 2);
    // project.yml is the source of truth; the .xcodeproj is generated and not committed.
    assert.ok(/xcodegen generate/.test(jobBlock), 'ios-app-tests must run xcodegen generate');
    // A bare Kotlin/Native test binary runs outside the simulator's daemon environment, where every
    // SecItem* call returns errSecNotAvailable. The Keychain answers only inside an app-hosted
    // bundle, which is why this is xcodebuild and not Gradle.
    assert.ok(/\bxcodebuild\s+test\b/.test(jobBlock), 'ios-app-tests must run xcodebuild test');
    // xcodebuild only ever builds the simulator slice, so without this nothing in CI compiles the
    // Kotlin framework for a real phone.
    assert.ok(
      /\.\/gradlew\b[^\n]*\s:iosApp:linkDebugFrameworkIosArm64(\s|$)/m.test(jobBlock),
      'ios-app-tests must link the iosArm64 device framework'
    );
    // A hardcoded device name pins the job to a simulator runtime the image may not have installed
    // that week; a UDID read from `simctl list devices available` degrades to whatever is there.
    assert.ok(
      !/-destination[^\n]*name=/.test(jobBlock),
      'ios-app-tests must not select a simulator by device name; use a UDID from simctl'
    );
    assert.ok(
      /-destination[^\n]*id=/.test(jobBlock),
      'ios-app-tests must select the simulator by UDID'
    );
  });

  await t.test('The generated Xcode project is not tracked', () => {
    // project.yml and .gitignore both say the .xcodeproj and the Info.plist are generated, but they
    // slipped into the index before those ignore rules existed. CI regenerates them with xcodegen,
    // which is what makes project.yml authoritative in fact and not just in comment.
    const tracked = execFileSync('git', ['ls-files', '--', 'apps/household-hub/client/iosApp'], { cwd: ROOT_DIR, encoding: 'utf8' });
    for (const generated of ['HouseholdHub.xcodeproj', 'HouseholdHub/Info.plist']) {
      assert.ok(
        !tracked.includes(generated),
        `apps/household-hub/client/iosApp/${generated} is generated by xcodegen and must not be tracked`
      );
    }
  });

  await t.test('gradlew is executable in the git index', () => {
    const indexEntry = execFileSync('git', ['ls-files', '-s', '--', GRADLEW_PATH], { cwd: ROOT_DIR, encoding: 'utf8' });
    assert.ok(indexEntry, `${GRADLEW_PATH} must be tracked`);
    assert.ok(indexEntry.startsWith('100755 '), `${GRADLEW_PATH} must be mode 100755 in the git index so it runs on Linux runners`);
  });
});
