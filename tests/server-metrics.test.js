const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const ROOT_DIR = path.resolve(__dirname, '..');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

const compose = read(path.join(ROOT_DIR, 'hosts/server/docker-compose.yml'));

test('Prometheus scrapes the server\'s exporters', async (t) => {
  const config = read(path.join(ROOT_DIR, 'config/prometheus/prometheus.yml'));

  // One job's text: from its "- job_name:" line to the next one.
  const job = (name) => (config.match(new RegExp(`^  - job_name: ${name}\\n([\\s\\S]*?)(?=^  - job_name:|(?![\\s\\S]))`, 'm')) || [])[1] || '';

  await t.test('every 15 seconds, and every host\'s numbers carry its name', () => {
    assert.match(config, /^global:\s*\n(?:  .*\n)*?  scrape_interval: 15s$/m);
    // The desktop may get a row of its own later: the label keeps the two apart.
    for (const name of ['node', 'smart', 'gpu']) {
      assert.match(job(name), /labels:\s*\n\s+host: server\b/, `${name} must label its numbers host=server`);
    }
  });

  await t.test('reads the host through the name that leads out of the stack', () => {
    assert.match(job('node'), /targets: \[host\.docker\.internal:9100\]/);
  });

  await t.test('reads the disks once a minute, which is how often the exporter asks them', () => {
    assert.match(job('smart'), /targets: \[smartctl-exporter:9633\]/);
    assert.match(job('smart'), /^    scrape_interval: 60s$/m);
  });

  await t.test('reads the GPU, and itself', () => {
    assert.match(job('gpu'), /targets: \[nvidia-gpu-exporter:9835\]/);
    assert.match(job('prometheus'), /targets: \[localhost:9090\]/);
  });

  await t.test('names only services the compose file runs', () => {
    const targets = [...config.matchAll(/targets: \[([a-z0-9.-]+):\d+\]/g)].map((m) => m[1]);
    assert.equal(targets.length, 4);
    for (const host of targets.filter((h) => !['localhost', 'host.docker.internal'].includes(h))) {
      assert.match(compose, new RegExp(`^  ${host}:\\s*$`, 'm'), `${host} must be a service on the server`);
    }
  });

  await t.test('is mounted read-only from the repo', () => {
    assert.ok(compose.includes('- ${CONFIG_PATH}/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro'));
    // config/*/** is ignored by default; this file must be let through.
    const ignored = spawnSync('git', ['check-ignore', '-q', 'config/prometheus/prometheus.yml'], { cwd: ROOT_DIR }).status === 0;
    assert.ok(!ignored, 'config/prometheus/prometheus.yml must not be git-ignored');
  });
});

