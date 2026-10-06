const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PORT_TO_SERVICE } = require('../config/homepage/adapt-links.js');

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
  const desktop = read(path.join(ROOT_DIR, 'docker-compose.yml'));
  const envExample = read(path.join(SERVER_DIR, '.env.example'));
  const names = serviceNames(compose);
  const block = (name) => serviceBlock(compose, name);

  await t.test('runs every desktop service except Ollama, which stays on the RTX 5080', () => {
    const expected = [
      'alloy', 'bazarr', 'cleanuparr', 'flaresolverr', 'gluetun', 'grafana', 'grafana-renderer', 'household-hub',
      'jellyfin', 'jellystat', 'jellystat-db', 'loki', 'maintainerr', 'prowlarr', 'qbittorrent', 'radarr',
      'recyclarr', 'searxng', 'seerr', 'sonarr', 'watchtower',
    ];
    assert.deepEqual([...names].sort(), expected);
    assert.ok(!names.includes('ollama'));
    // Until the media disk is in, the desktop still runs the media services too.
    assert.ok(serviceNames(desktop).includes('ollama'));
    for (const name of names) {
      assert.match(block(name), new RegExp(`^    container_name:\\s*${name}\\s*$`, 'm'), `${name} keeps its container name`);
    }
  });

  await t.test('publishes the same ports the Pi\'s Caddy and Homepage already use', () => {
    const expected = {
      jellyfin: ['8096:8096'], seerr: ['5055:5055'], jellystat: ['3005:3000'], maintainerr: ['6246:6246'],
      sonarr: ['8989:8989'], radarr: ['7878:7878'], prowlarr: ['9696:9696'], bazarr: ['6767:6767'],
      flaresolverr: ['8191:8191'], gluetun: ['8080:8080', '8000:8000'], cleanuparr: ['11011:11011'],
      grafana: ['3002:3000'], 'household-hub': ['3051:3050'], alloy: ['4318:4318'],
    };
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
    assert.match(jellyfin, /image:\s*jellyfin\/jellyfin:12\.0\s*$/m);
    assert.match(jellyfin, /driver:\s*nvidia\s*\n\s*count:\s*all\s*\n\s*capabilities:\s*\[gpu\]/);
    const gpuUsers = names.filter((name) => /driver:\s*nvidia/.test(block(name)));
    assert.deepEqual(gpuUsers, ['jellyfin'], 'only Jellyfin uses the GPU on the server');
  });

  await t.test('the Hub is always on, built from the repo, and asks the desktop for the model', () => {
    const hub = block('household-hub');
    assert.doesNotMatch(hub, /profiles:/, 'the hub is not optional on the server');
    assert.match(hub, /context:\s*\.\.\/\.\.\/apps\/household-hub\/backend\s*$/m);
    assert.ok(hub.includes('- OLLAMA_BASE_URL=${OLLAMA_BASE_URL:-http://192.168.1.20:11434}'));
    assert.ok(hub.includes('- household_hub_data:/data'));
    assert.ok(hub.includes('- 127.0.0.1:3050:3050'));
    assert.deepEqual(dependsOn(hub), ['searxng']);
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

  await t.test('Watchtower is the maintained fork, pinned, without the old API workaround', () => {
    const watchtower = block('watchtower');
    // containrrr/watchtower was archived in December 2025.
    assert.match(watchtower, /image:\s*nickfedor\/watchtower:\d+\.\d+\.\d+\s*$/m);
    assert.doesNotMatch(watchtower, /DOCKER_API_VERSION/);
    assert.ok(watchtower.includes('- WATCHTOWER_LABEL_ENABLE=true'));
    assert.ok(watchtower.includes('- WATCHTOWER_SCHEDULE=${WATCHTOWER_SCHEDULE:-0 0 4 * * *}'));
    assert.doesNotMatch(watchtower, /watchtower\.enable/, 'pinned; it must not update itself');
  });

  await t.test('the log stack is pinned, with the renderer on demand', () => {
    const image = (text, name) => (serviceBlock(text, name).match(/^    image:\s*(\S+)/m) || [])[1];
    for (const name of ['loki', 'alloy', 'grafana', 'grafana-renderer']) {
      assert.match(image(compose, name), /:v?\d+\.\d+\.\d+$/, `${name} must be pinned to a release`);
    }
    // The desktop keeps an Alloy of its own, shipping here; the two must not drift apart.
    assert.equal(image(compose, 'alloy'), image(desktop, 'alloy'));
    assert.match(block('grafana-renderer'), /^    profiles:\s*\["render"\]\s*$/m);
    for (const volume of ['household_hub_data', 'loki_data', 'alloy_data', 'grafana_data']) {
      assert.match(compose, new RegExp(`^volumes:\\s*\\n[\\s\\S]*?^  ${volume}:\\s*\\n\\s+name:\\s*${volume}\\s*$`, 'm'), `${volume} keeps its fixed name`);
    }
    assert.doesNotMatch(compose, /ollama_models/, 'no Ollama volume on the server');
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

  await t.test('records why nouveau is blocked first, and how to move to other hardware', () => {
    assert.match(readme, /nouveau/);
    assert.match(readme, /## Moving to other hardware/);
  });

  await t.test('says what is live on it and what still waits for the media disk', () => {
    assert.match(readme, /## What is live/);
    const live = readme.match(/## What is live\n([\s\S]*?)(?=\n## )/)[1];
    for (const service of ['Household Hub', 'SearXNG', 'Loki', 'Grafana', 'Alloy']) {
      assert.ok(live.includes(service), `${service} is live on the server`);
    }
    assert.match(live, /media/i, 'it must say the media services are still on the desktop');
    assert.doesNotMatch(readme, /\*\*Not live yet\.\*\*/);
  });

  await t.test('explains the Hub\'s key and what changing it costs', () => {
    assert.ok(readme.includes('HUB_SECRET_KEY'));
    assert.match(readme, /signs every phone out/);
    assert.match(readme, /calendar/i);
  });

  await t.test('documents the one-port rule that lets the desktop ship its logs', () => {
    assert.ok(readme.includes('SERVER_PORT_SOURCES'));
    assert.match(readme, /3100/);
  });
});
