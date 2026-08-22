const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const STARTUP_SCRIPT_PATH = path.join(ROOT_DIR, 'startup_homelab.ps1');
const STOP_SCRIPT_PATH = path.join(ROOT_DIR, 'stop_homelab.ps1');
const VIRT_SCRIPT_PATH = path.join(ROOT_DIR, 'enable_virtualization.ps1');

test('PowerShell Automation Scripts Suite', async (t) => {
  const startupContent = fs.readFileSync(STARTUP_SCRIPT_PATH, 'utf8');
  const stopContent = fs.readFileSync(STOP_SCRIPT_PATH, 'utf8');
  const virtContent = fs.readFileSync(VIRT_SCRIPT_PATH, 'utf8');

  await t.test('startup_homelab.ps1 validates Docker CLI and handles .env fallback', () => {
    // Verifies Docker command check exists
    assert.ok(
      startupContent.includes('Get-Command docker'),
      'startup_homelab.ps1 must check for Docker installation'
    );

    // Verifies .env fallback copy from .env.example
    assert.ok(
      startupContent.includes('.env.example') && startupContent.includes('.env'),
      'startup_homelab.ps1 must check and fallback-copy .env from .env.example'
    );

    // Verifies docker compose up invocation
    assert.ok(
      /docker\s+compose.*up\s+-d/i.test(startupContent),
      'startup_homelab.ps1 must invoke docker compose up -d'
    );
  });

  await t.test('stop_homelab.ps1 terminates Docker stack and legacy processes', () => {
    // Verifies docker compose down invocation
    assert.ok(
      /docker\s+compose.*down/i.test(stopContent),
      'stop_homelab.ps1 must invoke docker compose down'
    );

    // Verifies process names to clean up
    const expectedProcesses = ['Sonarr', 'Radarr', 'Prowlarr', 'qbittorrent', 'jellyfin', 'flaresolverr'];
    for (const proc of expectedProcesses) {
      assert.ok(
        stopContent.includes(`"${proc}"`) || stopContent.includes(`'${proc}'`) || stopContent.includes(proc),
        `stop_homelab.ps1 cleanup list must include process: ${proc}`
      );
    }
  });

  await t.test('enable_virtualization.ps1 targets required Windows virtualization features', () => {
    assert.ok(
      virtContent.includes('VirtualMachinePlatform'),
      'enable_virtualization.ps1 must enable VirtualMachinePlatform'
    );
    assert.ok(
      virtContent.includes('Microsoft-Windows-Subsystem-Linux'),
      'enable_virtualization.ps1 must enable Microsoft-Windows-Subsystem-Linux'
    );
    assert.ok(
      virtContent.includes('hypervisorlaunchtype auto'),
      'enable_virtualization.ps1 must set hypervisorlaunchtype to auto'
    );
    assert.ok(
      virtContent.includes('wsl --update'),
      'enable_virtualization.ps1 must trigger wsl --update'
    );
  });
});
