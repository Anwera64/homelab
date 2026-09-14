const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { SERVICE_PORTS, PORT_TO_SERVICE } = require('../config/homepage/adapt-links.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const CADDYFILE_PATH = path.join(ROOT_DIR, 'config/caddy/Caddyfile');
const DOCKER_COMPOSE_PATH = path.join(ROOT_DIR, 'docker-compose.yml');
const SERVICES_YAML_PATH = path.join(ROOT_DIR, 'config/homepage/services.yaml');
const BOOKMARKS_YAML_PATH = path.join(ROOT_DIR, 'config/homepage/bookmarks.yaml');
const ENV_EXAMPLE_PATH = path.join(ROOT_DIR, '.env.example');
const SEARXNG_SETTINGS_PATH = path.join(ROOT_DIR, 'config/searxng/settings.yml');

test('Cross-Configuration & Infrastructure Integrity Suite', async (t) => {
  const caddyfileContent = fs.readFileSync(CADDYFILE_PATH, 'utf8');
  const dockerComposeContent = fs.readFileSync(DOCKER_COMPOSE_PATH, 'utf8');
  const servicesYamlContent = fs.readFileSync(SERVICES_YAML_PATH, 'utf8');
  const bookmarksYamlContent = fs.readFileSync(BOOKMARKS_YAML_PATH, 'utf8');
  const envExampleContent = fs.readFileSync(ENV_EXAMPLE_PATH, 'utf8');
  const searxngSettingsContent = fs.readFileSync(SEARXNG_SETTINGS_PATH, 'utf8');

  await t.test('Caddyfile contains DuckDNS wildcard TLS block with dns duckdns plugin', () => {
    assert.ok(
      caddyfileContent.includes('*.spicy-llama.duckdns.org, spicy-llama.duckdns.org'),
      'Caddyfile must contain wildcard entry for spicy-llama.duckdns.org'
    );
    assert.ok(
      caddyfileContent.includes('dns duckdns {$DUCKDNS_TOKEN}'),
      'Caddyfile must configure tls with dns duckdns {$DUCKDNS_TOKEN}'
    );
  });

  await t.test('Caddyfile contains reverse proxy handlers for all core homelab services', () => {
    const requiredServices = ['jellyfin', 'sonarr', 'radarr', 'prowlarr', 'bazarr', 'maintainerr', 'flaresolverr'];
    for (const service of requiredServices) {
      const handlerPattern = new RegExp(`@${service}\\s+host\\s+${service}\\.spicy-llama\\.duckdns\\.org`, 'm');
      assert.ok(
        handlerPattern.test(caddyfileContent),
        `Caddyfile must contain a named host matcher for @${service}`
      );
    }
  });

  await t.test('Caddyfile contains local direct HTTP port proxy blocks', () => {
    for (const port of Object.keys(PORT_TO_SERVICE)) {
      const httpPattern = new RegExp(`http://:${port}\\s*\\{`, 'm');
      assert.ok(
        httpPattern.test(caddyfileContent),
        `Caddyfile must contain an HTTP reverse proxy block for local port :${port}`
      );
    }
  });

  await t.test('Tailscale container exposes standard ingress and local service ports in docker-compose.yml', () => {
    const tailscaleMatch = dockerComposeContent.match(/container_name:\s*tailscale[\s\S]*?ports:\s*\n([\s\S]*?)(?=\n\s*[a-z_]+:|\n\s*volumes:|\n\s*restart:|$)/);
    assert.ok(tailscaleMatch, 'docker-compose.yml must contain a tailscale service with a ports section');

    const tailscalePortsSection = tailscaleMatch[1];
    const exposedPorts = new Set();
    const portRegex = /-\s*(\d+):/g;
    let match;
    while ((match = portRegex.exec(tailscalePortsSection)) !== null) {
      exposedPorts.add(match[1]);
    }

    assert.ok(exposedPorts.has('80'), 'Tailscale must expose port 80');
    assert.ok(exposedPorts.has('443'), 'Tailscale must expose port 443');
    assert.ok(exposedPorts.has('3000'), 'Tailscale must expose port 3000');

    for (const port of Object.keys(PORT_TO_SERVICE)) {
      assert.ok(
        exposedPorts.has(port),
        `Tailscale service in docker-compose.yml must expose local port ${port}`
      );
    }
  });

  await t.test('Caddy service in docker-compose.yml builds custom image and injects DUCKDNS_TOKEN', () => {
    assert.ok(
      dockerComposeContent.includes('context: ./config/caddy'),
      'Caddy service must configure build context as ./config/caddy'
    );
    assert.ok(
      dockerComposeContent.includes('DUCKDNS_TOKEN=${DUCKDNS_TOKEN}'),
      'Caddy service must receive DUCKDNS_TOKEN environment variable'
    );
  });

  await t.test('Homepage service.yaml {{HOMEPAGE_VAR_*}} tokens are mapped in docker-compose.yml', () => {
    const varPattern = /\{\{HOMEPAGE_VAR_([A-Z0-9_]+)\}\}/g;
    const requiredVars = new Set();
    let match;
    while ((match = varPattern.exec(servicesYamlContent)) !== null) {
      requiredVars.add(`HOMEPAGE_VAR_${match[1]}`);
    }

    assert.ok(requiredVars.size > 0, 'Should find HOMEPAGE_VAR_* variables in services.yaml');

    for (const varName of requiredVars) {
      const composeEnvPattern = new RegExp(`-\\s*${varName}=`);
      assert.ok(
        composeEnvPattern.test(dockerComposeContent),
        `docker-compose.yml homepage container must define environment variable ${varName}`
      );
    }
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

  await t.test('Caddyfile reverse proxies Seerr on port 5055 and provides /emby/* strip fallback', () => {
    assert.ok(
      caddyfileContent.includes('reverse_proxy seerr:5055'),
      'Caddyfile must reverse proxy port 5055 to seerr:5055'
    );
    assert.ok(
      caddyfileContent.includes('handle_path /emby/*') || caddyfileContent.includes('uri strip_prefix /emby'),
      'Caddyfile must contain /emby/* strip fallback for Jellyfin 12'
    );
  });

  await t.test('Homepage services.yaml configures native Seerr widget and Jellyfin version: 2', () => {
    assert.ok(
      /container:\s*seerr/.test(servicesYamlContent),
      'Homepage services.yaml must define container: seerr'
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
});
