const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { HTTP_TO_HTTPS_PORT } = require('../config/homepage/adapt-links.js');

const ROOT_DIR = path.resolve(__dirname, '..');
const CADDYFILE_PATH = path.join(ROOT_DIR, 'config/caddy/Caddyfile');
const DOCKER_COMPOSE_PATH = path.join(ROOT_DIR, 'docker-compose.yml');
const SERVICES_YAML_PATH = path.join(ROOT_DIR, 'config/homepage/services.yaml');
const ENV_EXAMPLE_PATH = path.join(ROOT_DIR, '.env.example');

test('Cross-Configuration & Infrastructure Integrity Suite', async (t) => {
  const caddyfileContent = fs.readFileSync(CADDYFILE_PATH, 'utf8');
  const dockerComposeContent = fs.readFileSync(DOCKER_COMPOSE_PATH, 'utf8');
  const servicesYamlContent = fs.readFileSync(SERVICES_YAML_PATH, 'utf8');
  const envExampleContent = fs.readFileSync(ENV_EXAMPLE_PATH, 'utf8');

  await t.test('Caddyfile contains reverse proxy blocks for all HTTP_TO_HTTPS_PORT mappings', () => {
    for (const [httpPort, httpsPort] of Object.entries(HTTP_TO_HTTPS_PORT)) {
      // Check HTTP port block exists
      const httpPattern = new RegExp(`http://:${httpPort}\\s*\\{`, 'm');
      assert.ok(
        httpPattern.test(caddyfileContent),
        `Caddyfile must contain an HTTP reverse proxy block for port :${httpPort}`
      );

      // Check HTTPS port block exists
      const httpsPattern = new RegExp(`:${httpsPort}\\s*\\{`, 'm');
      assert.ok(
        httpsPattern.test(caddyfileContent),
        `Caddyfile must contain a dedicated HTTPS block for port :${httpsPort}`
      );
    }
  });

  await t.test('Tailscale container exposes all required HTTP and HTTPS ingress ports in docker-compose.yml', () => {
    // Extract ports from tailscale service block in docker-compose.yml
    const tailscaleMatch = dockerComposeContent.match(/container_name:\s*tailscale[\s\S]*?ports:\s*\n([\s\S]*?)(?=\n\s*[a-z_]+:|\n\s*volumes:|\n\s*restart:|$)/);
    assert.ok(tailscaleMatch, 'docker-compose.yml must contain a tailscale service with a ports section');

    const tailscalePortsSection = tailscaleMatch[1];
    const exposedPorts = new Set();
    const portRegex = /-\s*(\d+):/g;
    let match;
    while ((match = portRegex.exec(tailscalePortsSection)) !== null) {
      exposedPorts.add(match[1]);
    }

    for (const [httpPort, httpsPort] of Object.entries(HTTP_TO_HTTPS_PORT)) {
      assert.ok(
        exposedPorts.has(httpPort),
        `Tailscale service in docker-compose.yml must expose HTTP port ${httpPort}`
      );
      assert.ok(
        exposedPorts.has(httpsPort),
        `Tailscale service in docker-compose.yml must expose HTTPS port ${httpsPort}`
      );
    }
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
    // Match ${VAR_NAME} or ${VAR_NAME:-default}
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
    // Extract service names defined under services: in docker-compose.yml
    const serviceNameRegex = /^\s{2}([a-z0-9_-]+):\s*$/gm;
    const definedServices = new Set();
    let match;
    while ((match = serviceNameRegex.exec(dockerComposeContent)) !== null) {
      definedServices.add(match[1]);
    }

    // Check URLs in services.yaml (e.g. url: http://sonarr:8989, ping: http://jellystat:3000/)
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

  await t.test('qBittorrent service enforces healthy Gluetun dependency in docker-compose.yml', () => {
    const qbitMatch = dockerComposeContent.match(/container_name:\s*qbittorrent[\s\S]*?depends_on:\s*\n([\s\S]*?)(?=\n\s{4}[a-z_]+:|\n\s{2}[a-z_]+:|\n\s*restart:|$)/);
    assert.ok(qbitMatch, 'docker-compose.yml must contain a qbittorrent service with depends_on section');
    const qbitDepends = qbitMatch[1];
    assert.ok(qbitDepends.includes('gluetun'), 'qBittorrent must depend on gluetun');
    assert.ok(qbitDepends.includes('condition: service_healthy'), 'qBittorrent must check gluetun service_healthy condition');
  });
});
