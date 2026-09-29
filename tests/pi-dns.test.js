const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const PI_DIR = path.join(ROOT_DIR, 'hosts/pi');
const COMPOSE_PATH = path.join(PI_DIR, 'docker-compose.yml');
const UNBOUND_CONF_PATH = path.join(PI_DIR, 'unbound/unbound.conf');
const ENV_EXAMPLE_PATH = path.join(PI_DIR, '.env.example');
const GITIGNORE_PATH = path.join(ROOT_DIR, '.gitignore');
const BOOTSTRAP_PATH = path.join(PI_DIR, 'bootstrap.sh');
const GITATTRIBUTES_PATH = path.join(ROOT_DIR, '.gitattributes');

// Git on Windows may check files out with CRLF; the assertions are about content, not line endings.
const read = (file) => fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');

// The block of one compose service: from its "  name:" line up to the next top-level service or section.
function serviceBlock(compose, name) {
  const match = compose.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [a-z0-9_-]+:\\n|^[a-z]|(?![\\s\\S]))`, 'm'));
  assert.ok(match, `docker-compose.yml must define a ${name} service`);
  return match[1];
}

test('Pi DNS stack: Pi-hole + Unbound', async (t) => {
  const compose = read(COMPOSE_PATH);
  const pihole = serviceBlock(compose, 'pihole');
  const unbound = serviceBlock(compose, 'unbound');

  await t.test('both services use host networking and restart unless stopped', () => {
    for (const [name, block] of [['pihole', pihole], ['unbound', unbound]]) {
      assert.match(block, /network_mode:\s*host/, `${name} must use network_mode: host`);
      assert.match(block, /restart:\s*unless-stopped/, `${name} must restart unless stopped`);
    }
  });

  await t.test('images are pinned to a release, never latest', () => {
    for (const [name, block] of [['pihole', pihole], ['unbound', unbound]]) {
      const image = block.match(/image:\s*(\S+)/);
      assert.ok(image, `${name} must declare an image`);
      assert.match(image[1], /:[0-9]/, `${name} image must be pinned to a version tag`);
    }
  });

  await t.test('Pi-hole resolves through the local Unbound on 5335', () => {
    assert.match(pihole, /FTLCONF_dns_upstreams:\s*['"]?127\.0\.0\.1#5335['"]?/);
  });

  await t.test('Pi-hole saves its query database hourly and keeps 30 days, to spare the SD card', () => {
    assert.match(pihole, /FTLCONF_database_DBinterval:\s*['"]?3600['"]?/);
    assert.match(pihole, /FTLCONF_database_maxDBdays:\s*['"]?30['"]?/);
  });

  await t.test('Pi-hole keeps its logs in RAM', () => {
    assert.match(pihole, /tmpfs:[\s\S]*?-\s*\/var\/log\/pihole/);
  });

  await t.test('Pi-hole answers Tailscale clients too, not only the LAN', () => {
    assert.match(pihole, /FTLCONF_dns_listeningMode:\s*['"]?all['"]?/i);
  });

  await t.test('the admin password comes from .env, never a literal', () => {
    assert.match(pihole, /FTLCONF_webserver_api_password:\s*['"]?\$\{PIHOLE_PASSWORD[:?}]/);
  });

  await t.test('Unbound mounts the repo config and has a healthcheck on 5335', () => {
    assert.match(unbound, /\.\/unbound\/unbound\.conf:\/etc\/unbound\/unbound\.conf:ro/);
    assert.match(unbound, /healthcheck:[\s\S]*drill-hc[\s\S]*5335/);
  });

  await t.test('Pi-hole starts after Unbound is healthy', () => {
    assert.match(pihole, /depends_on:[\s\S]*unbound:[\s\S]*condition:\s*service_healthy/);
  });

  await t.test('unbound.conf listens only on localhost:5335 with DNSSEC', () => {
    const conf = read(UNBOUND_CONF_PATH);
    const interfaces = [...conf.matchAll(/^\s*interface:\s*(\S+)/gm)].map((m) => m[1]);
    assert.deepEqual(interfaces, ['127.0.0.1'], 'Unbound must bind to 127.0.0.1 only');
    assert.match(conf, /^\s*port:\s*5335\s*$/m);
    assert.match(conf, /^\s*auto-trust-anchor-file:/m, 'DNSSEC trust anchor must be configured');
    assert.match(conf, /^\s*harden-dnssec-stripped:\s*yes/m);
    assert.doesNotMatch(conf, /^\s*include-toplevel:/m, 'config must be self-contained');
  });

  await t.test('.env.example declares the password and Barcelona timezone', () => {
    const env = read(ENV_EXAMPLE_PATH);
    assert.match(env, /^PIHOLE_PASSWORD=/m);
    assert.match(env, /^TZ=Europe\/Madrid$/m);
  });

  await t.test('Pi-hole can serve DHCP, off until .env switches it on', () => {
    assert.match(pihole, /cap_add:\s*\n\s*-\s*NET_ADMIN/);
    assert.match(pihole, /FTLCONF_dhcp_active:\s*['"]?\$\{PIHOLE_DHCP_ACTIVE:-false\}/);
  });

  await t.test('Pi-hole DHCP matches the Livebox range, so devices keep their IPs', () => {
    assert.match(pihole, /FTLCONF_dhcp_start:\s*['"]?192\.168\.1\.10['"]?/);
    assert.match(pihole, /FTLCONF_dhcp_end:\s*['"]?192\.168\.1\.150['"]?/);
    assert.match(pihole, /FTLCONF_dhcp_router:\s*['"]?192\.168\.1\.1['"]?\s*$/m);
    assert.match(pihole, /FTLCONF_dhcp_netmask:\s*['"]?255\.255\.255\.0['"]?/);
    assert.match(pihole, /FTLCONF_dhcp_leaseTime:\s*['"]?24h['"]?/);
  });

  await t.test('static leases come from .env, never the repo', () => {
    assert.match(pihole, /FTLCONF_dhcp_hosts:\s*['"]?\$\{PIHOLE_DHCP_HOSTS:-\}/);
    const env = read(ENV_EXAMPLE_PATH);
    assert.match(env, /^PIHOLE_DHCP_ACTIVE=false$/m);
    assert.match(env, /^PIHOLE_DHCP_HOSTS=$/m);
    assert.match(env, /MAC,IP,name/, '.env.example must document the lease format');
    const macs = compose.match(/([0-9a-f]{2}:){5}[0-9a-f]{2}/gi) || [];
    assert.deepEqual(macs, [], 'no MAC addresses in the compose file');
  });

  await t.test('the Pi .env and runtime data stay out of git', () => {
    const ignore = read(GITIGNORE_PATH);
    assert.match(ignore, /^\.env$/m, '.env must be ignored at every depth');
    assert.match(ignore, /^hosts\/pi\/data\/$/m, 'hosts/pi/data/ must be ignored');
    assert.match(pihole, /\.\/data\/pihole:\/etc\/pihole/);
  });
});

test('Pi bootstrap script', async (t) => {
  const script = read(BOOTSTRAP_PATH);

  await t.test('is strict bash and refuses to run without root', () => {
    assert.match(script, /^#!\/usr\/bin\/env bash\n/);
    assert.match(script, /^set -euo pipefail$/m);
    assert.match(script, /id -u\)?"?\s*-ne\s*0/, 'must check for root');
  });

  await t.test('checks before each install, so reruns change nothing', () => {
    assert.match(script, /grep -qxF "dtoverlay=\$1"/, 'config.txt overlays guarded by grep');
    assert.match(script, /disable-wifi/);
    assert.match(script, /disable-bt/);
    assert.match(script, /dpkg -s log2ram/, 'log2ram guarded by dpkg -s');
    assert.match(script, /command -v docker/, 'Docker guarded by command -v');
    assert.match(script, /command -v tailscale/, 'Tailscale guarded by command -v');
  });

  await t.test('sends Docker logs to journald so log2ram keeps them in RAM', () => {
    assert.match(script, /"log-driver":\s*"journald"/);
  });

  await t.test('pins the Pi to 192.168.1.35 itself, since it becomes the DHCP server', () => {
    assert.match(script, /nmcli -g ipv4\.method con show/, 'must check the current method first');
    assert.match(script, /ipv4\.method manual/);
    assert.match(script, /ipv4\.addresses 192\.168\.1\.35\/24/);
    assert.match(script, /ipv4\.gateway 192\.168\.1\.1/);
  });

  await t.test('the Pi resolves without its own Pi-hole, so it can always repair itself', () => {
    assert.match(script, /ipv4\.ignore-auto-dns yes/);
    assert.match(script, /ipv4\.dns "1\.1\.1\.1 9\.9\.9\.9"/);
    assert.match(script, /tailscale set --accept-dns=false/);
  });

  await t.test('advertises the home network to the tailnet, with forwarding on', () => {
    assert.match(script, /\/etc\/sysctl\.d\/99-tailscale\.conf/);
    assert.match(script, /net\.ipv4\.ip_forward = 1/);
    assert.match(script, /net\.ipv6\.conf\.all\.forwarding = 1/);
    assert.match(script, /sysctl -p \/etc\/sysctl\.d\/99-tailscale\.conf/);
    assert.match(script, /tailscale set --advertise-routes=192\.168\.1\.0\/24/);
  });

  await t.test('turns off the unused Wi-Fi client', () => {
    assert.match(script, /systemctl is-enabled --quiet wpa_supplicant/);
    assert.match(script, /systemctl disable --now wpa_supplicant/);
  });

  await t.test('reminds about a pending reboot on every run until it happens', () => {
    assert.match(script, /\/sys\/class\/net\/wlan0/);
    assert.match(script, /systemctl is-active --quiet log2ram/);
  });

  await t.test('turns on unattended security upgrades', () => {
    assert.match(script, /APT::Periodic::Unattended-Upgrade "1"/);
  });

  await t.test('leaves the Tailscale login and secrets to a human', () => {
    assert.doesNotMatch(script, /^\s*(sudo\s+)?tailscale up/m, 'must never run tailscale up itself');
    assert.doesNotMatch(script, /PIHOLE_PASSWORD=\S/);
    assert.match(script, /\.env\.example/, 'must seed .env from .env.example');
  });

  await t.test('brings the stack up from its own folder', () => {
    assert.match(script, /docker compose --project-directory "\$PI_DIR" up -d/);
  });

  await t.test('keeps LF line endings, even from a Windows checkout', () => {
    assert.doesNotMatch(fs.readFileSync(BOOTSTRAP_PATH, 'utf8'), /\r/);
    assert.match(read(GITATTRIBUTES_PATH), /^hosts\/pi\/\*\*\/\*\.sh text eol=lf$/m);
  });

  await t.test('passes bash -n (and shellcheck when installed)', (st) => {
    const { spawnSync } = require('node:child_process');
    // On Windows plain `bash` is WSL's, which may have no shell; use Git Bash.
    const bashCmd = process.platform === 'win32'
      ? path.join(process.env.ProgramFiles || 'C:\\Program Files', 'Git', 'bin', 'bash.exe')
      : 'bash';
    const bash = spawnSync(bashCmd, ['-n'], { input: script, encoding: 'utf8' });
    if (bash.error) return st.skip('bash not installed');
    assert.equal(bash.status, 0, bash.stderr);
    const shellcheck = spawnSync('shellcheck', ['-s', 'bash', '-'], { input: script, encoding: 'utf8' });
    if (!shellcheck.error) assert.equal(shellcheck.status, 0, shellcheck.stdout);
  });
});