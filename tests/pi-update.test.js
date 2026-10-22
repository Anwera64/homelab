const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const PI_DIR = path.join(ROOT_DIR, 'hosts/pi');
const SYSTEM_DIR = path.join(PI_DIR, 'system');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

test('Pi nightly update: units', async (t) => {
  const unit = read(path.join(SYSTEM_DIR, 'pi-update.service'));
  const timer = read(path.join(SYSTEM_DIR, 'pi-update.timer'));
  const bootstrap = read(path.join(PI_DIR, 'bootstrap.sh'));

  await t.test('runs at 04:30, after the server\'s update, and catches up on a missed night', () => {
    assert.match(timer, /^OnCalendar=\*-\*-\* 04:30:00$/m);
    assert.match(timer, /^Persistent=true$/m);
    assert.match(timer, /^WantedBy=timers\.target$/m);
    assert.match(unit, /^Type=oneshot$/m);
    assert.match(unit, /^ExecStart=@PI_DIR@\/system\/pi-update\.sh$/m);
    assert.match(unit, /^After=docker\.service network-online\.target$/m);
    assert.match(unit, /^Wants=network-online\.target$/m);
    // The timer starts it; enabling the service itself would run an update on every boot.
    assert.doesNotMatch(unit, /^\[Install\]$/m);
  });

  await t.test('bootstrap installs the timer, and the service without enabling it', () => {
    assert.match(bootstrap, /^place_unit pi-update\.service\b/m);
    assert.match(bootstrap, /systemctl enable --now pi-update\.timer/);
    // A timer keeps its old schedule until it is restarted.
    assert.match(bootstrap, /^if place_unit pi-update\.timer; then$/m);
    assert.match(bootstrap, /systemctl restart pi-update\.timer/);
    assert.doesNotMatch(bootstrap, /enable[^\n]*pi-update\.service/);
    // Units are rewritten only when they differ, with the checkout's path filled in.
    assert.match(bootstrap, /s\|@PI_DIR@\|\$PI_DIR\|g/);
    assert.match(bootstrap, /cmp -s/);
  });

  await t.test('bootstrap installs it only once the stack can start, since the update starts it', () => {
    const env = bootstrap.indexOf('Created $PI_DIR/.env');
    const install = bootstrap.indexOf('place_unit pi-update.service');
    assert.ok(env >= 0 && install > env, 'the timer is installed after the .env check');
  });

  await t.test('units and script keep LF line endings on any checkout', () => {
    const attributes = read(path.join(ROOT_DIR, '.gitattributes'));
    assert.match(attributes, /^hosts\/pi\/system\/\*\.service text eol=lf$/m);
    assert.match(attributes, /^hosts\/pi\/system\/\*\.timer text eol=lf$/m);
  });
});

// Behaviour, against a throwaway origin and a sparse checkout like the Pi's.
// docker, runuser and curl are stubs on PATH that log their calls.
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const probe = spawnSync('bash', ['-c', 'command -v git'], { encoding: 'utf8' });
const canRun = process.platform !== 'win32' && !probe.error && probe.status === 0;
const skip = !canRun && 'needs bash and git';
const git = (cwd, ...args) => {
  const r = spawnSync('git', ['-c', 'user.name=t', '-c', 'user.email=t@t', '-c', 'init.defaultBranch=master', ...args], { cwd, encoding: 'utf8' });
  assert.equal(r.status, 0, `git ${args.join(' ')}: ${r.stderr}`);
  return r.stdout.trim();
};

