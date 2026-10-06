"""
Every dependency is pinned to an exact version, so an image rebuild installs what was tested.
Renovate bumps the pins in a PR; the tools that only the tests need stay out of the image.
"""
import re
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parent.parent
PINNED = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*(\[[a-z0-9,_-]+\])?==[0-9][A-Za-z0-9.+-]*$")
TEST_TOOLS = {"pytest", "pytest-asyncio", "pytest-cov"}


def _requirements(name: str) -> list[str]:
    path = BACKEND_ROOT / name
    assert path.exists(), f"{name} must exist"
    lines = [line.split("#", 1)[0].strip() for line in path.read_text(encoding="utf-8").splitlines()]
    return [line for line in lines if line]


def _package(line: str) -> str:
    return re.split(r"[\[<>=!~;\s]", line, maxsplit=1)[0].lower()


def test_every_runtime_dependency_is_pinned():
    for line in _requirements("requirements.txt"):
        assert PINNED.match(line), f"{line!r} must be pinned with =="


def test_the_image_leaves_out_the_test_tools():
    packages = {_package(line) for line in _requirements("requirements.txt")}
    assert not packages & TEST_TOOLS


def test_the_dev_requirements_add_the_pinned_test_tools_to_the_runtime_ones():
    lines = _requirements("requirements-dev.txt")
    assert lines[0] == "-r requirements.txt"
    for line in lines[1:]:
        assert PINNED.match(line), f"{line!r} must be pinned with =="
    assert {_package(line) for line in lines[1:]} == TEST_TOOLS


def test_the_python_base_image_is_pinned_to_a_patch_release():
    first = (BACKEND_ROOT / "Dockerfile").read_text(encoding="utf-8").splitlines()[0]
    assert re.fullmatch(r"FROM python:3\.12\.\d+-slim", first), first
