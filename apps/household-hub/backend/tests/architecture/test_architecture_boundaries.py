import ast
import os
from pathlib import Path
import pytest

APP_DIR = Path(__file__).resolve().parent.parent.parent / "app"


def get_imports_from_file(file_path: Path):
    """Parses a Python file with ast and yields (line_number, imported_module_name)."""
    try:
        content = file_path.read_text(encoding="utf-8")
        tree = ast.parse(content, filename=str(file_path))
    except Exception as e:
        pytest.fail(f"Could not parse {file_path}: {e}")

    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                yield node.lineno, alias.name
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                yield node.lineno, node.module


def get_all_python_files(directory: Path):
    """Yields all .py files in directory, ignoring __pycache__."""
    if not directory.exists():
        return
    for root, _, files in os.walk(directory):
        for f in files:
            if f.endswith(".py") and not f.startswith("."):
                yield Path(root) / f


def test_domain_has_zero_external_dependencies():
    """
    Domain is the Independent Core.
    It must have ZERO imports from:
    - app.data
    - app.presentation
    - app.bootstrap
    - fastapi
    - sqlalchemy
    - pydantic
    """
    domain_dir = APP_DIR / "domain"
    if not domain_dir.exists():
        pytest.skip("Domain directory not created yet")

    forbidden_prefixes = (
        "app.data",
        "app.presentation",
        "app.bootstrap",
        "app.models",
        "app.services",
        "app.api",
        "fastapi",
        "sqlalchemy",
        "pydantic",
        "aiosqlite",
    )

    violations = []
    for file_path in get_all_python_files(domain_dir):
        rel_path = file_path.relative_to(APP_DIR)
        for lineno, mod_name in get_imports_from_file(file_path):
            for forbidden in forbidden_prefixes:
                if mod_name == forbidden or mod_name.startswith(forbidden + "."):
                    violations.append(
                        f"[{rel_path}:{lineno}] Domain file imports forbidden module '{mod_name}'"
                    )

    assert not violations, "Clean Architecture Domain Violation:\n" + "\n".join(violations)


def test_presentation_has_zero_data_dependencies():
    """
    Presentation depends ONLY on Domain.
    It must have ZERO imports from:
    - app.data
    - app.bootstrap
    - app.models
    - app.services
    - app.api
    - sqlalchemy
    - aiosqlite
    """
    presentation_dir = APP_DIR / "presentation"
    if not presentation_dir.exists():
        pytest.skip("Presentation directory not created yet")

    forbidden_prefixes = (
        "app.data",
        "app.bootstrap",
        "app.models",
        "app.services",
        "app.api",
        "sqlalchemy",
        "aiosqlite",
    )

    violations = []
    for file_path in get_all_python_files(presentation_dir):
        rel_path = file_path.relative_to(APP_DIR)
        for lineno, mod_name in get_imports_from_file(file_path):
            for forbidden in forbidden_prefixes:
                if mod_name == forbidden or mod_name.startswith(forbidden + "."):
                    violations.append(
                        f"[{rel_path}:{lineno}] Presentation file imports forbidden module '{mod_name}'"
                    )

    assert not violations, "Clean Architecture Presentation Violation:\n" + "\n".join(violations)


def test_data_has_zero_presentation_dependencies():
    """
    Data depends ONLY on Domain.
    It must have ZERO imports from:
    - app.presentation
    - app.bootstrap
    - app.services
    - app.api
    - fastapi
    """
    data_dir = APP_DIR / "data"
    if not data_dir.exists():
        pytest.skip("Data directory not created yet")

    forbidden_prefixes = (
        "app.presentation",
        "app.bootstrap",
        "app.services",
        "app.api",
        "fastapi",
    )

    violations = []
    for file_path in get_all_python_files(data_dir):
        rel_path = file_path.relative_to(APP_DIR)
        for lineno, mod_name in get_imports_from_file(file_path):
            for forbidden in forbidden_prefixes:
                if mod_name == forbidden or mod_name.startswith(forbidden + "."):
                    violations.append(
                        f"[{rel_path}:{lineno}] Data file imports forbidden module '{mod_name}'"
                    )

    assert not violations, "Clean Architecture Data Violation:\n" + "\n".join(violations)


def test_di_providers_are_async_coroutines_to_prevent_threadpool_offloading():
    """
    DI providers in app.dependency_overrides must be async coroutines to avoid
    FastAPI offloading request-scoped database sessions to anyio worker threads.
    """
    import inspect
    from app.main import app
    from app.bootstrap.di import setup_dependency_injection

    setup_dependency_injection(app)
    violations = []
    for stub, provider in app.dependency_overrides.items():
        if not inspect.iscoroutinefunction(provider):
            stub_name = getattr(stub, "__name__", str(stub))
            provider_name = getattr(provider, "__name__", str(provider))
            violations.append(f"Provider '{provider_name}' for stub '{stub_name}' is synchronous, causing threadpool offloading.")

    assert not violations, "Synchronous DI Provider Violation:\n" + "\n".join(violations)
