const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { SERVICE_PORTS, PORT_TO_SERVICE } = require('../config/homepage/adapt-links.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const DOCKER_COMPOSE_PATH = path.join(ROOT_DIR, 'docker-compose.yml');
const SERVICES_YAML_PATH = path.join(ROOT_DIR, 'config/homepage/services.yaml');
const BOOKMARKS_YAML_PATH = path.join(ROOT_DIR, 'config/homepage/bookmarks.yaml');
const ENV_EXAMPLE_PATH = path.join(ROOT_DIR, '.env.example');
const SEARXNG_SETTINGS_PATH = path.join(ROOT_DIR, 'config/searxng/settings.yml');
const OLLAMA_MODELS_DIR = path.join(ROOT_DIR, 'config/ollama-models');

test('Cross-Configuration & Infrastructure Integrity Suite', async (t) => {
  const dockerComposeContent = fs.readFileSync(DOCKER_COMPOSE_PATH, 'utf8');
  const servicesYamlContent = fs.readFileSync(SERVICES_YAML_PATH, 'utf8');
  const bookmarksYamlContent = fs.readFileSync(BOOKMARKS_YAML_PATH, 'utf8');
  const envExampleContent = fs.readFileSync(ENV_EXAMPLE_PATH, 'utf8');
  const searxngSettingsContent = fs.readFileSync(SEARXNG_SETTINGS_PATH, 'utf8');

  // A tracked config file; a missing one reads as empty so its case fails with its own message.
  const readConfig = (file) => {
    const full = path.join(ROOT_DIR, file);
    return fs.existsSync(full) ? fs.readFileSync(full, 'utf8').replace(/\r\n/g, '\n') : '';
  };

  // Body of one root compose service.
  const composeService = (name) => {
    const block = dockerComposeContent.match(new RegExp(`^  ${name}:\\r?\\n([\\s\\S]*?)(?=^  [a-z0-9_-]+:\\r?\\n|^[a-z]|(?![\\s\\S]))`, 'm'));
    assert.ok(block, `docker-compose.yml must define ${name}`);
    return block[1];
  };

  // Ports block of one root compose service.
  const composePorts = (name) => {
    const ports = composeService(name).match(/ports:\s*\r?\n((?:\s+-[^\n]*\n)+)/);
    return ports ? ports[1] : '';
  };

  // A named volume declared at the root with a fixed name.
  const declaresVolume = (name) =>
    new RegExp(`^volumes:\\s*\\n[\\s\\S]*?^  ${name}:\\s*\\n\\s+name:\\s*${name}\\s*$`, 'm').test(dockerComposeContent);

  await t.test('The desktop runs no Caddy: the Pi terminates HTTPS and the containers publish their own ports', () => {
    assert.doesNotMatch(dockerComposeContent, /^\s*caddy:\s*$/m, 'no caddy service on the desktop');
    assert.ok(!fs.existsSync(path.join(ROOT_DIR, 'config/caddy/Caddyfile')), 'config/caddy/Caddyfile must be gone');
    assert.doesNotMatch(dockerComposeContent, /DUCKDNS_TOKEN/, 'only the Pi needs the DuckDNS token');
    for (const port of ['80', '443', '3000', '3080', '3001']) {
      assert.doesNotMatch(dockerComposeContent, new RegExp(`^\\s+-\\s*${port}:`, 'm'), `nothing on the desktop publishes ${port} any more`);
    }
  });

  await t.test('Each desktop service publishes its own LAN port, the ones the Pi and Homepage use', () => {
    const expected = {
      jellyfin: ['8096:8096'], seerr: ['5055:5055'], jellystat: ['3005:3000'], maintainerr: ['6246:6246'],
      sonarr: ['8989:8989'], radarr: ['7878:7878'], prowlarr: ['9696:9696'], bazarr: ['6767:6767'],
      flaresolverr: ['8191:8191'], gluetun: ['8080:8080', '8000:8000'], cleanuparr: ['11011:11011'],
      grafana: ['3002:3000'],
    };
    for (const [service, mappings] of Object.entries(expected)) {
      const ports = composePorts(service);
      for (const mapping of mappings) {
        assert.match(ports, new RegExp(`-\\s*${mapping}\\b`), `${service} must publish ${mapping}`);
      }
    }
    // Every port the link adapter knows is covered by the table above.
    const published = Object.values(expected).flat().map((m) => m.split(':')[0]);
    for (const port of Object.keys(PORT_TO_SERVICE)) {
      assert.ok(published.includes(port), `port ${port} must be published by its service`);
    }
  });
  await t.test('Cleanuparr cleans the download queue and sees the torrents at qBittorrent\'s own path', () => {
    const block = dockerComposeContent.match(/^  cleanuparr:\r?\n([\s\S]*?)(?=^  [a-z0-9_-]+:\r?\n|^[a-z]|(?![\s\S]))/m);
    assert.ok(block, 'docker-compose.yml must define cleanuparr');
    const svc = block[1];
    assert.match(svc, /image:\s*ghcr\.io\/cleanuparr\/cleanuparr:/);
    assert.match(svc, /-\s*\$\{CONFIG_PATH\}\/cleanuparr:\/config/);
    // Hardlink detection needs the same path qBittorrent downloads to.
    assert.match(svc, /-\s*\$\{MEDIA_ROOT\}:\/data/);
    assert.match(svc, /-\s*PORT=11011/);
    assert.match(svc, /-\s*PUID=1000/);
    assert.match(svc, /-\s*PGID=1000/);
    assert.match(svc, /-\s*TZ=\$\{TZ/);
    assert.match(svc, /com\.centurylinklabs\.watchtower\.enable=true/);
    assert.match(svc, /restart:\s*unless-stopped/);
    for (const dep of ['sonarr', 'radarr', 'qbittorrent']) {
      assert.match(svc, new RegExp(`depends_on:[\\s\\S]*-\\s*${dep}\\b`), `cleanuparr must start after ${dep}`);
    }
  });

  await t.test('Logs outlive their containers: Loki, Alloy and Grafana run beside the stack', () => {
    const loki = composeService('loki');
    const alloy = composeService('alloy');
    const grafana = composeService('grafana');

    // Pinned to a release and bumped in a PR, so Watchtower leaves them alone.
    assert.match(loki, /image:\s*grafana\/loki:\d+\.\d+\.\d+\s*$/m);
    assert.match(alloy, /image:\s*grafana\/alloy:v\d+\.\d+\.\d+\s*$/m);
    assert.match(grafana, /image:\s*grafana\/grafana:\d+\.\d+\.\d+\s*$/m);
    for (const [name, svc] of Object.entries({ loki, alloy, grafana })) {
      assert.doesNotMatch(svc, /watchtower\.enable/, `${name} is pinned; Watchtower must not update it`);
      // Logs are collected whatever is started: -NoAI and -ArrOnly included.
      assert.doesNotMatch(svc, /profiles:/, `${name} must be in the default profile`);
      assert.match(svc, new RegExp(`container_name:\\s*${name}\\s*$`, 'm'));
      assert.match(svc, /restart:\s*unless-stopped/);
    }

    assert.match(loki, /command:\s*-config\.file=\/etc\/loki\/loki-config\.yaml/);
    assert.ok(loki.includes('- ${CONFIG_PATH}/loki/loki-config.yaml:/etc/loki/loki-config.yaml:ro'));
    assert.ok(loki.includes('- loki_data:/loki'), 'Loki must keep its logs in the loki_data volume');

    assert.match(alloy, /command:\s*run --storage\.path=\/var\/lib\/alloy\/data \/etc\/alloy\/config\.alloy/);
    assert.ok(alloy.includes('- ${CONFIG_PATH}/alloy/config.alloy:/etc/alloy/config.alloy:ro'));
    assert.ok(alloy.includes('- /var/run/docker.sock:/var/run/docker.sock:ro'), 'Alloy only reads the Docker socket');
    // Read positions: a restart of Alloy neither re-sends nor skips lines.
    assert.ok(alloy.includes('- alloy_data:/var/lib/alloy/data'));
    assert.match(alloy, /depends_on:\s*\n\s+- loki\b/);

    // Only the data sources folder: mounting all of provisioning/ hides the image's other
    // folders and Grafana logs an error for each one at every start.
    assert.ok(grafana.includes('- ${CONFIG_PATH}/grafana/provisioning/datasources:/etc/grafana/provisioning/datasources:ro'));
    assert.ok(grafana.includes('- grafana_data:/var/lib/grafana'));
    assert.ok(grafana.includes('- GF_SECURITY_ADMIN_USER=${GRAFANA_ADMIN_USER:-admin}'));
    assert.ok(grafana.includes('- GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-admin}'));
    assert.ok(grafana.includes('- GF_SERVER_ROOT_URL=https://grafana.${DOMAIN_NAME:-spicy-llama.duckdns.org}'));
    assert.ok(grafana.includes('- GF_ANALYTICS_REPORTING_ENABLED=false'));
    assert.match(grafana, /-\s*TZ=\$\{TZ/);
    assert.match(grafana, /depends_on:\s*\n\s+- loki\b/);

    // Only Grafana is reachable, and it asks for a login; Loki has none of its own.
    assert.equal(composePorts('loki'), '', 'Loki must not publish a port');
    assert.equal(composePorts('alloy'), '', 'Alloy must not publish a port');

    for (const volume of ['loki_data', 'alloy_data', 'grafana_data']) {
      assert.ok(declaresVolume(volume), `docker-compose.yml must declare the ${volume} volume with a fixed name`);
    }
  });

  await t.test('Loki keeps 30 days on its volume and drops a runaway stream instead of filling the disk', () => {
    const loki = readConfig('config/loki/loki-config.yaml');
    assert.match(loki, /^auth_enabled:\s*false\s*$/m);
    assert.match(loki, /path_prefix:\s*\/loki\s*$/m);
    assert.match(loki, /chunks_directory:\s*\/loki\/chunks\s*$/m);
    assert.match(loki, /store:\s*inmemory/);
    // Retention needs the tsdb index in 24h periods.
    assert.match(loki, /store:\s*tsdb/);
    assert.match(loki, /object_store:\s*filesystem/);
    assert.match(loki, /period:\s*24h/);
    const compactor = loki.match(/^compactor:\s*\n((?:  .*\n)+)/m);
    assert.ok(compactor, 'loki-config.yaml must configure the compactor');
    assert.match(compactor[1], /retention_enabled:\s*true/);
    assert.match(compactor[1], /delete_request_store:\s*filesystem/);
    assert.match(compactor[1], /working_directory:\s*\/loki\//);
    const limits = loki.match(/^limits_config:\s*\n((?:  .*\n)+)/m);
    assert.ok(limits, 'loki-config.yaml must set limits_config');
    assert.match(limits[1], /retention_period:\s*720h/);
    assert.match(limits[1], /per_stream_rate_limit:\s*1MB/);
    assert.match(limits[1], /per_stream_rate_limit_burst:\s*5MB/);
    assert.match(limits[1], /ingestion_rate_mb:\s*4\s*$/m);
    assert.match(loki, /^analytics:\s*\n\s+reporting_enabled:\s*false/m);
  });

  await t.test('Alloy ships every container\'s output to Loki, except those labelled logs=off', () => {
    const alloy = readConfig('config/alloy/config.alloy');
    assert.match(alloy, /discovery\.docker "[a-z_]+" \{\s*\n\s*host\s*=\s*"unix:\/\/\/var\/run\/docker\.sock"/);
    // The default is a minute: a container that crashes at start would be gone before Alloy saw it.
    assert.match(alloy, /refresh_interval\s*=\s*"5s"/);
    assert.match(alloy, /loki\.source\.docker "[a-z_]+" \{/);
    // The container's name without Docker's leading slash is the label to search by.
    assert.match(alloy, /source_labels\s*=\s*\["__meta_docker_container_name"\]\s*\n\s*regex\s*=\s*"\/\(\.\*\)"\s*\n\s*target_label\s*=\s*"container"/);
    assert.match(alloy, /source_labels\s*=\s*\["__meta_docker_container_label_logs"\]\s*\n\s*regex\s*=\s*"off"\s*\n\s*action\s*=\s*"drop"/);
    assert.match(alloy, /url\s*=\s*"http:\/\/loki:3100\/loki\/api\/v1\/push"/);
    // The Pi's logs slot in later under their own host.
    assert.match(alloy, /host\s*=\s*"desktop"/);
  });

  await t.test('Grafana gets Loki as its data source from the repo, and no password is tracked', () => {
    const datasource = readConfig('config/grafana/provisioning/datasources/loki.yaml');
    assert.match(datasource, /^apiVersion:\s*1\s*$/m);
    assert.match(datasource, /type:\s*loki\s*$/m);
    assert.match(datasource, /url:\s*http:\/\/loki:3100\s*$/m);
    assert.match(datasource, /isDefault:\s*true/);
    for (const file of ['config/loki/loki-config.yaml', 'config/alloy/config.alloy', 'config/grafana/provisioning/datasources/loki.yaml']) {
      assert.doesNotMatch(readConfig(file), /password|secret|token/i, `${file} must hold no credentials`);
      // config/*/** is ignored by default; these three must be let through.
      const ignored = spawnSync('git', ['check-ignore', '-q', file], { cwd: ROOT_DIR }).status === 0;
      assert.ok(!ignored, `${file} must not be git-ignored`);
    }
  });

  await t.test('Docker keeps a capped local log for every service, now that Loki holds the history', () => {
    const anchor = dockerComposeContent.match(/^x-logging:\s*&default-logging\s*\n([\s\S]*?)(?=^[a-z])/m);
    assert.ok(anchor, 'docker-compose.yml must define x-logging: &default-logging');
    assert.match(anchor[1], /driver:\s*json-file/);
    assert.match(anchor[1], /max-size:\s*"10m"/);
    assert.match(anchor[1], /max-file:\s*"3"/);

    const services = dockerComposeContent.match(/^services:\s*\n([\s\S]*?)(?=^[a-z]|(?![\s\S]))/m)[1];
    const names = [...services.matchAll(/^  ([a-z0-9_-]+):\s*$/gm)].map((m) => m[1]);
    assert.ok(names.length >= 20, 'the service list should be readable');
    for (const name of names) {
      assert.match(composeService(name), /^    logging:\s*\*default-logging\s*$/m, `${name} must use the capped logging block`);
    }
  });

  await t.test('The desktop runs no Tailscale: the Pi is the subnet router', () => {
    assert.doesNotMatch(dockerComposeContent, /^\s*tailscale:\s*$/m, 'no tailscale service');
    assert.doesNotMatch(dockerComposeContent, /network_mode:\s*"?service:tailscale/, 'nothing may borrow a tailscale network');
    assert.doesNotMatch(dockerComposeContent, /tailscale_sock/, 'no tailscale socket volume');
    assert.doesNotMatch(dockerComposeContent, /\$\{TS_[A-Z_]+/, 'no TS_* variables');
    assert.doesNotMatch(envExampleContent, /^TS_[A-Z_]+=/m, '.env.example must not document TS_* variables');
  });

  await t.test('Homepage (on the Pi) reaches desktop services by LAN IP, never by container name', () => {
    const urls = [...servicesYamlContent.matchAll(/^\s*(?:url|ping|siteMonitor):\s*(\S+)/gm)].map((m) => m[1]);
    assert.ok(urls.length > 0, 'services.yaml should declare service URLs');
    for (const url of urls) {
      const host = new URL(url).hostname;
      assert.ok(host.includes('.') || host === 'localhost', `${url} uses a bare container name the Pi can't resolve`);
    }
  });

  await t.test('Desktop services are checked over HTTP; only the Pi\'s own containers use Docker status', () => {
    const caddyCard = servicesYamlContent.match(/- Caddy[^:]*:\r?\n([\s\S]*?)(?=\r?\n\s*- [A-Z]|$)/);
    assert.ok(caddyCard, 'services.yaml must list the Pi Caddy');
    assert.match(caddyCard[1], /server:\s*my-docker\s*\r?\n\s*container:\s*caddy/);
    const containers = [...servicesYamlContent.matchAll(/^\s*container:\s*(\S+)/gm)].map((m) => m[1]).sort();
    assert.deepEqual(containers, ['caddy', 'pihole', 'unbound']);
    const expectedMonitors = {
      Jellyfin: 8096, Seerr: 5055, Jellystat: 3005, Sonarr: 8989, Radarr: 7878, Prowlarr: 9696,
      Bazarr: 6767, Maintainerr: 6246, qBittorrent: 8080, FlareSolverr: 8191, Cleanuparr: 11011,
      Grafana: 3002,
    };
    for (const [name, port] of Object.entries(expectedMonitors)) {
      const block = servicesYamlContent.match(new RegExp(`- ${name}:\\r?\\n([\\s\\S]*?)(?=\\r?\\n\\s*- [A-Z]|$)`));
      assert.ok(block, `services.yaml must list ${name}`);
      const monitor = `http://192.168.1.20:${port}`;
      assert.match(block[1], new RegExp(`siteMonitor:\\s*${monitor.replace(/\./g, '\\.')}/?\\s*$`, 'm'), `${name} must be monitored at ${monitor}`);
    }
  });

  await t.test('The Cleanuparr card shows what it did to the queue, read keyless from its stats API', () => {
    const card = servicesYamlContent.match(/- Cleanuparr:\r?\n([\s\S]*?)(?=\r?\n\s*- [A-Z]|$)/);
    assert.ok(card, 'services.yaml must list Cleanuparr');
    // Homepage has no native cleanuparr widget, so the card maps /api/v2/stats itself.
    assert.match(card[1], /type:\s*customapi/);
    assert.match(card[1], /url:\s*http:\/\/192\.168\.1\.20:11011\/api\/v2\/stats\s*$/m);
    const mappings = {
      'strikes.total': 'Strikes', 'removals.total': 'Removed', 'cleaned.total': 'Cleaned', 'searches.grabbed': 'Grabbed',
    };
    for (const [field, label] of Object.entries(mappings)) {
      assert.match(card[1], new RegExp(`-\\s*field:\\s*${field.replace('.', '\\.')}\\s*\\r?\\n\\s*label:\\s*${label}\\s*$`, 'm'), `${field} must show as ${label}`);
    }
    // Cleanuparr asks for no key on the LAN, so the Pi .env stays untouched.
    assert.doesNotMatch(card[1], /HOMEPAGE_VAR_/, 'the Cleanuparr widget needs no API key');
  });

  await t.test('The Grafana card opens the log search and holds no credentials', () => {
    const card = servicesYamlContent.match(/- Grafana:\r?\n([\s\S]*?)(?=\r?\n\s*- [A-Z]|$)/);
    assert.ok(card, 'services.yaml must list Grafana');
    assert.match(card[1], /icon:\s*grafana\.png/);
    assert.match(card[1], /href:\s*https:\/\/grafana\.spicy-llama\.duckdns\.org\s*$/m);
    assert.match(card[1], /description:\s*Container Logs \(Loki, 30 days\)\s*$/m);
    // A Grafana widget would put the admin password on the Pi; the HTTP check is enough.
    assert.doesNotMatch(card[1], /widget:|HOMEPAGE_VAR_/, 'the Grafana card must stay a link with an HTTP check');
  });

  await t.test('Homepage moved to the Pi: the desktop runs none', () => {
    assert.doesNotMatch(dockerComposeContent, /^\s*homepage:\s*$/m, 'no homepage service on the desktop');
    assert.doesNotMatch(dockerComposeContent, /HOMEPAGE_VAR_/, 'HOMEPAGE_VAR_* now live in hosts/pi');
  });
  await t.test('All environment variables in docker-compose.yml are documented in .env.example', () => {
    const envVarPattern = /\$\{([A-Z0-9_]+)(?::-.*?)?\}/g;
    const composeVars = new Set();
    let match;
    while ((match = envVarPattern.exec(dockerComposeContent)) !== null) {
      composeVars.add(match[1]);
    }

    assert.ok(composeVars.size > 0, 'Should find environment variables in docker-compose.yml');

    for (const varName of composeVars) {
      const envExamplePattern = new RegExp(`^#?\\s*${varName}=`, 'm');
      assert.ok(
        envExamplePattern.test(envExampleContent),
        `.env.example must define or document variable ${varName}`
      );
    }
  });

  await t.test('Homepage services.yaml internal container URLs point to valid services in docker-compose.yml', () => {
    const serviceNameRegex = /^\s{2}([a-z0-9_-]+):\s*$/gm;
    const definedServices = new Set();
    let match;
    while ((match = serviceNameRegex.exec(dockerComposeContent)) !== null) {
      definedServices.add(match[1]);
    }

    const urlPattern = /(?:url|ping):\s*http:\/\/([a-z0-9_-]+):(\d+)/g;
    while ((match = urlPattern.exec(servicesYamlContent)) !== null) {
      const hostname = match[1];
      assert.ok(
        definedServices.has(hostname),
        `Service hostname "${hostname}" in services.yaml must correspond to a service defined in docker-compose.yml`
      );
    }
  });

  await t.test('Recyclarr service receives Sonarr and Radarr API keys in docker-compose.yml', () => {
    const recyclarrMatch = dockerComposeContent.match(/container_name:\s*recyclarr[\s\S]*?environment:\s*\n([\s\S]*?)(?=\n\s*[a-z_]+:|\n\s*volumes:|\n\s*depends_on:|$)/);
    assert.ok(recyclarrMatch, 'docker-compose.yml must contain a recyclarr service with environment section');
    const recyclarrEnv = recyclarrMatch[1];
    assert.ok(recyclarrEnv.includes('SONARR_API_KEY'), 'Recyclarr service must receive SONARR_API_KEY');
    assert.ok(recyclarrEnv.includes('RADARR_API_KEY'), 'Recyclarr service must receive RADARR_API_KEY');
  });

  await t.test('SearXNG settings.yml does not commit a secret_key', () => {
    assert.ok(
      !/^\s*secret_key\s*:/m.test(searxngSettingsContent),
      'config/searxng/settings.yml must not contain secret_key; it is injected via SEARXNG_SECRET'
    );
  });

  await t.test('SearXNG service receives SEARXNG_SECRET in docker-compose.yml', () => {
    const searxngMatch = dockerComposeContent.match(/container_name:\s*searxng[\s\S]*?environment:\s*\n([\s\S]*?)(?=\n\s*[a-z_]+:|$)/);
    assert.ok(searxngMatch, 'docker-compose.yml must contain a searxng service with environment section');
    assert.ok(
      searxngMatch[1].includes('SEARXNG_SECRET=${SEARXNG_SECRET}'),
      'SearXNG service must receive SEARXNG_SECRET=${SEARXNG_SECRET}'
    );
  });

  await t.test('qBittorrent service enforces healthy Gluetun dependency in docker-compose.yml', () => {
    const qbitMatch = dockerComposeContent.match(/container_name:\s*qbittorrent[\s\S]*?depends_on:\s*\n([\s\S]*?)(?=\n\s{4}[a-z_]+:|\n\s{2}[a-z_]+:|\n\s*restart:|$)/);
    assert.ok(qbitMatch, 'docker-compose.yml must contain a qbittorrent service with depends_on section');
    const qbitDepends = qbitMatch[1];
    assert.ok(qbitDepends.includes('gluetun'), 'qBittorrent must depend on gluetun');
    assert.ok(qbitDepends.includes('condition: service_healthy'), 'qBittorrent must check gluetun service_healthy condition');
  });

  await t.test('Homepage bookmarks.yaml contains valid bookmark entries with hrefs', () => {
    const hrefMatches = [...bookmarksYamlContent.matchAll(/-\s*href:\s*(https?:\/\/[^\s]+)/g)];
    assert.ok(hrefMatches.length > 0, 'bookmarks.yaml must contain at least one bookmark with an href');

    const malformedPattern = /-\s*icon:.*\n\s*-\s*href:/;
    assert.ok(!malformedPattern.test(bookmarksYamlContent), 'bookmarks.yaml must not have separate array items for icon and href');
  });

  await t.test('docker-compose.yml pins Jellyfin to 12.0 and defines Seerr with init: true', () => {
    assert.ok(
      /container_name:\s*jellyfin[\s\S]*?image:\s*jellyfin\/jellyfin:12\.0(\.0)?/.test(dockerComposeContent) ||
      /image:\s*jellyfin\/jellyfin:12\.0(\.0)?[\s\S]*?container_name:\s*jellyfin/.test(dockerComposeContent),
      'docker-compose.yml must pin jellyfin to jellyfin/jellyfin:12.0'
    );

    assert.ok(
      /container_name:\s*seerr/.test(dockerComposeContent),
      'docker-compose.yml must contain container_name: seerr'
    );
    assert.ok(
      /image:\s*ghcr\.io\/seerr-team\/seerr:latest/.test(dockerComposeContent),
      'docker-compose.yml must use image ghcr.io/seerr-team/seerr:latest'
    );
    assert.ok(
      /init:\s*true/.test(dockerComposeContent),
      'docker-compose.yml must specify init: true for seerr'
    );
    assert.ok(
      /\$\{CONFIG_PATH\}\/seerr:\/app\/config/.test(dockerComposeContent),
      'docker-compose.yml must map ${CONFIG_PATH}/seerr:/app/config'
    );
  });

  await t.test('Homepage services.yaml configures native Seerr widget and Jellyfin version: 2', () => {
    assert.ok(
      /url:\s*http:\/\/192\.168\.1\.20:5055/.test(servicesYamlContent),
      'Homepage services.yaml must point the Seerr widget at the desktop (192.168.1.20:5055)'
    );
    assert.ok(
      /type:\s*seerr/.test(servicesYamlContent),
      'Homepage services.yaml must define widget type: seerr'
    );
    assert.ok(
      /version:\s*2/.test(servicesYamlContent),
      'Homepage services.yaml Jellyfin widget must specify version: 2 to prevent legacy /emby/ calls'
    );
  });

  await t.test('Ollama model manifest entries are complete and point at tracked Modelfiles', () => {
    const manifestPath = path.join(OLLAMA_MODELS_DIR, 'models.json');
    assert.ok(fs.existsSync(manifestPath), 'config/ollama-models/models.json must exist');
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    assert.ok(Array.isArray(manifest.models) && manifest.models.length > 0, 'manifest must list at least one model');

    // Two kinds of entry: a GGUF downloaded and checked against its SHA256 (for models the Ollama
    // library doesn't carry), or a model pulled from the Ollama library, which verifies it itself.
    for (const model of manifest.models) {
      for (const key of ['name', 'modelfile']) {
        assert.ok(model[key], `manifest entry ${model.name || '?'} must have ${key}`);
      }
      const modelfile = path.join(OLLAMA_MODELS_DIR, model.modelfile);
      assert.ok(fs.existsSync(modelfile), `${model.name} Modelfile ${model.modelfile} must be tracked in config/ollama-models`);
      const modelfileContent = fs.readFileSync(modelfile, 'utf8');
      if (model.ollama_pull) {
        assert.ok(!model.gguf_url, `${model.name} must name either ollama_pull or gguf_url, not both`);
        assert.match(modelfileContent, new RegExp(`^FROM ${model.ollama_pull}$`, 'm'), `${model.name} Modelfile must build FROM the pulled ${model.ollama_pull}`);
      } else {
        for (const key of ['gguf_url', 'sha256']) {
          assert.ok(model[key], `manifest entry ${model.name} must have ${key}`);
        }
        assert.match(model.sha256, /^[0-9a-f]{64}$/, `${model.name} sha256 must be 64 lowercase hex chars`);
        assert.ok(model.gguf_url.startsWith('https://huggingface.co/'), `${model.name} must download from huggingface.co`);
        assert.match(modelfileContent, /^FROM \/root\/\.ollama\/imports\//m, `${model.name} Modelfile must build FROM the imports folder`);
      }
    }

    const rvn = manifest.models.find((m) => m.name === 'qwen3.8-rvn');
    assert.ok(rvn, 'manifest must provision qwen3.8-rvn, the household default');
    assert.equal(rvn.sha256, 'a0f64d73d2ccfb5333a2e9dde9b079200d2a3e46f9ebaf19bc1a3cf14489d06b');
    const rvnModelfile = fs.readFileSync(path.join(OLLAMA_MODELS_DIR, rvn.modelfile), 'utf8');
    for (const line of ['RENDERER qwen3.8', 'PARSER qwen3.5', 'PARAMETER num_ctx 28672']) {
      assert.ok(rvnModelfile.includes(line), `qwen3.8-rvn Modelfile must contain: ${line}`);
    }

    // The hub budgets each turn against this window; the two must agree or answers get cut off.
    const hubConfig = fs.readFileSync(path.join(ROOT_DIR, 'apps/household-hub/backend/app/core/config.py'), 'utf8');
    const hubWindow = hubConfig.match(/LLM_CONTEXT_TOKENS:\s*int\s*=\s*(\d+)/);
    assert.ok(hubWindow, 'the hub must declare LLM_CONTEXT_TOKENS');
    assert.equal(hubWindow[1], rvnModelfile.match(/PARAMETER num_ctx (\d+)/)[1], 'LLM_CONTEXT_TOKENS must match the Modelfile num_ctx');

    // The embedder runs on the CPU so the chat model keeps the whole GPU.
    const embedder = manifest.models.find((m) => m.name === 'bge-m3-cpu');
    assert.ok(embedder, 'manifest must provision bge-m3-cpu, the source index embedder');
    assert.equal(embedder.ollama_pull, 'bge-m3', 'bge-m3-cpu is built from the Ollama library bge-m3');
    const embedderModelfile = fs.readFileSync(path.join(OLLAMA_MODELS_DIR, embedder.modelfile), 'utf8');
    assert.ok(embedderModelfile.includes('PARAMETER num_gpu 0'), 'bge-m3-cpu must run on the CPU (num_gpu 0)');
  });

  await t.test('Every tool the hub offers has words of its own in the app', () => {
    // A tool added on the hub without a label shows as the generic "Used a tool" (read_page and
    // lookup_sources did, in #36). The hub's list and the app's label map must name the same tools.
    const hubTools = fs.readFileSync(
      path.join(ROOT_DIR, 'apps/household-hub/backend/app/domain/use_cases/integrations/list_available_tools.py'), 'utf8');
    const appLabels = fs.readFileSync(
      path.join(ROOT_DIR, 'apps/household-hub/client/composeApp/src/commonMain/kotlin/com/homelab/household/app/screens/conversation/ToolLabel.kt'), 'utf8');
    // The labels name each tool through HubTool, the one place the app spells the hub's names.
    const hubToolNames = fs.readFileSync(
      path.join(ROOT_DIR, 'apps/household-hub/client/core/domain/src/commonMain/kotlin/com/homelab/household/domain/model/HubTool.kt'), 'utf8');
    const named = Object.fromEntries([...hubToolNames.matchAll(/const val ([A-Z_]+) = "([a-z_]+)"/g)].map((m) => [m[1], m[2]]));
    const offered = [...hubTools.matchAll(/name="([a-z_]+)"/g)].map((m) => m[1]);
    const labelled = new Set([
      ...[...appLabels.matchAll(/^\s*"([a-z_]+)" to$/gm)].map((m) => m[1]),
      ...[...appLabels.matchAll(/^\s*HubTool\.([A-Z_]+) to$/gm)].map((m) => named[m[1]]),
    ]);
    assert.ok(offered.length >= 7, 'the hub tool list should be readable');
    const missing = offered.filter((tool) => !labelled.has(tool));
    assert.deepEqual(missing, [], `these hub tools have no label in ToolLabel.kt: ${missing.join(', ')}`);
  });

  await t.test('Ollama models live in a named volume, not on the Windows share', () => {
    const ollamaMatch = dockerComposeContent.match(/container_name:\s*ollama[\s\S]*?volumes:\s*\n([\s\S]*?)(?=\n\s*[a-z_]+:\s*\n)/);
    assert.ok(ollamaMatch, 'docker-compose.yml must declare volumes for the ollama service');
    assert.ok(
      ollamaMatch[1].includes('- ollama_models:/root/.ollama/models'),
      'ollama must mount the ollama_models volume at /root/.ollama/models'
    );
    assert.ok(
      ollamaMatch[1].includes('- ${CONFIG_PATH}/ollama:/root/.ollama'),
      'ollama must keep the config bind mount for keys and Modelfiles'
    );
    assert.ok(
      /^volumes:\s*\n[\s\S]*?^  ollama_models:\s*\n\s+name:\s*ollama_models\s*$/m.test(dockerComposeContent),
      'docker-compose.yml must declare the ollama_models volume with a fixed name'
    );
  });

  await t.test('Ollama keeps the KV cache at q8_0, half the f16 size', () => {
    // At f16 the chat model's 28k cache took 1,792 MiB and left the RTX 5080 nearly full (#82).
    assert.ok(
      composeService('ollama').includes('- OLLAMA_KV_CACHE_TYPE=${OLLAMA_KV_CACHE_TYPE:-q8_0}'),
      'ollama must default OLLAMA_KV_CACHE_TYPE to q8_0'
    );
    assert.match(envExampleContent, /^OLLAMA_KV_CACHE_TYPE=q8_0$/m, '.env.example must set OLLAMA_KV_CACHE_TYPE=q8_0');
    assert.match(
      envExampleContent,
      /^#[^\n]*\bf16\b[^\n]*\nOLLAMA_KV_CACHE_TYPE=/m,
      '.env.example must name f16 as the way back, just above OLLAMA_KV_CACHE_TYPE'
    );
  });

  await t.test('Household Hub runs in compose with its data in a named volume', () => {
    // On the host the backend could not resolve searxng:8080; inside compose every service name resolves.
    const hubMatch = dockerComposeContent.match(/^  household-hub:\s*\n([\s\S]*?)(?=^  [a-z][\w-]*:\s*\n|^[a-z]+:\s*\n)/m);
    assert.ok(hubMatch, 'docker-compose.yml must declare a household-hub service');
    const hub = hubMatch[1];
    assert.match(hub, /profiles:\s*\["ai"\]/, 'household-hub must be in the ai profile so an arr-only start leaves it off');
    assert.match(hub, /context:\s*\.\/apps\/household-hub\/backend/, 'household-hub must build from the backend folder');
    assert.ok(hub.includes('- household_hub_data:/data'), 'household-hub must keep its SQLite database in the household_hub_data volume');
    assert.ok(hub.includes('- 127.0.0.1:3050:3050'), 'household-hub must keep 3050 on loopback for /docs from the PC');
    assert.match(hub, /-\s*3051:3050\b/, 'household-hub must publish 3051 on the LAN for the Pi\'s Caddy');
    assert.match(hub, /depends_on:\s*\n\s+- ollama\s*\n\s+- searxng/, 'household-hub must start after ollama and searxng');
    assert.ok(
      /^volumes:\s*\n[\s\S]*?^  household_hub_data:\s*\n\s+name:\s*household_hub_data\s*$/m.test(dockerComposeContent),
      'docker-compose.yml must declare the household_hub_data volume with a fixed name'
    );
  });
});
