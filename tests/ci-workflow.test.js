const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const ROOT_DIR = path.resolve(__dirname, '..');
const WORKFLOW_PATH = path.join(ROOT_DIR, '.github/workflows/household-hub-client.yml');
const SETUP_ACTION_PATH = path.join(ROOT_DIR, '.github/actions/setup-client/action.yml');
const GRADLEW_PATH = 'apps/household-hub/client/gradlew';

// Missing files read as empty so each check fails with its own message instead of aborting the suite.
function readIfExists(filePath) {
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, 'utf8') : '';
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

  await t.test('gradlew is executable in the git index', () => {
    const indexEntry = execFileSync('git', ['ls-files', '-s', '--', GRADLEW_PATH], { cwd: ROOT_DIR, encoding: 'utf8' });
    assert.ok(indexEntry, `${GRADLEW_PATH} must be tracked`);
    assert.ok(indexEntry.startsWith('100755 '), `${GRADLEW_PATH} must be mode 100755 in the git index so it runs on Linux runners`);
  });
});
