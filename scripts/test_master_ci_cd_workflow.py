"""Contract tests for the backend-only master Docker CI/CD workflow."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "master-ci-cd.yml"
DOCKERFILE = ROOT / "Dockerfile"
DOCKERIGNORE = ROOT / ".dockerignore"
DOCKER_ENV_EXAMPLE = ROOT / ".env.docker.example"


class MasterCiCdWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not WORKFLOW.is_file():
            raise AssertionError(f"missing workflow: {WORKFLOW}")
        cls.content = WORKFLOW.read_text(encoding="utf-8")

    def test_master_pull_request_and_push_are_the_only_triggers(self) -> None:
        self.assertRegex(
            self.content,
            r"pull_request:\s*\n\s*branches:\s*\n\s*- master",
        )
        self.assertRegex(
            self.content,
            r"push:\s*\n\s*branches:\s*\n\s*- master",
        )
        self.assertIn("contents: read", self.content)
        self.assertNotIn("pull_request_target", self.content)

    def test_backend_only_jobs_and_gradle_commands_are_present(self) -> None:
        self.assertIn("backend-test:", self.content)
        self.assertIn("integration-and-container-smoke:", self.content)
        self.assertIn("publish-image:", self.content)
        self.assertIn("./gradlew build", self.content)
        self.assertIn("./gradlew migrationTest", self.content)
        self.assertIn("./gradlew integrationTest", self.content)
        self.assertNotIn("setup-node", self.content)
        self.assertNotIn("npm ", self.content)
        self.assertNotRegex(self.content, r"(?m)^\s{2}frontend:")

    def test_schema_migration_runs_before_integration_and_uses_separate_credentials(self) -> None:
        migration_position = self.content.index("./gradlew migrationTest")
        integration_position = self.content.index("./gradlew integrationTest")

        self.assertLess(migration_position, integration_position)
        self.assertIn("DB_MIGRATION_USERNAME", self.content)
        self.assertIn("DB_MIGRATION_PASSWORD", self.content)
        self.assertRegex(self.content, r"GRANT\s+SELECT,\s*INSERT,\s*UPDATE,\s*DELETE")
        self.assertIn("Verify runtime user cannot change the schema", self.content)
        self.assertIn("Runtime database user unexpectedly has DDL permission", self.content)
        self.assertIn("--env DB_MIGRATION_USERNAME", self.content)
        self.assertIn("--env DB_MIGRATION_PASSWORD", self.content)

    def test_integration_job_uses_pinned_mysql_and_smoke_tests_the_image(self) -> None:
        self.assertRegex(
            self.content,
            r"image:\s*mysql:8\.4\.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6",
        )
        self.assertIn("mysqladmin ping", self.content)
        self.assertIn("MYSQL_DATABASE", self.content)
        self.assertIn("DB_URL", self.content)
        self.assertIn("KAKAO_CLIENT_ID: ci-test-client", self.content)
        self.assertIn("load: true", self.content)
        self.assertIn("/actuator/health", self.content)
        self.assertIn("docker run", self.content)

    def test_publish_is_gated_to_successful_master_pushes(self) -> None:
        self.assertRegex(
            self.content,
            r"needs:\s*\[[^\]]*backend-test[^\]]*integration-and-container-smoke[^\]]*\]",
        )
        self.assertIn("github.event_name == 'push'", self.content)
        self.assertIn("github.ref == 'refs/heads/master'", self.content)
        self.assertIn("environment: docker-hub", self.content)
        self.assertIn("vars.DOCKERHUB_USERNAME", self.content)
        self.assertIn("secrets.DOCKERHUB_TOKEN", self.content)

    def test_publish_uses_traceable_tags_cache_and_attestations(self) -> None:
        self.assertIn("type=raw,value=latest", self.content)
        self.assertIn("type=sha,prefix=sha-,format=short", self.content)
        self.assertIn("DOCKER_METADATA_SHORT_SHA_LENGTH: '12'", self.content)
        self.assertIn("platforms: linux/amd64", self.content)
        self.assertIn("cache-from: type=gha", self.content)
        self.assertIn("cache-to: type=gha,mode=max", self.content)
        self.assertIn("provenance: mode=max", self.content)
        self.assertIn("sbom: true", self.content)
        self.assertIn("push-to-registry: true", self.content)

    def test_reusable_actions_are_pinned_to_full_commit_shas(self) -> None:
        action_references = re.findall(r"uses:\s*([^\s#]+)", self.content)
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
