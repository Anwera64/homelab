const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const PI_DIR = path.join(ROOT_DIR, 'hosts/pi');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

// The block of one compose service: from its "  name:" line up to the next top-level service or section.
const serviceBlock = (compose, name) =>
  (compose.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [a-z0-9_-]+:\\n|^[a-z]|(?![\\s\\S]))`, 'm')) || [])[1] || '';

test('lemonpi\'s node exporter', async (t) => {
  const compose = read(path.join(PI_DIR, 'docker-compose.yml'));
  const exporter = serviceBlock(compose, 'node-exporter');
  const bootstrap = read(path.join(PI_DIR, 'bootstrap.sh'));

  await t.test('runs the server\'s pinned release, so Renovate bumps both together', () => {
    const server = serviceBlock(read(path.join(ROOT_DIR, 'hosts/server/docker-compose.yml')), 'node-exporter');
    const image = (block) => (block.match(/^    image: (\S+)$/m) || [])[1];
    assert.match(image(exporter) || '', /^prom\/node-exporter:v\d+\.\d+\.\d+$/);
    assert.equal(image(exporter), image(server));
  });

  await t.test('reads the host itself: its network, processes and filesystems', () => {
    assert.match(exporter, /^    network_mode: host$/m);
    assert.match(exporter, /^    pid: host$/m);
    assert.match(exporter, /^      - \/:\/host:ro,rslave$/m);
    assert.match(exporter, /^      - --path\.rootfs=\/host$/m);
  });

  await t.test('answers on the LAN address only, so the tailnet cannot read it', () => {
    assert.match(exporter, /^      - --web\.listen-address=192\.168\.1\.35:9100$/m);
  });

  await t.test('reads the numbers pi-update.sh leaves, from the folder bootstrap makes', () => {
    assert.match(exporter, /^      - --collector\.textfile\.directory=\/host\/var\/lib\/node_exporter\/textfile$/m);
    const folder = bootstrap.indexOf('install -d -m 0755 /var/lib/node_exporter/textfile');
    const up = bootstrap.indexOf('docker compose --project-directory "$PI_DIR" up');
    assert.ok(folder >= 0, 'bootstrap must create the textfile folder');
    assert.ok(folder < up, 'before the stack starts, or Docker makes it as root-only');
  });

  await t.test('stays small on a 1 GB Pi, and comes back after a restart', () => {
    assert.match(exporter, /^    mem_limit: 64m$/m);
    assert.match(exporter, /^    restart: unless-stopped$/m);
  });
});

test('lemonpi dashboard', async (t) => {
  const { spawnSync } = require('node:child_process');
  const file = 'config/grafana/dashboards/lemonpi.json';
  const raw = read(path.join(ROOT_DIR, file));
  let dashboard = {};
  try { dashboard = JSON.parse(raw); } catch { /* the first check reports it */ }
  const panels = dashboard.panels || [];
  const rows = panels.filter((p) => p.type === 'row');
  const drawn = panels.filter((p) => p.type !== 'row');
  const exprs = (panel) => (panel.targets || []).map((target) => target.expr);
  const titled = (title) => drawn.find((p) => p.title === title) || {};
  const steps = (panel) => panel.fieldConfig.defaults.thresholds.steps.map((s) => s.value);
  // The section a panel sits in: the last row above it.
  const section = (panel) => (rows.filter((row) => panel.gridPos && row.gridPos.y <= panel.gridPos.y).pop() || {}).title;

  await t.test('is a file in the repo that Grafana loads, opening on the last week', () => {
    assert.ok(raw, `${file} must exist`);
    assert.equal(dashboard.uid, 'lemonpi');
    assert.equal(dashboard.title, 'lemonpi');
    assert.deepEqual(dashboard.time, { from: 'now-7d', to: 'now' });
    assert.equal(dashboard.editable, true);
    // An id belongs to one Grafana's database; the file must not carry one.
    assert.equal(dashboard.id, null);
    const ignored = spawnSync('git', ['check-ignore', '-q', file], { cwd: ROOT_DIR }).status === 0;
    assert.ok(!ignored, `${file} must not be git-ignored`);
  });

  await t.test('has one open section per subject, in order, on the grid', () => {
    assert.deepEqual(rows.map((r) => r.title), ['Health', 'Activity', 'Updates']);
    for (const row of rows) assert.equal(row.collapsed, false, `${row.title} starts open`);
    for (const panel of drawn) assert.ok(panel.gridPos.x + panel.gridPos.w <= 24, `${panel.title} fits the grid`);
    const ids = panels.map((p) => p.id);
    assert.equal(new Set(ids).size, ids.length, 'panel ids are unique');
  });

  await t.test('reads everything from Prometheus, and only the Pi\'s numbers', () => {
    assert.ok(drawn.length >= 10, 'the panels should be readable');
    for (const panel of drawn) {
      assert.deepEqual(panel.datasource, { type: 'prometheus', uid: 'prometheus' }, `${panel.title} must use Prometheus`);
      assert.ok(exprs(panel).length > 0, `${panel.title} must ask for something`);
      for (const expr of exprs(panel)) {
        // Every selector names the host, or the server's numbers would mix in.
        const selectors = expr.match(/\b(?:node|pi)_[A-Za-z0-9_]+(\{[^}]*\})?/g) || [];
        assert.ok(selectors.length > 0, `${panel.title}: ${expr}`);
        for (const s of selectors) assert.ok(s.includes('host="lemonpi"'), `${panel.title}: ${s} must carry host="lemonpi"`);
      }
    }
  });

  await t.test('asks only for numbers the exporter and pi-update.sh really report', () => {
    const known = [
      'node_boot_time_seconds', 'node_thermal_zone_temp', 'node_cpu_seconds_total',
      'node_memory_MemAvailable_bytes', 'node_memory_MemTotal_bytes',
      'node_filesystem_avail_bytes', 'node_filesystem_size_bytes',
      'node_network_receive_bytes_total', 'node_network_transmit_bytes_total', 'node_disk_written_bytes_total',
      'pi_update_last_run_timestamp_seconds', 'pi_update_last_run_success', 'pi_update_last_applied_timestamp_seconds',
    ];
    const script = read(path.join(PI_DIR, 'system/pi-update.sh'));
    for (const name of known.filter((n) => n.startsWith('pi_'))) assert.ok(script.includes(name), `pi-update.sh must write ${name}`);
    const used = new Set(drawn.flatMap(exprs).flatMap((expr) => expr.match(/\b(?:node|pi)_[A-Za-z0-9_]+/g) || []));
    for (const name of used) assert.ok(known.includes(name), `${name} is not a number anything reports`);
    for (const name of known) assert.ok(used.has(name), `${name} should be on the dashboard`);
  });

  await t.test('shows the CPU temperature as a gauge, amber at 75 and red where the Pi 5 throttles', () => {
    const gauge = titled('CPU temperature');
    assert.equal(gauge.type, 'gauge');
    assert.equal(section(gauge), 'Health');
    assert.equal(gauge.fieldConfig.defaults.unit, 'celsius');
    assert.deepEqual(steps(gauge), [null, 75, 85]);
  });

  await t.test('counts what is written to the SD card a day, the card\'s wear', () => {
    const writes = titled('SD card writes, last 24 h');
    assert.equal(section(writes), 'Activity');
    assert.deepEqual(exprs(writes), ['increase(node_disk_written_bytes_total{device="mmcblk0",host="lemonpi"}[1d])']);
    assert.equal(writes.fieldConfig.defaults.unit, 'bytes');
    const free = titled('SD card free');
    assert.equal(section(free), 'Health');
    assert.equal(free.fieldConfig.defaults.unit, 'percent');
  });

  await t.test('shows the nightly update as the server\'s dashboard does', () => {
    const updates = drawn.filter((p) => section(p) === 'Updates');
    assert.deepEqual(updates.map((p) => p.title), ['Nightly update ran', 'Nightly update result', 'Last update applied']);
    assert.deepEqual(exprs(updates[0]), ['1000 * pi_update_last_run_timestamp_seconds{host="lemonpi"}']);
    assert.deepEqual(exprs(updates[1]), ['pi_update_last_run_success{host="lemonpi"}']);
    assert.deepEqual(exprs(updates[2]), ['1000 * pi_update_last_applied_timestamp_seconds{host="lemonpi"}']);
    const texts = updates[1].fieldConfig.defaults.mappings[0].options;
    assert.equal(texts['0'].text, 'Failed');
    assert.equal(texts['1'].text, 'OK');
    assert.match(updates[1].description, /journalctl -u pi-update/);
  });
});

test('lemonpi\'s numbers are documented', async (t) => {
  const pi = read(path.join(PI_DIR, 'README.md'));

  await t.test('the Pi README names the exporter, who reads it and where to look', () => {
    assert.match(pi, /^\| Node exporter \(Docker\) \| [^|]*192\.168\.1\.35:9100[^|]*\|$/m);
    const section = pi.match(/^## Metrics\n([\s\S]*?)(?=^## )/m);
    assert.ok(section, 'the Pi README must have a Metrics section');
    for (const needle of ['"lemonpi"', 'config/grafana/dashboards/lemonpi.json', 'config/prometheus/prometheus.yml', 'Health', 'Activity', 'Updates', 'node_thermal_zone_temp', 'curl -s 192.168.1.35:9100/metrics']) {
      assert.ok(section[1].includes(needle), `Metrics must mention ${needle}`);
    }
  });

  await t.test('the server README says Prometheus also reads lemonpi', () => {
    const server = read(path.join(ROOT_DIR, 'hosts/server/README.md'));
    assert.match(server, /^\| Metrics \(Docker\) \| [^\n]*lemonpi's node exporter[^\n]*\|$/m);
  });
});
