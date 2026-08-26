"""CI contracts for local and single-EC2 Prometheus/Grafana monitoring."""

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build.gradle.kts"
APP_CONFIG = ROOT / "src" / "main" / "resources" / "application.yml"
SECURITY = ROOT / "src" / "main" / "kotlin" / "com" / "ridervoice" / "api" / "common" / "security" / "SecurityConfig.kt"
COMPOSE = ROOT / "monitoring" / "compose.yml"
PRODUCTION_COMPOSE = ROOT / "monitoring" / "compose.prod.yml"
PROMETHEUS_LOCAL = ROOT / "monitoring" / "prometheus" / "prometheus-local.yml"
PROMETHEUS_PROD = ROOT / "monitoring" / "prometheus" / "prometheus-prod.yml"
DATASOURCE = ROOT / "monitoring" / "grafana" / "provisioning" / "datasources" / "prometheus.yml"
DASHBOARD_PROVIDER = ROOT / "monitoring" / "grafana" / "provisioning" / "dashboards" / "rider-voice.yml"
DASHBOARD = ROOT / "monitoring" / "grafana" / "dashboards" / "rider-voice-overview.json"
NGINX = ROOT / "deploy" / "ec2" / "nginx.conf.template"
BOOTSTRAP = ROOT / "deploy" / "ec2" / "bootstrap.sh"
DEPLOY_SCRIPT = ROOT / "deploy" / "ec2" / "deploy.sh"
RELEASE_SCRIPT = ROOT / "deploy" / "ec2" / "deploy-release.sh"


