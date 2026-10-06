const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const ROOT_DIR = path.resolve(__dirname, '..');
const SERVER_DIR = path.join(ROOT_DIR, 'hosts/server');
const SYSTEM_DIR = path.join(SERVER_DIR, 'system');
const SCRIPTS = [
  'bootstrap.sh', 'system/server-firewall.sh', 'system/server-media.sh', 'system/server-battery.sh',
  'system/server-stack-up.sh', 'system/server-update.sh', 'system/server-renovate.sh',
];

// Missing files read as empty so each check fails with its own message. CRLF checkouts are normalised.
const read = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : '');
const at = (text, needle) => {
  const index = needle instanceof RegExp ? text.search(needle) : text.indexOf(needle);
  assert.ok(index >= 0, `the script must contain ${needle}`);
  return index;
};

test('Server bootstrap script', async (t) => {
  const script = read(path.join(SERVER_DIR, 'bootstrap.sh'));

  await t.test('is strict bash and refuses to run without root', () => {
    assert.match(script, /^#!\/usr\/bin\/env bash\n/);
    assert.match(script, /^set -euo pipefail$/m);
    assert.match(script, /id -u\)?"?\s*-ne\s*0/, 'must check for root');
  });

  await t.test('checks before each install, so reruns change nothing', () => {
    assert.match(script, /dpkg -s nvidia-kernel-dkms/, 'the NVIDIA driver guarded by dpkg -s');
    assert.match(script, /command -v docker/, 'Docker guarded by command -v');
    assert.match(script, /command -v nvidia-ctk/, 'the container toolkit guarded by command -v');
    // Files are only rewritten, and their services only restarted, when the content differs.
    assert.match(script, /^write_if_changed\(\) \{$/m);
    assert.match(script, /cmp -s/);
  });

  await t.test('enables the non-free package sections the NVIDIA driver lives in', () => {
    assert.match(script, /contrib/);
    assert.match(script, /non-free/);
    assert.match(script, /\/etc\/apt\/sources\.list/);
  });

  await t.test('blocks nouveau and stops for a reboot before the NVIDIA packages go in', () => {
    // nouveau hangs this GPU on resume, which froze lspci and the driver install (Oct 2026).
    const blacklist = at(script, 'blacklist nouveau');
    const initramfs = at(script, 'update-initramfs -u');
    const stillLoaded = at(script, /lsmod \| grep -q '\^nouveau'/);
    const install = at(script, /apt-get install[^\n]*nvidia-kernel-dkms/);
    assert.ok(blacklist < initramfs && initramfs < stillLoaded && stillLoaded < install, 'blacklist, rebuild the boot image, check, then install');
    const stop = script.slice(stillLoaded, install);
    assert.match(stop, /exit 0/, 'must stop there while nouveau is still loaded');
  });

  await t.test('installs the driver set that was proven on the machine, without a desktop stack', () => {
    for (const pkg of ['linux-headers-amd64', 'nvidia-kernel-dkms', 'nvidia-smi', 'libnvidia-encode1', 'libnvcuvid1', 'firmware-misc-nonfree']) {
      assert.match(script, new RegExp(`\\b${pkg}\\b`), `must install ${pkg}`);
    }
    assert.doesNotMatch(script, /apt-get install[^\n]*\bnvidia-driver\b/, 'the full nvidia-driver package pulls in Xorg');
  });

  await t.test('installs Docker and the NVIDIA Container Toolkit from their own repositories', () => {
    assert.match(script, /download\.docker\.com\/linux\/debian/);
    assert.match(script, /docker-ce docker-ce-cli containerd\.io docker-buildx-plugin docker-compose-plugin/);
    assert.match(script, /nvidia\.github\.io\/libnvidia-container/);
    assert.match(script, /nvidia-ctk runtime configure --runtime=docker/);
    assert.match(script, /usermod -aG docker "\$TARGET_USER"/);
  });

  await t.test('merges Docker\'s daemon.json so the NVIDIA runtime entry survives', () => {
    assert.match(script, /jq /, 'daemon.json must be merged with jq, not overwritten');
    assert.match(script, /"log-driver":\s*"json-file"/);
    assert.match(script, /"max-size":\s*"10m"/);
    assert.doesNotMatch(script, /echo[^\n]*>\s*"?\$DAEMON_JSON"?\s*$/m, 'never write daemon.json from a literal');
  });

  await t.test('keeps Debian and Docker patched, rebooting at 05:00 when an update needs it', () => {
    assert.match(script, /APT::Periodic::Unattended-Upgrade "1"/);
    assert.match(script, /"origin=Docker"/, 'Docker\'s repository must be an allowed origin');
    assert.match(script, /Unattended-Upgrade::Automatic-Reboot "true"/);
    assert.match(script, /Unattended-Upgrade::Automatic-Reboot-Time "05:00"/);
  });

  await t.test('unblocks the radios, which came up in airplane mode after the install', () => {
    assert.match(script, /rfkill unblock all/);
  });

  await t.test('a closed lid changes nothing and the panel goes dark after a minute', () => {
    for (const key of ['HandleLidSwitch', 'HandleLidSwitchExternalPower', 'HandleLidSwitchDocked']) {
      assert.match(script, new RegExp(`${key}=ignore`));
    }
    assert.match(script, /\/etc\/systemd\/logind\.conf\.d\//);
    assert.match(script, /consoleblank=60/);
    assert.match(script, /grep -q consoleblank \/etc\/default\/grub/, 'the kernel option is added once');
    assert.match(script, /update-grub/);
  });

  await t.test('SSH takes keys only, and never locks out a user without one', () => {
    const guard = at(script, /-s "\$USER_HOME\/\.ssh\/authorized_keys"/);
    const harden = at(script, 'PasswordAuthentication no');
    assert.ok(guard < harden, 'the authorized_keys check comes first');
    assert.match(script, /PermitRootLogin no/);
    assert.match(script, /sshd -t/, 'the config is validated before the reload');
  });

  await t.test('keeps the laptop-only steps in one marked section', () => {
    const start = at(script, '# >>> laptop');
    const end = at(script, '# <<< laptop');
    const section = script.slice(start, end);
    const outside = script.slice(0, start) + script.slice(end);
    for (const marker of ['nouveau', 'nvidia-kernel-dkms', 'HandleLidSwitch', 'consoleblank', 'server-battery']) {
      assert.ok(section.includes(marker), `${marker} belongs in the laptop section`);
      assert.ok(!outside.includes(marker), `${marker} must not appear outside the laptop section`);
    }
  });

  await t.test('mounts the media disk by UUID, and nothing can fill the SSD while it is away', () => {
    assert.match(script, /DATA_DISK_UUID/);
    assert.match(script, /UUID=\$DATA_DISK_UUID \/data ext4 [^\n]*nofail/);
    assert.match(script, /x-systemd\.device-timeout=/, 'a missing disk must not hold the boot for long');
    assert.match(script, /grep -q '\[\[:space:\]\]\/data\[\[:space:\]\]' \/etc\/fstab/, 'the fstab line is added once');
    // An immutable empty folder: a container started without the disk cannot write into it.
    const notMounted = at(script, /mountpoint -q \/data \|\|/);
    assert.match(script.slice(notMounted), /chattr \+i \/data/);
  });

  await t.test('installs the firewall, disk guard and battery units with the repo path filled in', () => {
    for (const unit of ['server-firewall.service', 'server-media.service', 'server-battery.service', 'server-update.timer', 'server-renovate.timer']) {
      assert.ok(script.includes(unit), `must install ${unit}`);
    }
    assert.match(script, /s\|@SERVER_DIR@\|\$SERVER_DIR\|g/);
    assert.match(script, /systemctl daemon-reload/);
    assert.match(script, /systemctl enable --now/);
  });

  await t.test('creates the app settings folders itself, before Docker would create them as root', () => {
    // Seerr, Maintainerr and Recyclarr run as user 1000 and cannot write to a root-owned folder.
    const folders = (script.match(/^APP_FOLDERS=\(([^)]*)\)$/m) || ['', ''])[1].split(/\s+/).filter(Boolean);
    for (const app of ['seerr', 'maintainerr', 'recyclarr', 'sonarr', 'radarr', 'jellyfin/config', 'jellyfin/cache']) {
      assert.ok(folders.includes(app), `${app} must be in APP_FOLDERS`);
    }
    const create = at(script, 'install -d -o 1000 -g 1000 "$CONFIG_PATH/$app"');
    assert.match(script, /\[ -d "\$CONFIG_PATH\/\$app" \] \|\|/, 'existing folders keep their owner');
    assert.ok(create < at(script, '"$SERVER_DIR/system/server-stack-up.sh"'));
  });

  await t.test('seeds .env on the first run and leaves the secrets to a human', () => {
    assert.match(script, /cp "\$SERVER_DIR\/\.env\.example" "\$SERVER_DIR\/\.env"/);
    assert.match(script, /chmod 600 "\$SERVER_DIR\/\.env"/);
    assert.doesNotMatch(script, /WIREGUARD_PRIVATE_KEY=\S/);
  });

  await t.test('builds and starts the stack from its own folder, then lets the disk guard decide', () => {
    // One script starts the stack, for bootstrap and the nightly update alike, so the two cannot drift.
    const stackUp = read(path.join(SYSTEM_DIR, 'server-stack-up.sh'));
    assert.match(script, /^  "\$SERVER_DIR\/system\/server-stack-up\.sh"$/m);
    assert.doesNotMatch(script, /docker compose/, 'bootstrap leaves compose to server-stack-up.sh');
    const build = at(stackUp, 'docker compose --project-directory "$SERVER_DIR" build');
    const up = at(stackUp, 'docker compose --project-directory "$SERVER_DIR" up -d --remove-orphans');
    const guard = at(stackUp, /server-media\.sh" --once/);
    assert.ok(build < up && up < guard);
    // Without the disk the media services are left out; they cannot mount /data.
    assert.match(stackUp, /if mountpoint -q \/data; then/);
    assert.match(stackUp, /COMPOSE_PROFILES="" docker compose --project-directory "\$SERVER_DIR" up -d --remove-orphans/);
    // Host setup alone, for the days before the data is migrated.
    assert.match(script, /--no-stack/);
  });

  await t.test('pulls the embedding model once the stack is up, and only when it is missing', () => {
    const up = at(script, '"$SERVER_DIR/system/server-stack-up.sh"');
    const pull = at(script, 'docker exec ollama ollama pull "$EMBEDDING_MODEL"');
    assert.ok(up < pull, 'Ollama must be running before the pull');
    assert.match(script, /^EMBEDDING_MODEL="bge-m3"$/m);
    // Ollama takes a moment to answer after it starts; the wait is bounded.
    assert.match(script, /for _ in \$\(seq 1 30\); do\n\s+docker exec ollama ollama list/);
    // Braced: shellcheck reads "$EMBEDDING_MODEL[" as an array index (SC1087).
    assert.match(script, /ollama list[^\n]*\| grep -q "\^\$\{EMBEDDING_MODEL\}\[:\[:space:\]\]"/, 'checks before it pulls');
    // Without the model the Hub still answers, by keywords: a failed pull must not stop the script.
    assert.match(script, /ollama pull "\$EMBEDDING_MODEL" \|\| echo/);
    // The model it pulls is the one the Hub asks for.
    const compose = read(path.join(SERVER_DIR, 'docker-compose.yml'));
    assert.ok(compose.includes('- EMBEDDING_MODEL=bge-m3'));
  });

  await t.test('reminds about a pending reboot and the temporary sudo rule', () => {
    assert.match(script, /^finish\(\) \{$/m);
    assert.match(script, /\/sys\/module\/nvidia/);
    assert.match(script, /\/etc\/sudoers\.d\/90-setup-temp/);
    assert.doesNotMatch(script, /rm[^\n]*90-setup-temp/, 'removing it is the owner\'s call');
  });
});

test('Server firewall', async (t) => {
  const script = read(path.join(SYSTEM_DIR, 'server-firewall.sh'));
  const unit = read(path.join(SYSTEM_DIR, 'server-firewall.service'));

  await t.test('filters published container ports in DOCKER-USER, where Docker looks first', () => {
    // Docker forwards published ports before the host's own input rules ever see them.
    const flush = at(script, 'iptables -F DOCKER-USER');
    const established = at(script, /iptables -A DOCKER-USER -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN/);
    const allow = at(script, /iptables -A DOCKER-USER -i "\$iface" -s "\$source" -j RETURN/);
    const drop = at(script, /iptables -A DOCKER-USER -i "\$iface" -j DROP/);
    assert.ok(flush < established && established < allow && allow < drop, 'flush, established, allowed sources, then drop');
    assert.match(script, /LAN_IFACES=\(en\+ wl\+\)/, 'only traffic arriving from the LAN is filtered, not the containers\' own');
  });

  await t.test('takes the allowed sources from .env, lemonpi by default', () => {
    assert.match(script, /SERVER_ALLOWED_SOURCES/);
    assert.match(script, /192\.168\.1\.35/);
    assert.doesNotMatch(script, /192\.168\.1\.20/, 'the desktop is allowed through .env, not the script');
  });

  await t.test('admits one port for one address, matched on the port the caller dialled', () => {
    // SERVER_PORT_SOURCES="3100=192.168.1.20": the desktop's Alloy reaches Loki and nothing else.
    assert.match(script, /SERVER_PORT_SOURCES/);
    const perPort = at(script, /iptables -A DOCKER-USER -i "\$iface" -s "\$address" -p tcp -m conntrack --ctorigdstport "\$port" -j RETURN/);
    const drop = at(script, /iptables -A DOCKER-USER -i "\$iface" -j DROP/);
    assert.ok(perPort < drop, 'the per-port accepts come before the drop');
    assert.doesNotMatch(script, /3100/, 'ports come from .env, not the script');
  });

  await t.test('drops everything else aimed at the host, IPv6 included, but keeps SSH for the LAN', () => {
    assert.match(script, /table inet server_host/);
    assert.match(script, /type filter hook input priority 0; policy drop;/);
    assert.match(script, /iif lo accept/);
    assert.match(script, /ct state established,related accept/);
    assert.match(script, /ip saddr 192\.168\.1\.0\/24 tcp dport 22 accept/);
    assert.match(script, /udp dport 68 accept/, 'the DHCP lease must keep renewing');
    assert.match(script, /iifname "docker0" accept/);
    assert.match(script, /iifname "br-\*" accept/);
  });

  await t.test('can be tried with an automatic rollback, so a mistake cannot lock SSH out', () => {
    assert.match(script, /--try\)/);
    assert.match(script, /--confirm\)/);
    assert.match(script, /--clear\)/);
    assert.match(script, /systemd-run --on-active=120/);
    assert.match(script, /nft delete table inet server_host/);
  });

  await t.test('is applied after Docker starts and again whenever Docker restarts', () => {
    assert.match(unit, /^After=docker\.service$/m);
    assert.match(unit, /^PartOf=docker\.service$/m);
    assert.match(unit, /^Type=oneshot$/m);
    assert.match(unit, /^RemainAfterExit=yes$/m);
    assert.match(unit, /^ExecStart=@SERVER_DIR@\/system\/server-firewall\.sh$/m);
    assert.match(unit, /^WantedBy=multi-user\.target docker\.service$/m);
  });
});

test('Server disk guard', async (t) => {
  const script = read(path.join(SYSTEM_DIR, 'server-media.sh'));
  const unit = read(path.join(SYSTEM_DIR, 'server-media.service'));
  const compose = read(path.join(SERVER_DIR, 'docker-compose.yml'));

  await t.test('guards exactly the services in the compose file\'s media profile', () => {
    const inProfile = [...compose.matchAll(/^  ([a-z0-9_-]+):\n    profiles: \["media"\]$/gm)].map((m) => m[1]).sort();
    const listed = (script.match(/^MEDIA_SERVICES=\(([^)]*)\)$/m) || ['', ''])[1].split(/\s+/).filter(Boolean).sort();
    assert.ok(inProfile.length > 0, 'the compose file must have a media profile');
    assert.deepEqual(listed, inProfile);
  });

  await t.test('counts the disk as present only while its device is still there', () => {
    // A USB disk that drops out leaves a dead mount behind; mountpoint alone would say "fine".
    assert.match(script, /mountpoint -q \/data/);
    assert.match(script, /findmnt -no SOURCE \/data/);
    assert.match(script, /\[ -b "\$source" \]/);
  });

  await t.test('stops the media services when the disk goes, and only restarts what already existed', () => {
    assert.match(script, /docker stop "\$\{MEDIA_SERVICES\[@\]\}"/);
    assert.match(script, /umount -l \/data/, 'the dead mount is released so a replugged disk can mount again');
    // `docker start` never creates a container: bootstrap.sh decides what runs, the guard only resumes it.
    assert.match(script, /docker start "\$\{MEDIA_SERVICES\[@\]\}"/);
    // By container name, not through compose: a half-filled .env must not stop the guard working.
    assert.doesNotMatch(script, /docker compose/, 'the guard must not depend on the compose file resolving');
  });

  await t.test('watches in a loop and only acts on a change', () => {
    assert.match(script, /--once\)/, 'bootstrap.sh runs a single pass');
    assert.match(script, /while true; do/);
    assert.match(script, /\[ "\$state" != "\$last" \]/);
    assert.match(unit, /^After=docker\.service$/m);
    assert.match(unit, /^ExecStart=@SERVER_DIR@\/system\/server-media\.sh$/m);
    assert.match(unit, /^Restart=always$/m);
    assert.match(unit, /^WantedBy=multi-user\.target$/m);
  });
});

