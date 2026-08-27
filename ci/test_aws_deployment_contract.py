"""CI contracts for the single-EC2 AWS production deployment."""

import json
import re
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROD_CONFIG = ROOT / "src" / "main" / "resources" / "application-prod.yml"
PUBLISH_WORKFLOW = ROOT / ".github" / "workflows" / "master-publish.yml"
ROLLBACK_WORKFLOW = ROOT / ".github" / "workflows" / "production-rollback.yml"
CLEANUP_WORKFLOW = ROOT / ".github" / "workflows" / "production-docker-cleanup.yml"
BOOTSTRAP = ROOT / "deploy" / "ec2" / "bootstrap.sh"
DEPLOY = ROOT / "deploy" / "ec2" / "deploy.sh"
RELEASE_DEPLOY = ROOT / "deploy" / "ec2" / "deploy-release.sh"
NGINX = ROOT / "deploy" / "ec2" / "nginx.conf.template"
SSM_DEPLOY = ROOT / "deploy" / "github" / "send-ssm-deploy.sh"
AWS_GUIDE = ROOT / "deploy" / "aws" / "README.md"
OIDC_TRUST_POLICY = ROOT / "deploy" / "aws" / "github-oidc-trust-policy.json"
EC2_PARAMETER_POLICY = ROOT / "deploy" / "aws" / "ec2-parameter-policy.json"


