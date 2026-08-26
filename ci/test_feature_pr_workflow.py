"""CI contract tests for the feature branch Draft PR workflow."""

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
        self.assertIn("github-token:", self.content)
        self.assertIn("secrets.PR_AUTOMATION_TOKEN", self.content)
        self.assertIn("github.token", self.content)

    def test_draft_pr_creation_is_master_targeted_and_idempotent(self) -> None:
        self.assertRegex(self.content, r"baseBranch\s*=\s*['\"]master['\"]")
        self.assertNotIn("develop", self.content)
        self.assertIn("draft: true", self.content)
        self.assertIn("pulls.list", self.content)
        self.assertIn("pulls.create", self.content)
        self.assertIn("compareCommits", self.content)

    def test_feature_push_does_not_duplicate_backend_or_frontend_ci(self) -> None:
        self.assertNotIn("./gradlew", self.content)
        self.assertNotIn("actions/setup-java", self.content)
        self.assertNotIn("gradle/actions/setup-gradle", self.content)
        self.assertNotRegex(self.content, r"(?m)^  backend:")
        self.assertNotIn("integrationTest", self.content)
        self.assertNotIn("setup-node", self.content)
        self.assertNotIn("npm ", self.content)
        self.assertNotRegex(self.content, r"(?m)^\s{2}frontend:")

    def test_generated_pr_body_is_written_in_korean(self) -> None:
        self.assertIn("## 변경 요약", self.content)
        self.assertIn("## 확인 사항", self.content)
        self.assertIn("PR 전체 CI가 통과했습니다", self.content)

    def test_native_codex_review_does_not_embed_an_api_key(self) -> None:
        self.assertNotIn("OPENAI_API_KEY", self.content)
        self.assertNotIn("openai/codex-action", self.content)


if __name__ == "__main__":
    unittest.main()
