"""Contracts for local and single-EC2 Prometheus/Grafana monitoring."""

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
DEPLOY_SCRIPT = ROOT / "deploy" / "ec2" / "deploy.sh"


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

        self.assertRegex(compose, r"prom/prometheus:v3\.5\.5@sha256:[0-9a-f]{64}")
        self.assertRegex(compose, r"grafana/grafana:13\.1\.4@sha256:[0-9a-f]{64}")
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


if __name__ == "__main__":
    unittest.main()