test('Server battery watcher', async (t) => {
  const script = read(path.join(SYSTEM_DIR, 'server-battery.sh'));
  const unit = read(path.join(SYSTEM_DIR, 'server-battery.service'));

  await t.test('holds the charge at 60% to slow the battery\'s wear', () => {
    assert.match(script, /conservation_mode/);
    assert.match(script, /echo 1 > "\$CONSERVATION"/);
  });

  await t.test('shuts down cleanly at 10%, and only while running on battery', () => {
    assert.match(script, /BATTERY_SHUTDOWN_PERCENT:-10/);
    assert.match(script, /\[ "\$online" = "0" \] && \[ "\$capacity" -le "\$THRESHOLD" \]/);
    // Every container, by ID: this must work whatever state .env is in.
    const stop = at(script, /docker stop \$\(docker ps -q\)/);
    const poweroff = at(script, 'systemctl poweroff');
    assert.ok(stop < poweroff, 'the containers stop before the power goes');
  });

  await t.test('can be rehearsed without shutting anything down', () => {
    assert.match(script, /DRY_RUN/);
    assert.match(script, /--once\)/);
  });

  await t.test('runs as a service that restarts if it dies', () => {
    assert.match(unit, /^ExecStart=@SERVER_DIR@\/system\/server-battery\.sh$/m);
    assert.match(unit, /^Restart=always$/m);
    assert.match(unit, /^WantedBy=multi-user\.target$/m);
  });
});

