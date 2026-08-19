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

  await t.test('Local LAN IP (HTTP): retains local port 8096 in pure HTTP', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'http://desktop-kujo8mp:8096';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:8096/');
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

  await t.test('Relative and malformed links resilience', () => {
    assert.equal(adaptServiceUrl('/local-path', remoteOrigin), '/local-path');
    assert.equal(adaptServiceUrl('', remoteOrigin), '');
    assert.equal(adaptServiceUrl(null, remoteOrigin), null);
    assert.equal(adaptServiceUrl(undefined, remoteOrigin), undefined);
  });
});
