#!/usr/bin/env python3
"""Run project checks before Codex executes a git commit command."""

from __future__ import annotations

import json
import os
import shlex
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any


CONTROL_TOKENS = {";", "&&", "||", "|", "&", "(", ")"}
GIT_OPTIONS_WITH_VALUE = {
    "-C",
    "-c",
    "--exec-path",
    "--git-dir",
    "--namespace",
    "--super-prefix",
    "--work-tree",
}


def read_payload() -> dict[str, Any]:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError):
        return {}
    return payload if isinstance(payload, dict) else {}


def shell_segments(command: str) -> list[list[str]]:
    """Split a shell command enough to identify direct git invocations."""
    try:
        lexer = shlex.shlex(
            command.replace("\n", ";"),
            posix=True,
            punctuation_chars=";&|()",
        )
        lexer.whitespace_split = True
        lexer.commenters = "#"
        tokens = list(lexer)
    except ValueError:
        return []

    segments: list[list[str]] = []
    current: list[str] = []
    for token in tokens:
        if token in CONTROL_TOKENS:
            if current:
                segments.append(current)
                current = []
            continue
        current.append(token)
    if current:
        segments.append(current)
    return segments


def skip_environment_prefix(tokens: list[str]) -> int:
    index = 0
    if index < len(tokens) and os.path.basename(tokens[index]) == "command":
        index += 1
    if index < len(tokens) and os.path.basename(tokens[index]) == "env":
        index += 1
        while index < len(tokens):
            token = tokens[index]
            if "=" in token and not token.startswith("="):
                index += 1
                continue
            if token.startswith("-"):
                index += 1
                continue
            break
    while index < len(tokens):
        token = tokens[index]
        if "=" not in token or token.startswith("="):
            break
        index += 1
    return index


def git_subcommand(tokens: list[str], index: int) -> str | None:
    index += 1
    while index < len(tokens):
        token = tokens[index]
        if token in GIT_OPTIONS_WITH_VALUE:
            index += 2
            continue
        if any(token.startswith(f"{option}=") for option in GIT_OPTIONS_WITH_VALUE):
            index += 1
            continue
        if token.startswith("-"):
            index += 1
            continue
        return token
    return None


def is_git_commit(command: str) -> bool:
    for tokens in shell_segments(command):
        index = skip_environment_prefix(tokens)
        if index >= len(tokens) or os.path.basename(tokens[index]) != "git":
            continue
        if git_subcommand(tokens, index) == "commit":
            return True
    return False


def deny(reason: str) -> None:
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                }
            },
            ensure_ascii=False,
        )
    )


def repository_root() -> Path | None:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        return None
    return Path(result.stdout.strip())


def package_scripts(package_json: Path) -> set[str] | None:
    try:
        payload = json.loads(package_json.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(payload, dict):
        return None
    scripts = payload.get("scripts")
    return set(scripts) if isinstance(scripts, dict) else set()


def main() -> int:
    payload = read_payload()
    tool_input = payload.get("tool_input")
    if not isinstance(tool_input, dict):
        return 0

    command = tool_input.get("command", tool_input.get("cmd", ""))
    if not isinstance(command, str) or not is_git_commit(command):
        return 0

    root = repository_root()
    if root is None:
        deny("PRE-COMMIT CHECK: Git 저장소 루트를 찾을 수 없어 commit을 중단했습니다.")
        return 0

    package_json = root / "package.json"
    if not package_json.is_file():
        deny("PRE-COMMIT CHECK: package.json이 없어 lint, build, test를 실행할 수 없습니다.")
        return 0

    required_checks = ("lint", "build", "test")
    scripts = package_scripts(package_json)
    if scripts is None:
        deny("PRE-COMMIT CHECK: package.json을 읽거나 파싱할 수 없습니다.")
        return 0

    missing = [name for name in required_checks if name not in scripts]
    if missing:
        deny(
            "PRE-COMMIT CHECK: package.json에 필요한 npm script가 없습니다: "
            + ", ".join(missing)
        )
        return 0

    if shutil.which("npm") is None:
        deny("PRE-COMMIT CHECK: npm 실행 파일을 찾을 수 없습니다.")
        return 0

    for check in required_checks:
        print(f"pre-commit: running npm run {check}", file=sys.stderr)
        result = subprocess.run(
            ["npm", "run", check],
            cwd=root,
            stdout=sys.stderr,
            stderr=sys.stderr,
            check=False,
        )
        if result.returncode != 0:
            deny(f"PRE-COMMIT CHECK: npm run {check} 실패로 commit을 중단했습니다.")
            return 0

    print("pre-commit: lint, build, and test passed", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
