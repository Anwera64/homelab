const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PORT_TO_SERVICE } = require('./service-ports.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const SERVER_DIR = path.join(ROOT_DIR, 'hosts/server');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

const serviceNames = (compose) => {
  const services = compose.match(/^services:\s*\n([\s\S]*?)(?=^[a-z]|(?![\s\S]))/m);
  return services ? [...services[1].matchAll(/^  ([a-z0-9_-]+):\s*$/gm)].map((m) => m[1]) : [];
};

const serviceBlock = (compose, name) => {
  const match = compose.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [a-z0-9_-]+:\\n|^[a-z]|(?![\\s\\S]))`, 'm'));
  return match ? match[1] : '';
};

const imageOf = (block) => (block.match(/^    image:\s*(\S+)\s*$/m) || [])[1];

const portsOf = (block) => {
  const ports = block.match(/^    ports:\s*\n((?:\s+-[^\n]*\n)+)/m);
  return ports ? ports[1] : '';
};

const dependsOn = (block) => {
  const deps = block.match(/^    depends_on:\s*\n((?:      [^\n]*\n)+)/m);
  return deps ? [...deps[1].matchAll(/^      (?:- )?([a-z0-9_-]+):?\s*$/gm)].map((m) => m[1]) : [];
};

test('Server stack: the desktop services, on the always-on host', async (t) => {
  const compose = read(path.join(SERVER_DIR, 'docker-compose.yml'));
  const desktop = read(path.join(ROOT_DIR, 'hosts/desktop/docker-compose.yml'));
  const envExample = read(path.join(SERVER_DIR, '.env.example'));
  const names = serviceNames(compose);
  const block = (name) => serviceBlock(compose, name);

  await t.test('runs every desktop service, with an Ollama of its own for the embedder only', () => {
    const expected = [
      'alloy', 'bazarr', 'cleanuparr', 'flaresolverr', 'gluetun', 'grafana', 'grafana-renderer', 'household-hub',
      'jellyfin', 'jellystat', 'jellystat-db', 'loki', 'maintainerr', 'node-exporter', 'nvidia-gpu-exporter', 'ollama',
      'prometheus', 'prowlarr', 'qbittorrent', 'radarr', 'recyclarr', 'renovate', 'searxng', 'seerr', 'smartctl-exporter',
      'sonarr',
    ];
    assert.deepEqual([...names].sort(), expected);
    // The chat model stays on the desktop's RTX 5080, with a log shipper beside it.
    assert.deepEqual(serviceNames(desktop).sort(), ['alloy', 'ollama']);
    for (const name of names) {
      assert.match(block(name), new RegExp(`^    container_name:\\s*${name}\\s*$`, 'm'), `${name} keeps its container name`);
    }
  });

  await t.test('publishes the same ports the Pi\'s Caddy and Homepage already use', () => {
    const expected = {
      jellyfin: ['8096:8096'], seerr: ['5055:5055'], jellystat: ['3005:3000'], maintainerr: ['6246:6246'],
      sonarr: ['8989:8989'], radarr: ['7878:7878'], prowlarr: ['9696:9696'], bazarr: ['6767:6767'],
      gluetun: ['8080:8080', '8000:8000'], cleanuparr: ['11011:11011'],
      grafana: ['3002:3000'], 'household-hub': ['3051:3050'], alloy: ['4318:4318'],
    };
    // FlareSolverr has no login: Prowlarr reaches it inside the stack, and nothing else does.
    assert.doesNotMatch(block('flaresolverr'), /^    ports:/m, 'FlareSolverr must publish no port');
    for (const [service, mappings] of Object.entries(expected)) {
      for (const mapping of mappings) {
        assert.match(portsOf(block(service)), new RegExp(`-\\s*${mapping}\\b`), `${service} must publish ${mapping}`);
      }
    }
    const published = Object.values(expected).flat().map((m) => m.split(':')[0]);
    for (const port of Object.keys(PORT_TO_SERVICE)) {
      assert.ok(published.includes(port), `port ${port} must be published by its service`);
    }
    // For the desktop's Alloy; the firewall admits nobody else on it.
    assert.match(portsOf(block('loki')), /-\s*3100:3100\b/, 'Loki must publish 3100');
    assert.match(envExample, /^SERVER_PORT_SOURCES=3100=192\.168\.1\.20$/m);
  });

  await t.test('pins every image to a release, so the repo says what runs here', () => {
    // A full version, never latest or a bare major. A suffix may follow: Postgres' -alpine,
    // linuxserver's image build (-ls479), qBittorrent's libtorrent version (_v2.0.15).
    const pinned = /:v?\d+\.\d+(\.\d+)*([-_][a-z0-9._-]+)?$/;
    for (const name of names) {
      const image = imageOf(block(name));
      if (!image) {
        assert.match(block(name), /^    build:/m, `${name} must have an image or be built here`);
        continue;
      }
      assert.match(image, pinned, `${name}: "${image}" must be pinned to a release`);
      assert.doesNotMatch(image, /:latest$/, `${name} must not run latest`);
    }
    // qBittorrent's VueTorrent mod publishes only latest and sha- tags: the one floating reference.
    const floating = [...compose.matchAll(/(\S+:latest)\b/g)].map((m) => m[1]);
    assert.deepEqual(floating, ['DOCKER_MODS=ghcr.io/vuetorrent/vuetorrent-lsio-mod:latest']);
    // qBittorrent's current line carries the libtorrent version. The 5.1.x tags without it are an
    // old line: pinning one of those would be a downgrade.
    assert.match(imageOf(block('qbittorrent')), /:\d+\.\d+\.\d+_v\d+\.\d+\.\d+-ls\d+$/);
  });

  await t.test('caps the memory of every service: the host has 16 GB', () => {
    for (const name of names) {
      assert.match(block(name), /^    mem_limit:\s*\d+[mg]\s*$/m, `${name} must set mem_limit`);
    }
    assert.match(block('jellyfin'), /^    mem_limit:\s*4g\s*$/m);
    assert.match(block('qbittorrent'), /^    mem_limit:\s*2g\s*$/m);
  });

  await t.test('keeps a capped local log for every service, as on the desktop', () => {
    const anchor = compose.match(/^x-logging:\s*&default-logging\s*\n([\s\S]*?)(?=^[a-z])/m);
    assert.ok(anchor, 'docker-compose.yml must define x-logging: &default-logging');
    assert.match(anchor[1], /driver:\s*json-file/);
    assert.match(anchor[1], /max-size:\s*"10m"/);
    assert.match(anchor[1], /max-file:\s*"3"/);
    for (const name of names) {
      assert.match(block(name), /^    logging:\s*\*default-logging\s*$/m, `${name} must use the capped logging block`);
    }
  });

  await t.test('the media profile is exactly the services that need the media disk', () => {
    // The disk guard switches this profile: a service is in it when it mounts the disk or
    // depends on one that does.
    const mounts = names.filter((name) => /\$\{(MEDIA_ROOT|MOVIES_PATH|SHOWS_PATH)\}/.test(block(name)));
    const needsDisk = new Set(mounts);
    for (let grew = true; grew;) {
      grew = false;
      for (const name of names) {
        if (!needsDisk.has(name) && dependsOn(block(name)).some((dep) => needsDisk.has(dep))) {
          needsDisk.add(name);
          grew = true;
        }
      }
    }
    const inProfile = names.filter((name) => /^    profiles:\s*\["media"\]\s*$/m.test(block(name)));
    assert.deepEqual(inProfile.sort(), [...needsDisk].sort());
    assert.deepEqual(inProfile.sort(), ['bazarr', 'cleanuparr', 'jellyfin', 'maintainerr', 'qbittorrent', 'radarr', 'recyclarr', 'sonarr']);
    // Plain `docker compose` commands on the server still cover them.
    assert.match(envExample, /^COMPOSE_PROFILES=media$/m);
  });

  await t.test('mounts the whole disk as /data so hardlinks work, and Jellyfin keeps its library paths', () => {
    for (const name of ['qbittorrent', 'radarr', 'sonarr', 'bazarr', 'cleanuparr']) {
      assert.ok(block(name).includes('- ${MEDIA_ROOT}:/data'), `${name} must mount \${MEDIA_ROOT} at /data`);
    }
    const jellyfin = block('jellyfin');
    assert.ok(jellyfin.includes('- ${MEDIA_ROOT}:/data:ro'));
    assert.ok(jellyfin.includes('- ${MOVIES_PATH}:/media/movies:ro'));
    assert.ok(jellyfin.includes('- ${SHOWS_PATH}:/media/tv:ro'));
    // TRaSH layout on the disk.
    assert.match(envExample, /^MEDIA_ROOT=\/data$/m);
    assert.match(envExample, /^MOVIES_PATH=\/data\/media\/movies$/m);
    assert.match(envExample, /^SHOWS_PATH=\/data\/media\/tv$/m);
  });

  await t.test('Jellyfin transcodes on the laptop\'s NVIDIA GPU, pinned to the desktop\'s version', () => {
    const jellyfin = block('jellyfin');
    // Jellyfin's release tags are major.minor (12.0, 12.1); each one is a fixed release.
    assert.match(imageOf(jellyfin), /^jellyfin\/jellyfin:\d+\.\d+$/);
    assert.match(jellyfin, /driver:\s*nvidia\s*\n\s*count:\s*all\s*\n\s*capabilities:\s*\[gpu\]/);
    const gpuUsers = names.filter((name) => /driver:\s*nvidia/.test(block(name)));
    assert.deepEqual(gpuUsers, ['jellyfin', 'ollama', 'nvidia-gpu-exporter'], 'only Jellyfin, the embedder and the GPU\'s exporter use it');
  });

  await t.test('Jellyfin runs as the apps\' user, not root', () => {
    // Its image starts as root unless told otherwise. The GPU's devices are open to every user.
    assert.match(block('jellyfin'), /^    user:\s*"1000:1000"\s*$/m);
    const asRoot = names.filter((name) => /^    user:\s*"?(?:root|0)\b/m.test(block(name)));
    assert.deepEqual(asRoot, ['smartctl-exporter'], 'only the SMART exporter asks for root');
  });

  await t.test('the embedder has an Ollama on the laptop\'s GPU that only the Hub can reach', () => {
    const ollama = block('ollama');
    // Pinned, and bumped in a PR like every other image.
    assert.match(imageOf(ollama), /^ollama\/ollama:\d+\.\d+\.\d+$/);
    assert.match(ollama, /driver:\s*nvidia\s*\n\s*count:\s*all\s*\n\s*capabilities:\s*\[gpu\]/);
    assert.equal(portsOf(ollama), '', 'Ollama publishes nothing: the Hub reaches it inside the stack');
    assert.doesNotMatch(ollama, /profiles:/, 'it does not wait for the media disk');
    // Loaded through a conversation, unloaded when the house is quiet (AGENTS.md: models load on demand).
    assert.ok(ollama.includes('- OLLAMA_KEEP_ALIVE=30m'));
    assert.ok(ollama.includes('- ollama_models:/root/.ollama'));
    assert.match(ollama, /^    mem_limit:\s*2g\s*$/m);
  });

  await t.test('the Hub is always on, built from the repo, and asks the desktop for the chat model only', () => {
    const hub = block('household-hub');
    assert.doesNotMatch(hub, /profiles:/, 'the hub is not optional on the server');
    assert.match(hub, /context:\s*\.\.\/\.\.\/apps\/household-hub\/backend\s*$/m);
    assert.ok(hub.includes('- OLLAMA_BASE_URL=${OLLAMA_BASE_URL:-http://192.168.1.20:11434}'));
    // What a turn reads is embedded here, by the stack's own Ollama (#106).
    assert.ok(hub.includes('- EMBEDDING_BASE_URL=http://ollama:11434'));
    assert.ok(hub.includes('- EMBEDDING_MODEL=bge-m3'));
    assert.ok(hub.includes('- household_hub_data:/data'));
    assert.ok(hub.includes('- 127.0.0.1:3050:3050'));
    // The comment on that port names the health check where it really is.
    assert.match(hub, /127\.0\.0\.1:3050:3050\s+#[^\n]*\/api\/v1\/health/);
    // Start order only: without the embedder the Hub still answers, by keywords.
    assert.deepEqual(dependsOn(hub), ['searxng', 'ollama']);
    assert.doesNotMatch(hub, /condition:/);
    assert.doesNotMatch(block('searxng'), /profiles:/);
    assert.ok(block('searxng').includes('- SEARXNG_SECRET=${SEARXNG_SECRET}'));
  });

  await t.test('the Hub cannot start on the default signing key from the public repo', () => {
    const hub = block('household-hub');
    // In production mode the Hub refuses the built-in default and any key under 32 characters (#102).
    assert.ok(hub.includes('- ENVIRONMENT=production'));
    assert.ok(hub.includes('- SECRET_KEY=${HUB_SECRET_KEY:?set HUB_SECRET_KEY in hosts/server/.env}'), 'the key is required and has no default');
    assert.match(envExample, /^HUB_SECRET_KEY=$/m, 'the key must be present and empty in .env.example');
    assert.match(envExample, /openssl rand -hex 32/);
  });

  await t.test('the VPN kill-switch is unchanged: qBittorrent only has Gluetun\'s network', () => {
    const qbit = block('qbittorrent');
    assert.match(qbit, /network_mode:\s*"service:gluetun"/);
    assert.match(qbit, /depends_on:\s*\n\s+gluetun:\s*\n\s+condition:\s*service_healthy/);
    assert.equal(portsOf(qbit), '', 'qBittorrent publishes nothing itself');
  });

  await t.test('FlareSolverr routes through Gluetun\'s VPN network', () => {
    const flaresolverr = block('flaresolverr');
    assert.match(flaresolverr, /network_mode:\s*"service:gluetun"/, 'FlareSolverr must use Gluetun network');
    assert.match(flaresolverr, /depends_on:\s*\n\s+gluetun:\s*\n\s+condition:\s*service_healthy/, 'FlareSolverr must wait for healthy Gluetun');
    assert.doesNotMatch(flaresolverr, /dns:/, 'DNS is inherited from Gluetun');
  });

  await t.test('the VPN provider and port forwarding come from .env, and default to NordVPN without forwarding', () => {
    const gluetun = block('gluetun');
    assert.ok(gluetun.includes('- VPN_SERVICE_PROVIDER=${VPN_PROVIDER:-nordvpn}'));
    assert.ok(gluetun.includes('- VPN_PORT_FORWARDING=${VPN_PORT_FORWARDING:-off}'));
    // Only the providers with port forwarding have such servers; off is Gluetun's own default.
    assert.ok(gluetun.includes('- PORT_FORWARD_ONLY=${VPN_PORT_FORWARD_ONLY:-off}'));
    for (const [name, value] of [['VPN_PROVIDER', 'nordvpn'], ['VPN_PORT_FORWARDING', 'off'], ['VPN_PORT_FORWARD_ONLY', 'off']]) {
      assert.match(envExample, new RegExp(`^# ${name}=${value}$`, 'm'), `.env.example must show ${name} with its default`);
    }
  });

  await t.test('Gluetun enables the HTTP proxy for stack services like Prowlarr', () => {
    const gluetun = block('gluetun');
    assert.ok(gluetun.includes('- HTTPPROXY=${HTTPPROXY:-on}'), 'Gluetun must enable the HTTP proxy');
    assert.ok(gluetun.includes('- HTTPPROXY_STEALTH=${HTTPPROXY_STEALTH:-on}'), 'Gluetun must enable stealth proxying');
  });

  await t.test('Gluetun hands the forwarded port to qBittorrent, and resets it when forwarding stops', () => {
    const gluetun = block('gluetun');
    const command = (name) => (gluetun.match(new RegExp(`^      - '${name}=(.*)'$`, 'm')) || ['', ''])[1];
    const preferences = 'http://127.0.0.1:8080/api/v2/app/setPreferences';
    // qBittorrent shares Gluetun's network, so its Web UI is Gluetun's localhost.
    const up = command('VPN_PORT_FORWARDING_UP_COMMAND');
    assert.ok(up.includes('\\"listen_port\\":{{PORT}}'), 'the forwarded port becomes the listening port');
    assert.ok(up.includes('\\"current_network_interface\\":\\"{{VPN_INTERFACE}}\\"'));
    assert.ok(up.includes(preferences));
    // qBittorrent does not pick a forwarded port up again after a disconnect unless it was reset.
    const down = command('VPN_PORT_FORWARDING_DOWN_COMMAND');
    assert.ok(down.includes('\\"listen_port\\":0'));
    assert.ok(!down.includes('\\"current_network_interface\\":\\"lo\\"'), 'down command must not trap qBittorrent on loopback');
    assert.ok(down.includes(preferences));
    assert.doesNotMatch(up + down, /\$/, 'nothing for compose to interpolate');
  });

  await t.test('runs no Watchtower: pinned images change only through a merged PR', () => {
    assert.ok(!names.includes('watchtower'));
    assert.doesNotMatch(compose, /watchtower/i, 'no service carries a Watchtower label');
    assert.doesNotMatch(envExample, /WATCHTOWER/);
  });

  await t.test('Renovate runs here, one run at a time, with the repo token and nothing else', () => {
    const renovate = block('renovate');
    assert.match(imageOf(renovate), /^renovate\/renovate:\d+\.\d+\.\d+$/, 'pinned; it bumps itself in the weekly PR');
    // Its own profile: the stack never starts it, server-renovate.timer does.
    assert.match(renovate, /^    profiles:\s*\["renovate"\]\s*$/m);
    assert.match(renovate, /^    restart:\s*"no"\s*$/m, 'a run ends; nothing restarts it');
    assert.equal(portsOf(renovate), '', 'it serves nothing');
    assert.doesNotMatch(renovate, /docker\.sock/, 'it reads registries and GitHub, not this host');
    for (const line of [
      '- RENOVATE_PLATFORM=github',
      '- RENOVATE_REPOSITORIES=Anwera64/homelab',
      '- RENOVATE_TOKEN=${RENOVATE_TOKEN:-}',
      // The config is renovate.json in the repo; without one it must not open an onboarding PR.
      '- RENOVATE_ONBOARDING=false',
      '- RENOVATE_REQUIRE_CONFIG=required',
    ]) {
      assert.ok(renovate.includes(line), `renovate must set ${line}`);
    }
    assert.ok(renovate.includes('- renovate_cache:/tmp/renovate'), 'lookups are cached between runs');
    assert.match(compose, /^volumes:\s*\n[\s\S]*?^  renovate_cache:\s*\n\s+name:\s*renovate_cache\s*$/m);
    assert.match(renovate, /^    mem_limit:\s*2g\s*$/m);
    // Empty until a human creates the token; the timer skips its runs until then.
    assert.match(envExample, /^RENOVATE_TOKEN=$/m);
  });

  await t.test('the log stack is pinned, with the renderer on demand', () => {
    const image = (text, name) => (serviceBlock(text, name).match(/^    image:\s*(\S+)/m) || [])[1];
    for (const name of ['loki', 'alloy', 'grafana', 'grafana-renderer']) {
      assert.match(image(compose, name), /:v?\d+\.\d+\.\d+$/, `${name} must be pinned to a release`);
    }
    // The desktop keeps an Alloy of its own, shipping here; the two must not drift apart.
    assert.equal(image(compose, 'alloy'), image(desktop, 'alloy'));
    assert.match(block('grafana-renderer'), /^    profiles:\s*\["render"\]\s*$/m);
    for (const volume of ['household_hub_data', 'ollama_models', 'loki_data', 'alloy_data', 'grafana_data']) {
      assert.match(compose, new RegExp(`^volumes:\\s*\\n[\\s\\S]*?^  ${volume}:\\s*\\n\\s+name:\\s*${volume}\\s*$`, 'm'), `${volume} keeps its fixed name`);
    }
  });

  await t.test('the metrics stack keeps a year, and publishes nothing', () => {
    const metrics = ['prometheus', 'node-exporter', 'smartctl-exporter', 'nvidia-gpu-exporter'];
    for (const name of metrics) {
      assert.match(imageOf(block(name)) || '', /:v?\d+\.\d+\.\d+$/, `${name} must be pinned to a release`);
      // Prometheus and the exporters have no login: only the stack reaches them.
      assert.doesNotMatch(block(name), /^    ports:/m, `${name} must publish no port`);
      assert.doesNotMatch(block(name), /profiles:/, `${name} does not wait for the media disk`);
    }

    const prometheus = block('prometheus');
    assert.match(imageOf(prometheus), /^prom\/prometheus:/);
    assert.ok(prometheus.includes('- --storage.tsdb.retention.time=1y'));
    assert.ok(prometheus.includes('- --config.file=/etc/prometheus/prometheus.yml'));
    assert.ok(prometheus.includes('- prometheus_data:/prometheus'));
    // The node exporter is on the host's network: this name is how a container reaches the host.
    assert.match(prometheus, /extra_hosts:\s*\n\s+- host\.docker\.internal:host-gateway/);
    assert.match(prometheus, /^    mem_limit:\s*1g\s*$/m);
    assert.match(compose, /^volumes:\s*\n[\s\S]*?^  prometheus_data:\s*\n\s+name:\s*prometheus_data\s*$/m);

    // The host's own numbers, network traffic included: its network, its processes, its disks.
    const node = block('node-exporter');
    assert.match(imageOf(node), /^prom\/node-exporter:/);
    assert.match(node, /^    network_mode:\s*host\s*$/m);
    assert.match(node, /^    pid:\s*host\s*$/m);
    assert.ok(node.includes('- /:/host:ro,rslave'));
    assert.ok(node.includes('- --path.rootfs=/host'));
    // Where server-update.sh and server-metrics.sh leave their numbers.
    assert.ok(node.includes('- --collector.textfile.directory=/host/var/lib/node_exporter/textfile'));

    // SMART needs the raw devices. A disk in standby is left asleep, as smartd leaves it.
    const smart = block('smartctl-exporter');
    assert.match(imageOf(smart), /^prometheuscommunity\/smartctl-exporter:/);
    assert.match(smart, /^    privileged:\s*true\s*$/m);
    assert.match(smart, /^    user:\s*root\s*$/m);

    const gpu = block('nvidia-gpu-exporter');
    assert.match(imageOf(gpu), /^utkuozdemir\/nvidia_gpu_exporter:/);
    assert.match(gpu, /driver:\s*nvidia\s*\n\s*count:\s*all\s*\n\s*capabilities:\s*\[gpu\]/);

    for (const name of ['node-exporter', 'smartctl-exporter', 'nvidia-gpu-exporter']) {
      assert.match(block(name), /^    mem_limit:\s*128m\s*$/m, `${name} is capped at 128m`);
    }
  });

  await t.test('runs no ingress of its own: the Pi terminates HTTPS and routes the tailnet', () => {
    assert.doesNotMatch(compose, /^\s*(caddy|homepage|tailscale):\s*$/m);
    assert.doesNotMatch(compose, /DUCKDNS_TOKEN|HOMEPAGE_VAR_/);
  });

  await t.test('every variable the compose file reads is documented in .env.example', () => {
    const vars = new Set([...compose.matchAll(/\$\{([A-Z0-9_]+)(?::-.*?)?\}/g)].map((m) => m[1]));
    assert.ok(vars.size > 10, 'should find the compose variables');
    for (const name of vars) {
      assert.match(envExample, new RegExp(`^#?\\s*${name}=`, 'm'), `.env.example must define or document ${name}`);
    }
  });

  await t.test('.env.example is in Madrid time and holds no real secret', () => {
    assert.match(envExample, /^TZ=Europe\/Madrid$/m);
    assert.match(envExample, /^CONFIG_PATH=\/home\/anwera97\/homelab\/config$/m);
    for (const secret of ['WIREGUARD_PRIVATE_KEY', 'GLUETUN_API_KEY', 'SONARR_API_KEY', 'RADARR_API_KEY', 'SEARXNG_SECRET', 'JELLYSTAT_DB_PASSWORD', 'JELLYSTAT_JWT_SECRET']) {
      assert.match(envExample, new RegExp(`^${secret}=$`, 'm'), `${secret} must be present and empty`);
    }
  });

  await t.test('the secrets that defaulted to a known value on the desktop are required here', () => {
    assert.ok(compose.includes('${JELLYSTAT_DB_PASSWORD:?'), 'the Jellystat database password must not fall back to a default');
    assert.ok(compose.includes('${JELLYSTAT_JWT_SECRET:?'), 'the Jellystat token secret must not fall back to a default');
  });
});