class AwsDeploymentContractTest(unittest.TestCase):
    def test_prod_accepts_forwarded_headers_only_at_the_nginx_boundary(self) -> None:
        prod = PROD_CONFIG.read_text(encoding="utf-8")
        self.assertRegex(prod, r"forward-headers-strategy:\s*framework")

        nginx = NGINX.read_text(encoding="utf-8")
        self.assertIn("proxy_pass http://127.0.0.1:8080", nginx)
        self.assertIn("proxy_set_header X-Forwarded-For $remote_addr", nginx)
        self.assertNotIn("$proxy_add_x_forwarded_for", nginx)
        self.assertIn("proxy_set_header X-Forwarded-Proto $scheme", nginx)
        self.assertIn('proxy_set_header Forwarded ""', nginx)
        self.assertIn("proxy_set_header X-Forwarded-Host $host", nginx)

    def test_bootstrap_installs_https_proxy_and_rds_truststore(self) -> None:
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertIn("certbot certonly", bootstrap)
        self.assertIn("global-bundle.pem", bootstrap)
        self.assertIn("rds-truststore.p12", bootstrap)
        self.assertIn("nginx -t", bootstrap)
        self.assertIn("amazon-ssm-agent", bootstrap)
        self.assertNotIn("monitoring.sh", bootstrap)
        self.assertNotIn("compose.prod.yml", bootstrap)
        self.assertNotIn("MONITORING_INSTALL_DIR", bootstrap)

    def test_deploy_uses_ssm_env_an_immutable_image_and_health_rollback(self) -> None:
        deploy = DEPLOY.read_text(encoding="utf-8")
        self.assertIn("/rider-voice/prod", deploy)
        self.assertIn("get-parameters-by-path", deploy)
        self.assertIn('DEPLOY_AWS_REGION="ap-northeast-2"', deploy)
        self.assertIn("--with-decryption", deploy)
        self.assertIn("chmod 600", deploy)
        self.assertRegex(deploy, r"sha-\[0-9a-f\].*12")
        self.assertIn("127.0.0.1:8080:8080", deploy)
        self.assertNotIn("rider-voice-observability", deploy)
        self.assertIn("rds-truststore.p12", deploy)
        self.assertIn("/actuator/health", deploy)
        self.assertIn("rollback", deploy.lower())
        self.assertNotRegex(deploy, r"(?m)^\s*(source|\.)\s+.*api\.env")

    def test_production_monitoring_assets_are_absent_and_public_paths_are_blocked(self) -> None:
        nginx = NGINX.read_text(encoding="utf-8")
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")

        self.assertFalse((ROOT / "monitoring" / "compose.prod.yml").exists())
        self.assertFalse((ROOT / "monitoring" / "prometheus" / "prometheus-prod.yml").exists())
        self.assertRegex(nginx, r"location\s+=\s+/grafana\s*\{\s*return\s+404;\s*\}")
        self.assertRegex(nginx, r"location\s+\^~\s+/grafana/\s*\{\s*return\s+404;\s*\}")
        self.assertNotIn("proxy_pass http://127.0.0.1:3000", nginx)
        self.assertNotIn("GRAFANA_ROOT_URL", bootstrap)

    def test_master_publish_deploys_with_oidc_only_after_image_publish(self) -> None:
        workflow = PUBLISH_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("needs: publish-image", workflow)
        self.assertIn("environment: production", workflow)
        self.assertIn("id-token: write", workflow)
        self.assertIn("aws-actions/configure-aws-credentials@", workflow)
        self.assertIn("AWS_DEPLOY_ROLE_ARN", workflow)
        self.assertIn("EC2_INSTANCE_ID", workflow)
        self.assertIn("send-ssm-deploy.sh", workflow)
        self.assertIn('"$GITHUB_SHA"', workflow)
        self.assertNotIn("AWS_ACCESS_KEY_ID", workflow)
        self.assertNotIn("AWS_SECRET_ACCESS_KEY", workflow)

        action_references = re.findall(r"uses:\s*([^\s#]+)", workflow)
        for action_reference in action_references:
            with self.subTest(action=action_reference):
                self.assertRegex(action_reference, r"^[^@]+@[0-9a-f]{40}$")

    def test_github_oidc_trust_uses_the_immutable_repository_subject(self) -> None:
        policy = json.loads(OIDC_TRUST_POLICY.read_text(encoding="utf-8"))
        condition = policy["Statement"][0]["Condition"]["StringEquals"]

        self.assertEqual(
            condition["token.actions.githubusercontent.com:aud"],
            "sts.amazonaws.com",
        )
        subject = condition["token.actions.githubusercontent.com:sub"]
        self.assertEqual(
            subject,
            "repo:RudySeo@78248966/Rider-Voice@1308728176:environment:production",
        )
        self.assertNotIn("*", subject)

    def test_ec2_role_can_read_path_and_single_monitoring_parameter(self) -> None:
        policy = json.loads(EC2_PARAMETER_POLICY.read_text(encoding="utf-8"))
        actions = policy["Statement"][0]["Action"]

        self.assertIn("ssm:GetParameter", actions)
        self.assertIn("ssm:GetParametersByPath", actions)

    def test_manual_rollback_reuses_the_production_oidc_path(self) -> None:
        rollback = ROLLBACK_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", rollback)
        self.assertIn("image_tag:", rollback)
        self.assertIn("environment: production", rollback)
        self.assertIn("id-token: write", rollback)
        self.assertIn("send-ssm-deploy.sh", rollback)
        self.assertIn("git rev-parse HEAD", rollback)
        self.assertIn("steps.release.outputs.sha", rollback)
        self.assertNotIn("AWS_ACCESS_KEY_ID", rollback)

    def test_manual_disk_cleanup_prunes_only_unreferenced_images_through_ssm(self) -> None:
        cleanup = CLEANUP_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("workflow_dispatch:", cleanup)
        self.assertIn("if: github.ref == 'refs/heads/master'", cleanup)
        self.assertIn("environment: production", cleanup)
        self.assertIn("group: rider-voice-production-deploy", cleanup)
        self.assertIn("id-token: write", cleanup)
        self.assertIn("aws ssm send-command", cleanup)
        self.assertIn("docker image prune --all --force", cleanup)
        self.assertIn("df -h /var/lib/containerd", cleanup)
        self.assertNotIn("docker system prune", cleanup)
        self.assertNotIn("docker container prune", cleanup)
        self.assertNotIn("docker volume prune", cleanup)
        self.assertNotIn("AWS_ACCESS_KEY_ID", cleanup)
        action_references = re.findall(r"uses:\s*([^\s#]+)", cleanup)
        self.assertGreaterEqual(len(action_references), 1)
        for action_reference in action_references:
            self.assertRegex(action_reference, r"^[^@]+@[0-9a-f]{40}$")

    def test_github_only_sends_a_validated_ssm_command(self) -> None:
        sender = SSM_DEPLOY.read_text(encoding="utf-8")
        self.assertIn("aws ssm send-command", sender)
        self.assertIn("AWS-RunShellScript", sender)
        self.assertIn("get-command-invocation", sender)
        self.assertRegex(sender, r"sha-\[0-9a-f\].*12")
        self.assertRegex(sender, r"RELEASE_SHA.*\^\[0-9a-f\].*40")
        self.assertIn("deploy-release.sh", sender)
        self.assertIn("raw.githubusercontent.com/RudySeo/Rider-Voice", sender)

    def test_github_runs_long_release_outside_the_ssm_document_worker(self) -> None:
        sender = SSM_DEPLOY.read_text(encoding="utf-8")

        self.assertIn("systemd-run", sender)
        self.assertIn("systemd-run --no-block", sender)
        self.assertIn("--property=Type=oneshot", sender)
        self.assertIn("REMOTE_STATUS_FILE", sender)
        self.assertIn("REMOTE_LOG_FILE", sender)
        self.assertIn("start_release", sender)
        self.assertIn("poll_release", sender)
        self.assertIn('executionTimeout: ["60"]', sender)
        self.assertNotIn('executionTimeout: ["900"]', sender)

    def test_ssm_poll_is_posix_and_tolerates_the_systemd_exit_status_file_race(self) -> None:
        sender = SSM_DEPLOY.read_text(encoding="utf-8")
        poll_command = sender.split('poll_command="', 1)[1].split('"\n', 1)[0]

        self.assertGreaterEqual(poll_command.count("[ -f ${REMOTE_STATUS_FILE} ]"), 2)
        self.assertNotIn("[[", poll_command)
        self.assertIn("readonly MISSING_GRACE_ATTEMPTS=3", sender)
        self.assertIn("missing_attempts=$((missing_attempts + 1))", sender)
        self.assertIn(
            "if (( missing_attempts < MISSING_GRACE_ATTEMPTS )); then",
            sender,
        )

        with tempfile.TemporaryDirectory() as temporary_directory:
            status_file = Path(temporary_directory) / "release.status"
            status_file.write_text("0\n", encoding="utf-8")
            executable_poll = (
                poll_command.replace("\\$", "$")
                .replace('\\"', '"')
                .replace("${REMOTE_STATUS_FILE}", shlex.quote(str(status_file)))
                .replace("${UNIT_NAME}", "rider-voice-missing-test-unit")
            )
            completed = subprocess.run(
                ["/bin/sh", "-c", executable_poll],
                check=False,
                capture_output=True,
                text=True,
            )

        self.assertEqual(completed.returncode, 0)
        self.assertEqual(completed.stdout, "COMPLETE 0\n")
        self.assertEqual(completed.stderr, "")

    def test_release_uses_exact_commit_backend_assets_and_decommissions_monitoring(self) -> None:
        release = RELEASE_DEPLOY.read_text(encoding="utf-8")

        self.assertIn("Rider-Voice/archive/${RELEASE_SHA}.tar.gz", release)
        self.assertIn("deploy/ec2/deploy.sh", release)
        self.assertIn("deploy/ec2/nginx.conf.template", release)
        self.assertIn("rider-voice-prometheus", release)
        self.assertIn("rider-voice-grafana", release)
        self.assertIn("rider-voice-prometheus-data", release)
        self.assertIn("rider-voice-grafana-data", release)
        self.assertIn('MONITORING_INSTALL_DIR="${INSTALL_DIR}/monitoring"', release)
        self.assertIn("docker volume rm", release)
        self.assertNotIn("docker compose", release)
        self.assertNotIn("GRAFANA_ADMIN_PASSWORD", release)
        self.assertIn("flock --nonblock", release)
        self.assertIn("KAKAO_REDIRECT_URI", release)
        self.assertIn("aws ssm get-parameter", release)

    def test_release_reclaims_unreferenced_images_before_backend_deploy(self) -> None:
        release = RELEASE_DEPLOY.read_text(encoding="utf-8")

        prune = "docker image prune --all --force"
        deploy = '"${INSTALL_DIR}/deploy.sh" "${IMAGE_NAME}" "${IMAGE_TAG}"'
        self.assertIn(prune, release)
        self.assertIn(deploy, release)
        self.assertLess(release.index(prune), release.index(deploy))
        self.assertNotIn("docker system prune", release)
        self.assertNotIn("docker container prune", release)
        self.assertNotIn("docker volume prune", release)

    def test_console_guide_covers_the_required_security_and_cost_boundaries(self) -> None:
        guide = AWS_GUIDE.read_text(encoding="utf-8")
        ssm_section = guide.split("## 5. SSM Parameter Store", 1)[1].split("## 6.", 1)[0]
        self.assertNotIn("monitoring.sh", guide)
        self.assertNotIn("compose.prod.yml", guide)
        self.assertNotIn("GRAFANA_ADMIN_PASSWORD", ssm_section)
        self.assertIn("GRAFANA_ADMIN_PASSWORD`를 삭제", guide)
        for required in (
            "db.t4g.micro",
            "MySQL 8.4.10",
            "Single-AZ",
            "삭제 방지",
            "7일",
            "SecureString",
            "AmazonSSMManagedInstanceCore",
            "3306",
            "production",
            "OIDC",
            "repo:RudySeo@78248966/Rider-Voice@1308728176:environment:production",
            "80%",
            "100%",
            "application log",
            "/grafana/",
        ):
            with self.subTest(required=required):
                self.assertIn(required, guide)


if __name__ == "__main__":
    unittest.main()
