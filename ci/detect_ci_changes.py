#!/usr/bin/env python3
"""Classify changed repository paths for the monorepo PR workflow."""

from __future__ import annotations

import argparse
import subprocess
from collections.abc import Iterable


ALL_AREA_PATHS = {
    ".github/workflows/master-ci-cd.yml",
    "ci/detect_ci_changes.py",
    "ci/test_ci_change_detection.py",
    "ci/test_master_ci_cd_workflow.py",
}

BACKEND_PREFIXES = (
    "src/",
    "gradle/",
    "deploy/",
    "monitoring/",
    "ci/",
)

BACKEND_FILES = {
    ".dockerignore",
    ".env.docker.example",
    "AGENTS.md",
    "Dockerfile",
    "build.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "settings.gradle.kts",
    ".github/workflows/master-publish.yml",
}


def classify_paths(paths: Iterable[str]) -> dict[str, bool]:
    """Return which application areas need validation for the changed paths."""

    normalized_paths = {
        path.strip().removeprefix("./") for path in paths if path.strip()
    }
    if normalized_paths & ALL_AREA_PATHS:
        return {"backend": True, "mobile": True}

    return {
        "backend": any(
            path in BACKEND_FILES or path.startswith(BACKEND_PREFIXES)
            for path in normalized_paths
        ),
        "mobile": any(path.startswith("mobile/") for path in normalized_paths),
    }


def changed_paths(base: str, head: str) -> list[str]:
    """Read added, copied, modified, renamed and type-changed paths from Git."""

    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACDMRT", base, head, "--"],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    args = parser.parse_args()

    classifications = classify_paths(changed_paths(args.base, args.head))
    for area in ("backend", "mobile"):
        print(f"{area}={str(classifications[area]).lower()}")


if __name__ == "__main__":
    main()
