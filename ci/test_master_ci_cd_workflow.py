"""CI contract tests for path-aware PR checks and backend-only publishing."""

from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PR_WORKFLOW = ROOT / ".github" / "workflows" / "master-ci-cd.yml"
PUBLISH_WORKFLOW = ROOT / ".github" / "workflows" / "master-publish.yml"
DOCKERFILE = ROOT / "Dockerfile"
DOCKERIGNORE = ROOT / ".dockerignore"
DOCKER_ENV_EXAMPLE = ROOT / ".env.docker.example"
MOBILE_PACKAGE = ROOT / "mobile" / "package.json"
MOBILE_APP_CONFIG = ROOT / "mobile" / "app.json"


class MasterCiCdWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not PR_WORKFLOW.is_file():
            raise AssertionError(f"missing workflow: {PR_WORKFLOW}")
        if not PUBLISH_WORKFLOW.is_file():
            raise AssertionError(f"missing workflow: {PUBLISH_WORKFLOW}")
        cls.pr_content = PR_WORKFLOW.read_text(encoding="utf-8")
        cls.publish_content = PUBLISH_WORKFLOW.read_text(encoding="utf-8")
        cls.mobile_package = json.loads(MOBILE_PACKAGE.read_text(encoding="utf-8"))
        cls.mobile_app_config = json.loads(MOBILE_APP_CONFIG.read_text(encoding="utf-8"))

    def test_pr_ci_runs_only_for_master_pull_requests(self) -> None:
        self.assertRegex(
            self.pr_content,
            r"pull_request:\s*\n\s*branches:\s*\n\s*- master",
        )
        self.assertNotRegex(self.pr_content, r"(?m)^  push:")
        self.assertIn("contents: read", self.pr_content)
        self.assertNotIn("pull_request_target", self.pr_content)

    def test_pr_ci_contains_full_backend_validation_without_publish(self) -> None:
        self.assertIn("backend-test:", self.pr_content)
        self.assertIn("integration-and-container-smoke:", self.pr_content)
        self.assertNotIn("publish-image:", self.pr_content)
        self.assertIn("./gradlew build", self.pr_content)
        self.assertIn("ci.test_aws_deployment_contract", self.pr_content)
        self.assertIn("ci.test_monitoring_contract", self.pr_content)
        self.assertIn("./gradlew migrationTest", self.pr_content)
        self.assertIn("./gradlew integrationTest", self.pr_content)
        self.assertNotIn("DOCKERHUB_TOKEN", self.pr_content)

    def test_pr_ci_detects_changes_and_scopes_backend_jobs(self) -> None:
        self.assertIn("changes:", self.pr_content)
        self.assertIn("python3 ci/detect_ci_changes.py", self.pr_content)
        self.assertIn("backend: ${{ steps.filter.outputs.backend }}", self.pr_content)
        self.assertIn("mobile: ${{ steps.filter.outputs.mobile }}", self.pr_content)
        self.assertNotIn("steps.filter.outputs.frontend", self.pr_content)
        self.assertGreaterEqual(self.pr_content.count("needs: changes"), 3)
        self.assertGreaterEqual(
            self.pr_content.count("needs.changes.outputs.backend == 'true'"),
            2,
        )

    def test_pr_ci_has_no_frontend_job(self) -> None:
        self.assertNotIn("frontend-test:", self.pr_content)
        self.assertNotIn("working-directory: frontend", self.pr_content)
        self.assertNotIn("npm ci", self.pr_content)

    def test_mobile_job_uses_locked_expo_validation(self) -> None:
        self.assertIn("mobile-test:", self.pr_content)
        self.assertIn("needs.changes.outputs.mobile == 'true'", self.pr_content)
        self.assertIn("corepack enable", self.pr_content)
        self.assertIn("pnpm install --frozen-lockfile", self.pr_content)
        self.assertIn("pnpm run typecheck", self.pr_content)
        self.assertIn("pnpm run lint", self.pr_content)
        self.assertIn("pnpm run test", self.pr_content)
        self.assertIn("pnpm exec expo install --check", self.pr_content)
        self.assertNotIn("pnpm exec expo export --platform all", self.pr_content)
        self.assertIn("pnpm exec expo export --platform ios", self.pr_content)
        self.assertIn("$RUNNER_TEMP/mobile-ios-export", self.pr_content)
        self.assertIn("pnpm exec expo export --platform android", self.pr_content)
        self.assertIn("$RUNNER_TEMP/mobile-android-export", self.pr_content)

    def test_mobile_app_has_no_expo_web_target(self) -> None:
        self.assertNotIn("web", self.mobile_package["scripts"])
        self.assertNotIn("react-dom", self.mobile_package["dependencies"])
        self.assertNotIn("react-native-web", self.mobile_package["dependencies"])
        self.assertNotIn("web", self.mobile_app_config["expo"])
        self.assertIn("expo-web-browser", self.mobile_package["dependencies"])

    def test_pr_ci_exposes_one_stable_final_gate(self) -> None:
        self.assertIn("ci-gate:", self.pr_content)
        self.assertIn("name: PR CI gate", self.pr_content)
        self.assertIn("if: always()", self.pr_content)
        self.assertIn("backend-test", self.pr_content)
        self.assertIn("integration-and-container-smoke", self.pr_content)
        self.assertNotIn("frontend-test", self.pr_content)
        self.assertIn("mobile-test", self.pr_content)
        self.assertIn("success|skipped", self.pr_content)

    def test_schema_migration_runs_before_integration_and_uses_separate_credentials(self) -> None:
        migration_position = self.pr_content.index("./gradlew migrationTest")
        integration_position = self.pr_content.index("./gradlew integrationTest")

        self.assertLess(migration_position, integration_position)
        self.assertIn("DB_MIGRATION_USERNAME", self.pr_content)
        self.assertIn("DB_MIGRATION_PASSWORD", self.pr_content)
        self.assertRegex(self.pr_content, r"GRANT\s+SELECT,\s*INSERT,\s*UPDATE,\s*DELETE")
        self.assertIn("Verify runtime user cannot change the schema", self.pr_content)
        self.assertIn("Runtime database user unexpectedly has DDL permission", self.pr_content)
        self.assertIn("--env DB_MIGRATION_USERNAME", self.pr_content)
        self.assertIn("--env DB_MIGRATION_PASSWORD", self.pr_content)

    def test_integration_job_uses_pinned_mysql_and_smoke_tests_the_image(self) -> None:
        self.assertRegex(
            self.pr_content,
            r"image:\s*mysql:8\.4\.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6",
        )
        self.assertIn("mysqladmin ping", self.pr_content)
        self.assertIn("MYSQL_DATABASE", self.pr_content)
        self.assertIn("DB_URL", self.pr_content)
        self.assertIn("KAKAO_CLIENT_ID: ci-test-client", self.pr_content)
        self.assertIn("load: true", self.pr_content)
        self.assertIn("/actuator/health", self.pr_content)
        self.assertIn("docker run", self.pr_content)
        self.assertIn("Verify monitoring stack", self.pr_content)
        self.assertIn("monitoring/compose.prod.yml", self.pr_content)
        self.assertIn("docker compose", self.pr_content)
        self.assertNotIn("--name rider-voice-prometheus-smoke", self.pr_content)
        self.assertNotIn("--name rider-voice-grafana-smoke", self.pr_content)
        self.assertIn("up{job=", self.pr_content)
        self.assertIn("GRAFANA_ROOT_URL: http://127.0.0.1:3000/grafana/", self.pr_content)
        self.assertIn("/grafana/api/health", self.pr_content)

    def test_publish_runs_for_master_push_or_manual_recovery_without_full_validation(self) -> None:
        self.assertRegex(
            self.publish_content,
            r"push:\s*\n\s*branches:\s*\n\s*- master",
        )
        self.assertRegex(self.publish_content, r"(?m)^  workflow_dispatch:\s*$")
        self.assertIn("if: github.ref == 'refs/heads/master'", self.publish_content)
        self.assertNotRegex(self.publish_content, r"(?m)^  pull_request:")
        self.assertIn("publish-image:", self.publish_content)
        self.assertIn("environment: docker-hub", self.publish_content)
        self.assertIn("vars.DOCKERHUB_USERNAME", self.publish_content)
        self.assertIn("secrets.DOCKERHUB_TOKEN", self.publish_content)
        self.assertNotIn("./gradlew", self.publish_content)
        self.assertNotIn("mysql:", self.publish_content)
        self.assertNotIn("integrationTest", self.publish_content)
        self.assertNotIn("migrationTest", self.publish_content)
        self.assertNotIn("docker run", self.publish_content)
        self.assertNotIn("/actuator/health", self.publish_content)

    def test_publish_trigger_is_limited_to_backend_impacting_paths(self) -> None:
        self.assertRegex(
            self.publish_content,
            r"branches:\s*\n\s*- master\s*\n\s*paths:",
        )
        for backend_path in (
            "'src/**'",
            "'gradle/**'",
            "'build.gradle.kts'",
            "'settings.gradle.kts'",
            "'gradlew'",
            "'Dockerfile'",
            "'.dockerignore'",
            "'deploy/**'",
            "'monitoring/**'",
            "'.github/workflows/master-publish.yml'",
        ):
            with self.subTest(path=backend_path):
                self.assertIn(f"- {backend_path}", self.publish_content)
        self.assertNotRegex(self.publish_content, r"(?m)^\s*- ['\"]frontend/")
        self.assertNotRegex(self.publish_content, r"(?m)^\s*- ['\"]mobile/")
        self.assertNotRegex(self.publish_content, r"(?m)^\s*- ['\"]docs/")

    def test_publish_uses_traceable_tags_cache_and_attestations(self) -> None:
        self.assertIn("type=raw,value=latest", self.publish_content)
        self.assertIn("type=sha,prefix=sha-,format=short", self.publish_content)
        self.assertIn("DOCKER_METADATA_SHORT_SHA_LENGTH: '12'", self.publish_content)
        self.assertIn("platforms: linux/amd64", self.publish_content)
        self.assertIn("cache-from: type=gha", self.publish_content)
        self.assertIn("cache-to: type=gha,mode=max", self.publish_content)
        self.assertIn("provenance: mode=max", self.publish_content)
        self.assertIn("sbom: true", self.publish_content)
        self.assertIn("push-to-registry: true", self.publish_content)

    def test_reusable_actions_are_pinned_to_full_commit_shas(self) -> None:
        action_references = re.findall(
            r"uses:\s*([^\s#]+)",
            self.pr_content + "\n" + self.publish_content,
        )
        self.assertGreaterEqual(len(action_references), 6)
        for action_reference in action_references:
            with self.subTest(action=action_reference):
                self.assertRegex(action_reference, r"^[^@]+@[0-9a-f]{40}$")

    def test_docker_assets_exclude_frontend_and_secrets(self) -> None:
        self.assertTrue(DOCKERFILE.is_file(), "Dockerfile must exist")
        self.assertTrue(DOCKERIGNORE.is_file(), ".dockerignore must exist")
        self.assertTrue(DOCKER_ENV_EXAMPLE.is_file(), ".env.docker.example must exist")

        dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        dockerignore = DOCKERIGNORE.read_text(encoding="utf-8")
        env_example = DOCKER_ENV_EXAMPLE.read_text(encoding="utf-8")

        self.assertIn("eclipse-temurin:25", dockerfile)
        self.assertIn("USER", dockerfile)
        self.assertIn("EXPOSE 8080", dockerfile)
        self.assertNotIn("frontend", dockerfile)
        self.assertNotIn("KAKAO_CLIENT_ID", dockerfile)
        self.assertNotIn("DB_PASSWORD", dockerfile)
        self.assertNotIn("frontend/", dockerignore)
        self.assertIn("ci/", dockerignore)
        self.assertIn(".env", dockerignore)
        self.assertIn("DB_URL=", env_example)
        self.assertIn("KAKAO_CLIENT_ID=", env_example)
        self.assertNotIn("AUTH_JWT_SECRET", env_example)


if __name__ == "__main__":
    unittest.main()
