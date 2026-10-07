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

  await t.test('says who updates what: Renovate PRs for every host, and no Watchtower anywhere', () => {
    const serverRow = readme.match(/^\| \*\*Server\*\*.*$/m)[0];
    assert.match(serverRow, /nightly update/, 'the Server row must name the nightly update');
    assert.doesNotMatch(readme, /Watchtower/, 'nothing runs Watchtower any more');
    // The desktop's two images are pinned; a merged bump reaches it on the next pull and start.
    assert.match(readme, /`git pull`[^\n]*`startup_homelab\.ps1`/, 'README must say how a merged bump reaches the desktop');
    // Self-hosted: a container on the server, not the GitHub app.
    assert.match(readme, /^\| \*\*Renovate\*\* \| Server \| — \| [^|]*the hub[^|]*\|$/m);
    assert.match(serverRow, /Renovate/, 'the Server row must name Renovate');
    assert.match(readme, /^\| \*\*Nightly update\*\* \| Server \| — \| [^|]*4 AM[^|]*\|$/m);
    const paths = treePaths(readme);
    for (const p of ['renovate.json', 'tests/renovate.test.js', '.github/workflows/household-hub-backend.yml', 'tests/backend-workflow.test.js']) {
      assert.ok(paths.includes(p), `the tree must list ${p}`);
    }
    // The Pi's pins are Renovate's too.
    assert.match(readme, /Image versions are pinned in `hosts\/pi\/docker-compose\.yml`; Renovate opens the bump PRs\./);
  });

  await t.test('the server README describes the bump PRs and the nightly update', () => {
    const server = read('hosts/server/README.md');
    assert.doesNotMatch(server, /Watchtower/);
    assert.match(server, /^\| Nightly update \(systemd timer\) \| [^|]*04:00[^|]*\|$/m);
    const updating = server.match(/^## Updating\n([\s\S]*?)(?=^## )/m);
    assert.ok(updating, 'the server README must have an Updating section');
    for (const needle of ['Renovate', 'server-update.timer', 'journalctl -u server-update', 'sudo hosts/server/bootstrap.sh', 'Jellyfin', 'Postgres', "The Hub's Python packages"]) {
      assert.ok(updating[1].includes(needle), `Updating must mention ${needle}`);
    }
    // Renovate runs here: the token it needs, how to run it by hand, and how to rehearse a run.
    assert.match(server, /^\| Renovate \(Docker, systemd timer\) \| [^|]*every hour[^|]*\|$/m);
    for (const needle of [
      'RENOVATE_TOKEN', 'Contents', 'Pull requests', 'Issues', 'Commit statuses', 'Workflows', 'Dependabot alerts', 'server-renovate.timer',
      'sudo systemctl start server-renovate', 'journalctl -u server-renovate', 'RENOVATE_DRY_RUN=full',
    ]) {
      assert.ok(updating[1].includes(needle), `Updating must mention ${needle}`);
    }
  });

  await t.test('lists the hub backend CI and says how its lock files are regenerated', () => {
    const paths = treePaths(readme);
    for (const p of ['.github/workflows/household-hub-backend.yml', 'tests/backend-workflow.test.js']) {
      assert.ok(paths.includes(p), `the tree must list ${p}`);
    }
    const backend = read('apps/household-hub/backend/README.md');
    assert.ok(backend.includes('pip install -r requirements-dev.txt'));
    assert.ok(backend.includes('uv pip compile requirements.in --output-file=requirements.txt --python-version=3.12 --universal'));
    assert.ok(backend.includes('uv pip compile requirements-dev.in --output-file=requirements-dev.txt --python-version=3.12 --universal'));
  });

  await t.test('the desktop section matches its scripts: no flags, found by name through PATH', () => {
    const script = read('hosts/desktop/startup_homelab.ps1');
    const params = new Set([...script.matchAll(/\[switch\]\$(\w+)/g)].map((m) => m[1]));
    const used = [...readme.matchAll(/startup_homelab\.ps1 -(\w+)/g)].map((m) => m[1]);
    for (const flag of used) {
      assert.ok(params.has(flag), `-${flag} is not a parameter of startup_homelab.ps1`);
    }
    const paths = treePaths(readme);
    for (const p of ['hosts/desktop/README.md', 'hosts/desktop/docker-compose.yml', 'hosts/desktop/.env.example', 'hosts/desktop/startup_homelab.ps1', 'hosts/desktop/stop_homelab.ps1', 'hosts/desktop/compact_docker_disk.ps1', 'hosts/desktop/enable_virtualization.ps1']) {
      assert.ok(paths.includes(p), `the tree must list ${p}`);
    }
    assert.ok(readme.includes('hosts/desktop/README.md'), 'README must point to the desktop README');
    const desktop = read('hosts/desktop/README.md');
    // The scripts are run by name from any folder.
    assert.match(desktop, /hosts\\desktop/, 'the desktop README must give the folder to add to PATH');
    assert.match(desktop, /\[Environment\]::SetEnvironmentVariable\('Path'/, 'and the command that adds it');
    for (const needle of ['startup_homelab.ps1', 'stop_homelab.ps1', 'compact_docker_disk.ps1', 'New-NetFirewallRule -DisplayName "Homelab Ollama (server)"', 'config/ollama-models/models.json']) {
      assert.ok(desktop.includes(needle), `the desktop README must mention ${needle}`);
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
    for (const moved of ['Loki', 'Grafana', 'Household Hub', 'SearXNG', 'Jellyfin', 'Sonarr', 'Radarr', 'qBittorrent', 'Gluetun', 'Seerr']) {
      assert.ok(!desktopRow[0].includes(moved), `${moved} no longer runs on the desktop`);
    }
    assert.ok(desktopRow[0].includes('hosts/desktop/'), 'the Desktop row must link its folder');
    for (const media of ['Jellyfin', 'Seerr', 'Sonarr', 'Radarr', 'qBittorrent', 'Gluetun']) {
      assert.ok(serverRow[0].includes(media), `the Server row must name ${media}`);
    }
    assert.doesNotMatch(readme, /once its (media )?disk is (in|installed)/, 'the media services have moved');
    // In the endpoints, only Ollama and its log shipper are on the desktop.
    const onDesktop = [...readme.matchAll(/^\| \*\*([^*]+)\*\* \| Desktop[^|]*\|/gm)].map((m) => m[1]).sort();
    assert.deepEqual(onDesktop, ['Alloy', 'Ollama']);

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
    assert.match(readme, /^\| \*\*Ollama\*\* \| Desktop \| — \| [^|]*port 11434, the server only/m);

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
    // The desktop opens Ollama's port, for the server alone.
    assert.match(readme, /New-NetFirewallRule[^\n]*-LocalPort 11434[^\n]*-RemoteAddress 192\.168\.1\.30/);
  });
  await t.test('the telemetry route is in the endpoints and the graph', () => {
    assert.match(readme, /^\| \*\*Alloy\*\* \| Server \| `https:\/\/telemetry\.spicy-llama\.duckdns\.org` \| Ships/m);
    const graph = readme.match(/```mermaid\n([\s\S]*?)```/)[1];
    assert.match(graph, /CADDY -->\|telemetry, token checked by the hub\| ALLOY/);
    assert.ok(readme.includes('{service_name="household-hub-app"}'), 'README must show how to find the app\'s lines');
  });
  await t.test('the desktop opens one port, for the server: the media rule is gone', () => {
    // The media ports left with the media services. Only Ollama answers, and only the server.
    assert.match(readme, /New-NetFirewallRule -DisplayName "Homelab Ollama \(server\)"[^\n]*-LocalPort 11434 -RemoteAddress 192\.168\.1\.30/);
    assert.doesNotMatch(readme, /Set-NetFirewallRule/, 'no rule is left to widen');
    assert.match(readme, /Remove-NetFirewallRule -DisplayName "Homelab Stack \(LAN\)"/, 'README must say how to remove the old media rule');
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
    // FlareSolverr has no login, so it has no name and no open port.
    assert.ok(!readme.includes('flaresolverr.spicy-llama.duckdns.org'), 'FlareSolverr has no https:// name');
    assert.doesNotMatch(readme, /8191/, 'FlareSolverr\'s port is published nowhere');
    assert.doesNotMatch(readme, /or LAN devices need/, 'LAN devices do not reach the service ports directly');
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
