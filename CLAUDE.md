# CLAUDE.md

## Project Purpose

Camunda 8 Connector for the [Apify](https://apify.com) platform, letting BPMN processes drive Apify web scraping and automation.

- **Outbound** (`io.camunda:apify-outbound:1`) — service-task operations: Run Actor, Run Task, Scrape Single URL, Get Dataset Items, Get Key-Value Store Record.
- **Inbound** (`io.camunda:apify-inbound:1`) — a webhook connector that creates an Apify webhook on activation, correlates `ACTOR.RUN.*` events into the process, and deletes the webhook on deactivation. Supported patterns: Start Event, Message Start Event, Intermediate Catch Event, Boundary Event.

Distributed as a shaded JAR via GitHub Releases and as element templates via the Camunda Marketplace.

## Repository Structure

| Path | Description |
|---|---|
| `src/main/java/io/camunda/connector/apify/outbound/` | `ApifyFunction` (outbound entry point) + `dto/` per-operation input/response records |
| `src/main/java/io/camunda/connector/apify/inbound/` | `ApifyInboundExecutable` (webhook executable), `ApifyInboundProperties`, `ApifyInboundEvent` + `dto/` |
| `src/main/java/io/camunda/connector/apify/common/` | `ApifyClient` (Apify REST calls against `https://api.apify.com`), `ApifyApiException`, `RunOptions`, `URLValidator`, `dto/Authentication` |
| `src/main/resources/META-INF/services/` | SPI registration — **must** list both connector classes or the runtime won't discover them |
| `src/main/resources/icon.svg` | Icon embedded into generated element templates |
| `src/test/java/io/camunda/connector/apify/LocalConnectorRuntime.java` | Spring Boot local connector runtime for manual testing |
| `src/test/resources/application.properties` | Local runtime config (port `9898`, self-managed OIDC against Keycloak) |
| `element-templates/*.json` | Five **generated but committed** templates: 1 outbound + 4 inbound event types |
| `docs/` | Screenshots, architecture diagram, marketplace listing assets |
| `.github/workflows/` | `ci.yml` (build+test), `release.yml` (multi-version release), `claude-md-maintenance.yml` |

Documentation: `README.md` (user-facing reference — operations, payloads, field docs), `CONTRIBUTING.md`, `DEVELOPMENT.md`, `RELEASING.md`, `COMPATIBILITY.md`, `TROUBLESHOOTING.md`, `SECURITY.md`.

## Technology Stack

- **Java 21** (`maven.compiler.release=21`), Maven 3.8+ (see `.tool-versions`)
- **Camunda Connector SDK** `io.camunda.connector:connector-core`, version via `${version.connectors}` (default `8.8.8`)
- **element-template-generator** — templates generated from `@ElementTemplate` annotations
- Apache HttpClient 5 for Apify API calls; Jackson for JSON
- **Tests:** JUnit 5 (Jupiter), Mockito, AssertJ; `spring-boot-starter-camunda-connectors` for the local runtime
- **Build:** `maven-shade-plugin` (fat JAR), `maven-surefire-plugin`
- Dependencies resolve from Camunda's Artifactory (`artifacts.camunda.com/artifactory/connectors`), configured in `pom.xml`

## Build, Test & Run

```bash
# Build + run all tests
mvn clean verify

# Build shaded JAR for a specific Camunda minor
mvn clean package -DskipTests -Dversion.connectors=8.8.8   # Camunda 8.8
mvn clean package -DskipTests -Dversion.connectors=8.9.0   # Camunda 8.9

# Regenerate element templates (activates the generate-templates profile)
mvn clean package -Dgenerate.templates=true

# Run the local connector runtime (listens on :9898)
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test
```

A Camunda 8.8 or 8.9 stack from [camunda-distributions](https://github.com/camunda/camunda-distributions) (`docker-compose-full.yaml`) plus Docker is needed for anything beyond unit tests. Web Modeler runs at `http://localhost:8070/` (`demo`/`demo`). Full local-runtime, Docker-bundle, and webhook/ngrok procedures live in `DEVELOPMENT.md`.

## Conventions

- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) — `feat:` (minor), `fix:` (patch), `feat!:`/`BREAKING CHANGE:` (major); `docs:`/`chore:`/`ci:`/`refactor:`/`test:`/`style:` are release-neutral. Release versioning is derived from these via git-cliff.
- **Branching:** branch from `main`, PR back into `main`. Run `mvn clean verify` before pushing; add tests for behavior changes.
- **Java style:** records for DTOs, `ConnectorInputException` for user-input errors, SLF4J for logging, 2-space indent in `outbound`/`common` and 4-space in `inbound` — match the surrounding file.
- **Element template versioning:** bump the template `version` integer only for template JSON changes (not JAR-only code changes); bump the template `id` suffix (`:v1` → `:v2`) for breaking redesigns.
- Line endings are normalized to LF (`.gitattributes`).

## Key Notes for AI Assistants

- **Don't change dependency scopes.** `connector-core`, `element-template-generator-core`, and all `httpclient5`/`httpcore5` deps are `provided` on purpose — the `connectors-bundle` runtime supplies them. Shading `httpclient5` shadows the runtime's copy and breaks `DefaultHostnameVerifier` with `NoClassDefFoundError`.
- **`${version.connectors}` must stay ≤ the lowest supported runtime patch.** Provided deps resolve at runtime, so compiling against a newer SDK API breaks on older runtimes. Only the *minor* must match the target runtime; patches are interchangeable.
- **8.8 and 8.9 need separate JARs** (Spring Boot 3.x → 4.0.x, type-safe pagination). Release builds both and suffixes them `-c8.8` / `-c8.9`. Adding a Camunda minor means updating the matrix in `.github/workflows/release.yml` *and* `COMPATIBILITY.md`.
- **`element-templates/*.json` are generated artifacts that are committed.** Change the `@ElementTemplate`/`@TemplateProperty` annotations and regenerate with `-Dgenerate.templates=true`; don't hand-edit the JSON. Template IDs and file names are wired up in the `generate-templates` profile in `pom.xml`.
- **Adding an operation** touches several places: a new `dto/` input record, a case in `ApifyFunction.executeConnector`, the `inputVariables` list on `@OutboundConnector`, and regenerated templates.
- **Adding a connector class** requires registering it in the matching `src/main/resources/META-INF/services/` file.
- **Error semantics matter for retries.** `ApifyFunction.raiseApiFailure` maps 4xx (except 429) to `ConnectorInputException` (incident, no retry) and 5xx/429/transport failures to `RuntimeException` (retried). Preserve this split when touching error handling.
- **Inbound needs an explicitly configured public URL.** The SDK does not expose the runtime's own address, so the `Camunda webhook URL` template field is required; the callback is `<baseUrl>/inbound/<context>`. Webhook creation uses a SHA-256 idempotency key over `callbackUrl:resourceId`.
- **Secret env var naming differs by version:** `{{secrets.APIFY_TOKEN}}` resolves from `APIFY_TOKEN` on 8.8 but `SECRET_APIFY_TOKEN` on 8.9 (default `SECRET_` prefix). See `COMPATIBILITY.md`.
- Tests are self-contained with mocked Apify API calls — never add tests that hit the real Apify API or require a token.
- Releases are workflow-driven (Actions → *Create a release*); don't bump `pom.xml` versions or tag by hand. `RELEASING.md` covers mid-flight failure recovery.
- Resource identifiers accept `username/name` and are normalized to `username~name` before hitting the Apify API.
