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
const OLLAMA_MODELS_DIR = path.join(ROOT_DIR, 'config/ollama-models');

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
    for (const line of ['RENDERER qwen3.8', 'PARSER qwen3.5', 'PARAMETER num_ctx 32768']) {
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

  await t.test('Household Hub runs in compose with its data in a named volume', () => {
    // On the host the backend could not resolve searxng:8080; inside compose every service name resolves.
    const hubMatch = dockerComposeContent.match(/^  household-hub:\s*\n([\s\S]*?)(?=^  [a-z][\w-]*:\s*\n|^[a-z]+:\s*\n)/m);
    assert.ok(hubMatch, 'docker-compose.yml must declare a household-hub service');
    const hub = hubMatch[1];
    assert.match(hub, /profiles:\s*\["ai"\]/, 'household-hub must be in the ai profile so an arr-only start leaves it off');
    assert.match(hub, /context:\s*\.\/apps\/household-hub\/backend/, 'household-hub must build from the backend folder');
    assert.ok(hub.includes('- household_hub_data:/data'), 'household-hub must keep its SQLite database in the household_hub_data volume');
    assert.ok(hub.includes('- 127.0.0.1:3050:3050'), 'household-hub must publish 3050 on loopback only');
    assert.match(hub, /depends_on:\s*\n\s+- ollama\s*\n\s+- searxng/, 'household-hub must start after ollama and searxng');
    assert.ok(
      /^volumes:\s*\n[\s\S]*?^  household_hub_data:\s*\n\s+name:\s*household_hub_data\s*$/m.test(dockerComposeContent),
      'docker-compose.yml must declare the household_hub_data volume with a fixed name'
    );
  });

  await t.test('Caddy proxies the hub to the container, not the host', () => {
    assert.ok(caddyfileContent.includes('reverse_proxy household-hub:3050'), 'the hub route must target the household-hub container');
    assert.ok(!caddyfileContent.includes('host.docker.internal:3050'), 'the hub route must not reach back to the Windows host');
  });
});
