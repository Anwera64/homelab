const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PORT_TO_SERVICE } = require('../config/homepage/adapt-links.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const WORKFLOW_PATH = path.join(ROOT_DIR, '.github/workflows/caddy-image.yml');
const PI_COMPOSE_PATH = path.join(ROOT_DIR, 'hosts/pi/docker-compose.yml');
const PI_CADDYFILE_PATH = path.join(ROOT_DIR, 'hosts/pi/caddy/Caddyfile');
const PI_ENV_EXAMPLE_PATH = path.join(ROOT_DIR, 'hosts/pi/.env.example');

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');

function serviceBlock(compose, name) {
  const match = compose.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [a-z0-9_-]+:\\n|^[a-z]|(?![\\s\\S]))`, 'm'));
  return match ? match[1] : '';
}

test('Caddy image for the Pi is built by CI', async (t) => {
  const workflow = read(WORKFLOW_PATH);

  await t.test('builds config/caddy/Dockerfile for arm64 and amd64', () => {
    assert.match(workflow, /file:\s*config\/caddy\/Dockerfile/);
    assert.match(workflow, /platforms:\s*linux\/amd64,\s*linux\/arm64/);
  });

  await t.test('pushes to the repo-owned GHCR image', () => {
    assert.match(workflow, /ghcr\.io\/anwera64\/caddy-duckdns/);
    assert.match(workflow, /packages:\s*write/);
    assert.match(workflow, /push:\s*true/);
  });

  await t.test('rebuilds when the Dockerfile changes and monthly for Caddy fixes', () => {
    assert.match(workflow, /paths:\s*\n\s*-\s*['"]?config\/caddy\/Dockerfile['"]?/);
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

  await t.test('every desktop service goes to the desktop Caddy\'s LAN port', () => {
    for (const [port, service] of Object.entries(PORT_TO_SERVICE)) {
      assert.match(
        caddyfile,
        new RegExp(`@${service} host ${service}\\.spicy-llama\\.duckdns\\.org[^\\n]*\\n\\s*handle @${service} \\{\\s*\\n\\s*reverse_proxy 192\\.168\\.1\\.20:${port}\\b`),
        `${service} must proxy to 192.168.1.20:${port}`
      );
    }
    assert.match(caddyfile, /@hub host hub\.spicy-llama\.duckdns\.org\s*\n\s*handle @hub \{\s*\n\s*reverse_proxy 192\.168\.1\.20:3051/);
  });

  await t.test('plain HTTP on the Pi shows Homepage', () => {
    assert.match(caddyfile, /http:\/\/192\.168\.1\.35, http:\/\/lemonpi[^{]*\{[^}]*reverse_proxy 127\.0\.0\.1:3000/);
  });

  await t.test('.env.example declares the DuckDNS token, empty', () => {
    assert.match(read(PI_ENV_EXAMPLE_PATH), /^DUCKDNS_TOKEN=$/m);
  });
});
