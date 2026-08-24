"""Contracts for the single-EC2 AWS production deployment."""

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROD_CONFIG = ROOT / "src" / "main" / "resources" / "application-prod.yml"
PUBLISH_WORKFLOW = ROOT / ".github" / "workflows" / "master-publish.yml"
ROLLBACK_WORKFLOW = ROOT / ".github" / "workflows" / "production-rollback.yml"
BOOTSTRAP = ROOT / "deploy" / "ec2" / "bootstrap.sh"
DEPLOY = ROOT / "deploy" / "ec2" / "deploy.sh"
MONITORING_COMPOSE = ROOT / "monitoring" / "compose.prod.yml"
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
        self.assertIn("docker-compose-plugin", bootstrap)
        self.assertIn("compose.prod.yml", bootstrap)
        self.assertNotIn("monitoring.sh", bootstrap)
        self.assertIn("prometheus-prod.yml", bootstrap)
        self.assertIn("rider-voice-overview.json", bootstrap)

    def test_deploy_uses_ssm_env_an_immutable_image_and_health_rollback(self) -> None:
        deploy = DEPLOY.read_text(encoding="utf-8")
        self.assertIn("/rider-voice/prod", deploy)
        self.assertIn("get-parameters-by-path", deploy)
        self.assertIn('DEPLOY_AWS_REGION="ap-northeast-2"', deploy)
        self.assertIn("--with-decryption", deploy)
        self.assertIn("chmod 600", deploy)
        self.assertRegex(deploy, r"sha-\[0-9a-f\].*12")
        self.assertIn("127.0.0.1:8080:8080", deploy)
        self.assertIn("rider-voice-observability", deploy)
        self.assertIn("rds-truststore.p12", deploy)
        self.assertIn("/actuator/health", deploy)
        self.assertIn("rollback", deploy.lower())
        self.assertNotRegex(deploy, r"(?m)^\s*(source|\.)\s+.*api\.env")

    def test_monitoring_compose_uses_private_ports_secret_and_persistent_volumes(self) -> None:
        monitoring = MONITORING_COMPOSE.read_text(encoding="utf-8")

        self.assertIn("GF_SECURITY_ADMIN_PASSWORD__FILE", monitoring)
        self.assertIn("/run/secrets/grafana_admin_password", monitoring)
        self.assertIn("127.0.0.1:3000:3000", monitoring)
        self.assertIn("127.0.0.1:9090:9090", monitoring)
        self.assertIn("rider-voice-prometheus-data", monitoring)
        self.assertIn("rider-voice-grafana-data", monitoring)
        self.assertIn("rider-voice-observability", monitoring)
        self.assertIn("external: true", monitoring)
        self.assertIn("no-new-privileges:true", monitoring)
        self.assertIn("healthcheck:", monitoring)
        self.assertNotIn("latest", monitoring)

    def test_grafana_uses_the_existing_https_domain_without_public_container_ports(self) -> None:
        monitoring = MONITORING_COMPOSE.read_text(encoding="utf-8")
        nginx = NGINX.read_text(encoding="utf-8")
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("GF_SERVER_ROOT_URL=${GRAFANA_ROOT_URL:?set GRAFANA_ROOT_URL}", monitoring)
        self.assertIn("GF_SERVER_SERVE_FROM_SUB_PATH=true", monitoring)
        self.assertIn("GF_SECURITY_COOKIE_SECURE=true", monitoring)
        self.assertIn('location ^~ /grafana/', nginx)
        self.assertIn("proxy_pass http://127.0.0.1:3000", nginx)
        self.assertIn("GRAFANA_ROOT_URL", bootstrap)
        self.assertIn("https://${DOMAIN}/grafana/", bootstrap)

    def test_master_publish_deploys_with_oidc_only_after_image_publish(self) -> None:
        workflow = PUBLISH_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("needs: publish-image", workflow)
        self.assertIn("environment: production", workflow)
        self.assertIn("id-token: write", workflow)
        self.assertIn("aws-actions/configure-aws-credentials@", workflow)
        self.assertIn("AWS_DEPLOY_ROLE_ARN", workflow)
        self.assertIn("EC2_INSTANCE_ID", workflow)
        self.assertIn("send-ssm-deploy.sh", workflow)
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
        self.assertNotIn("AWS_ACCESS_KEY_ID", rollback)

    def test_github_only_sends_a_validated_ssm_command(self) -> None:
        sender = SSM_DEPLOY.read_text(encoding="utf-8")
        self.assertIn("aws ssm send-command", sender)
        self.assertIn("AWS-RunShellScript", sender)
        self.assertIn("get-command-invocation", sender)
        self.assertRegex(sender, r"sha-\[0-9a-f\].*12")

    def test_console_guide_covers_the_required_security_and_cost_boundaries(self) -> None:
        guide = AWS_GUIDE.read_text(encoding="utf-8")
        self.assertNotIn("monitoring.sh", guide)
        self.assertIn("compose.prod.yml", guide)
        self.assertIn("GF_SECURITY_ADMIN_PASSWORD__FILE", guide)
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
            "GRAFANA_ADMIN_PASSWORD",
            "AWS-StartPortForwardingSession",
            "3000",
            "9090",
        ):
            with self.subTest(required=required):
                self.assertIn(required, guide)


if __name__ == "__main__":
    unittest.main()
