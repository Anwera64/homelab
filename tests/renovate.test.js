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

  await t.test('reads the compose files and nothing it was not asked to', () => {
    assert.ok(asList(config.enabledManagers).includes('docker-compose'));
  });

  await t.test('opens its PRs once a week, on releases at least three days old', () => {
    assert.equal(config.timezone, 'Europe/Madrid');
    assert.deepEqual(config.schedule, ['before 6am on monday']);
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
    // In the weekly group it would hold back every other bump: it gets a PR of its own.
    assert.equal(rules[index].groupName, 'Jellyfin');
  });

  await t.test('in the desktop stack, only Alloy: it must match the server\'s, and the rest is on Watchtower', () => {
    const [rule] = ruleFor((r) => asList(r.matchFileNames).includes('docker-compose.yml') && r.enabled === false);
    assert.ok(rule, 'a rule must turn the root docker-compose.yml off');
    assert.deepEqual(rule.matchPackageNames, ['!grafana/alloy']);
  });

  await t.test('reads linuxserver tags by their own scheme, never an arch or version- variant', () => {
    // Renovate's regex versioning counts the revision only when there is a build, so where the app
    // version has three parts the -lsNNN image build has to be the build, or rebuilds go unseen.
    const schemes = ruleFor((r) => asList(r.matchPackageNames).some((n) => n.startsWith('lscr.io/linuxserver/')));
    assert.equal(schemes.length, 2, 'one scheme for four-part app versions, one for three-part');
    const counted = (groups) => groups.build !== undefined;
    const lsNumber = (tag) => tag.match(/-ls(\d+)$/)[1];
    const samples = { 4: ['6.4.4.10685-ls319', '2.6.5.5623-ls162', '4.0.20.3014-ls326'], 3: ['5.1.4-r3-ls453', 'v1.6.2-ls366', '3.0.6-ls104'] };
    const variants = ['latest', 'nightly', 'arm32v7-6.4.4.10685-ls319', 'amd64-5.1.4-r3-ls453', 'version-5.1.4-r3', 'develop-4.0.20.3014-ls326'];
    for (const rule of schemes) {
      assert.match(rule.versioning, /^regex:/, 'linuxserver images need a regex versioning');
      const scheme = versioningRegex(rule);
      const parts = scheme.source.includes('-r') ? 3 : 4;
      for (const tag of samples[parts]) {
        const groups = tag.match(scheme)?.groups;
        assert.ok(groups, `${tag} is a ${parts}-part linuxserver release`);
        assert.ok(counted(groups), `${tag}: the image build must be counted`);
        assert.ok([groups.build, groups.revision].includes(lsNumber(tag)), `${tag}: -ls${lsNumber(tag)} must be compared`);
      }
      for (const tag of variants) {
        assert.doesNotMatch(tag, scheme, `${tag} must never be picked`);
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

  await t.test('every image on the server and the Pi has a version Renovate can compare', () => {
    for (const image of [...serverImages, ...piImages]) {
      assert.match(image, /:v?\d/, `${image} must carry a version tag`);
    }
  });
});
