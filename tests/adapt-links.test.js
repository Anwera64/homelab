const test = require('node:test');
const assert = require('node:assert/strict');
const { adaptServiceUrl } = require('../config/homepage/adapt-links.js');

test('Dynamic Ingress Link Adapter Suite', async (t) => {
  await t.test('Tailscale Remote Ingress: adapts hostname AND https protocol', () => {
    const remoteOrigin = 'https://homelab.llama-porbeagle.ts.net/';
    const input = 'http://desktop-kujo8mp:8096';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:8096/');
  });

  await t.test('Tailscale Remote Ingress: adapts Jellyseerr with https protocol', () => {
    const remoteOrigin = 'https://homelab.llama-porbeagle.ts.net/dashboard';
    const input = 'http://desktop-kujo8mp:5055';
    const result = adaptServiceUrl(input, remoteOrigin);
    assert.equal(result, 'https://homelab.llama-porbeagle.ts.net:5055/');
  });

  await t.test('Local LAN IP Ingress: adapts hostname and preserves http protocol', () => {
    const lanOrigin = 'http://192.168.1.20/';
    const input = 'https://homelab.llama-porbeagle.ts.net:8096';
    const result = adaptServiceUrl(input, lanOrigin);
    assert.equal(result, 'http://192.168.1.20:8096/');
  });

  await t.test('Local Hostname Ingress: preserves desktop-kujo8mp and http protocol', () => {
    const localHostOrigin = 'http://desktop-kujo8mp/';
    const input = 'http://192.168.1.20:8989';
    const result = adaptServiceUrl(input, localHostOrigin);
    assert.equal(result, 'http://desktop-kujo8mp:8989/');
  });

  await t.test('Localhost Ingress: adapts link to localhost and current protocol', () => {
    const localhostOrigin = 'http://localhost:3000/';
    const input = 'http://desktop-kujo8mp:7878';
    const result = adaptServiceUrl(input, localhostOrigin);
    assert.equal(result, 'http://localhost:7878/');
  });

  await t.test('External Links Safety: never modifies external websites regardless of protocol', () => {
    const remoteOrigin = 'https://homelab.llama-porbeagle.ts.net/';
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
    const remoteOrigin = 'https://homelab.llama-porbeagle.ts.net/';
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
    const origin = 'https://homelab.llama-porbeagle.ts.net/';
    assert.equal(adaptServiceUrl('/local-path', origin), '/local-path');
    assert.equal(adaptServiceUrl('', origin), '');
    assert.equal(adaptServiceUrl(null, origin), null);
    assert.equal(adaptServiceUrl(undefined, origin), undefined);
  });
});
