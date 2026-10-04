const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PORT_TO_SERVICE } = require('../config/homepage/adapt-links.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(ROOT_DIR, file), 'utf8').replace(/\r\n/g, '\n');

// Paths from the "Repository Structure" tree: each line's entry, joined onto its parent folders by indent.
function treePaths(readme) {
  const tree = readme.match(/## 📁 Repository Structure[\s\S]*?```\n([\s\S]*?)```/);
  assert.ok(tree, 'README must have a Repository Structure tree');
  const stack = [];
  const paths = [];
  for (const line of tree[1].split('\n')) {
    const match = line.match(/^((?:│   |    )*)(?:├── |└── )([^\s#]+)/);
    if (!match) continue;
    const depth = match[1].length / 4;
    const name = match[2].replace(/\/$/, '');
    stack.length = depth;
    stack.push(name);
    paths.push(stack.join('/'));
  }
  return paths;
}

test('Root README matches the repository', async (t) => {
  const readme = read('README.md');

  await t.test('every path in the repository tree exists', () => {
    const paths = treePaths(readme);
    assert.ok(paths.length > 20, 'the tree should list the main files');
    for (const p of paths) {
      assert.ok(fs.existsSync(path.join(ROOT_DIR, p)), `README lists ${p}, which does not exist`);
    }
  });

  await t.test('points to the Pi README and not to removed pieces', () => {
    assert.ok(readme.includes('hosts/pi/README.md'));
    assert.ok(!readme.includes('config/caddy'), 'config/caddy was removed');
    assert.ok(!readme.includes('-DisableAI'), 'startup_homelab.ps1 has no -DisableAI flag');
  });

  await t.test('every startup_homelab.ps1 flag it mentions is real', () => {
    const script = read('startup_homelab.ps1');
    const params = new Set([...script.matchAll(/\[switch\]\$(\w+)/g)].map((m) => m[1]));
    const used = [...readme.matchAll(/startup_homelab\.ps1 -(\w+)/g)].map((m) => m[1]);
    assert.ok(used.length > 0);
    for (const flag of used) {
      assert.ok(params.has(flag), `-${flag} is not a parameter of startup_homelab.ps1`);
    }
  });

  await t.test('badges are live status badges of workflows that exist', () => {
    const badges = [...readme.matchAll(/!\[[^\]]*\]\(([^)]+)\)/g)].map((m) => m[1]);
    assert.ok(badges.length >= 1);
    for (const badge of badges) {
      const workflow = badge.match(/^https:\/\/github\.com\/Anwera64\/homelab\/actions\/workflows\/([\w.-]+\.yml)\/badge\.svg$/);
      assert.ok(workflow, `${badge} is not a GitHub Actions status badge`);
      assert.ok(fs.existsSync(path.join(ROOT_DIR, '.github/workflows', workflow[1])), `${workflow[1]} does not exist`);
    }
  });

  await t.test('the log stack is in the Hosts table, the graph, the endpoints and the tree', () => {
    const desktopRow = readme.match(/^\| \*\*Desktop\*\*.*$/m);
    assert.ok(desktopRow, 'the Hosts table must have a Desktop row');
    for (const service of ['Loki', 'Alloy', 'Grafana']) {
      assert.ok(desktopRow[0].includes(service), `the Desktop row must name ${service}`);
    }

    const graph = readme.match(/```mermaid\n([\s\S]*?)```/);
    assert.ok(graph, 'README must have the network flow graph');
    const logs = graph[1].match(/subgraph Logs \[[^\]]*\]\n([\s\S]*?)\n\s*end/);
    assert.ok(logs, 'the graph must have a Logs subgraph');
    assert.match(logs[1], /ALLOY\[[^\]]*Alloy[^\]]*\] --> LOKI\[[^\]]*Loki[^\]]*30 days[^\]]*\] --> GRAFANA\[[^\]]*Grafana[^\]]*\]/);
    assert.match(graph[1], /-\.->\|Docker socket\| ALLOY/, 'the graph must show Alloy reading the Docker socket');
    assert.match(graph[1], /CADDY -->\|published LAN ports\|[^\n]*\bGRAFANA\b/, 'Caddy must route to Grafana in the graph');

    assert.match(readme, /^\| \*\*Grafana\*\* \| Desktop \| `https:\/\/grafana\.spicy-llama\.duckdns\.org` \| `http:\/\/desktop-kujo8mp\.lan:3002` \|/m);
    assert.match(readme, /^\| \*\*Loki\*\* \| Desktop \| — \| internal `http:\/\/loki:3100` \|/m);

    const paths = treePaths(readme);
    for (const p of ['config/loki/loki-config.yaml', 'config/alloy/config.alloy', 'config/grafana/provisioning/datasources/loki.yaml']) {
      assert.ok(paths.includes(p), `the tree must list ${p}`);
    }
  });

  await t.test('says how to start the dashboard image renderer, which does not run by itself', () => {
    assert.ok(readme.includes('docker compose --profile render up -d grafana-renderer'), 'README must show how to start the renderer');
    assert.ok(readme.includes('docker compose --profile render stop grafana-renderer'), 'README must show how to stop it');
    const desktopRow = readme.match(/^\| \*\*Desktop\*\*.*$/m)[0];
    assert.match(desktopRow, /image renderer \(on demand\)/, 'the Desktop row must list the renderer as on demand');
  });

  await t.test('says how to search the logs, what is kept and how to open the port', () => {
    assert.ok(readme.includes('{container="household-hub"}'), 'README must show a LogQL query by container');
    assert.match(readme, /30 days/);
    assert.ok(readme.includes('logs=off'), 'README must name the opt-out label');
    assert.match(readme, /calendar titles and note text/, 'README must say what the hub\'s lines carry');
    const firewall = readme.match(/Set-NetFirewallRule[^\n]*-LocalPort ([\d,]+)/);
    assert.ok(firewall, 'README must show the firewall rule');
    assert.ok(firewall[1].split(',').includes('3002'), 'the firewall rule must admit 3002');
  });

  await t.test('the telemetry route is in the endpoints, the graph and the firewall rule', () => {
    assert.match(readme, /^\| \*\*Alloy\*\* \| Desktop \| `https:\/\/telemetry\.spicy-llama\.duckdns\.org` \| `http:\/\/desktop-kujo8mp\.lan:4318` \|/m);
    const graph = readme.match(/```mermaid\n([\s\S]*?)```/)[1];
    assert.match(graph, /CADDY -->\|telemetry, token checked by the hub\| ALLOY/);
    const firewall = readme.match(/Set-NetFirewallRule[^\n]*-LocalPort ([\d,]+)/);
    assert.ok(firewall[1].split(',').includes('4318'), 'the firewall rule must admit 4318');
    assert.ok(readme.includes('{service_name="household-hub-app"}'), 'README must show how to find the app\'s lines');
  });

  await t.test('the firewall rule admits every port the Pi proxies to', () => {
    // -LocalPort replaces the whole list, so a port missing here is closed by copying the command
    // (Cleanuparr's 11011 was, and its HTTPS name answered 502).
    const firewall = readme.match(/Set-NetFirewallRule[^\n]*-LocalPort ([\d,]+)/);
    assert.ok(firewall, 'README must show the firewall rule');
    const admitted = firewall[1].split(',');
    // The hub (3051), Alloy's OTLP receiver (4318) and the Gluetun API (8000) are reached from the Pi
    // without being in the link adapter.
    for (const port of [...Object.keys(PORT_TO_SERVICE), '3051', '4318', '8000']) {
      assert.ok(admitted.includes(port), `the firewall rule must admit ${port}`);
    }
  });

  await t.test('host IPs appear only in the Hosts table', () => {
    const hosts = readme.match(/## 🖥️ Hosts\n[\s\S]*?(?=\n## )/);
    assert.ok(hosts, 'README must have a Hosts section');
    assert.match(hosts[0], /192\.168\.1\.35/);
    assert.match(hosts[0], /192\.168\.1\.20/);
    const rest = readme.replace(hosts[0], '');
    const stray = rest.match(/192\.168\.1\.\d+(?!\/\d)/g) || [];
    assert.deepEqual(stray, [], 'use lemonpi.lan / desktop-kujo8mp.lan outside the Hosts table');
  });
});
