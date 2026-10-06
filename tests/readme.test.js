const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PORT_TO_SERVICE } = require('./service-ports.js');

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

  await t.test('points to the server README and lists the server files in the tree', () => {
    assert.ok(readme.includes('hosts/server/README.md'));
    const paths = treePaths(readme);
    for (const p of ['hosts/server/README.md', 'hosts/server/bootstrap.sh', 'hosts/server/docker-compose.yml', 'hosts/server/.env.example', 'hosts/server/system', 'tests/server-stack.test.js', 'tests/server-bootstrap.test.js']) {
      assert.ok(paths.includes(p), `the tree must list ${p}`);
    }
    assert.match(readme, /`docker compose config` for the desktop, Pi and server stacks/);
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

  await t.test('the log stack is on the server in the Hosts table, the graph, the endpoints and the tree', () => {
    const serverRow = readme.match(/^\| \*\*Server\*\*.*$/m);
    assert.ok(serverRow, 'the Hosts table must have a Server row');
    for (const service of ['Household Hub', 'SearXNG', 'Loki', 'Alloy', 'Grafana']) {
      assert.ok(serverRow[0].includes(service), `the Server row must name ${service}`);
    }
    const desktopRow = readme.match(/^\| \*\*Desktop\*\*.*$/m);
    assert.ok(desktopRow, 'the Hosts table must have a Desktop row');
    assert.ok(desktopRow[0].includes('Ollama'));
    // The desktop keeps a collector for good, beside Ollama.
    assert.ok(desktopRow[0].includes('Alloy'));
    for (const moved of ['Loki', 'Grafana', 'Household Hub', 'SearXNG']) {
      assert.ok(!desktopRow[0].includes(moved), `${moved} no longer runs on the desktop`);
    }

    const graph = readme.match(/```mermaid\n([\s\S]*?)```/);
    assert.ok(graph, 'README must have the network flow graph');
    const server = graph[1].match(/subgraph Server \[[^\]]*\]\n([\s\S]*?)\n    end/);
    assert.ok(server, 'the graph must have a Server subgraph');
    const logs = server[1].match(/subgraph Logs \[[^\]]*\]\n([\s\S]*?)\n\s*end/);
    assert.ok(logs, 'the Logs subgraph must be inside the server');
    assert.match(logs[1], /ALLOY\[[^\]]*Alloy[^\]]*\] --> LOKI\[[^\]]*Loki[^\]]*30 days[^\]]*\] --> GRAFANA\[[^\]]*Grafana[^\]]*\]/);
    assert.match(server[1], /HUB\[[^\]]*Household Hub[^\]]*\]/, 'the Hub must be inside the server');
    assert.match(graph[1], /Server -\.->\|Docker socket\| ALLOY/, 'the graph must show Alloy reading the server\'s Docker socket');
    assert.match(graph[1], /DALLOY\[[^\]]*Alloy[^\]]*\] -->\|ships logs\| LOKI/, 'the desktop\'s Alloy must ship to the server\'s Loki');
    assert.match(graph[1], /HUB -->\|LAN\| OLLAMA/, 'the Hub must reach Ollama on the desktop');
    assert.match(graph[1], /CADDY -->\|published LAN ports\|[^\n]*\bGRAFANA\b/, 'Caddy must route to Grafana in the graph');

    assert.match(readme, /^\| \*\*Grafana\*\* \| Server \| `https:\/\/grafana\.spicy-llama\.duckdns\.org` \| Log search/m);
    assert.match(readme, /^\| \*\*Loki\*\* \| Server \| — \| [^|]*port 3100, the desktop's Alloy only/m);
    assert.match(readme, /^\| \*\*Household Hub\*\* \| Server \| `https:\/\/hub\.spicy-llama\.duckdns\.org` \| Family assistant backend/m);
    assert.match(readme, /^\| \*\*Ollama\*\* \| Desktop \(AI profile\) \| — \| [^|]*port 11434, the server only/m);

    const paths = treePaths(readme);
    for (const p of ['config/loki/loki-config.yaml', 'config/alloy/config.alloy', 'config/grafana/provisioning/datasources/loki.yaml']) {
      assert.ok(paths.includes(p), `the tree must list ${p}`);
    }
  });
  await t.test('says how to start the dashboard image renderer, which does not run by itself', () => {
    assert.ok(readme.includes('docker compose --profile render up -d grafana-renderer'), 'README must show how to start the renderer');
    assert.ok(readme.includes('docker compose --profile render stop grafana-renderer'), 'README must show how to stop it');
    const serverRow = readme.match(/^\| \*\*Server\*\*.*$/m)[0];
    assert.match(serverRow, /image renderer \(on demand\)/, 'the Server row must list the renderer as on demand');
  });
  await t.test('says how to search the logs, what is kept and which ports the desktop opens', () => {
    assert.ok(readme.includes('{container="household-hub"}'), 'README must show a LogQL query by container');
    assert.ok(readme.includes('{host="desktop", container="ollama"}'), 'README must show how to find the desktop\'s lines');
    assert.match(readme, /30 days/);
    assert.ok(readme.includes('logs=off'), 'README must name the opt-out label');
    assert.match(readme, /calendar titles and note text/, 'README must say what the hub\'s lines carry');
    const firewall = readme.match(/Set-NetFirewallRule[^\n]*-LocalPort ([\d,]+)/);
    assert.ok(firewall, 'README must show the firewall rule for the media ports');
    const ports = firewall[1].split(',');
    assert.ok(ports.includes('8096'), 'the rule must still admit Jellyfin');
    for (const moved of ['3002', '3051', '4318', '11434']) {
      assert.ok(!ports.includes(moved), `the LAN-wide rule must not admit ${moved}`);
    }
    // Ollama is for the server alone.
    assert.match(readme, /New-NetFirewallRule[^\n]*-LocalPort 11434[^\n]*-RemoteAddress 192\.168\.1\.30/);
  });
  await t.test('the telemetry route is in the endpoints and the graph', () => {
    assert.match(readme, /^\| \*\*Alloy\*\* \| Server \| `https:\/\/telemetry\.spicy-llama\.duckdns\.org` \| Ships/m);
    const graph = readme.match(/```mermaid\n([\s\S]*?)```/)[1];
    assert.match(graph, /CADDY -->\|telemetry, token checked by the hub\| ALLOY/);
    assert.ok(readme.includes('{service_name="household-hub-app"}'), 'README must show how to find the app\'s lines');
  });
  await t.test('the firewall rule admits every port the Pi proxies to on the desktop', () => {
    // -LocalPort replaces the whole list, so a port missing here is closed by copying the command
    // (Cleanuparr's 11011 was, and its HTTPS name answered 502).
    const firewall = readme.match(/Set-NetFirewallRule[^\n]*-LocalPort ([\d,]+)/);
    assert.ok(firewall, 'README must show the firewall rule');
    const admitted = firewall[1].split(',');
    // Grafana (3002) moved to the server, with the hub and Alloy's receiver.
    const onServer = ['3002'];
    const onDesktop = Object.keys(PORT_TO_SERVICE).filter((port) => !onServer.includes(port));
    // The Gluetun API (8000) is reached from the Pi without being in the service table.
    for (const port of [...onDesktop, '8000']) {
      assert.ok(admitted.includes(port), `the firewall rule must admit ${port}`);
    }
  });

  await t.test('the endpoints table gives the https:// name only: direct ports are not a way in', () => {
    const endpoints = readme.match(/## 🌐 Service Endpoints\n([\s\S]*?)\n---/);
    assert.ok(endpoints, 'README must have a Service Endpoints section');
    const rows = endpoints[1].split('\n').filter((line) => line.startsWith('|'));
    assert.equal(rows[0], '| Service | Host | HTTPS (home + Tailscale) | Role |');
    assert.ok(rows.length > 20, 'the table should list the services');
    for (const row of rows) {
      assert.equal(row.split('|').length - 2, 4, `this row must have four cells: ${row}`);
    }
    assert.ok(!readme.includes('Local HTTP'), 'the Local HTTP column was removed');
    assert.doesNotMatch(endpoints[1], /http:\/\/[\w.-]+\.lan/, 'no direct .lan address in the endpoints');
  });

  await t.test('host IPs appear only in the Hosts table, and in the one firewall rule that needs an address', () => {
    const hosts = readme.match(/## 🖥️ Hosts\n[\s\S]*?(?=\n## )/);
    assert.ok(hosts, 'README must have a Hosts section');
    for (const ip of ['192.168.1.35', '192.168.1.20', '192.168.1.30']) {
      assert.ok(hosts[0].includes(ip), `the Hosts table must give ${ip}`);
    }
    const rest = readme.replace(hosts[0], '').split('\n').filter((line) => !line.includes('-RemoteAddress')).join('\n');
    const stray = rest.match(/192\.168\.1\.\d+(?!\/\d)/g) || [];
    assert.deepEqual(stray, [], 'use lemonpi.lan / server.lan / desktop-kujo8mp.lan outside the Hosts table');
  });
});
