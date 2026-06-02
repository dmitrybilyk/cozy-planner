# AGENTS.md — Quick start for AI coding agents

Checklist (what an agent should do first)
- [ ] Read this file (AGENTS.md) and the project README.md
- [ ] Inspect the three main services: `planner/`, `notifications/`, `search-service/`
- [ ] Start a local dev environment (Docker Compose) and run the smallest smoke test (seed script)
- [ ] Consult OpenAPI (`planner/src/main/resources/api/planner-api.yaml`) for REST surface
- [ ] Run e2e tests in `e2e/` and the k6 scripts at repo root

1) Big-picture architecture (short)
- This repo contains a small multi-component system:
  - `planner/` — the main Spring Boot WebFlux application (reactive). Hosts the web UI, REST API under `/api/v1`, OAuth2 login and HTML views (Thymeleaf). Uses R2DBC Postgres and Flyway migrations.
  - `notifications/` — a Spring Boot service for inbound/outbound notifications (Telegram integration, Kafka client). It can forward external webhook requests into the planner API.
  - `search-service/` — a service that consumes events (Kafka) and indexes into Elasticsearch for search.
- Infrastructure composed via `docker-compose.yml` (Postgres, Kafka, Elasticsearch, Kibana, optional monitoring stacks in `docker-compose/docker-compose-monitoring.yaml`). Services talk over:
  - REST API (planner exposes `/api/v1/*` — see OpenAPI)
  - Kafka topics for events (planner publishes via a SearchEventPublisher implementation)
  - Elasticsearch consumed by `search-service`
  - Telegram webhooks (planner exposes `/api/v1/telegram/webhook*`) and `notifications/` contains forwarding helpers

2) Quick dev workflows and exact commands
- Start the full local stack (DB + Kafka + ES + search-service):

  docker compose -f docker-compose/docker-compose.yaml up -d

- Start monitoring (Prometheus + Grafana):

  docker compose -f docker-compose/docker-compose-monitoring.yaml up -d

- Build and run `planner` locally (inside project root or inside `planner/`):
  - From repository root (Gradle wrapper present):

    ./gradlew :planner:bootJar
    java -jar planner/build/libs/planner-0.0.1-SNAPSHOT.jar

  - Or from inside `planner/`:

    ./gradlew bootJar
    java -jar build/libs/planner-0.0.1-SNAPSHOT.jar

- Build and run `notifications` and `search-service` similarly with their Gradle tasks or by using their `Dockerfile`s when composing images.
- Seed data + load testing (k6):
  - Seed (creates a dedicated k6 user and sample sessions):

    k6 run seed-data.js

    Note: seed-data.js expects the application on http://localhost:8080 and uses `/k6-login`. See `planner/src/main/java/com/cozy/planner/controllers/K6AuthController.java`.
  - Load test:

    k6 run load-test.js

- Run E2E (Playwright) tests:

  cd e2e
  npm install
  npm test

  The Playwright config uses BASE_URL (default http://localhost:8080). See `e2e/playwright.config.ts`.

3) Project-specific conventions and patterns (do not assume standard Spring Boot defaults)
- Reactive-first: `planner` is implemented with Spring WebFlux, R2DBC and reactive repositories — avoid blocking calls in controllers/services. See `planner/build.gradle.kts` and `SecurityConfig.java`.
- OpenAPI-driven controllers: `planner` generates API interfaces from `planner/src/main/resources/api/planner-api.yaml` using the `openApiGenerate` Gradle task. Generated interfaces are added to `build/generated-sources` before compilation.
- k6 login shortcut: There is a special non-OAuth login endpoint `/k6-login` implemented in `K6AuthController` to create a seed user and set session cookies — used by `seed-data.js` and `load-test.js`.
- Event-driven search indexing: Planner publishes session events via `SearchEventPublisher` (Kafka-backed implementation). `search-service` consumes Kafka topics and writes to Elasticsearch (see `docker-compose/docker-compose.yaml` for service wiring).
- Telegram integration: Planner contains full Telegram webhook handlers under `planner/src/main/java/com/cozy/planner/controllers/TelegramController.java`. `notifications/` has a forwarding controller (`notifications/src/main/java/com/cozy/notifications/controller/TelegramWebhookController.java`) used when running the notifications service.
- Graal/AOT hooks: `planner` configures GraalVM native build (`graalvmNative` in `planner/build.gradle.kts`); AOT/generated artifacts are present in `build/` — be careful when editing generated sources.

4) Integration points & important files to read first (ordered)
- `planner/`
  - `build.gradle.kts` — dependencies, openapi generation and Graal config
  - `src/main/resources/api/planner-api.yaml` — canonical API surface
  - `src/main/java/com/cozy/planner/controllers/K6AuthController.java` — how seed/login works
  - `src/main/java/com/cozy/planner/config/SecurityConfig.java` — OAuth2 and paths that are public/permitted
  - `src/main/java/com/cozy/planner/controllers/TelegramController.java` — Telegram flow and webhook formats
- `notifications/`
  - `build.gradle.kts` — simple Spring Boot web service with Kafka
  - `src/main/java/com/cozy/notifications/controller/TelegramWebhookController.java` — webhook forwarding
- `search-service/`
  - `build.gradle.kts` and `src/main/java/.../SearchEventConsumer.java` — Kafka consumer -> Elasticsearch index
- Root and infra
  - `docker-compose.yml` and `docker-compose/docker-compose-monitoring.yaml` — how local infra is wired
  - `seed-data.js`, `load-test.js` — k6 scripts for seeding and load testing
  - `e2e/` — Playwright tests and config
  - `planner/src/main/resources/application-prod.yaml` — production webhook targets (useful for debugging)

5) How an AI agent should propose and test code changes
- Small focused changes: run unit or integration tests locally if present; otherwise, start stack with docker-compose and run seed-data to smoke test.
- To validate REST API behaviors, prefer using the OpenAPI spec in `planner-api.yaml` as the source-of-truth for endpoint signatures and payload shapes.
- When changing data-layer code, be mindful of R2DBC reactive repositories and Flyway migrations.
- If you change generated sources, update `openApiGenerate` and ensure `./gradlew :planner:compileJava` picks up generated files.

6) Quick debugging pointers
- Missing data on startup: ensure Postgres container (planner-db) is healthy and Flyway runs — check logs with `docker compose logs db` and `docker compose logs app`.
- OAuth locally: the app uses Google OAuth by default; for automated tests use `/k6-login` or set `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_*` env vars in `docker-compose.yml`.
- Kafka/Elastic issues: use `kafka-ui` (ports configured in docker-compose) and Kibana (5601) to inspect topics/indices.

If you need more: run a quick scan for TODOs and issues (`grep -R "TODO" -n`), open Playwright tests in `e2e/tests/` for UI flows, and consult `planner/src/main/resources/api/*` for contract changes.

---
File generated automatically: AGENTS.md — created to help AI/code-assistant agents become productive quickly in this repository.

