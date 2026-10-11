const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DESKTOP_DIR = path.join(ROOT_DIR, 'hosts/desktop');
const STARTUP_SCRIPT_PATH = path.join(DESKTOP_DIR, 'startup_homelab.ps1');
const STOP_SCRIPT_PATH = path.join(DESKTOP_DIR, 'stop_homelab.ps1');
const VIRT_SCRIPT_PATH = path.join(DESKTOP_DIR, 'enable_virtualization.ps1');
const COMPACT_SCRIPT_PATH = path.join(DESKTOP_DIR, 'compact_docker_disk.ps1');

test('PowerShell Automation Scripts Suite', async (t) => {
  const startupContent = fs.readFileSync(STARTUP_SCRIPT_PATH, 'utf8');
  const stopContent = fs.readFileSync(STOP_SCRIPT_PATH, 'utf8');
  const virtContent = fs.readFileSync(VIRT_SCRIPT_PATH, 'utf8');

  await t.test('startup_homelab.ps1 checks Docker, seeds .env and starts the two containers', () => {
    assert.ok(startupContent.includes('Get-Command docker'), 'must check that Docker is installed');
    assert.ok(startupContent.includes('Docker Desktop.exe'), 'must start Docker Desktop when the engine is down');
    assert.ok(
      startupContent.includes('Copy-Item "$PSScriptRoot\\.env.example" "$PSScriptRoot\\.env"'),
      'must seed .env from .env.example, both beside the script'
    );
    // Its own folder, whatever the caller's: it is on PATH and run from anywhere.
    assert.match(
      startupContent,
      /docker compose -f "\$PSScriptRoot\\docker-compose\.yml" --env-file "\$PSScriptRoot\\\.env" up -d\s*$/m,
      'must start the stack from the compose file beside it'
    );
    assert.ok(startupContent.includes('$failedContainers'), 'must report a container that failed to start');
  });

  await t.test('startup_homelab.ps1 has nothing left of the media stack', () => {
    for (const gone of ['gluetun', 'qbittorrent', 'Jellyfin', 'Sonarr', 'WIREGUARD', '.last_update', '--profile', '$NoAI', '$ArrOnly', '$SkipUpdate', '$ForceUpdate', '--build', 'household-hub']) {
      assert.ok(!startupContent.toLowerCase().includes(gone.toLowerCase()), `startup_homelab.ps1 must not mention ${gone}`);
    }
    assert.doesNotMatch(startupContent, /\[switch\]/, 'no switches are left: there is one way to start');
    assert.ok(!startupContent.includes('homelab.llama-porbeagle.ts.net'), 'no deprecated Tailscale URL');
  });

  await t.test('startup_homelab.ps1 provisions the Ollama models listed in the manifest', () => {
    // config/ stays in the repo root, two folders up from the script.
    assert.ok(
      startupContent.includes('$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path'),
      'must find the repo root from its own folder'
    );
    assert.ok(
      startupContent.includes('Join-Path $repoRoot "config\\ollama-models\\models.json"'),
      'must read the tracked model manifest config/ollama-models/models.json'
    );
    assert.ok(
      startupContent.includes('Get-FileHash') && startupContent.includes('SHA256'),
      'startup_homelab.ps1 must verify each downloaded GGUF against its SHA256'
    );
    assert.ok(
      /ollama pull \$\(\$model\.ollama_pull\)/.test(startupContent),
      'startup_homelab.ps1 must pull manifest entries that name an ollama_pull model'
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

  await t.test('stop_homelab.ps1 stops the stack and nothing else', () => {
    assert.match(
      stopContent,
      /docker compose -f "\$PSScriptRoot\\docker-compose\.yml" --env-file "\$PSScriptRoot\\\.env" down\s*$/m,
      'must stop the stack from the compose file beside it'
    );
    // The list of Windows processes dates from before Docker; none of those apps is installed here now.
    assert.doesNotMatch(stopContent, /Stop-Process|Get-Process/, 'must not kill host processes');
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


