const test = require('node:test');
const assert = require('node:assert/strict');
const { adaptServiceUrl, SERVICE_PORTS, PORT_TO_SERVICE, SERVICES_HOST, PI_HOSTS } = require('../config/homepage/adapt-links.js');

test('DuckDNS Subdomain & Ingress Link Adapter Suite', async (t) => {
  const duckdnsOrigin = 'https://spicy-llama.duckdns.org/';
  const localHostOrigin = 'http://desktop-kujo8mp/';
  const lanIpOrigin = 'http://192.168.1.20/';

  await t.test('Subdomain Ingress: preserves clean HTTPS subdomains when accessed via DuckDNS', () => {
    assert.equal(
      adaptServiceUrl('https://jellyfin.spicy-llama.duckdns.org', duckdnsOrigin),
      'https://jellyfin.spicy-llama.duckdns.org'
    );
    assert.equal(
      adaptServiceUrl('https://sonarr.spicy-llama.duckdns.org', duckdnsOrigin),
      'https://sonarr.spicy-llama.duckdns.org'
    );
    assert.equal(
      adaptServiceUrl('https://radarr.spicy-llama.duckdns.org', duckdnsOrigin),
      'https://radarr.spicy-llama.duckdns.org'
    );
  });

  await t.test('Port-to-Subdomain Migration: converts legacy local port URLs to clean subdomains on HTTPS', () => {
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:8096', duckdnsOrigin),
      'https://jellyfin.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:8989', duckdnsOrigin),
      'https://sonarr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:7878', duckdnsOrigin),
      'https://radarr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:9696', duckdnsOrigin),
      'https://prowlarr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:6767', duckdnsOrigin),
      'https://bazarr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:8080', duckdnsOrigin),
      'https://qbit.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:5055', duckdnsOrigin),
      'https://seerr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:3005', duckdnsOrigin),
      'https://stat.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:6246', duckdnsOrigin),
      'https://maintainerr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:11011', duckdnsOrigin),
      'https://cleanuparr.spicy-llama.duckdns.org/'
    );
    assert.equal(
      adaptServiceUrl('http://desktop-kujo8mp:8191', duckdnsOrigin),
      'https://flaresolverr.spicy-llama.duckdns.org/'
    );
  });

  await t.test('Local LAN Fallback: adapts DuckDNS subdomain URLs to local HTTP ports on desktop-kujo8mp', () => {
    assert.equal(
      adaptServiceUrl('https://jellyfin.spicy-llama.duckdns.org', localHostOrigin),
      'http://desktop-kujo8mp:8096/'
    );
    assert.equal(
      adaptServiceUrl('https://sonarr.spicy-llama.duckdns.org', localHostOrigin),
      'http://desktop-kujo8mp:8989/'
    );
    assert.equal(
      adaptServiceUrl('https://radarr.spicy-llama.duckdns.org', localHostOrigin),
      'http://desktop-kujo8mp:7878/'
    );
    assert.equal(
      adaptServiceUrl('https://seerr.spicy-llama.duckdns.org', localHostOrigin),
      'http://desktop-kujo8mp:5055/'
    );
  });

  await t.test('Local LAN IP Fallback: adapts DuckDNS subdomain URLs to local HTTP ports on LAN IP', () => {
    assert.equal(
      adaptServiceUrl('https://jellyfin.spicy-llama.duckdns.org', lanIpOrigin),
      'http://192.168.1.20:8096/'
    );
    assert.equal(
      adaptServiceUrl('https://qbit.spicy-llama.duckdns.org', lanIpOrigin),
      'http://192.168.1.20:8080/'
    );
  });

  await t.test('Pi Origin: Homepage served by the Pi sends local links to the services host, not the Pi', () => {
    for (const piOrigin of ['http://192.168.1.35:3000/', 'http://lemonpi.lan:3000/', 'http://lemonpi:3000/']) {
      assert.equal(
        adaptServiceUrl('https://jellyfin.spicy-llama.duckdns.org', piOrigin),
        `http://${SERVICES_HOST}:8096/`
      );
      assert.equal(
        adaptServiceUrl('https://sonarr.spicy-llama.duckdns.org', piOrigin),
        `http://${SERVICES_HOST}:8989/`
      );
    }
    assert.equal(SERVICES_HOST, '192.168.1.20');
  });

  await t.test('External Links Safety: never modifies external websites', () => {
    assert.equal(
      adaptServiceUrl('https://github.com/Anwera64/homelab', duckdnsOrigin),
      'https://github.com/Anwera64/homelab'
    );
    assert.equal(
      adaptServiceUrl('https://trash-guides.info/', duckdnsOrigin),
      'https://trash-guides.info/'
    );
    assert.equal(
      adaptServiceUrl('https://example.com:8443/api', duckdnsOrigin),
      'https://example.com:8443/api'
    );
  });

  await t.test('Code Parity: custom.js and adapt-links.js have identical SERVICE_PORTS and PORT_TO_SERVICE maps', () => {
    const fs = require('node:fs');
    const path = require('node:path');
    const customJsPath = path.resolve(__dirname, '../config/homepage/custom.js');
    const customJsContent = fs.readFileSync(customJsPath, 'utf8');

    const servicePortsMatch = customJsContent.match(/var SERVICE_PORTS\s*=\s*(\{[\s\S]*?\});/);
    assert.ok(servicePortsMatch, 'SERVICE_PORTS should exist in custom.js');
    const customServicePorts = Function(`return ${servicePortsMatch[1]}`)();
    assert.deepEqual(customServicePorts, SERVICE_PORTS);

    const portToServiceMatch = customJsContent.match(/var PORT_TO_SERVICE\s*=\s*(\{[\s\S]*?\});/);
    assert.ok(portToServiceMatch, 'PORT_TO_SERVICE should exist in custom.js');
    const customPortToService = Function(`return ${portToServiceMatch[1]}`)();
    assert.deepEqual(customPortToService, PORT_TO_SERVICE);

    const servicesHostMatch = customJsContent.match(/var SERVICES_HOST\s*=\s*'([^']+)';/);
    assert.ok(servicesHostMatch, 'SERVICES_HOST should exist in custom.js');
    assert.equal(servicesHostMatch[1], SERVICES_HOST);

    const piHostsMatch = customJsContent.match(/var PI_HOSTS\s*=\s*(\[[\s\S]*?\]);/);
    assert.ok(piHostsMatch, 'PI_HOSTS should exist in custom.js');
    assert.deepEqual(Function(`return ${piHostsMatch[1]}`)(), PI_HOSTS);
  });
});
