const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const HUB_RELEASE_PATH = path.join(ROOT_DIR, '.github/workflows/hub-release.yml');
const RELEASE_TAGS_PATH = path.join(ROOT_DIR, '.github/workflows/release-tags.yml');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

// Text of a top-level YAML key's block, up to the next top-level key.
const topLevelBlock = (content, key) => {
  const match = content.match(new RegExp(`^${key}:[^\\n]*\\n((?:[ \\t]+[^\\n]*\\n|[ \\t]*\\n)*)`, 'm'));
  return match ? match[1] : '';
};

const usesRefs = (workflow) => [...workflow.matchAll(/^\s*(?:-\s*)?uses:\s*["']?([^\s"'#]+)/gm)].map((m) => m[1]);

test('Hub release workflow', async (t) => {
  const workflow = read(HUB_RELEASE_PATH);

  await t.test('runs on a pushed hub-v* tag, or when the tagging workflow calls it', () => {
    assert.ok(fs.existsSync(HUB_RELEASE_PATH), '.github/workflows/hub-release.yml must exist');
    const on = topLevelBlock(workflow, 'on');
    assert.match(on, /^  push:\n    tags: \['hub-v\*'\]\n/m);
    assert.match(on, /^  workflow_call:\n    inputs:\n      tag:\n/m, 'the caller names the tag it just pushed');
    assert.doesNotMatch(on, /pull_request/, 'a pull request never publishes');
  });

  await t.test('may write the release and the package, and nothing else', () => {
    const permissions = topLevelBlock(workflow, 'permissions').trim().split('\n').map((l) => l.trim()).sort();
    assert.deepEqual(permissions, ['contents: write', 'packages: write']);
  });

  await t.test('refuses a tag that is not the version in the code', () => {
    assert.match(workflow, /TAG: \$\{\{ inputs\.tag \|\| github\.ref_name \}\}/);
    assert.match(workflow, /ref: \$\{\{ env\.TAG \}\}/, 'it builds the tagged commit, not the branch tip');
    assert.match(workflow, /sed -n 's\/\^__version__ = "\\\(\.\*\\\)"\$\/\\1\/p' app\/__init__\.py/);
    assert.match(workflow, /\[ "hub-v\$version" = "\$TAG" \]/);
  });

  await t.test('tests before it publishes, and publishes only the version', () => {
    const pytest = workflow.indexOf('python -m pytest tests');
    const push = workflow.indexOf('docker push');
    assert.ok(pytest >= 0 && push >= 0, 'pytest and docker push must both run');
    assert.ok(pytest < push, 'the tests run before the push');
    assert.match(workflow, /IMAGE: ghcr\.io\/anwera64\/household-hub$/m);
    assert.match(workflow, /docker build --platform linux\/amd64 [^\n]*--tag "\$IMAGE:\$version"/, 'the server is x86');
    assert.match(workflow, /docker push "\$IMAGE:\$version"/);
    assert.doesNotMatch(workflow, /:latest\b/, 'prod pins a version; there is no moving tag');
    // The package is linked to this repository, so it stays private with it.
    assert.match(workflow, /--label "org\.opencontainers\.image\.source=https:\/\/github\.com\/\$\{\{ github\.repository \}\}"/);
  });

  await t.test('fails the release when anyone could pull the image', () => {
    // The first push came out public (#134), whatever the docs say about new packages.
    const push = workflow.indexOf('docker push');
    const check = workflow.indexOf('name: Check the image is private');
    const notes = workflow.indexOf('gh release create');
    assert.ok(check > push && check < notes, 'after the push, before the release is written');
    assert.match(workflow, /curl -s -o \/dev\/null -w '%\{http_code\}' "https:\/\/ghcr\.io\/token\?scope=repository:anwera64\/household-hub:pull"/, 'an anonymous pull token');
    // 401 private, 403 unknown; 200 is public. Anything unexpected fails rather than passes.
    assert.match(workflow, /case "\$status" in\n\s+401\|403\) ;;/);
  });

  await t.test('writes release notes and attaches nothing', () => {
    assert.match(workflow, /^\s*gh release create "\$TAG" --verify-tag --generate-notes --title "\$TAG"$/m);
    assert.doesNotMatch(workflow, /upload-artifact/, 'no build output leaves the runner but the private image');
  });

  await t.test('pins every action to a full commit SHA', () => {
    const refs = usesRefs(workflow);
    assert.ok(refs.length >= 2, 'checkout and setup-python');
    for (const ref of refs) {
      assert.match(ref, /@[0-9a-f]{40}$/, `"${ref}" must be pinned to a 40-character commit SHA`);
    }
  });
});

// Text of a job, up to the next job.
const jobBlock = (workflow, name) => {
  const match = workflow.match(new RegExp(`^  ${name}:\n((?:    [^\n]*\n|[ \t]*\n)*)`, 'm'));
  return match ? match[1] : '';
};

test('Merging a version bump tags and releases it', async (t) => {
  const workflow = read(RELEASE_TAGS_PATH);

  await t.test('runs on master, only when a version or this workflow changed', () => {
    assert.ok(fs.existsSync(RELEASE_TAGS_PATH), '.github/workflows/release-tags.yml must exist');
    const on = topLevelBlock(workflow, 'on');
    const push = on.match(/^  push:\n((?:    [^\n]*\n)*)/m);
    assert.ok(push, 'must run on push');
    assert.match(push[1], /branches: \[master\]/);
    const paths = [...push[1].matchAll(/^      - (\S+)$/gm)].map((m) => m[1]).sort();
    assert.deepEqual(paths, [
      '.github/workflows/release-tags.yml',
      'apps/household-hub/backend/app/__init__.py',
      'apps/household-hub/client/gradle.properties',
    ]);
    assert.doesNotMatch(on, /pull_request|tags:/, 'only a merge tags');
    assert.match(topLevelBlock(workflow, 'permissions'), /^  contents: read$/m, 'jobs ask for more themselves');
  });

  await t.test('tags a version once, and leaves a tagged one alone', () => {
    const tag = jobBlock(workflow, 'tag');
    assert.ok(tag, 'a tag job');
    assert.match(tag, /permissions:\n      contents: write\n/);
    assert.match(tag, /sed -n 's\/\^__version__ = "\\\(\.\*\\\)"\$\/\\1\/p' apps\/household-hub\/backend\/app\/__init__\.py/);
    assert.match(tag, /git ls-remote --exit-code --tags origin "refs\/tags\/\$tag"/, 'an existing tag is never moved');
    assert.match(tag, /git tag "\$tag" "\$GITHUB_SHA"/);
    assert.match(tag, /git push origin "refs\/tags\/\$tag"/);
    assert.match(tag, /echo "hub=\$tag" >> "\$GITHUB_OUTPUT"/, 'only a new tag is handed on');
    assert.match(tag, /outputs:\n      hub: \$\{\{ steps\.hub\.outputs\.hub \}\}/);
  });

  await t.test('calls the release itself: a tag pushed with GITHUB_TOKEN starts no workflow', () => {
    const hub = jobBlock(workflow, 'hub');
    assert.ok(hub, 'a hub job');
    assert.match(hub, /needs: tag/);
    assert.match(hub, /if: needs\.tag\.outputs\.hub != ''/);
    assert.match(hub, /uses: \.\/\.github\/workflows\/hub-release\.yml/);
    assert.match(hub, /tag: \$\{\{ needs\.tag\.outputs\.hub \}\}/);
    const permissions = hub.match(/permissions:\n((?:      [^\n]*\n)+)/);
    assert.ok(permissions, 'the caller grants what the release needs');
    assert.deepEqual(permissions[1].trim().split('\n').map((l) => l.trim()).sort(), ['contents: write', 'packages: write']);
  });

  await t.test('pins every action to a full commit SHA', () => {
    const refs = usesRefs(workflow).filter((ref) => !ref.startsWith('./'));
    assert.ok(refs.length >= 1, 'checkout');
    for (const ref of refs) {
      assert.match(ref, /@[0-9a-f]{40}$/, `"${ref}" must be pinned to a 40-character commit SHA`);
    }
  });
});

test('App release workflow', async (t) => {
  const APP_RELEASE_PATH = path.join(ROOT_DIR, '.github/workflows/app-release.yml');
  const workflow = read(APP_RELEASE_PATH);
  const job = (name) => jobBlock(workflow, name);

  await t.test('runs on a pushed app-v* tag, or when the tagging workflow calls it', () => {
    assert.ok(fs.existsSync(APP_RELEASE_PATH), '.github/workflows/app-release.yml must exist');
    const on = topLevelBlock(workflow, 'on');
    assert.match(on, /^  push:\n    tags: \['app-v\*'\]\n/m);
    assert.match(on, /^  workflow_call:\n    inputs:\n      tag:\n/m);
    assert.doesNotMatch(on, /pull_request/, 'no pull request reaches the signing keys');
    assert.deepEqual(topLevelBlock(workflow, 'permissions').trim().split('\n').map((l) => l.trim()), ['contents: write']);
  });

  await t.test('refuses a tag that is not app.version, and tests before anything is built for release', () => {
    const check = job('check');
    assert.ok(check, 'a check job');
    assert.match(check, /sed -n 's\/\^app\\.version=\/\/p' apps\/household-hub\/client\/gradle\.properties/);
    assert.match(check, /\[ "app-v\$version" = "\$TAG" \]/);
    assert.match(check, /\.\/gradlew jvmTest/);
    for (const name of ['android', 'ios']) {
      assert.match(job(name), /needs: check/, `${name} waits for the check`);
    }
  });

  await t.test('only the upload jobs see the keys, through the release environment', () => {
    assert.doesNotMatch(job('check'), /secrets\./, 'the check job needs no secret');
    for (const name of ['android', 'ios']) {
      assert.match(job(name), /^    environment: release$/m, `${name} runs in the release environment`);
    }
  });

  await t.test('Android: a signed release build to Firebase App Distribution, nothing kept', () => {
    const android = job('android');
    assert.match(android, /\.\/gradlew :androidApp:assembleRelease/);
    for (const name of ['HUB_KEYSTORE_PASSWORD', 'HUB_KEY_ALIAS', 'HUB_KEY_PASSWORD']) {
      assert.ok(android.includes(`${name}: \${{ secrets.${name} }}`), `${name} comes from the release environment`);
    }
    assert.match(android, /base64 --decode > "\$RUNNER_TEMP\/release\.keystore"/, 'the keystore lives outside the checkout');
    assert.match(android, /npx --yes firebase-tools@\d+\.\d+\.\d+ appdistribution:distribute /, 'a pinned Firebase CLI');
    assert.match(android, /--app "\$\{\{ secrets\.FIREBASE_ANDROID_APP_ID \}\}"/);
    assert.match(android, /--groups household/);
    assert.match(android, /GOOGLE_APPLICATION_CREDENTIALS: \$\{\{ runner\.temp \}\}\/firebase\.json/);
  });

  await t.test('iOS: an archive signed with the App Store Connect key, uploaded to TestFlight', () => {
    const ios = job('ios');
    assert.match(ios, /runs-on: macos-26/, 'the image the client CI builds iOS on');
    assert.match(ios, /xcodegen generate/);
    assert.match(ios, /xcodebuild archive[\s\S]*?-configuration Release/);
    assert.match(ios, /xcodebuild -exportArchive[\s\S]*?-exportOptionsPlist ExportOptions\.plist/);
    for (const flag of ['-allowProvisioningUpdates', '-authenticationKeyPath', '-authenticationKeyID', '-authenticationKeyIssuerID']) {
      assert.ok(ios.split(flag).length - 1 >= 2, `archive and export both pass ${flag}`);
    }
    assert.match(ios, /DEVELOPMENT_TEAM="\$\{\{ secrets\.APPLE_TEAM_ID \}\}"/);
    assert.match(ios, /MARKETING_VERSION="\$version" CURRENT_PROJECT_VERSION="\$code"|MARKETING_VERSION/, 'built as the tagged version');
    const options = read(path.join(ROOT_DIR, 'apps/household-hub/client/iosApp/ExportOptions.plist'));
    assert.match(options, /<key>method<\/key>\s*<string>app-store-connect<\/string>/);
    assert.match(options, /<key>destination<\/key>\s*<string>upload<\/string>/);
    assert.match(options, /<key>signingStyle<\/key>\s*<string>automatic<\/string>/);
  });

  await t.test('writes release notes only once both stores have the build, and keeps no build', () => {
    const notes = job('notes');
    assert.match(notes, /needs: \[android, ios\]/);
    assert.match(notes, /^\s*gh release create "\$TAG" --verify-tag --generate-notes --title "\$TAG"$/m);
    assert.doesNotMatch(workflow, /upload-artifact|\.apk"? *\n.*gh release|gh release upload/, 'no APK or IPA leaves the runners');
  });

  await t.test('pins every action to a full commit SHA', () => {
    for (const ref of usesRefs(workflow).filter((r) => !r.startsWith('./'))) {
      assert.match(ref, /@[0-9a-f]{40}$/, `"${ref}" must be pinned to a 40-character commit SHA`);
    }
  });
});

test('Merging an app version bump releases it too', async (t) => {
  const workflow = read(RELEASE_TAGS_PATH);

  await t.test('tags app.version once, beside the hub version', () => {
    const tag = jobBlock(workflow, 'tag');
    assert.match(tag, /sed -n 's\/\^app\\.version=\/\/p' apps\/household-hub\/client\/gradle\.properties/);
    assert.match(tag, /tag="app-v\$version"/);
    assert.match(tag, /echo "app=\$tag" >> "\$GITHUB_OUTPUT"/);
    assert.match(tag, /outputs:\n      hub: \$\{\{ steps\.hub\.outputs\.hub \}\}\n      app: \$\{\{ steps\.app\.outputs\.app \}\}/);
  });

  await t.test('calls the app release with the keys of the release environment', () => {
    const app = jobBlock(workflow, 'app');
    assert.match(app, /needs: tag/);
    assert.match(app, /if: needs\.tag\.outputs\.app != ''/);
    assert.match(app, /uses: \.\/\.github\/workflows\/app-release\.yml/);
    assert.match(app, /tag: \$\{\{ needs\.tag\.outputs\.app \}\}/);
    assert.match(app, /secrets: inherit/);
    assert.match(app, /permissions:\n      contents: write\n/);
  });
});