test('The server compose file is validated wherever the others are', async (t) => {
  // The required secrets get a stand-in value, or the compose file would refuse to resolve.
  const validate = 'HUB_SECRET_KEY=validate docker compose -f hosts/server/docker-compose.yml --env-file hosts/server/.env.example config -q';

  await t.test('in CI', () => {
    assert.ok(read(path.join(ROOT_DIR, '.github/workflows/test.yml')).includes(validate));
  });

  await t.test('in the pre-commit hook', () => {
    assert.ok(read(path.join(ROOT_DIR, '.githooks/pre-commit')).includes(validate));
  });

  await t.test('and so is the desktop\'s, at its new place', () => {
    const ci = read(path.join(ROOT_DIR, '.github/workflows/test.yml'));
    const hook = read(path.join(ROOT_DIR, '.githooks/pre-commit'));
    assert.ok(ci.includes('docker compose -f hosts/desktop/docker-compose.yml --env-file hosts/desktop/.env.example config -q'));
    assert.match(hook, /docker compose -f hosts\/desktop\/docker-compose\.yml --env-file "\$ENV_FILE" config -q/);
    assert.match(hook, /ENV_FILE="hosts\/desktop\/\.env"/);
    for (const text of [ci, hook]) {
      assert.doesNotMatch(text, /-f docker-compose\.yml|MEDIA_ROOT=/, 'the root compose file and its media variables are gone');
    }
  });
});

