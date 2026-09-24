"""
The image must hold everything startup reads: `init_db` migrates with alembic.ini and alembic/.
"""
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parent.parent


def _dockerfile_lines() -> list[str]:
    return [line.strip() for line in (BACKEND_ROOT / "Dockerfile").read_text(encoding="utf-8").splitlines()]


def _dockerignore_patterns() -> list[str]:
    path = BACKEND_ROOT / ".dockerignore"
    assert path.exists(), "the build context must exclude the venv and database via .dockerignore"
    return [line.strip() for line in path.read_text(encoding="utf-8").splitlines()]


def test_image_copies_the_alembic_config():
    assert "COPY alembic.ini ." in _dockerfile_lines()


def test_image_copies_the_alembic_migrations():
    assert "COPY alembic/ alembic/" in _dockerfile_lines()


def test_build_context_excludes_the_venv():
    assert ".venv/" in _dockerignore_patterns()


def test_build_context_excludes_the_database():
    assert "*.db*" in _dockerignore_patterns()
