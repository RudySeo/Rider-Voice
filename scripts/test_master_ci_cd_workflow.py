"""Contract tests for the split backend PR CI and master publish workflows."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PR_WORKFLOW = ROOT / ".github" / "workflows" / "master-ci-cd.yml"
PUBLISH_WORKFLOW = ROOT / ".github" / "workflows" / "master-publish.yml"
DOCKERFILE = ROOT / "Dockerfile"
DOCKERIGNORE = ROOT / ".dockerignore"
DOCKER_ENV_EXAMPLE = ROOT / ".env.docker.example"


class MasterCiCdWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not PR_WORKFLOW.is_file():
            raise AssertionError(f"missing workflow: {PR_WORKFLOW}")
        if not PUBLISH_WORKFLOW.is_file():
            raise AssertionError(f"missing workflow: {PUBLISH_WORKFLOW}")
        cls.pr_content = PR_WORKFLOW.read_text(encoding="utf-8")
        cls.publish_content = PUBLISH_WORKFLOW.read_text(encoding="utf-8")

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
        self.assertIn("scripts/test_aws_deployment_contract.py", self.pr_content)
        self.assertIn("scripts/test_monitoring_contract.py", self.pr_content)
        self.assertIn("./gradlew migrationTest", self.pr_content)
        self.assertIn("./gradlew integrationTest", self.pr_content)
        self.assertNotIn("DOCKERHUB_TOKEN", self.pr_content)
        self.assertNotIn("setup-node", self.pr_content)
        self.assertNotIn("npm ", self.pr_content)
        self.assertNotRegex(self.pr_content, r"(?m)^\s{2}frontend:")

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
        self.assertIn("/api/health", self.pr_content)

    def test_publish_runs_only_for_master_push_without_full_validation(self) -> None:
        self.assertRegex(
            self.publish_content,
            r"push:\s*\n\s*branches:\s*\n\s*- master",
        )
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
        self.assertIn("frontend/", dockerignore)
        self.assertIn(".env", dockerignore)
        self.assertIn("DB_URL=", env_example)
        self.assertIn("KAKAO_CLIENT_ID=", env_example)
        self.assertNotIn("AUTH_JWT_SECRET", env_example)


if __name__ == "__main__":
    unittest.main()
