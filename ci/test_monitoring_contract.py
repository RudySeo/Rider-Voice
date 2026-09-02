"""CI contracts for local-only Prometheus/Grafana monitoring."""

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build.gradle.kts"
APP_CONFIG = ROOT / "src" / "main" / "resources" / "application.yml"
SECURITY = ROOT / "src" / "main" / "kotlin" / "com" / "ridervoice" / "api" / "common" / "security" / "SecurityConfig.kt"
COMPOSE = ROOT / "monitoring" / "compose.yml"
PROMETHEUS_LOCAL = ROOT / "monitoring" / "prometheus" / "prometheus-local.yml"
DATASOURCE = ROOT / "monitoring" / "grafana" / "provisioning" / "datasources" / "prometheus.yml"


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

    def test_local_compose_is_private_persistent_and_uses_versioned_images(self) -> None:
        compose = COMPOSE.read_text(encoding="utf-8")

        self.assertRegex(compose, r"(?m)^\s*image: prom/prometheus:v3\.14\.0\s*$")
        self.assertRegex(compose, r"(?m)^\s*image: grafana/grafana:13\.2\.0\s*$")
        self.assertIn("127.0.0.1:9090:9090", compose)
        self.assertIn("127.0.0.1:3000:3000", compose)
        self.assertIn("prometheus-data:/prometheus", compose)
        self.assertIn("grafana-data:/var/lib/grafana", compose)
        self.assertIn("host.docker.internal:host-gateway", compose)

    def test_prometheus_targets_the_local_backend(self) -> None:
        local = PROMETHEUS_LOCAL.read_text(encoding="utf-8")

        self.assertIn("host.docker.internal:8080", local)
        self.assertIn("scrape_interval: 15s", local)
        self.assertIn("metrics_path: /actuator/prometheus", local)

    def test_grafana_provisions_prometheus_datasource(self) -> None:
        datasource = DATASOURCE.read_text(encoding="utf-8")

        self.assertIn("http://prometheus:9090", datasource)


if __name__ == "__main__":
    unittest.main()
