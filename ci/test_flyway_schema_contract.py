"""CI contracts for production Flyway schema ownership."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build.gradle.kts"
RESOURCES = ROOT / "src" / "main" / "resources"
MIGRATION = RESOURCES / "db" / "migration" / "V1__create_initial_schema.sql"


class FlywaySchemaContractTest(unittest.TestCase):
    def test_gradle_has_flyway_mysql_and_dedicated_migration_test(self) -> None:
        content = BUILD.read_text(encoding="utf-8")

        self.assertIn("spring-boot-starter-flyway", content)
        self.assertIn("org.flywaydb:flyway-mysql", content)
        self.assertIn('tasks.register<Test>("migrationTest")', content)
        self.assertRegex(content, r'excludeTags\([^)]*"migration"')
        self.assertRegex(content, r'includeTags\([^)]*"migration"')

    def test_profiles_keep_local_update_but_make_flyway_own_production(self) -> None:
        local = self.resource("application-local.yml")
        test = self.resource("application-test.yml")
        prod = self.resource("application-prod.yml")
        migration_test = self.resource("application-migration-test.yml")

        for non_production in (local, test):
            self.assertIn("ddl-auto: update", non_production)
            self.assertRegex(non_production, r"flyway:\s*\n\s*enabled: false")

        for flyway_profile in (prod, migration_test):
            self.assertIn("ddl-auto: validate", flyway_profile)
            self.assertRegex(flyway_profile, r"flyway:\s*\n\s*enabled: true")
            self.assertIn("user: ${DB_MIGRATION_USERNAME}", flyway_profile)
            self.assertNotIn("username: ${DB_MIGRATION_USERNAME}", flyway_profile)
            self.assertIn("password: ${DB_MIGRATION_PASSWORD}", flyway_profile)
            self.assertIn("clean-disabled: true", flyway_profile)
            self.assertIn("baseline-on-migrate: false", flyway_profile)
            self.assertIn("out-of-order: false", flyway_profile)
            self.assertIn("validate-on-migrate: true", flyway_profile)

    def test_v1_defines_all_domain_tables_and_named_constraints(self) -> None:
        sql = MIGRATION.read_text(encoding="utf-8")
        normalized = re.sub(r"\s+", " ", sql.lower())
        domain_tables = {
            "users",
            "oauth_accounts",
            "user_sessions",
            "pickup_locations",
            "restaurants",
            "restaurant_platforms",
            "reviews",
            "review_reports",
            "restaurant_info_reports",
            "moderation_audits",
        }

        created_tables = set(re.findall(r"create table ([a-z_]+)", normalized))
        self.assertEqual(domain_tables, created_tables)
        self.assertEqual(10, normalized.count("id bigint not null auto_increment"))
        self.assertIn("datetime(6)", normalized)
        self.assertIn("decimal(11, 8)", normalized)
        self.assertIn("default character set utf8mb4", normalized)
        self.assertIn("collate utf8mb4_0900_ai_ci", normalized)

        for constraint in (
            "uk_oauth_accounts_provider_subject",
            "uk_user_sessions_refresh_token_hash",
            "uk_pickup_locations_location_key",
            "uk_restaurants_pickup_location_brand_name",
            "uk_reviews_author_restaurant_current_slot",
            "uk_review_reports_reporter_review",
            "uk_restaurant_info_reports_reporter_restaurant",
            "fk_reviews_author_user",
            "fk_reviews_restaurant",
        ):
            with self.subTest(constraint=constraint):
                self.assertIn(f"constraint {constraint}", normalized)

    def resource(self, name: str) -> str:
        return (RESOURCES / name).read_text(encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
