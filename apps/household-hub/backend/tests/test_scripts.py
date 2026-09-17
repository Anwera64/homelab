"""
Tests for backend maintenance and development scripts.
"""
from pathlib import Path
import re


def test_wipe_db_scripts_exist():
    backend_root = Path(__file__).resolve().parent.parent
    scripts_dir = backend_root / "scripts"

    wipe_script = scripts_dir / "wipe-db.ps1"
    root_wrapper = backend_root / "wipe-db.ps1"

    assert wipe_script.exists(), f"Expected {wipe_script} to exist"
    assert root_wrapper.exists(), f"Expected {root_wrapper} to exist"


def test_dev_script_includes_wipe_command():
    backend_root = Path(__file__).resolve().parent.parent
    dev_script = backend_root / "scripts" / "dev.ps1"

    content = dev_script.read_text(encoding="utf-8")
    assert re.search(r'ValidateSet\([^)]*"wipe"', content), "dev.ps1 should validate 'wipe' command"


def test_wipe_db_script_targets_sqlite_database_files():
    backend_root = Path(__file__).resolve().parent.parent
    wipe_script = backend_root / "scripts" / "wipe-db.ps1"

    if not wipe_script.exists():
        return

    content = wipe_script.read_text(encoding="utf-8")
    assert "household_hub.db" in content
    assert "household_hub.db-wal" in content
    assert "household_hub.db-shm" in content


def test_powershell_scripts_have_valid_syntax():
    import subprocess
    import shutil

    pwsh = shutil.which("powershell") or shutil.which("pwsh")
    if not pwsh:
        return

    backend_root = Path(__file__).resolve().parent.parent
    scripts = [
        backend_root / "wipe-db.ps1",
        backend_root / "scripts" / "wipe-db.ps1",
        backend_root / "scripts" / "dev.ps1",
    ]

    for script_path in scripts:
        cmd = [
            pwsh,
            "-NoProfile",
            "-Command",
            f"$errs = @(); [System.Management.Automation.Language.Parser]::ParseFile('{script_path}', [ref]$null, [ref]$errs); if ($errs.Count -gt 0) {{ exit 1 }}",
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        assert result.returncode == 0, f"Syntax errors in {script_path.name}: {result.stderr or result.stdout}"

