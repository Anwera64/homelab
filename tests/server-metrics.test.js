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