test('Server README', async (t) => {
  const readme = read(path.join(SERVER_DIR, 'README.md'));

  await t.test('covers the install, the bootstrap and its host-only mode', () => {
    assert.match(readme, /Debian 13/);
    assert.ok(readme.includes('sudo hosts/server/bootstrap.sh'));
    assert.ok(readme.includes('--no-stack'));
    assert.match(readme, /reboot/i);
  });

  await t.test('explains the firewall, the allowed list and the safe way to try a change', () => {
    assert.ok(readme.includes('SERVER_ALLOWED_SOURCES'));
    assert.ok(readme.includes('server-firewall.sh --try'));
    assert.ok(readme.includes('server-firewall.sh --confirm'));
  });

  await t.test('explains the media disk, its guard and the battery rule', () => {
    assert.ok(readme.includes('DATA_DISK_UUID'));
    assert.match(readme, /\/data\/media\/movies/);
    assert.match(readme, /\/data\/torrents/);
    assert.match(readme, /10%/);
  });

  await t.test('says which user Jellyfin runs as and that it cannot change the media', () => {
    const jellyfin = (readme.match(/^Jellyfin runs as user 1000[^\n]*$/m) || [''])[0];
    assert.ok(jellyfin, 'a paragraph must start with "Jellyfin runs as user 1000"');
    assert.match(jellyfin, /read-only/);
    assert.match(jellyfin, /settings folder/, 'the stack start re-owns it');
  });

  await t.test('records why nouveau is blocked first, and how to move to other hardware', () => {
    assert.match(readme, /nouveau/);
    assert.match(readme, /## Moving to other hardware/);
  });

  await t.test('says what is live on it, the media services included', () => {
    assert.match(readme, /## What is live/);
    const live = readme.match(/## What is live\n([\s\S]*?)(?=\n## )/)[1];
    for (const service of ['Household Hub', 'SearXNG', 'Loki', 'Grafana', 'Alloy', 'Jellyfin', 'qBittorrent', 'Sonarr', 'Radarr']) {
      assert.ok(live.includes(service), `${service} is live on the server`);
    }
    assert.doesNotMatch(readme, /Still on the desktop|leaves them stopped here|It will run everything/, 'the media services have moved');
    assert.doesNotMatch(readme, /\*\*Not live yet\.\*\*/);
  });

  await t.test('says the embedder runs here while the chat model still needs the desktop', () => {
    const live = readme.match(/## What is live\n([\s\S]*?)(?=\n## )/)[1];
    assert.match(live, /bge-m3/, 'the embedder is live on the server');
    assert.match(live, /chat model needs the desktop awake/);
    assert.doesNotMatch(readme, /except Ollama/, 'the server runs an Ollama now');
    // Two services hold the GPU now; a move to other hardware touches both.
    const moving = readme.match(/## Moving to other hardware\n([\s\S]*?)(?=\n## |(?![\s\S]))/)[1];
    assert.match(moving, /Ollama/);
  });

  await t.test('explains the Hub\'s key and what changing it costs', () => {
    assert.ok(readme.includes('HUB_SECRET_KEY'));
    assert.match(readme, /signs every phone out/);
    assert.match(readme, /calendar/i);
  });

  await t.test('has a login checklist for every app, because the firewall does not guard the https:// names', () => {
    const section = readme.match(/## App logins\n([\s\S]*?)(?=\n## |(?![\s\S]))/);
    assert.ok(section, 'the README must have an App logins section');
    assert.match(section[1], /lemonpi/, 'it must say the apps see every request coming from lemonpi');
    const items = section[1].split('\n').filter((line) => line.startsWith('- [ ] '));
    for (const app of ['Sonarr', 'Radarr', 'Prowlarr', 'Bazarr', 'qBittorrent', 'Cleanuparr', 'Seerr', 'Jellystat', 'Maintainerr', 'FlareSolverr']) {
      assert.ok(items.some((item) => item.includes(app)), `the checklist must have an open item for ${app}`);
    }
    assert.match(section[1], /Authentication Required/, 'it must name the arr setting');
    assert.match(section[1], /Disable Auth for Local Addresses/, 'it must name the Cleanuparr switch');
    assert.match(section[1], /MAINTAINERR_PASSWORD_HASH/, 'it must say where the Maintainerr password lives');
    // The password is on the name only, so the direct port must stay closed: the server's firewall does that.
    const maintainerr = items.find((item) => item.includes('Maintainerr has no login'));
    assert.match(maintainerr, /direct port \(6246\) must stay closed: the firewall here does that/);
    assert.doesNotMatch(maintainerr, /desktop|Windows/, 'Maintainerr no longer runs on the desktop');
    assert.doesNotMatch(maintainerr, /answers the whole LAN/);
  });

  await t.test('says why the Wi-Fi is moved to 5 GHz, how to see it and how to switch it off', () => {
    assert.match(readme, /^\| Wi-Fi band \(systemd timer\) \|.*5 GHz/m, 'a row in "What runs here"');
    const section = readme.match(/## Wi-Fi\n([\s\S]*?)(?=\n## |(?![\s\S]))/);
    assert.ok(section, 'the README must have a Wi-Fi section');
    assert.match(section[1], /2\.4 GHz/);
    assert.match(section[1], /fallback/, '2.4 GHz stays available');
    assert.ok(section[1].includes('journalctl -u server-wifi-band'), 'where each move is written');
    assert.ok(section[1].includes('iw dev wlp7s0 link'), 'how to see the band');
    assert.ok(section[1].includes('systemctl disable --now server-wifi-band.timer'), 'how to switch it off');
    assert.doesNotMatch(section[1], /\/etc\/network\/interfaces[^.]*\bedit/, 'it changes no settings file');
  });

  await t.test('says how to change the VPN provider, and what port forwarding asks of qBittorrent\'s login', () => {
    const section = readme.match(/## VPN provider\n([\s\S]*?)(?=\n## |(?![\s\S]))/);
    assert.ok(section, 'the README must have a VPN provider section');
    for (const name of ['VPN_PROVIDER', 'WIREGUARD_PRIVATE_KEY', 'VPN_PORT_FORWARDING', 'VPN_PORT_FORWARD_ONLY']) {
      assert.ok(section[1].includes(`\`${name}\``), `it must name ${name}`);
    }
    assert.match(section[1], /NAT-PMP/, 'the ProtonVPN key must be made with port forwarding on');
    // qBittorrent has Gluetun's network: one recreated without the other loses it.
    assert.match(section[1], /docker compose up -d --force-recreate gluetun qbittorrent/);
    assert.match(section[1], /Bypass authentication for clients on localhost/);
    // The checklist no longer says to untick both boxes whatever the VPN does.
    const logins = readme.match(/## App logins\n([\s\S]*?)(?=\n## |(?![\s\S]))/)[1];
    const qbit = logins.split('\n').find((line) => line.startsWith('- [ ] qBittorrent'));
    assert.match(qbit, /whitelisted subnets/);
    assert.match(qbit, /port forwarding/, 'the localhost box depends on port forwarding');
  });

  await t.test('says what the Downloads dashboard shows, where its numbers come from and when they stop', () => {
    const section = readme.match(/## The downloads dashboard\n([\s\S]*?)(?=\n## |(?![\s\S]))/);
    assert.ok(section, 'the README must have a section for the downloads dashboard');
    assert.ok(section[1].includes('config/grafana/dashboards/downloads.json'));
    for (const shown of ['Average download', 'Peak download', 'Average upload', 'Wi-Fi band', 'Connectable', 'Peer connections']) {
      assert.ok(section[1].includes(shown), `it must name ${shown}`);
    }
    assert.match(section[1], /megabits/i);
    // The numbers are qBittorrent's own, read without a login through Gluetun.
    assert.match(section[1], /Bypass authentication for clients on localhost/);
    assert.match(section[1], /server_qbittorrent_up/, 'how to tell that qBittorrent stopped answering');
    assert.match(section[1], /once a minute/);
  });

  await t.test('says where the machine\'s own numbers are kept and where to look at them', () => {
    // In the "What runs here" table: the store and its exporters, and the timer behind the host's numbers.
    assert.match(readme, /^\| Metrics \(Docker\) \|.*Prometheus.*a year/m);
    assert.match(readme, /^\| Metrics \(Docker\) \|.*node exporter.*SMART.*GPU/m);
    assert.match(readme, /^\| Host metrics \(systemd timer\) \|.*every minute/m);
    // A section of its own for the dashboard.
    assert.match(readme, /^## The server dashboard$/m);
    assert.ok(readme.includes('config/grafana/dashboards/server.json'));
    assert.match(readme, /Power, Temperatures, Load, Activity, Disks,? and Updates/);
    // Editable, but the file wins: the one thing to know before changing it in Grafana.
    assert.match(readme, /saved? in Grafana[^.]*\.[^\n]*next change to the file replaces/i);
    assert.match(readme, /amber at 55 °C and red at 60 °C/);
    assert.match(readme, /65 °C/);
    // None of the four has a login, so none is reachable from the LAN.
    assert.match(readme, /publish(es)? no port/);
  });

  await t.test('documents the one-port rule that lets the desktop ship its logs', () => {
    assert.ok(readme.includes('SERVER_PORT_SOURCES'));
    assert.match(readme, /3100/);
  });
});
