const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const STARTUP_SCRIPT_PATH = path.join(ROOT_DIR, 'startup_homelab.ps1');
const STOP_SCRIPT_PATH = path.join(ROOT_DIR, 'stop_homelab.ps1');
const VIRT_SCRIPT_PATH = path.join(ROOT_DIR, 'enable_virtualization.ps1');
const COMPACT_SCRIPT_PATH = path.join(ROOT_DIR, 'compact_docker_disk.ps1');

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

    // Verifies update parameters, persistent timestamp gate, and image prune
    assert.ok(
      startupContent.includes('[switch]$SkipUpdate') && startupContent.includes('[switch]$ForceUpdate'),
      'startup_homelab.ps1 must declare SkipUpdate and ForceUpdate switches'
    );
    assert.ok(
      startupContent.includes('[switch]$NoAI') && startupContent.includes('[switch]$ArrOnly'),
      'startup_homelab.ps1 must declare NoAI and ArrOnly switches'
    );
    assert.ok(
      startupContent.includes('.last_update'),
      'startup_homelab.ps1 must manage persistent .last_update state'
    );
    assert.ok(
      startupContent.includes('docker image prune -f'),
      'startup_homelab.ps1 must invoke docker image prune -f after update'
    );

    // Verifies active DuckDNS HTTPS endpoint banner and absence of deprecated Tailscale URL
    assert.ok(
      !startupContent.includes('homelab.llama-porbeagle.ts.net'),
      'startup_homelab.ps1 must not contain deprecated Tailscale URL homelab.llama-porbeagle.ts.net'
    );
    assert.ok(
      startupContent.includes('DuckDNS HTTPS') && startupContent.includes('$domain'),
      'startup_homelab.ps1 must display DuckDNS HTTPS endpoints'
    );
  });

  await t.test('startup_homelab.ps1 provisions the Ollama models listed in the manifest', () => {
    assert.ok(
      startupContent.includes('config\\ollama-models\\models.json'),
      'startup_homelab.ps1 must read the tracked model manifest config/ollama-models/models.json'
    );
    assert.ok(
      startupContent.includes('Get-FileHash') && startupContent.includes('SHA256'),
      'startup_homelab.ps1 must verify each downloaded GGUF against its SHA256'
    );
    assert.ok(
      /ollama create/.test(startupContent),
      'startup_homelab.ps1 must register each model with ollama create'
    );
    for (const retired of ['qwen3:14b', 'bge-m3', 'deepseek-v4-flash', '$requiredModels']) {
      assert.ok(
        !startupContent.includes(retired),
        `startup_homelab.ps1 must not name models itself (found ${retired}); the manifest does`
      );
    }
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

  await t.test('compact_docker_disk.ps1 trims and compacts the Docker data disk', () => {
    assert.ok(fs.existsSync(COMPACT_SCRIPT_PATH), 'compact_docker_disk.ps1 must exist');
    const compactContent = fs.readFileSync(COMPACT_SCRIPT_PATH, 'utf8');
    assert.ok(
      compactContent.includes('#Requires -RunAsAdministrator'),
      'compact_docker_disk.ps1 must require administrator rights for diskpart'
    );
    assert.ok(
      compactContent.includes('docker_data.vhdx') && compactContent.includes('$env:LOCALAPPDATA'),
      'compact_docker_disk.ps1 must target the Docker Desktop data disk under LOCALAPPDATA'
    );
    assert.ok(
      compactContent.includes('fstrim'),
      'compact_docker_disk.ps1 must trim free space inside the Docker VM before compacting'
    );
    assert.ok(
      compactContent.indexOf('fstrim') < compactContent.indexOf('wsl --shutdown'),
      'compact_docker_disk.ps1 must trim before shutting WSL down'
    );
    for (const command of ['attach vdisk readonly', 'compact vdisk', 'detach vdisk']) {
      assert.ok(
        compactContent.includes(command),
        `compact_docker_disk.ps1 diskpart script must run: ${command}`
      );
    }
    assert.ok(
      /before/i.test(compactContent) && /after/i.test(compactContent),
      'compact_docker_disk.ps1 must report the disk size before and after'
    );
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