test('Server dashboard', async (t) => {
  const file = 'config/grafana/dashboards/server.json';
  const raw = read(path.join(ROOT_DIR, file));
  let dashboard = {};
  try { dashboard = JSON.parse(raw); } catch { /* the first check reports it */ }
  const panels = dashboard.panels || [];
  const rows = panels.filter((p) => p.type === 'row');
  const drawn = panels.filter((p) => p.type !== 'row');
  const exprs = (panel) => (panel.targets || []).map((target) => target.expr);
  const titled = (title) => drawn.find((p) => p.title === title) || {};
  // The section a panel sits in: the last row above it.
  const section = (panel) => (rows.filter((row) => panel.gridPos && row.gridPos.y <= panel.gridPos.y).pop() || {}).title;
  const steps = (panel) => panel.fieldConfig.defaults.thresholds.steps.map((s) => s.value);

  await t.test('is a file in the repo that Grafana loads, opening on the last week', () => {
    assert.ok(raw, `${file} must exist`);
    assert.equal(dashboard.uid, 'server');
    assert.equal(dashboard.title, 'Server');
    assert.deepEqual(dashboard.time, { from: 'now-7d', to: 'now' });
    assert.equal(dashboard.editable, true);
    // An id belongs to one Grafana's database; the file must not carry one.
    assert.equal(dashboard.id, null);
    const ignored = spawnSync('git', ['check-ignore', '-q', file], { cwd: ROOT_DIR }).status === 0;
    assert.ok(!ignored, `${file} must not be git-ignored`);
  });

  await t.test('has one open section per subject, in the agreed order', () => {
    assert.deepEqual(rows.map((r) => r.title), ['Power', 'Temperatures', 'Load', 'Activity', 'Disks', 'Updates']);
    for (const row of rows) assert.equal(row.collapsed, false, `${row.title} starts open`);
    for (const panel of drawn) assert.ok(panel.gridPos.x + panel.gridPos.w <= 24, `${panel.title} fits the grid`);
  });

  await t.test('reads everything from Prometheus', () => {
    assert.ok(drawn.length > 20, 'the panels should be readable');
    for (const panel of drawn) {
      const name = panel.title || `the panel at ${panel.gridPos.x},${panel.gridPos.y}`;
      assert.deepEqual(panel.datasource, { type: 'prometheus', uid: 'prometheus' }, `${name} must use Prometheus`);
      assert.ok(exprs(panel).length > 0 && exprs(panel).every(Boolean), `${name} must ask for something`);
    }
  });

  await t.test('asks only for numbers the exporters and the host scripts really report', () => {
    // Read from the exporters on the server (node 1.12.1, smartctl 0.14.0, nvidia 1.15.1),
    // and the names server-update.sh and server-metrics.sh write.
    const known = [
      'node_boot_time_seconds', 'node_cpu_seconds_total', 'node_memory_MemAvailable_bytes', 'node_memory_MemTotal_bytes',
      'node_hwmon_temp_celsius', 'node_power_supply_online', 'node_power_supply_capacity',
      'node_filesystem_avail_bytes', 'node_filesystem_size_bytes',
      'node_network_receive_bytes_total', 'node_network_transmit_bytes_total',
      'node_disk_read_bytes_total', 'node_disk_written_bytes_total',
      'smartctl_device', 'smartctl_device_smart_status', 'smartctl_device_attribute', 'smartctl_device_temperature',
      'smartctl_device_percentage_used', 'smartctl_device_power_on_seconds',
      'nvidia_smi_temperature_gpu', 'nvidia_smi_utilization_gpu_ratio', 'nvidia_smi_memory_used_bytes',
      'nvidia_smi_memory_total_bytes', 'nvidia_smi_power_draw_watts',
      'server_update_last_run_timestamp_seconds', 'server_update_last_run_success',
      'server_update_last_applied_timestamp_seconds', 'server_unattended_upgrade_last_run_timestamp_seconds',
      'server_media_disk_mounted', 'server_reboot_required', 'server_cpu_throttled_seconds_total',
      'server_smart_selftest_passed', 'server_smart_selftest_power_on_hours',
    ];
    const hostScripts = read(path.join(ROOT_DIR, 'hosts/server/system/server-update.sh')) + read(path.join(ROOT_DIR, 'hosts/server/system/server-metrics.sh'));
    for (const name of known.filter((n) => n.startsWith('server_'))) {
      assert.ok(hostScripts.includes(name), `a host script must write ${name}`);
    }
    const used = new Set(drawn.flatMap(exprs).flatMap((expr) => expr.match(/\b(?:node|smartctl|nvidia|server)_[A-Za-z0-9_]+/g) || []));
    for (const name of used) assert.ok(known.includes(name), `${name} is not a number anything reports`);
    for (const name of known) assert.ok(used.has(name), `${name} should be on the dashboard`);
  });

  await t.test('shows each temperature as a gauge, coloured by limits read from the hardware', () => {
    // Amber and red. Media disk: WD gives 65 as its maximum. CPU: critical at 100. GPU: its
    // target is 87, it slows down at 95. SSD: its maximum is 82.
    const limits = { CPU: [80, 95], GPU: [75, 87], 'Media disk': [55, 60], SSD: [60, 70] };
    for (const [name, [amber, red]] of Object.entries(limits)) {
      const gauge = drawn.find((p) => p.title === name && section(p) === 'Temperatures') || {};
      assert.equal(gauge.type, 'gauge', `${name} must be a gauge`);
      assert.equal(gauge.fieldConfig.defaults.unit, 'celsius');
      assert.deepEqual(steps(gauge), [null, amber, red]);
      // Average and maximum, as numbers under the gauge.
      const stat = drawn.find((p) => p.gridPos.x === gauge.gridPos.x && p.gridPos.y === gauge.gridPos.y + gauge.gridPos.h) || {};
      assert.equal(stat.type, 'stat');
      // No title: the gauge above names the device, and a title that long is cut off.
      assert.equal(stat.title, '');
      assert.deepEqual(stat.targets.map((target) => target.legendFormat), ['avg', 'max']);
      assert.match(exprs(stat)[0], /^avg\(avg_over_time\(/);
      assert.match(exprs(stat)[1], /^max\(max_over_time\(/);
      assert.deepEqual(steps(stat), [null, amber, red]);
    }
  });

  await t.test('shows how long the CPU was slowed down for heat, as bars across the section', () => {
    const panel = titled('CPU throttling');
    assert.equal(panel.type, 'timeseries');
    assert.equal(section(panel), 'Temperatures');
    assert.equal(panel.fieldConfig.defaults.custom.drawStyle, 'bars');
    assert.equal(panel.fieldConfig.defaults.unit, 's');
    // The script writes the counter once a minute: a shorter bar would have nothing to count.
    assert.equal(panel.interval, '1m');
    assert.deepEqual(exprs(panel), ['sum(increase(server_cpu_throttled_seconds_total{host="server"}[$__interval]))']);
    // Full width, under the gauges and the graph, above the next section.
    assert.equal(panel.gridPos.x, 0);
    assert.equal(panel.gridPos.w, 24);
    const above = drawn.filter((p) => section(p) === 'Temperatures' && p !== panel);
    assert.equal(panel.gridPos.y, Math.max(...above.map((p) => p.gridPos.y + p.gridPos.h)));
    const load = rows.find((row) => row.title === 'Load');
    assert.equal(load.gridPos.y, panel.gridPos.y + panel.gridPos.h);
  });

  await t.test('draws each temperature as an average line inside a band from its lowest to its highest', () => {
    const panel = titled('Temperatures');
    assert.equal(panel.type, 'timeseries');
    // The window follows the zoom, and is never shorter than a minute: the CPU's own
    // sensor swings by 20 degrees from one reading to the next.
    assert.equal(panel.interval, '1m');
    const byLegend = Object.fromEntries(panel.targets.map((target) => [target.legendFormat, target.expr]));
    assert.equal(panel.targets.length, 12);

    // What the overrides add up to for one series, in order.
    const matches = ({ id, options }, name) => (id === 'byName' ? options === name : id === 'byRegexp' && new RegExp(options.slice(1, -1)).test(name));
    const settings = (name) => Object.fromEntries(panel.fieldConfig.overrides
      .filter((override) => matches(override.matcher, name))
      .flatMap((override) => override.properties.map((property) => [property.id, property.value])));

    const colours = [];
    for (const name of ['CPU', 'GPU', 'Media disk', 'SSD']) {
      assert.match(byLegend[name], /^max\(avg_over_time\(.+\[\$__interval\]\)\)$/, `${name} is an average`);
      assert.match(byLegend[`${name} min`], /^min\(min_over_time\(.+\[\$__interval\]\)\)$/);
      assert.match(byLegend[`${name} max`], /^max\(max_over_time\(.+\[\$__interval\]\)\)$/);
      // The band: the highest filled down to the lowest, neither drawn as a line nor named below the graph.
      assert.equal(settings(`${name} max`)['custom.fillBelowTo'], `${name} min`);
      assert.ok(settings(`${name} max`)['custom.fillOpacity'] > 0);
      for (const edge of [`${name} min`, `${name} max`]) {
        assert.equal(settings(edge)['custom.lineWidth'], 0);
        assert.equal(settings(edge)['custom.hideFrom'].legend, true);
        assert.deepEqual(settings(edge).color, settings(name).color, `${edge} has its line's colour`);
      }
      assert.equal(settings(name)['custom.hideFrom'], undefined);
      assert.equal(settings(name).color.mode, 'fixed');
      colours.push(settings(name).color.fixedColor);
    }
    assert.equal(new Set(colours).size, 4);
  });

  await t.test('takes every average and maximum over the period picked at the top', () => {
    // The graphs average over a step of the time axis; the numbers over the whole period.
    const overTime = drawn.filter((p) => p.type !== 'timeseries').flatMap(exprs).filter((expr) => /(avg|max)_over_time/.test(expr));
    assert.ok(overTime.length >= 10);
    // A rate over the whole period reads low while the history is shorter than the period,
    // so usage is averaged from five-minute steps instead.
    for (const expr of overTime) assert.match(expr, /\[\$__range(:5m)?\]/, expr);
    assert.doesNotMatch(drawn.flatMap(exprs).join('\n'), /rate\([^)]*\[\$__range\]/);
    for (const title of ['CPU usage', 'Memory usage', 'GPU load', 'GPU memory']) {
      assert.equal(titled(title).type, 'stat', `${title} must be a number`);
      assert.equal(section(titled(title)), 'Load');
      assert.match(exprs(titled(title)).join(), /\$__range/, `${title} is the average over the period`);
    }
  });

  await t.test('has a panel for every reading the issue asked for, in its section', () => {
    const expected = {
      Power: ['Uptime', 'Mains', 'Battery', 'Battery and mains'],
      Temperatures: ['Temperatures', 'CPU throttling'],
      Load: ['CPU and memory', 'GPU load and power'],
      Activity: ['Network', 'Disk activity'],
      Disks: ['Disk health', 'Media disk', 'Free space', 'Free space left'],
      Updates: ['Nightly update ran', 'Nightly update result', 'Last update applied', 'Unattended upgrades ran', 'Reboot waiting'],
    };
    for (const [row, titles] of Object.entries(expected)) {
      for (const title of titles) {
        const panel = drawn.find((p) => p.title === title && section(p) === row);
        assert.ok(panel, `${row} must have a panel titled "${title}"`);
      }
    }
    const health = exprs(drawn.find((p) => p.title === 'Disk health') || {}).join('\n');
    for (const needle of ['smartctl_device_smart_status', 'Reallocated_Sector_Ct', 'Current_Pending_Sector', 'server_smart_selftest_passed', 'smartctl_device_percentage_used']) {
      assert.ok(health.includes(needle), `the disk health table must show ${needle}`);
    }
  });
});
