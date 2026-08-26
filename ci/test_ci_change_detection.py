"""Unit tests for PR CI path classification."""

import unittest
from unittest.mock import patch

from ci.detect_ci_changes import changed_paths, classify_paths


class CiChangeDetectionTest(unittest.TestCase):
    def test_removed_frontend_path_does_not_enable_application_jobs(self) -> None:
        self.assertEqual(
            classify_paths(["frontend/src/App.tsx"]),
            {"backend": False, "mobile": False},
        )

    def test_mobile_change_only_enables_mobile(self) -> None:
        self.assertEqual(
            classify_paths(["mobile/src/features/reviews/screen.tsx"]),
            {"backend": False, "mobile": True},
        )

    def test_backend_change_only_enables_backend(self) -> None:
        for path in (
            "src/main/kotlin/com/ridervoice/api/Application.kt",
            "gradle/wrapper/gradle-wrapper.properties",
            "build.gradle.kts",
            "Dockerfile",
            "deploy/github/send-ssm-deploy.sh",
            "monitoring/prometheus.yml",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    classify_paths([path]),
                    {"backend": True, "mobile": False},
                )

    def test_docs_only_change_skips_application_jobs(self) -> None:
        self.assertEqual(
            classify_paths(["docs/ADR.md", "README.md"]),
            {"backend": False, "mobile": False},
        )

    def test_ci_or_classifier_change_runs_every_application_job(self) -> None:
        for path in (
            ".github/workflows/master-ci-cd.yml",
            "ci/detect_ci_changes.py",
            "ci/test_ci_change_detection.py",
            "ci/test_master_ci_cd_workflow.py",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    classify_paths([path]),
                    {"backend": True, "mobile": True},
                )

    def test_harness_change_skips_application_jobs(self) -> None:
        for path in ("scripts/execute.py", "scripts/test_execute.py"):
            with self.subTest(path=path):
                self.assertEqual(
                    classify_paths([path]),
                    {"backend": False, "mobile": False},
                )

    def test_backend_ci_contract_change_only_enables_backend(self) -> None:
        self.assertEqual(
            classify_paths(["ci/test_aws_deployment_contract.py"]),
            {"backend": True, "mobile": False},
        )

    def test_backend_and_mobile_can_be_enabled_together(self) -> None:
        self.assertEqual(
            classify_paths(["build.gradle.kts", "mobile/package.json"]),
            {"backend": True, "mobile": True},
        )

    @patch("ci.detect_ci_changes.subprocess.run")
    def test_git_diff_includes_deleted_files(self, run) -> None:
        run.return_value.stdout = "mobile/src/removed.ts\n"

        self.assertEqual(changed_paths("base-sha", "head-sha"), ["mobile/src/removed.ts"])
        command = run.call_args.args[0]
        self.assertIn("--diff-filter=ACDMRT", command)


if __name__ == "__main__":
    unittest.main()