test('Server nightly update', async (t) => {
  const script = read(path.join(SYSTEM_DIR, 'server-update.sh'));
  const unit = read(path.join(SYSTEM_DIR, 'server-update.service'));
  const timer = read(path.join(SYSTEM_DIR, 'server-update.timer'));
  const bootstrap = read(path.join(SERVER_DIR, 'bootstrap.sh'));

  await t.test('runs at 04:00, and catches up on a night the server was off', () => {
    assert.match(timer, /^OnCalendar=\*-\*-\* 04:00:00$/m);
    assert.match(timer, /^Persistent=true$/m);
    assert.match(timer, /^WantedBy=timers\.target$/m);
    assert.match(unit, /^Type=oneshot$/m);
    assert.match(unit, /^ExecStart=@SERVER_DIR@\/system\/server-update\.sh$/m);
    assert.match(unit, /^After=docker\.service network-online\.target$/m);
    assert.match(unit, /^Wants=network-online\.target$/m);
    // The timer starts it; enabling the service itself would run an update on every boot.
    assert.doesNotMatch(unit, /^\[Install\]$/m);
  });

  await t.test('bootstrap installs the timer, and the service without starting it', () => {
    assert.match(bootstrap, /^place_unit server-update\.service/m);
    assert.match(bootstrap, /^install_unit server-update\.timer$/m);
    assert.doesNotMatch(bootstrap, /install_unit server-update\.service/);
  });

  await t.test('only fast-forwards, as the checkout\'s owner', () => {
    // git refuses a root-run command in a checkout someone else owns.
    assert.match(script, /runuser -u "\$OWNER" -- git -C "\$REPO_DIR"/);
    assert.match(script, /merge --ff-only/);
    assert.doesNotMatch(script, /reset --hard|push|rebase|git pull(?! --ff-only)/);
  });

  // Behaviour, against a throwaway origin and checkout. docker and runuser are stubs on PATH.
  const bash = spawnSync('bash', ['-c', 'command -v git'], { encoding: 'utf8' });
  const canRun = process.platform !== 'win32' && !bash.error && bash.status === 0;
  const git = (cwd, ...args) => {
    const r = spawnSync('git', ['-c', 'user.name=t', '-c', 'user.email=t@t', '-c', 'init.defaultBranch=master', ...args], { cwd, encoding: 'utf8' });
    assert.equal(r.status, 0, `git ${args.join(' ')}: ${r.stderr}`);
    return r.stdout.trim();
  };
  const setup = () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'server-update-'));
    const origin = path.join(root, 'origin.git');
    const seed = path.join(root, 'seed');
    const checkout = path.join(root, 'homelab');
    const bin = path.join(root, 'bin');
    const log = path.join(root, 'calls.log');
    fs.mkdirSync(bin);
    fs.writeFileSync(path.join(bin, 'docker'), `#!/bin/sh\necho "docker $*" >> "${log}"\n`, { mode: 0o755 });
    fs.writeFileSync(path.join(bin, 'runuser'), '#!/bin/sh\n[ "$1" = "-u" ] && [ "$3" = "--" ] || exit 2\nshift 3\nexec "$@"\n', { mode: 0o755 });
    git(root, 'init', '-q', '--bare', origin);
    git(root, 'clone', '-q', origin, seed);
    const system = path.join(seed, 'hosts/server/system');
    fs.mkdirSync(system, { recursive: true });
    fs.copyFileSync(path.join(SYSTEM_DIR, 'server-update.sh'), path.join(system, 'server-update.sh'));
    fs.chmodSync(path.join(system, 'server-update.sh'), 0o755);
    fs.writeFileSync(path.join(system, 'server-stack-up.sh'), `#!/bin/sh\necho "stack-up" >> "${log}"\n`, { mode: 0o755 });
    fs.writeFileSync(path.join(seed, 'hosts/server/docker-compose.yml'), 'image: app:1.0.0\n');
    fs.writeFileSync(path.join(seed, 'hosts/server/bootstrap.sh'), '#!/bin/sh\n');
    git(seed, 'add', '-A');
    git(seed, 'commit', '-qm', 'first');
    git(seed, 'push', '-q', 'origin', 'HEAD:master');
    git(root, 'clone', '-q', origin, checkout);
    const run = () => spawnSync(path.join(checkout, 'hosts/server/system/server-update.sh'), [], {
      encoding: 'utf8', env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH}` },
    });
    const calls = () => (fs.existsSync(log) ? fs.readFileSync(log, 'utf8') : '');
    const upstream = (file, content) => {
      fs.writeFileSync(path.join(seed, file), content);
      git(seed, 'commit', '-qam', `change ${file}`);
      git(seed, 'push', '-q', 'origin', 'HEAD:master');
    };
    return { root, seed, checkout, run, calls, upstream };
  };

  await t.test('does nothing when nothing was merged', { skip: !canRun && 'needs bash and git' }, () => {
    const { root, run, calls } = setup();
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(calls(), '', 'the stack is left alone');
    fs.rmSync(root, { recursive: true, force: true });
  });

  await t.test('applies a merged bump: fast-forwards, restarts the stack, prunes old images', { skip: !canRun && 'needs bash and git' }, () => {
    const { root, checkout, run, calls, upstream } = setup();
    upstream('hosts/server/docker-compose.yml', 'image: app:1.0.1\n');
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(path.join(checkout, 'hosts/server/docker-compose.yml'), 'utf8'), 'image: app:1.0.1\n');
    assert.deepEqual(calls().trim().split('\n'), ['stack-up', 'docker image prune -f']);
    assert.doesNotMatch(result.stdout, /bootstrap/, 'no host change, no reminder');
    fs.rmSync(root, { recursive: true, force: true });
  });

  await t.test('says when a merge changed the host setup, which only bootstrap applies', { skip: !canRun && 'needs bash and git' }, () => {
    const { root, run, upstream } = setup();
    upstream('hosts/server/bootstrap.sh', '#!/bin/sh\necho new step\n');
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /sudo hosts\/server\/bootstrap\.sh/);
    fs.rmSync(root, { recursive: true, force: true });
  });

  await t.test('changes nothing in a checkout with local edits', { skip: !canRun && 'needs bash and git' }, () => {
    const { root, checkout, run, calls, upstream } = setup();
    upstream('hosts/server/docker-compose.yml', 'image: app:1.0.1\n');
    fs.writeFileSync(path.join(checkout, 'hosts/server/bootstrap.sh'), '#!/bin/sh\necho edited by hand\n');
    const before = git(checkout, 'rev-parse', 'HEAD');
    const result = run();
    assert.notEqual(result.status, 0);
    assert.equal(git(checkout, 'rev-parse', 'HEAD'), before);
    assert.equal(calls(), '');
    fs.rmSync(root, { recursive: true, force: true });
  });

  await t.test('changes nothing in a checkout that has diverged from origin', { skip: !canRun && 'needs bash and git' }, () => {
    const { root, checkout, run, calls, upstream } = setup();
    upstream('hosts/server/docker-compose.yml', 'image: app:1.0.1\n');
    fs.writeFileSync(path.join(checkout, 'local.txt'), 'x\n');
    git(checkout, 'add', 'local.txt');
    git(checkout, 'commit', '-qm', 'local commit');
    const before = git(checkout, 'rev-parse', 'HEAD');
    const result = run();
    assert.notEqual(result.status, 0);
    assert.equal(git(checkout, 'rev-parse', 'HEAD'), before);
    assert.equal(calls(), '');
    fs.rmSync(root, { recursive: true, force: true });
  });
});

test('Server Renovate runs', async (t) => {
  const script = read(path.join(SYSTEM_DIR, 'server-renovate.sh'));
  const unit = read(path.join(SYSTEM_DIR, 'server-renovate.service'));
  const timer = read(path.join(SYSTEM_DIR, 'server-renovate.timer'));
  const bootstrap = read(path.join(SERVER_DIR, 'bootstrap.sh'));

  await t.test('start every hour: one run opens the PRs, a later one merges them once CI is green', () => {
    assert.match(timer, /^OnCalendar=hourly$/m);
    assert.match(timer, /^Persistent=true$/m);
    assert.match(timer, /^WantedBy=timers\.target$/m);
    assert.match(unit, /^Type=oneshot$/m);
    assert.match(unit, /^ExecStart=@SERVER_DIR@\/system\/server-renovate\.sh$/m);
    assert.match(unit, /^After=docker\.service network-online\.target$/m);
    assert.match(unit, /^Wants=network-online\.target$/m);
    // The timer starts it; enabling the service itself would run it on every boot.
    assert.doesNotMatch(unit, /^\[Install\]$/m);
  });

  await t.test('bootstrap installs the timer, and the service without starting it', () => {
    assert.match(bootstrap, /^place_unit server-renovate\.service/m);
    assert.match(bootstrap, /^install_unit server-renovate\.timer$/m);
    assert.doesNotMatch(bootstrap, /install_unit server-renovate\.service/);
  });

  // Behaviour, in a throwaway server folder. docker is a stub on PATH.
  const canRun = process.platform !== 'win32' && !spawnSync('bash', ['-c', 'true']).error;
  const setup = (envFile) => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'server-renovate-'));
    const bin = path.join(root, 'bin');
    const log = path.join(root, 'calls.log');
    fs.mkdirSync(bin);
    fs.mkdirSync(path.join(root, 'server/system'), { recursive: true });
    fs.writeFileSync(path.join(bin, 'docker'), `#!/bin/sh\necho "docker $*" >> "${log}"\n`, { mode: 0o755 });
    const target = path.join(root, 'server/system/server-renovate.sh');
    fs.copyFileSync(path.join(SYSTEM_DIR, 'server-renovate.sh'), target);
    fs.chmodSync(target, 0o755);
    if (envFile !== null) fs.writeFileSync(path.join(root, 'server/.env'), envFile);
    const run = () => spawnSync(target, [], { encoding: 'utf8', env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH}` } });
    const calls = () => (fs.existsSync(log) ? fs.readFileSync(log, 'utf8') : '');
    return { root, run, calls };
  };

  await t.test('skips the run, and says why, until the token is in .env', { skip: !canRun && 'needs bash' }, () => {
    for (const envFile of ['RENOVATE_TOKEN=\n', 'TZ=Europe/Madrid\n', null]) {
      const { root, run, calls } = setup(envFile);
      const result = run();
      assert.equal(result.status, 0, result.stderr);
      assert.match(result.stdout, /RENOVATE_TOKEN is not set/);
      assert.equal(calls(), '', 'Renovate must not start without a token');
      fs.rmSync(root, { recursive: true, force: true });
    }
  });

  await t.test('runs Renovate once through compose and reports how it ended', { skip: !canRun && 'needs bash' }, () => {
    const { root, run, calls } = setup('TZ=Europe/Madrid\nRENOVATE_TOKEN=github_pat_example\n');
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    const serverDir = path.join(root, 'server');
    assert.equal(calls().trim(), `docker compose --project-directory ${serverDir} --profile renovate up --exit-code-from renovate renovate`);
    // The token is read by compose from .env; it must never be printed.
    assert.doesNotMatch(result.stdout + result.stderr, /github_pat_example/);
    fs.rmSync(root, { recursive: true, force: true });
  });
});

test('Server scripts are safe to run from a Windows checkout', async (t) => {
  await t.test('keep LF line endings', () => {
    for (const file of SCRIPTS) {
      const full = path.join(SERVER_DIR, file);
      assert.ok(fs.existsSync(full), `${file} must exist`);
      assert.doesNotMatch(fs.readFileSync(full, 'utf8'), /\r/, `${file} must use LF`);
    }
    const attributes = read(path.join(ROOT_DIR, '.gitattributes'));
    assert.match(attributes, /^hosts\/server\/\*\*\/\*\.sh text eol=lf$/m);
    assert.match(attributes, /^hosts\/server\/system\/\*\.service text eol=lf$/m);
    assert.match(attributes, /^hosts\/server\/system\/\*\.timer text eol=lf$/m);
  });

  await t.test('are marked executable in git', () => {
    for (const file of SCRIPTS) {
      const listed = spawnSync('git', ['ls-files', '-s', `hosts/server/${file}`], { cwd: ROOT_DIR, encoding: 'utf8' }).stdout;
      assert.match(listed, /^100755 /, `hosts/server/${file} must be committed with the executable bit`);
    }
  });

  await t.test('pass bash -n (and shellcheck when installed)', (st) => {
    // On Windows plain `bash` is WSL's, which may have no shell; use Git Bash.
    const bashCmd = process.platform === 'win32'
      ? path.join(process.env.ProgramFiles || 'C:\\Program Files', 'Git', 'bin', 'bash.exe')
      : 'bash';
    for (const file of SCRIPTS) {
      const source = read(path.join(SERVER_DIR, file));
      assert.ok(source, `${file} must exist`);
      const bash = spawnSync(bashCmd, ['-n'], { input: source, encoding: 'utf8' });
      if (bash.error) return st.skip('bash not installed');
      assert.equal(bash.status, 0, `${file}: ${bash.stderr}`);
      const shellcheck = spawnSync('shellcheck', ['-s', 'bash', '-'], { input: source, encoding: 'utf8' });
      if (!shellcheck.error) assert.equal(shellcheck.status, 0, `${file}: ${shellcheck.stdout}`);
    }
  });
});