class MonitoringContractTest(unittest.TestCase):
    def test_spring_exposes_only_health_and_prometheus_with_histograms(self) -> None:
        build = BUILD.read_text(encoding="utf-8")
        config = APP_CONFIG.read_text(encoding="utf-8")
        security = SECURITY.read_text(encoding="utf-8")

        self.assertIn('runtimeOnly("io.micrometer:micrometer-registry-prometheus")', build)
        self.assertRegex(config, r"include:\s*health,prometheus")
        self.assertIn("percentiles-histogram", config)
        self.assertIn("http.server.requests", config)
        self.assertIn('"/actuator/prometheus"', security)

    def test_local_compose_is_private_persistent_and_uses_pinned_images(self) -> None:
        compose = COMPOSE.read_text(encoding="utf-8")

        self.assertRegex(compose, r"prom/prometheus:v3\.14\.0@sha256:[0-9a-f]{64}")
        self.assertRegex(compose, r"grafana/grafana:13\.2\.0@sha256:[0-9a-f]{64}")
        self.assertIn("127.0.0.1:9090:9090", compose)
        self.assertIn("127.0.0.1:3000:3000", compose)
        self.assertIn("prometheus-data:/prometheus", compose)
        self.assertIn("grafana-data:/var/lib/grafana", compose)
        self.assertIn("host.docker.internal:host-gateway", compose)
        self.assertIn("GF_USERS_ALLOW_SIGN_UP=false", compose)
        self.assertIn("GF_AUTH_ANONYMOUS_ENABLED=false", compose)
        self.assertIn("GF_PLUGINS_PREINSTALL_DISABLED=true", compose)
        self.assertIn("GF_PLUGINS_PLUGIN_ADMIN_ENABLED=false", compose)

    def test_prometheus_targets_are_environment_specific(self) -> None:
        local = PROMETHEUS_LOCAL.read_text(encoding="utf-8")
        prod = PROMETHEUS_PROD.read_text(encoding="utf-8")

        self.assertIn("host.docker.internal:8080", local)
        self.assertIn("rider-voice-api:8080", prod)
        for config in (local, prod):
            self.assertIn("scrape_interval: 15s", config)
            self.assertIn("metrics_path: /actuator/prometheus", config)

    def test_grafana_assets_provision_prometheus_and_core_dashboard_panels(self) -> None:
        datasource = DATASOURCE.read_text(encoding="utf-8")
        provider = DASHBOARD_PROVIDER.read_text(encoding="utf-8")
        dashboard = json.loads(DASHBOARD.read_text(encoding="utf-8"))
        serialized = json.dumps(dashboard)

        self.assertIn("http://prometheus:9090", datasource)
        self.assertIn("/var/lib/grafana/dashboards", provider)
        self.assertEqual(dashboard["uid"], "rider-voice-overview")
        for metric in (
            "up",
            "http_server_requests_seconds_count",
            "http_server_requests_seconds_bucket",
            "jvm_memory_used_bytes",
            "jvm_gc_pause_seconds",
            "process_cpu_usage",
            "jvm_threads_live_threads",
            "hikaricp_connections_active",
            "hikaricp_connections_pending",
        ):
            with self.subTest(metric=metric):
                self.assertIn(metric, serialized)

    def test_production_monitoring_is_private_persistent_and_independent(self) -> None:
        monitoring = PRODUCTION_COMPOSE.read_text(encoding="utf-8")
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        nginx = NGINX.read_text(encoding="utf-8")

        self.assertIn("rider-voice-observability", monitoring)
        self.assertIn("rider-voice-observability", deploy)
        self.assertIn("127.0.0.1:9090:9090", monitoring)
        self.assertIn("127.0.0.1:3000:3000", monitoring)
        self.assertIn("rider-voice-prometheus-data", monitoring)
        self.assertIn("rider-voice-grafana-data", monitoring)
        self.assertIn("GF_SECURITY_ADMIN_PASSWORD__FILE", monitoring)
        self.assertIn("/run/secrets/grafana_admin_password", monitoring)
        self.assertIn("GF_PLUGINS_PREINSTALL_DISABLED=true", monitoring)
        self.assertIn("--storage.tsdb.retention.time=7d", monitoring)
        self.assertIn("--storage.tsdb.retention.size=2GB", monitoring)
        self.assertIn("/api/health", monitoring)
        self.assertIn("/-/ready", monitoring)
        self.assertIn("healthcheck:", monitoring)
        self.assertIn("external: true", monitoring)
        self.assertFalse((ROOT / "deploy" / "ec2" / "monitoring.sh").exists())
        self.assertRegex(
            nginx,
            r"location\s+=\s+/actuator/prometheus\s*\{\s*return\s+404;\s*\}",
        )

    def test_grafana_is_exposed_only_through_the_https_subpath(self) -> None:
        monitoring = PRODUCTION_COMPOSE.read_text(encoding="utf-8")
        nginx = NGINX.read_text(encoding="utf-8")
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("GF_SERVER_ROOT_URL=${GRAFANA_ROOT_URL:?set GRAFANA_ROOT_URL}", monitoring)
        self.assertIn("GF_SERVER_SERVE_FROM_SUB_PATH=true", monitoring)
        self.assertIn("GF_SECURITY_COOKIE_SECURE=true", monitoring)
        self.assertIn("GF_SECURITY_DISABLE_BRUTE_FORCE_LOGIN_PROTECTION=false", monitoring)
        self.assertIn("GF_SECURITY_BRUTE_FORCE_LOGIN_PROTECTION_MAX_ATTEMPTS=5", monitoring)
        self.assertIn("127.0.0.1:3000:3000", monitoring)
        self.assertRegex(
            nginx,
            r"location\s+=\s+/grafana\s*\{\s*return\s+301\s+/grafana/;\s*\}",
        )
        self.assertRegex(nginx, r"location\s+\^~\s+/grafana/\s*\{")
        self.assertIn("proxy_pass http://127.0.0.1:3000", nginx)
        self.assertIn("map $http_upgrade $grafana_connection_upgrade", nginx)
        self.assertIn("proxy_set_header Upgrade $http_upgrade", nginx)
        self.assertIn("proxy_set_header Connection $grafana_connection_upgrade", nginx)
        self.assertIn('GRAFANA_ROOT_URL="https://${DOMAIN}/grafana/"', bootstrap)
        self.assertIn("MONITORING_ENV_FILE", bootstrap)
        self.assertIn('chmod 0600 "${MONITORING_ENV_FILE}"', bootstrap)

    def test_release_updates_monitoring_without_replacing_secrets_or_volumes(self) -> None:
        release = RELEASE_SCRIPT.read_text(encoding="utf-8")
        asset_block = re.search(r"MONITORING_ASSETS=\((.*?)\n\)", release, re.DOTALL)

        self.assertIsNotNone(asset_block)
        self.assertIn("compose.prod.yml", release)
        self.assertIn("prometheus-prod.yml", release)
        self.assertIn("rider-voice-overview.json", release)
        self.assertIn("docker compose", release)
        self.assertIn("--detach --wait", release)
        self.assertIn("/-/ready", release)
        self.assertIn("/grafana/api/health", release)
        self.assertIn('up{job="rider-voice-api"}', release)
        self.assertIn("restore_monitoring", release)
        self.assertNotIn(".env", asset_block.group(1))
        self.assertNotIn("secrets/", asset_block.group(1))
        self.assertNotIn("docker volume rm", release)


if __name__ == "__main__":
    unittest.main()
