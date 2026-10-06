const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PORT_TO_SERVICE } = require('./service-ports.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const WORKFLOW_PATH = path.join(ROOT_DIR, '.github/workflows/caddy-image.yml');
const PI_COMPOSE_PATH = path.join(ROOT_DIR, 'hosts/pi/docker-compose.yml');
const PI_CADDYFILE_PATH = path.join(ROOT_DIR, 'hosts/pi/caddy/Caddyfile');
const PI_ENV_EXAMPLE_PATH = path.join(ROOT_DIR, 'hosts/pi/.env.example');
const DESKTOP = '192\\.168\\.1\\.20';
const SERVER = '192\\.168\\.1\\.30';
// What already runs on the server; the media services follow once its disk is in.
const ON_SERVER = new Set(['grafana']);

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

function serviceBlock(compose, name) {
  const match = compose.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [a-z0-9_-]+:\\n|^[a-z]|(?![\\s\\S]))`, 'm'));
  return match ? match[1] : '';
}

test('Caddy image for the Pi is built by CI', async (t) => {
  const workflow = read(WORKFLOW_PATH);

  await t.test('builds hosts/pi/caddy/Dockerfile for arm64 and amd64', () => {
    assert.match(workflow, /file:\s*hosts\/pi\/caddy\/Dockerfile/);
    assert.match(workflow, /context:\s*hosts\/pi\/caddy/);
    assert.match(workflow, /platforms:\s*linux\/amd64,\s*linux\/arm64/);
  });

  await t.test('pushes to the repo-owned GHCR image', () => {
    assert.match(workflow, /ghcr\.io\/anwera64\/caddy-duckdns/);
    assert.match(workflow, /packages:\s*write/);
    assert.match(workflow, /push:\s*true/);
  });

  await t.test('rebuilds when the Dockerfile changes and monthly for Caddy fixes', () => {
    assert.match(workflow, /paths:\s*\n\s*-\s*['"]?hosts\/pi\/caddy\/Dockerfile['"]?/);
    assert.match(workflow, /schedule:\s*\n\s*-\s*cron:/);
  });
});

test('Pi Caddy terminates the DuckDNS HTTPS ingress', async (t) => {
  const compose = read(PI_COMPOSE_PATH);
  const caddy = serviceBlock(compose, 'caddy');
  const pihole = serviceBlock(compose, 'pihole');
  const caddyfile = read(PI_CADDYFILE_PATH);

  await t.test('the caddy service uses host networking and the pinned GHCR image', () => {
    assert.match(caddy, /image:\s*ghcr\.io\/anwera64\/caddy-duckdns:[0-9]/);
    assert.match(caddy, /network_mode:\s*host/);
    assert.match(caddy, /-\s*\.\/caddy\/Caddyfile:\/etc\/caddy\/Caddyfile:ro/);
    assert.match(caddy, /-\s*\.\/data\/caddy:\/data/);
    assert.match(caddy, /DUCKDNS_TOKEN:\s*\$\{DUCKDNS_TOKEN:\?/);
    assert.match(caddy, /restart:\s*unless-stopped/);
  });

  await t.test('Pi-hole hands ports 80/443 to Caddy and serves its UI on 8081', () => {
    assert.match(pihole, /FTLCONF_webserver_port:\s*['"]8081o['"]/);
  });

  await t.test('the wildcard block gets its certificate through the DuckDNS DNS challenge', () => {
    assert.ok(caddyfile.includes('*.spicy-llama.duckdns.org, spicy-llama.duckdns.org {'));
    assert.ok(caddyfile.includes('dns duckdns {$DUCKDNS_TOKEN}'));
  });

  await t.test('Pi-local names are served from the Pi', () => {
    assert.match(caddyfile, /@pihole host pihole\.spicy-llama\.duckdns\.org\s*\n\s*handle @pihole \{\s*\n\s*reverse_proxy 127\.0\.0\.1:8081/);
    assert.match(caddyfile, /# Default[^\n]*\n\s*handle \{\s*\n\s*reverse_proxy 127\.0\.0\.1:3000/);
  });

  await t.test('Jellyfin keeps the /emby fallback for old clients', () => {
    assert.match(caddyfile, /handle @jellyfin \{[\s\S]*?handle_path \/emby\/\* \{\s*\n\s*reverse_proxy 192\.168\.1\.20:8096/);
  });

  await t.test('every service goes to its own published port, on the machine that runs it', () => {
    for (const [port, service] of Object.entries(PORT_TO_SERVICE)) {
      const host = ON_SERVER.has(service) ? SERVER : DESKTOP;
      assert.match(
        caddyfile,
        new RegExp(`@${service} host ${service}\\.spicy-llama\\.duckdns\\.org[^\\n]*\\n\\s*handle @${service} \\{[\\s\\S]*?reverse_proxy ${host}:${port}\\b`),
        `${service} must proxy to port ${port} on ${ON_SERVER.has(service) ? 'the server' : 'the desktop'}`
      );
    }
    assert.match(caddyfile, new RegExp(`@hub host hub\\.spicy-llama\\.duckdns\\.org\\s*\\n\\s*handle @hub \\{\\s*\\n\\s*reverse_proxy ${SERVER}:3051`));
  });
  await t.test('telemetry goes to Alloy only after the hub accepts the sender\'s token', () => {
    const block = caddyfile.match(/@telemetry host telemetry\.spicy-llama\.duckdns\.org\s*\n\s*handle @telemetry \{\n([\s\S]*?)\n    \}/);
    assert.ok(block, 'the Caddyfile must route telemetry.spicy-llama.duckdns.org');
    const body = block[1];
    // Only log batches: nothing stores traces or metrics, and nothing else of Alloy is exposed.
    assert.match(body, /@logs \{\s*\n\s*method POST\s*\n\s*path \/v1\/logs\s*\n\s*\}/);
    // `route` keeps the written order. Without it Caddy runs request_header after forward_auth
    // and removes the member the hub just named.
    const guarded = body.match(/handle @logs \{\n(?:\s*#[^\n]*\n)?\s*route \{\n([\s\S]*?)\n            \}\n        \}/);
    assert.ok(guarded, 'log batches must be handled by an ordered route');
    const steps = guarded[1];
    // A sender cannot name the member itself: the header is removed, then set from the hub's answer.
    const strip = steps.indexOf('request_header -X-Member-Id');
    // Both the Hub and Alloy are on the server now.
    const auth = steps.search(/forward_auth 192\.168\.1\.30:3051 \{\s*\n\s*uri \/api\/v1\/auth\/verify\s*\n\s*copy_headers X-Member-Id\s*\n\s*\}/);
    const proxy = steps.indexOf('reverse_proxy 192.168.1.30:4318');
    assert.ok(strip >= 0 && auth >= 0 && proxy >= 0, 'strip, forward_auth and reverse_proxy must all be present');
    assert.ok(strip < auth && auth < proxy, 'strip the header, then ask the hub, then pass to Alloy');
    assert.match(body, /handle \{\s*\n\s*respond 404\s*\n\s*\}/, 'anything else on this name is refused');
  });

  await t.test('plain HTTP on the Pi\'s own names redirects to the dashboard\'s HTTPS name', () => {
    const plain = caddyfile.match(/^http:\/\/192\.168\.1\.35, http:\/\/lemonpi, http:\/\/lemonpi\.lan, http:\/\/lemonpi\.local \{\n([\s\S]*?)\n\}/m);
    assert.ok(plain, 'the plain HTTP block must cover the Pi\'s address and names');
    assert.match(plain[1], /^\s*redir https:\/\/home\.spicy-llama\.duckdns\.org\{uri\}\s*$/m);
    assert.ok(!plain[1].includes('reverse_proxy'), 'plain HTTP must not serve the dashboard itself');
  });

  await t.test('.env.example declares the DuckDNS token, empty', () => {
    assert.match(read(PI_ENV_EXAMPLE_PATH), /^DUCKDNS_TOKEN=$/m);
  });
});
