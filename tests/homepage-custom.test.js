const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const HOMEPAGE_DIR = path.resolve(__dirname, '../config/homepage');
const read = (file) => fs.readFileSync(path.join(HOMEPAGE_DIR, file), 'utf8').replace(/\r\n/g, '\n');

test('Homepage custom.js and its links', async (t) => {
  const customJs = read('custom.js');

  await t.test('custom.js rewrites no links: every service is opened by its https:// name', () => {
    for (const gone of ['rewriteLinks', 'adaptServiceUrl', 'SERVICES_HOST', 'PI_HOSTS', 'SERVICE_PORTS', 'PORT_TO_SERVICE', 'MutationObserver']) {
      assert.ok(!customJs.includes(gone), `custom.js must not contain ${gone}`);
    }
    assert.ok(!fs.existsSync(path.join(HOMEPAGE_DIR, 'adapt-links.js')), 'adapt-links.js was removed with the adapter');
  });

  await t.test('custom.js keeps the GPS weather badge, and still parses', () => {
    for (const kept of ['updateWeatherBadge', 'fetchGpsWeather', 'initGpsWeather']) {
      assert.ok(customJs.includes(kept), `custom.js must still contain ${kept}`);
    }
    assert.doesNotThrow(() => new Function(customJs));
  });

  await t.test('every link in services.yaml is an https:// address, so nothing needs rewriting', () => {
    const hrefs = [...read('services.yaml').matchAll(/^\s*href:\s*(\S+)/gm)].map((m) => m[1]);
    assert.ok(hrefs.length > 10, 'services.yaml should link its services');
    for (const href of hrefs) {
      assert.match(href, /^["']?https:\/\//, `${href} must be an https:// address`);
    }
  });
});
