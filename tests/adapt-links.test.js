const test = require('node:test');
const assert = require('node:assert/strict');
const { adaptServiceUrl, HTTP_TO_HTTPS_PORT } = require('../config/homepage/adapt-links.js');

test('Option 2 Split-Port Ingress Link Adapter Suite', async (t) => {
  const remoteOrigin = 'https://homelab.llama-porbeagle.ts.net/';

  await t.test('Remote HTTPS: adapts Jellyfin (:8096) to https :8443 port', () => {
    const input = 'http://desktop-kujo8mp:8096';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:8443/');
  });

  await t.test('Remote HTTPS: adapts Jellyseerr (:5055) to https :15055 port', () => {
    const input = 'http://desktop-kujo8mp:5055';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:15055/');
  });

  await t.test('Remote HTTPS: adapts Jellystat (:3005) to https :13005 port', () => {
    const input = 'http://desktop-kujo8mp:3005';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:13005/');
  });

  await t.test('Remote HTTPS: adapts Maintainerr (:6246) to https :16246 port', () => {
    const input = 'http://desktop-kujo8mp:6246';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:16246/');
  });

  await t.test('Remote HTTPS: adapts Sonarr (:8989) to https :18989 port', () => {
    const input = 'http://desktop-kujo8mp:8989';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:18989/');
  });

  await t.test('Remote HTTPS: adapts Radarr (:7878) to https :17878 port', () => {
    const input = 'http://desktop-kujo8mp:7878';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:17878/');
  });

  await t.test('Remote HTTPS: adapts qBittorrent (:8080) to https :18080 port', () => {
    const input = 'http://desktop-kujo8mp:8080';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:18080/');
  });

  await t.test('Remote HTTPS: adapts FlareSolverr (:8191) to https :18191 port', () => {
    const input = 'http://desktop-kujo8mp:8191';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:18191/');
  });

  await t.test('Local LAN IP (HTTP): retains local port 8096 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:8096';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:8096/');
  });

  await t.test('Local LAN IP (HTTP): retains local FlareSolverr port 8191 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:8191';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:8191/');
  });

  await t.test('Local LAN IP (HTTP): retains local Jellystat port 3005 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:3005';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:3005/');
  });

  await t.test('Local LAN IP (HTTP): retains local Maintainerr port 6246 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:6246';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:6246/');
  });

  await t.test('Local Hostname (HTTP): retains local port 5055 in pure HTTP', () => {
    const localHostOrigin = 'http://desktop-kujo8mp/';
    const input = 'http://192.168.1.20:5055';
    const result = adaptServiceUrl(input, localHostOrigin);
    assert.equal(result, 'http://desktop-kujo8mp:5055/');
  });

  await t.test('Localhost (HTTP): retains local port 7878 in pure HTTP', () => {
    const localhostOrigin = 'http://localhost:3000/';
    const input = 'http://desktop-kujo8mp:7878';
    const result = adaptServiceUrl(input, localhostOrigin);
    assert.equal(result, 'http://localhost:7878/');
  });

  await t.test('External Links Safety: never modifies external websites', () => {
    assert.equal(
      adaptServiceUrl('https://github.com/linuxserver/docker-jellyfin', remoteOrigin),
      'https://github.com/linuxserver/docker-jellyfin'
    );
    assert.equal(
      adaptServiceUrl('https://trash-guides.info/Radarr/', remoteOrigin),
      'https://trash-guides.info/Radarr/'
    );
  });

  await t.test('Standard Web Port Safety: never modifies port 80/443 links', () => {
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:80', remoteOrigin),
      'http://desktop-kujo8mp:80'
    );
    assert.equal(
      adaptServiceUrl('https://desktop-kujo8mp:443', remoteOrigin),
      'https://desktop-kujo8mp:443'
    );
  });

  await t.test('Remote HTTPS: adapts Prowlarr (:9696) to https :19696 port', () => {
    const input = 'http://desktop-kujo8mp:9696';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:19696/');
  });

  await t.test('Remote HTTPS: adapts Bazarr (:6767) to https :16767 port', () => {
    const input = 'http://desktop-kujo8mp:6767';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:16767/');
  });

  await t.test('Local LAN IP (HTTP): retains local Prowlarr port 9696 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:9696';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:9696/');
  });

  await t.test('Local LAN IP (HTTP): retains local Bazarr port 6767 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:6767';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:6767/');
  });

  await t.test('URL Preservation: preserves subpaths, query parameters, and hashes during rewrite', () => {
    const complexInput = 'http://desktop-kujo8mp:8096/web/index.html?token=xyz123&theme=dark#player-view';
    const result = adaptServiceUrl(complexInput, remoteOrigin);
    assert.equal(
      result,
      'https://homelab.llama-porbeagle.ts.net:8443/web/index.html?token=xyz123&theme=dark#player-view'
    );
  });

  await t.test('Unmapped Custom Ports: adapts origin and protocol while preserving unmapped port', () => {
    const customInput = 'http://desktop-kujo8mp:9999/status';
    const result = adaptServiceUrl(customInput, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:9999/status');
  });

  await t.test('Relative and malformed links resilience', () => {
    assert.equal(adaptServiceUrl('/local-path', remoteOrigin), '/local-path');
    assert.equal(adaptServiceUrl('', remoteOrigin), '');
    assert.equal(adaptServiceUrl(null, remoteOrigin), null);
    assert.equal(adaptServiceUrl(undefined, remoteOrigin), undefined);
  });

  await t.test('Code Parity: custom.js and adapt-links.js have identical HTTP_TO_HTTPS_PORT maps', () => {
    const fs = require('node:fs');
    const path = require('node:path');
    const customJsPath = path.resolve(__dirname, '../config/homepage/custom.js');
    const customJsContent = fs.readFileSync(customJsPath, 'utf8');

    // Extract HTTP_TO_HTTPS_PORT object from custom.js
    const match = customJsContent.match(/var HTTP_TO_HTTPS_PORT\s*=\s*(\{[\s\S]*?\});/);
    assert.ok(match, 'HTTP_TO_HTTPS_PORT should exist in custom.js');

    // Safely parse the object literal
    const customMap = Function(`return ${match[1]}`)();
    assert.deepEqual(customMap, HTTP_TO_HTTPS_PORT);
  });
});

