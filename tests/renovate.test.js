const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const CONFIG_PATH = path.join(ROOT_DIR, 'renovate.json');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');
const parse = (text) => {
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
};

const imagesOf = (compose) => [...compose.matchAll(/^    image:\s*(\S+)\s*$/gm)].map((m) => m[1]);
const asList = (value) => (value === undefined ? [] : [].concat(value));
// The regex of a "regex:..." versioning, as a JavaScript RegExp (the named groups read the same).
const versioningRegex = (rule) => new RegExp(rule.versioning.replace(/^regex:/, ''));
// Whether a matchPackageNames list picks a name: "/regex/" entries only, which is what these rules use.
const picks = (patterns, name) => asList(patterns).some((p) => /^\/.*\/$/.test(p) && new RegExp(p.slice(1, -1)).test(name));

test('Renovate: pinned images and client dependencies, bumped in PRs', async (t) => {
  const config = parse(read(CONFIG_PATH));
  const rules = config.packageRules || [];
  const ruleFor = (pred) => rules.filter(pred);
  const serverImages = imagesOf(read(path.join(ROOT_DIR, 'hosts/server/docker-compose.yml')));
  const piImages = imagesOf(read(path.join(ROOT_DIR, 'hosts/pi/docker-compose.yml')));

  await t.test('renovate.json is valid JSON with the schema', () => {
    assert.ok(fs.existsSync(CONFIG_PATH), 'renovate.json must exist at the repo root');
    assert.doesNotThrow(() => JSON.parse(read(CONFIG_PATH)));
    assert.equal(config.$schema, 'https://docs.renovatebot.com/renovate-schema.json');
    assert.ok(asList(config.extends).includes('config:recommended'));
  });

  await t.test('reads the compose files, the client\'s Gradle build and the hub\'s requirements and Dockerfile, and nothing it was not asked to', () => {
    assert.deepEqual([...config.enabledManagers].sort(), ['docker-compose', 'dockerfile', 'gradle', 'gradle-wrapper', 'pip-compile']);
    // The hub's lock files, which name their .in files in their headers. Renovate edits the .in
    // and regenerates the lock with uv; pip_requirements on its own would edit the lock by hand.
    const patterns = config['pip-compile'].managerFilePatterns.map((p) => new RegExp(p.slice(1, -1)));
    const matched = (file) => patterns.some((p) => p.test(file));
    assert.ok(matched('apps/household-hub/backend/requirements.txt'));
    assert.ok(matched('apps/household-hub/backend/requirements-dev.txt'));
    assert.ok(!matched('apps/household-hub/backend/requirements.in'));
    for (const lock of ['requirements.txt', 'requirements-dev.txt']) {
      const header = read(path.join(ROOT_DIR, 'apps/household-hub/backend', lock)).split('\n')[1];
      // The only form Renovate parses: uv, every option as --name=value.
      assert.match(header, /^#\s+uv pip compile \S+\.in --output-file=\S+ --python-version=3\.12 --universal$/, `${lock} header`);
    }
  });

  await t.test('opens its PRs whenever a bump is ready, on releases at least three days old', () => {
    // The lock file refresh still has a schedule, read in this timezone.
    assert.equal(config.timezone, 'Europe/Madrid');
    assert.equal(config.schedule, undefined, 'no schedule: bumps open at any time');
    // A weekly gate must not come back through a rule either.
    assert.deepEqual(ruleFor((r) => r.schedule !== undefined), [], 'no rule sets a schedule');
    // A broken release is usually pulled or fixed by then.
    assert.equal(config.minimumReleaseAge, '3 days');
    // Image registries often publish no release date. Without one a bump would wait forever,
    // so the wait applies where a date is known (Maven Central, the Gradle plugin portal).
    assert.equal(config.minimumReleaseAgeBehaviour, 'timestamp-optional');
  });

  await t.test('groups the minor and patch image bumps into one PR that merges itself once CI is green', () => {
    const [group] = ruleFor((r) => asList(r.matchManagers).includes('docker-compose') && r.groupName);
    assert.ok(group, 'a docker-compose group rule must exist');
    assert.deepEqual([...group.matchUpdateTypes].sort(), ['minor', 'patch']);
    assert.equal(group.automerge, true);
    // Renovate merges only after it has seen every check pass, with or without branch protection.
    assert.equal(config.platformAutomerge, false);
    assert.equal(config.automergeType, 'pr');
  });

  await t.test('never merges a major bump by itself', () => {
    assert.notEqual(config.automerge, true, 'automerge is per rule, not global');
    for (const rule of rules.filter((r) => r.automerge === true)) {
      const types = asList(rule.matchUpdateTypes);
      assert.ok(types.length > 0 && !types.includes('major'), `rule "${rule.description}" must not auto-merge majors`);
    }
  });

  await t.test('Postgres never gets a major bump: that needs a dump and restore by hand', () => {
    const [rule] = ruleFor((r) => asList(r.matchPackageNames).includes('postgres') && r.enabled === false);
    assert.ok(rule, 'a rule must disable Postgres majors');
    assert.deepEqual(rule.matchUpdateTypes, ['major']);
  });

  await t.test('Jellyfin bumps always wait for a human: its upgrades migrate the library database', () => {
    const index = rules.findIndex((r) => asList(r.matchPackageNames).includes('jellyfin/jellyfin') && r.automerge === false);
    assert.ok(index >= 0, 'a rule must turn auto-merge off for Jellyfin');
    const groupIndex = rules.findIndex((r) => asList(r.matchManagers).includes('docker-compose') && r.groupName);
    assert.ok(index > groupIndex, 'the Jellyfin rule must come after the group rule, which it overrides');
    // In the image group it would hold back every other bump: it gets a PR of its own.
    assert.equal(rules[index].groupName, 'Jellyfin');
  });

  await t.test('the desktop stack is read like the others: its Ollama and Alloy are bumped with the server\'s', () => {
    // Watchtower is gone from the desktop, so nothing may switch its compose file off.
    const off = ruleFor((r) => r.enabled === false && asList(r.matchFileNames).some((f) => /docker-compose\.yml$/.test(f)));
    assert.deepEqual(off, [], 'no compose file is switched off');
    const desktopImages = imagesOf(read(path.join(ROOT_DIR, 'hosts/desktop/docker-compose.yml')));
    assert.equal(desktopImages.length, 2);
    // Same image and same version on both machines: one grouped PR moves both.
    for (const image of desktopImages) {
      assert.ok(serverImages.includes(image), `${image} must be the version the server runs`);
    }
  });

  await t.test('reads linuxserver tags by their own scheme, never an arch or version- variant', () => {
    // Renovate's regex versioning counts the revision only when there is a build. So the -lsNNN
    // image build is the revision where the app version has a build of its own (four parts), and
    // the build everywhere else, or image rebuilds would go unseen.
    const expected = [
      {
        images: ['lscr.io/linuxserver/prowlarr', 'lscr.io/linuxserver/radarr', 'lscr.io/linuxserver/sonarr'],
        tags: ['6.4.4.10685-ls319', '2.6.5.5623-ls162', '4.0.20.3014-ls326'],
        never: ['6.4.4-ls319'],
      },
      { images: ['lscr.io/linuxserver/bazarr'], tags: ['v1.6.2-ls366', 'v1.6.1-ls364'], never: ['1.6.2.1-ls366'] },
      {
        // The current line names its libtorrent version. 5.1.4-r3-ls453 is an old line and
        // 5.2.4-libtorrentv1 another build: neither may ever be offered.
        images: ['lscr.io/linuxserver/qbittorrent'],
        tags: ['5.2.4_v2.0.15-ls479', '5.2.3_v2.0.14-ls477'],
        never: ['5.1.4-r3-ls453', '5.2.4-libtorrentv1', '5.2.4', 'libtorrentv1-version-release-5.2.0_v1.2.20'],
      },
    ];
    const variants = ['latest', 'nightly', 'arm32v7-6.4.4.10685-ls319', 'amd64-5.2.4_v2.0.15-ls479', 'version-5.2.4_v2.0.15', 'develop-4.0.20.3014-ls326'];
    const schemes = ruleFor((r) => asList(r.matchPackageNames).some((n) => n.startsWith('lscr.io/linuxserver/')));
    assert.equal(schemes.length, expected.length);
    for (const { images, tags, never } of expected) {
      const [rule] = schemes.filter((r) => [...r.matchPackageNames].sort().join() === [...images].sort().join());
      assert.ok(rule && /^regex:/.test(rule.versioning), `${images.join(', ')} need a regex versioning of their own`);
      const scheme = versioningRegex(rule);
      for (const tag of tags) {
        const groups = tag.match(scheme)?.groups;
        assert.ok(groups, `${tag} is a release of ${images[0]}`);
        const ls = tag.match(/-ls(\d+)$/)[1];
        assert.ok(groups.build !== undefined && [groups.build, groups.revision].includes(ls), `${tag}: -ls${ls} must be compared`);
      }
      for (const tag of [...never, ...variants]) {
        assert.doesNotMatch(tag, scheme, `${tag} must never be picked for ${images[0]}`);
      }
    }
    const linuxserver = serverImages.filter((image) => image.startsWith('lscr.io/linuxserver/'));
    assert.equal(linuxserver.length, 5);
    for (const image of linuxserver) {
      const [name, tag] = image.split(':');
      const matching = schemes.filter((r) => r.matchPackageNames.includes(name));
      assert.equal(matching.length, 1, `${name} must be in exactly one linuxserver scheme`);
      assert.match(tag, versioningRegex(matching[0]), `${image} must be readable by its scheme`);
    }
  });

  await t.test('reads SearXNG\'s dated tags, which end in a commit hash', () => {
    const [rule] = ruleFor((r) => asList(r.matchPackageNames).includes('searxng/searxng'));
    assert.ok(rule && /^regex:/.test(rule.versioning), 'SearXNG needs a regex versioning');
    const scheme = versioningRegex(rule);
    const tag = serverImages.find((image) => image.startsWith('searxng/searxng:')).split(':')[1];
    assert.match(tag, scheme);
    assert.doesNotMatch('latest', scheme);
  });

  await t.test('groups the client\'s minor and patch bumps into a PR of their own, merged once CI is green', () => {
    const [group] = ruleFor((r) => r.groupName === 'client dependencies');
    assert.ok(group, 'a client dependencies group must exist');
    assert.deepEqual([...group.matchManagers].sort(), ['gradle', 'gradle-wrapper']);
    assert.deepEqual(group.matchFileNames, ['apps/household-hub/client/**']);
    assert.deepEqual([...group.matchUpdateTypes].sort(), ['minor', 'patch']);
    assert.equal(group.automerge, true);
  });

  await t.test('Kotlin, Compose Multiplatform and AGP move together: a Kotlin release often needs its Compose', () => {
    const index = rules.findIndex((r) => r.groupName === 'Kotlin, Compose and AGP');
    assert.ok(index >= 0, 'a toolchain group must exist');
    const toolchain = rules[index];
    assert.ok(index > rules.findIndex((r) => r.groupName === 'client dependencies'), 'it must override the client group');
    assert.equal(toolchain.matchUpdateTypes, undefined, 'majors move together too');
    // Renovate's package names for the plugins in libs.versions.toml, and AGP's.
    for (const name of [
      'org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin',
      'org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin',
      'org.jetbrains.compose:org.jetbrains.compose.gradle.plugin',
      'com.android.application:com.android.application.gradle.plugin',
      'com.android.kotlin.multiplatform.library:com.android.kotlin.multiplatform.library.gradle.plugin',
    ]) {
      assert.ok(picks(toolchain.matchPackageNames, name), `${name} is in the toolchain group`);
    }
    for (const name of ['org.jetbrains.kotlinx:kotlinx-coroutines-core', 'io.ktor:ktor-client-core', 'androidx.activity:activity-compose']) {
      assert.ok(!picks(toolchain.matchPackageNames, name), `${name} is not toolchain`);
    }
  });

  await t.test('groups the hub\'s minor and patch bumps into one PR, merged once the backend CI is green', () => {
    const index = rules.findIndex((r) => r.groupName === 'hub dependencies');
    assert.ok(index >= 0, 'a hub dependencies group must exist');
    const group = rules[index];
    assert.deepEqual([...group.matchManagers].sort(), ['dockerfile', 'pip-compile']);
    assert.deepEqual(group.matchFileNames, ['apps/household-hub/backend/**']);
    assert.deepEqual([...group.matchUpdateTypes].sort(), ['minor', 'patch']);
    assert.equal(group.automerge, true);
    // The backend CI is what that green means.
    assert.ok(fs.existsSync(path.join(ROOT_DIR, '.github/workflows/household-hub-backend.yml')));
  });

  await t.test('refreshes the indirect packages of the hub once a week, merged once the backend CI is green', () => {
    // Nobody names Starlette or anyio, so no bump PR ever would: the weekly refresh regenerates the locks.
    assert.equal(config.lockFileMaintenance.enabled, true);
    assert.equal(config.lockFileMaintenance.automerge, true);
    assert.deepEqual(config.lockFileMaintenance.schedule, ['before 6am on monday']);
  });

  await t.test('a new Python release (3.12 to 3.13) gets a PR of its own and waits for a human', () => {
    const index = rules.findIndex((r) => r.groupName === 'Python');
    assert.ok(index >= 0, 'a Python rule must exist');
    const python = rules[index];
    assert.deepEqual(python.matchPackageNames, ['python']);
    assert.deepEqual(python.matchUpdateTypes, ['minor']);
    assert.equal(python.automerge, false);
    // Without this, a pending 3.14 hides the 3.12 patch releases, security fixes included.
    const [patches] = ruleFor((r) => asList(r.matchPackageNames).includes('python') && r.separateMinorPatch === true);
    assert.ok(patches, 'Python patch releases must still come, in the hub PR');
    assert.ok(index > rules.findIndex((r) => r.groupName === 'hub dependencies'), 'it must override the hub group');
  });

  await t.test('a hub release reaches prod through a PR of its own, merged by a human, as soon as it exists', () => {
    const image = 'ghcr.io/anwera64/household-hub';
    assert.ok(serverImages.some((i) => i.startsWith(`${image}:`)), 'prod runs the released image');
    const index = rules.findIndex((r) => asList(r.matchPackageNames).includes(image));
    assert.ok(index >= 0, 'a rule must match the prod image');
    const rule = rules[index];
    assert.deepEqual(rule.matchPackageNames, [image], 'that image alone');
    assert.equal(rule.groupName, 'Hub prod', 'out of the container images group');
    assert.equal(rule.automerge, false, 'putting a release on prod is a human\'s merge');
    // A release is ours and already tested: no three-day wait for it to be pulled upstream.
    assert.equal(rule.minimumReleaseAge, '0 days');
    const groupIndex = rules.findIndex((r) => asList(r.matchManagers).includes('docker-compose') && r.groupName === 'container images');
    assert.ok(index > groupIndex, 'the rule must come after the group rule, which it overrides');
  });

  await t.test('every image on the server and the Pi has a version Renovate can compare', () => {
    for (const image of [...serverImages, ...piImages]) {
      assert.match(image, /:v?\d/, `${image} must carry a version tag`);
    }
  });
});

test('A security fix for the hub releases a patch, so it can reach prod before the next slice', async (t) => {
  const config = parse(read(CONFIG_PATH));
  const alerts = config.vulnerabilityAlerts || {};
  const compose = read(path.join(ROOT_DIR, 'hosts/server/docker-compose.yml'));
  const renovate = (compose.match(/^  renovate:\n([\s\S]*?)(?=^  [a-z0-9_-]+:\n|^[a-z]|(?![\s\S]))/m) || [])[1] || '';
  const envExample = read(path.join(ROOT_DIR, 'hosts/server/.env.example'));
  const command = 'bash tools/bump-hub-patch.sh';

  await t.test('Renovate\'s security PRs bump the hub\'s patch version, once per PR, and wait for a human', () => {
    assert.deepEqual(asList(alerts.labels), ['security']);
    assert.equal(alerts.automerge, false, 'a security release is a human\'s merge');
    const tasks = alerts.postUpgradeTasks || {};
    assert.deepEqual(tasks.commands, [command]);
    assert.deepEqual(tasks.fileFilters, ['apps/household-hub/backend/app/__init__.py'], 'the bump commits nothing else');
    assert.equal(tasks.executionMode, 'branch', 'one bump per PR, however many packages it fixes');
  });

  await t.test('the server\'s Renovate may run that command and nothing else', () => {
    const allowed = renovate.match(/^      - RENOVATE_ALLOWED_COMMANDS=(.*)$/m);
    assert.ok(allowed, 'the renovate service must set RENOVATE_ALLOWED_COMMANDS');
    // A backslash reaches the container halved, which is no longer JSON: [.] spells the dot.
    assert.doesNotMatch(allowed[1], /\\/, 'no backslash in the pattern');
    // Compose reads $$ as a literal $.
    const patterns = JSON.parse(allowed[1].replace(/\$\$/g, '$'));
    assert.equal(patterns.length, 1);
    const regex = new RegExp(patterns[0]);
    assert.ok(regex.test(command));
    for (const other of [`${command}; rm -rf /`, 'bash tools/other.sh', `${command} && curl x`]) {
      assert.ok(!regex.test(other), `"${other}" must not be allowed`);
    }
  });

  await t.test('Renovate reads the private prod image with a token of its own, empty until set', () => {
    // GitHub Packages takes classic tokens only; RENOVATE_TOKEN is fine-grained.
    assert.ok(renovate.includes('- RENOVATE_HOST_RULES=[{"matchHost":"ghcr.io","hostType":"docker","username":"Anwera64","password":"${RENOVATE_GHCR_TOKEN:-}"}]'));
    assert.match(envExample, /^RENOVATE_GHCR_TOKEN=$/m);
    assert.match(envExample, /read:packages/);
  });
});

test('tools/bump-hub-patch.sh', async (t) => {
  const os = require('node:os');
  const { spawnSync } = require('node:child_process');
  const SCRIPT = path.join(ROOT_DIR, 'tools/bump-hub-patch.sh');
  const probe = spawnSync('bash', ['-c', 'command -v git'], { encoding: 'utf8' });
  const canRun = process.platform !== 'win32' && !probe.error && probe.status === 0;
  const skip = !canRun && 'needs bash and git';

  // A checkout with the hub's version file and a lock file, as Renovate leaves it before committing.
  const checkout = (version) => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'bump-'));
    const git = (...args) => spawnSync('git', ['-c', 'user.name=t', '-c', 'user.email=t@t', ...args], { cwd: dir, encoding: 'utf8' });
    fs.mkdirSync(path.join(dir, 'apps/household-hub/backend/app'), { recursive: true });
    fs.mkdirSync(path.join(dir, 'apps/household-hub/client'), { recursive: true });
    fs.writeFileSync(path.join(dir, 'apps/household-hub/backend/app/__init__.py'), `"""Household Hub Backend Application."""\n__version__ = "${version}"\n`);
    fs.writeFileSync(path.join(dir, 'apps/household-hub/backend/requirements.txt'), 'fastapi==1.0.0\n');
    fs.writeFileSync(path.join(dir, 'apps/household-hub/client/gradle.properties'), 'app.version=0.5.0\n');
    git('init', '-q');
    git('add', '-A');
    git('commit', '-q', '-m', 'base');
    return dir;
  };
  const versionIn = (dir) => read(path.join(dir, 'apps/household-hub/backend/app/__init__.py')).match(/__version__ = "([^"]+)"/)[1];
  const run = (dir) => spawnSync('bash', [SCRIPT], { cwd: dir, encoding: 'utf8' });

  await t.test('GIVEN a fixed hub package WHEN it runs THEN the patch goes up by one', { skip }, () => {
    const dir = checkout('0.5.0');
    fs.writeFileSync(path.join(dir, 'apps/household-hub/backend/requirements.txt'), 'fastapi==1.0.1\n');
    const r = run(dir);
    assert.equal(r.status, 0, r.stderr);
    assert.equal(versionIn(dir), '0.5.1');
  });

  await t.test('GIVEN a two-digit patch WHEN it runs THEN it counts on, not by text', { skip }, () => {
    const dir = checkout('0.5.9');
    fs.writeFileSync(path.join(dir, 'apps/household-hub/backend/requirements.txt'), 'fastapi==1.0.1\n');
    assert.equal(run(dir).status, 0);
    assert.equal(versionIn(dir), '0.5.10');
  });

  await t.test('GIVEN a security fix outside the hub WHEN it runs THEN the hub version stays', { skip }, () => {
    // Renovate's security settings apply to every alert, the client's included.
    const dir = checkout('0.5.0');
    fs.writeFileSync(path.join(dir, 'apps/household-hub/client/gradle.properties'), 'app.version=0.5.0\n# fixed\n');
    const r = run(dir);
    assert.equal(r.status, 0, r.stderr);
    assert.equal(versionIn(dir), '0.5.0');
  });

  await t.test('GIVEN no readable version WHEN it runs THEN it fails and changes nothing', { skip }, () => {
    const dir = checkout('0.5.0');
    fs.writeFileSync(path.join(dir, 'apps/household-hub/backend/app/__init__.py'), '__version__ = "dev"\n');
    fs.writeFileSync(path.join(dir, 'apps/household-hub/backend/requirements.txt'), 'fastapi==1.0.1\n');
    assert.notEqual(run(dir).status, 0);
    assert.match(read(path.join(dir, 'apps/household-hub/backend/app/__init__.py')), /"dev"/);
  });
});