function setup({ token = 'tk_test' } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'pi-update-'));
  const origin = path.join(root, 'origin.git');
  const seed = path.join(root, 'seed');
  const checkout = path.join(root, 'homelab');
  const bin = path.join(root, 'bin');
  const dockerLog = path.join(root, 'docker.log');
  const curlLog = path.join(root, 'curl.log');
  // How many more `docker compose up` calls fail, as a start does when a health check never passes.
  const failUps = path.join(root, 'fail-ups');
  const failCurl = path.join(root, 'fail-curl');
  fs.mkdirSync(bin);
  fs.writeFileSync(path.join(bin, 'docker'), `#!/bin/bash
echo "docker $*" >> "${dockerLog}"
if [[ " $* " == *" up "* ]] && [ -s "${failUps}" ]; then
  n=$(cat "${failUps}"); [ "$n" -gt 0 ] && { echo $((n - 1)) > "${failUps}"; exit 1; }
fi
exit 0
`, { mode: 0o755 });
  fs.writeFileSync(path.join(bin, 'runuser'), '#!/bin/sh\n[ "$1" = "-u" ] && [ "$3" = "--" ] || exit 2\nshift 3\nexec "$@"\n', { mode: 0o755 });
  // One call per record: its arguments split by \x1f, records ended by \x1e.
  fs.writeFileSync(path.join(bin, 'curl'), `#!/bin/bash
{ printf '%s\\x1f' "$@"; printf '\\x1e'; } >> "${curlLog}"
[ ! -e "${failCurl}" ]
`, { mode: 0o755 });
  git(root, 'init', '-q', '--bare', origin);
  git(root, 'clone', '-q', origin, seed);
  const system = path.join(seed, 'hosts/pi/system');
  fs.mkdirSync(system, { recursive: true });
  fs.mkdirSync(path.join(seed, 'hosts/pi/caddy'));
  fs.mkdirSync(path.join(seed, 'hosts/pi/unbound'));
  fs.mkdirSync(path.join(seed, 'hosts/server'), { recursive: true });
  fs.copyFileSync(path.join(SYSTEM_DIR, 'pi-update.sh'), path.join(system, 'pi-update.sh'));
  fs.chmodSync(path.join(system, 'pi-update.sh'), 0o755);
  fs.writeFileSync(path.join(seed, 'hosts/pi/docker-compose.yml'), 'image: pihole:1.0.0\n');
  fs.writeFileSync(path.join(seed, 'hosts/pi/caddy/Caddyfile'), 'home.example {}\n');
  fs.writeFileSync(path.join(seed, 'hosts/pi/unbound/unbound.conf'), 'server:\n');
  fs.writeFileSync(path.join(seed, 'hosts/pi/bootstrap.sh'), '#!/bin/sh\n');
  fs.writeFileSync(path.join(seed, 'hosts/pi/.gitignore'), '.env\n');
  fs.writeFileSync(path.join(seed, 'hosts/server/docker-compose.yml'), 'image: hub:1.0.0\n');
  git(seed, 'add', '-A');
  git(seed, 'commit', '-qm', 'first');
  git(seed, 'push', '-q', 'origin', 'HEAD:master');
  git(root, 'clone', '-q', '--sparse', origin, checkout);
  git(checkout, 'sparse-checkout', 'set', 'hosts/pi');
  const env = ['PIHOLE_PASSWORD=secret', 'NTFY_UPDATE_TOKEN=' + token, ''].join('\n');
  fs.writeFileSync(path.join(checkout, 'hosts/pi/.env'), env);

  const textfile = path.join(root, 'textfile');
  const run = () => spawnSync(path.join(checkout, 'hosts/pi/system/pi-update.sh'), [], {
    encoding: 'utf8',
    env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH}`, PI_NTFY_URL: 'http://ntfy.test/homelab', PI_TEXTFILE_DIR: textfile },
  });
  // What the run left for the node exporter, as { metric: value }.
  const prom = () => {
    const file = path.join(textfile, 'pi_update.prom');
    const lines = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').split('\n') : [];
    return Object.fromEntries(lines.filter((l) => l && !l.startsWith('#')).map((l) => l.split(' ')).map(([k, v]) => [k, Number(v)]));
  };
  // docker calls without the project directory, which is the throwaway checkout's.
  const calls = () => (fs.existsSync(dockerLog) ? fs.readFileSync(dockerLog, 'utf8') : '')
    .split('\n').filter(Boolean).map((l) => l.replace(` --project-directory ${path.join(checkout, 'hosts/pi')}`, ''));
  const alerts = () => (fs.existsSync(curlLog) ? fs.readFileSync(curlLog, 'utf8') : '')
    .split('\x1e').filter(Boolean).map((r) => r.split('\x1f').slice(0, -1));
  // The text an alert carries: its title header and its body.
  const alertText = (args) => args.join('\n');
  const clearCalls = () => { fs.rmSync(dockerLog, { force: true }); fs.rmSync(curlLog, { force: true }); };
  const upstream = (file, content) => {
    fs.mkdirSync(path.dirname(path.join(seed, file)), { recursive: true });
    fs.writeFileSync(path.join(seed, file), content);
    git(seed, 'add', '-A');
    git(seed, 'commit', '-qm', `change ${file}`);
    git(seed, 'push', '-q', 'origin', 'HEAD:master');
  };
  const failStarts = (n) => fs.writeFileSync(failUps, `${n}\n`);
  const breakCurl = () => fs.writeFileSync(failCurl, '');
  const cleanup = () => fs.rmSync(root, { recursive: true, force: true });
  return { seed, checkout, textfile, prom, run, calls, alerts, alertText, clearCalls, upstream, failStarts, breakCurl, cleanup };
}

const START = 'docker compose up -d --remove-orphans --wait';
const PRUNE = 'docker image prune -f';

test('Pi nightly update: applying what was merged', async (t) => {
  await t.test('does nothing when nothing was merged', { skip }, () => {
    const h = setup();
    const result = h.run();
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(h.calls(), [], 'the stack is left alone');
    assert.deepEqual(h.alerts(), []);
    h.cleanup();
  });

  await t.test('applies a merged bump: fast-forwards, starts the stack, prunes old images', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    const result = h.run();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(path.join(h.checkout, 'hosts/pi/docker-compose.yml'), 'utf8'), 'image: pihole:1.0.1\n');
    assert.deepEqual(h.calls(), [START, PRUNE]);
    assert.deepEqual(h.alerts(), [], 'a good night is quiet');
    h.cleanup();
  });

  await t.test('after an applied bump, the next quiet night touches nothing', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    assert.equal(h.run().status, 0);
    h.clearCalls();
    const result = h.run();
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(h.calls(), []);
    h.cleanup();
  });

  await t.test('only fast-forwards, as the checkout\'s owner', () => {
    const script = read(path.join(SYSTEM_DIR, 'pi-update.sh'));
    // git refuses a root-run command in a checkout someone else owns.
    assert.match(script, /runuser -u "\$OWNER" -- git -C "\$REPO_DIR"/);
    assert.match(script, /merge --ff-only/);
    assert.doesNotMatch(script, /push|rebase|git pull/);
  });
});

test('Pi nightly update: files mounted one by one', async (t) => {
  // A merge writes a new file, and a container holding a single-file bind mount keeps
  // reading the old one, so the service is recreated, never just reloaded.
  for (const [file, service] of [['hosts/pi/caddy/Caddyfile', 'caddy'], ['hosts/pi/unbound/unbound.conf', 'unbound']]) {
    await t.test(`a changed ${path.basename(file)} recreates ${service}`, { skip }, () => {
      const h = setup();
      h.upstream(file, '# changed\n');
      const result = h.run();
      assert.equal(result.status, 0, result.stderr);
      assert.deepEqual(h.calls(), [START, `docker compose up -d --force-recreate --wait ${service}`, PRUNE]);
      h.cleanup();
    });
  }

  await t.test('both changed recreates both, in one call', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/caddy/Caddyfile', '# changed\n');
    h.upstream('hosts/pi/unbound/unbound.conf', '# changed\n');
    assert.equal(h.run().status, 0);
    assert.deepEqual(h.calls(), [START, 'docker compose up -d --force-recreate --wait caddy unbound', PRUNE]);
    h.cleanup();
  });

  await t.test('a change elsewhere recreates nothing', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    assert.equal(h.run().status, 0);
    assert.ok(!h.calls().some((c) => c.includes('--force-recreate')));
    h.cleanup();
  });
});

test('Pi nightly update: refusals, and the alert they send', async (t) => {
  const header = (args, name) => args.find((a) => a.startsWith(`${name}:`));

  await t.test('changes nothing in a checkout with local edits, and says so through ntfy', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    fs.writeFileSync(path.join(h.checkout, 'hosts/pi/bootstrap.sh'), '#!/bin/sh\necho edited by hand\n');
    const head = git(h.checkout, 'rev-parse', 'HEAD');
    const result = h.run();
    assert.equal(result.status, 1);
    assert.equal(git(h.checkout, 'rev-parse', 'HEAD'), head);
    assert.deepEqual(h.calls(), []);
    const alerts = h.alerts();
    assert.equal(alerts.length, 1);
    const [alert] = alerts;
    assert.equal(alert.at(-1), 'http://ntfy.test/homelab');
    assert.equal(header(alert, 'Authorization'), 'Authorization: Bearer tk_test');
    assert.match(header(alert, 'Title'), /lemonpi/);
    assert.match(h.alertText(alert), /local edits/);
    assert.match(h.alertText(alert), /journalctl -u pi-update/);
    assert.ok(alert.includes('--max-time'), 'a dead ntfy must not hold the run');
    h.cleanup();
  });

  await t.test('changes nothing in a checkout that has diverged from origin', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    fs.writeFileSync(path.join(h.checkout, 'hosts/pi/local.txt'), 'x\n');
    git(h.checkout, 'add', 'hosts/pi/local.txt');
    git(h.checkout, 'commit', '-qm', 'local commit');
    const head = git(h.checkout, 'rev-parse', 'HEAD');
    const result = h.run();
    assert.equal(result.status, 1);
    assert.equal(git(h.checkout, 'rev-parse', 'HEAD'), head);
    assert.deepEqual(h.calls(), []);
    assert.match(h.alertText(h.alerts()[0]), /commits origin does not have/);
    h.cleanup();
  });

  await t.test('a fetch that fails is reported too', { skip }, () => {
    const h = setup();
    git(h.checkout, 'remote', 'set-url', 'origin', path.join(os.tmpdir(), 'no-such-origin.git'));
    const result = h.run();
    assert.notEqual(result.status, 0);
    assert.deepEqual(h.calls(), []);
    assert.match(h.alertText(h.alerts()[0]), /fetch/);
    h.cleanup();
  });

  await t.test('without a token nothing is sent, and the run ends the same', { skip }, () => {
    const h = setup({ token: '' });
    fs.writeFileSync(path.join(h.checkout, 'hosts/pi/bootstrap.sh'), '#!/bin/sh\necho edited by hand\n');
    assert.equal(h.run().status, 1);
    assert.deepEqual(h.alerts(), []);
    h.cleanup();
  });

  await t.test('an alert that cannot be sent does not change how the run ends', { skip }, () => {
    const h = setup();
    h.breakCurl();
    fs.writeFileSync(path.join(h.checkout, 'hosts/pi/bootstrap.sh'), '#!/bin/sh\necho edited by hand\n');
    const result = h.run();
    assert.equal(result.status, 1);
    assert.equal(h.alerts().length, 1);
    h.cleanup();
  });

  await t.test('reads the token from .env without running it', () => {
    const script = read(path.join(SYSTEM_DIR, 'pi-update.sh'));
    assert.match(script, /sed -n 's\/\^NTFY_UPDATE_TOKEN=\/\/p' "\$PI_DIR\/\.env"/);
    assert.doesNotMatch(script, /^\s*(source|\.) [^\n]*\.env/m);
  });
});

test('Pi nightly update: a start that fails is rolled back', async (t) => {
  await t.test('goes back to the commit that last started, starts it again, and says so', { skip }, () => {
    const h = setup();
    const good = git(h.checkout, 'rev-parse', 'HEAD');
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    const bad = git(h.seed, 'rev-parse', 'HEAD');
    h.failStarts(1);
    const result = h.run();
    assert.equal(result.status, 1);
    assert.equal(git(h.checkout, 'rev-parse', 'HEAD'), good);
    assert.equal(fs.readFileSync(path.join(h.checkout, 'hosts/pi/docker-compose.yml'), 'utf8'), 'image: pihole:1.0.0\n');
    assert.deepEqual(h.calls(), [START, START], 'no prune: the old images are what runs now');
    const text = h.alertText(h.alerts()[0]);
    assert.match(text, /rolled back/);
    assert.ok(text.includes(bad.slice(0, 7)) && text.includes(good.slice(0, 7)), 'the alert names both commits');
    h.cleanup();
  });

  await t.test('the next night tries the same change again', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    h.failStarts(1);
    assert.equal(h.run().status, 1);
    h.clearCalls();
    const result = h.run();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(git(h.checkout, 'rev-parse', 'HEAD'), git(h.seed, 'rev-parse', 'HEAD'));
    assert.deepEqual(h.calls(), [START, PRUNE]);
    assert.deepEqual(h.alerts(), []);
    h.cleanup();
  });

  await t.test('a rolled-back Caddyfile is recreated again, so Caddy reads the old one', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/caddy/Caddyfile', '# broken\n');
    h.failStarts(1);
    assert.equal(h.run().status, 1);
    assert.deepEqual(h.calls(), [START, START, 'docker compose up -d --force-recreate --wait caddy']);
    h.cleanup();
  });

  await t.test('when the old commit does not start either, the alert says that', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    h.failStarts(2);
    assert.equal(h.run().status, 1);
    assert.match(h.alertText(h.alerts()[0]), /rollback failed too/);
    h.cleanup();
  });

  await t.test('the only reset is back to the commit that last started', () => {
    const script = read(path.join(SYSTEM_DIR, 'pi-update.sh'));
    const resets = script.match(/reset --hard[^\n]*/g) || [];
    assert.deepEqual(resets, ['reset --hard --quiet "$before"']);
  });
});

test('Pi nightly update: host setup, which only bootstrap applies', async (t) => {
  for (const file of ['hosts/pi/bootstrap.sh', 'hosts/pi/system/pi-update.timer']) {
    await t.test(`a merged change to ${file} is applied, then pointed out`, { skip }, () => {
      const h = setup();
      h.upstream(file, '# new step\n');
      const result = h.run();
      assert.equal(result.status, 0, result.stderr);
      assert.match(result.stdout, /sudo hosts\/pi\/bootstrap\.sh/);
      const alerts = h.alerts();
      assert.equal(alerts.length, 1);
      assert.match(h.alertText(alerts[0]), /sudo hosts\/pi\/bootstrap\.sh/);
      h.cleanup();
    });
  }

  await t.test('a change to the stack alone is not pointed out', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    const result = h.run();
    assert.doesNotMatch(result.stdout, /bootstrap/);
    assert.deepEqual(h.alerts(), []);
    h.cleanup();
  });

  await t.test('a change merged on a rolled-back night is pointed out when it applies', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/bootstrap.sh', '# new step\n');
    h.failStarts(1);
    assert.equal(h.run().status, 1);
    h.clearCalls();
    const result = h.run();
    assert.equal(result.status, 0, result.stderr);
    assert.match(h.alertText(h.alerts()[0]), /sudo hosts\/pi\/bootstrap\.sh/);
    h.cleanup();
  });
});

test('Pi nightly update: the numbers it leaves for the node exporter', async (t) => {
  await t.test('writes them where the exporter reads them, whole and readable by it', () => {
    const script = read(path.join(SYSTEM_DIR, 'pi-update.sh'));
    assert.match(script, /^TEXTFILE_DIR="\$\{PI_TEXTFILE_DIR:-\/var\/lib\/node_exporter\/textfile\}"$/m);
    // The exporter does not run as root, and must never read a half-written file.
    assert.match(script, /chmod 644/);
    assert.match(script, /mv "\$tmp" "\$PROM"/);
  });

  await t.test('a quiet night records that it ran, and no update', { skip }, () => {
    const h = setup();
    const started = Math.floor(Date.now() / 1000);
    assert.equal(h.run().status, 0);
    assert.ok(h.prom().pi_update_last_run_timestamp_seconds >= started);
    assert.equal(h.prom().pi_update_last_run_success, 1);
    assert.equal(h.prom().pi_update_last_applied_timestamp_seconds, undefined, 'nothing was applied yet');
    h.cleanup();
  });

  await t.test('an applied bump records when, and later quiet nights keep that time', { skip }, () => {
    const h = setup();
    const started = Math.floor(Date.now() / 1000);
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    assert.equal(h.run().status, 0);
    const applied = h.prom().pi_update_last_applied_timestamp_seconds;
    assert.ok(applied >= started);
    assert.equal(h.run().status, 0);
    assert.equal(h.prom().pi_update_last_applied_timestamp_seconds, applied);
    assert.deepEqual(fs.readdirSync(h.textfile), ['pi_update.prom']);
    assert.equal(fs.statSync(path.join(h.textfile, 'pi_update.prom')).mode & 0o777, 0o644);
    h.cleanup();
  });

  await t.test('a refused run is recorded as failed', { skip }, () => {
    const h = setup();
    fs.writeFileSync(path.join(h.checkout, 'hosts/pi/bootstrap.sh'), '#!/bin/sh\necho edited by hand\n');
    assert.equal(h.run().status, 1);
    assert.equal(h.prom().pi_update_last_run_success, 0);
    h.cleanup();
  });

  await t.test('a rolled-back night is recorded as failed, and applies nothing', { skip }, () => {
    const h = setup();
    h.upstream('hosts/pi/docker-compose.yml', 'image: pihole:1.0.1\n');
    h.failStarts(1);
    assert.equal(h.run().status, 1);
    assert.equal(h.prom().pi_update_last_run_success, 0);
    assert.equal(h.prom().pi_update_last_applied_timestamp_seconds, undefined);
    h.cleanup();
  });
});
