---
name: review
description: Use when working in /Users/jaeha/Projects/codex-live-demo and the user asks to review local changes, validate a diff, check project guardrails, or references the former /review command. The review checks AGENTS.md, docs/ARCHITECTURE.md, docs/ADR.md, tests, CRITICAL rules, and buildability.
---

# Project Review

Use this skill to review changes in this project against its local contracts.

## Inputs To Read

Before reviewing changes, read:

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/ADR.md`

Then inspect the changed files with Git.

## Checklist

Check these items:

1. **Architecture compliance**: Do changed files follow the structure defined in `ARCHITECTURE.md`?
2. **Stack compliance**: Do changes stay within the technical decisions in `ADR.md`?
3. **Tests**: Are tests present for new behavior?
4. **CRITICAL rules**: Do changes avoid violating CRITICAL rules in `AGENTS.md`?
5. **Buildability**: Does the build command pass without errors?

Run validation commands when appropriate for the requested review scope. If a command cannot be run, state that clearly in the review.

## Output

For code review requests, lead with concrete findings ordered by severity and include file/line references. If there are no findings, say so explicitly.

Then include this checklist table:

| Item | Result | Notes |
| --- | --- | --- |
| Architecture compliance | pass/fail | {detail} |
| Stack compliance | pass/fail | {detail} |
| Tests | pass/fail | {detail} |
| CRITICAL rules | pass/fail | {detail} |
| Buildability | pass/fail | {detail} |

If there are violations, propose concrete fixes.
