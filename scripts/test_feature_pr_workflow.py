"""GitHub feature branch PR/CI workflow contract tests."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "feature-pr.yml"


class FeaturePrWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not WORKFLOW.is_file():
            raise AssertionError(f"missing workflow: {WORKFLOW}")
        cls.content = WORKFLOW.read_text(encoding="utf-8")

    def test_feature_branch_push_trigger_and_minimum_permissions(self) -> None:
        self.assertRegex(
            self.content,
            r"branches:\s*\n\s*- ['\"]feature/\*\*['\"]",
        )
        self.assertRegex(
            self.content,
            r"branches:[\s\S]*- ['\"]feat/\*\*['\"]",
        )
        self.assertIn("contents: read", self.content)
        self.assertIn("pull-requests: write", self.content)

    def test_draft_pr_creation_is_develop_targeted_and_idempotent(self) -> None:
        self.assertIn("develop", self.content)
        self.assertIn("draft: true", self.content)
        self.assertIn("pulls.list", self.content)
        self.assertIn("pulls.create", self.content)
        self.assertIn("compareCommits", self.content)

    def test_backend_and_frontend_ci_commands_are_present(self) -> None:
        self.assertIn("./gradlew build", self.content)
        self.assertIn("npm ci", self.content)
        self.assertIn("npm run lint", self.content)
        self.assertIn("npm test", self.content)
        self.assertIn("npm run build", self.content)
        self.assertNotIn("integrationTest", self.content)

    def test_native_codex_review_does_not_embed_an_api_key(self) -> None:
        self.assertNotIn("OPENAI_API_KEY", self.content)
        self.assertNotIn("openai/codex-action", self.content)


if __name__ == "__main__":
    unittest.main()
