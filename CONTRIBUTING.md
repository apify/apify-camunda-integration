# Contributing to Apify Camunda Connector

Short version: get the stack running, verify tests, open a clean PR.

## I just want to...

- **Run the connector locally quickly** -> [Quick Start (5 min)](#quick-start-5-min)
- **Test outbound or inbound flows in Modeler** -> [Development guide](DEVELOPMENT.md)
- **Fix a bug or add a feature** -> [Contributing workflow](#contributing-workflow)
- **Use the release workflow** (maintainers) -> [Releasing guide](RELEASING.md)
- **Debug setup/runtime issues** -> [Troubleshooting](TROUBLESHOOTING.md)
- **Understand connector behavior and payloads** -> [README.md](README.md)

---

## Quick Start (5 min)

### 1) Start Camunda

```bash
git clone https://github.com/camunda/camunda-distributions.git
cd camunda-distributions/docker-compose/versions/camunda-8.9
docker compose -f docker-compose-full.yaml up -d
```

Wait for <http://localhost:8070/> to show the login page.

### 2) Build this project

```bash
git clone https://github.com/apify/apify-camunda-integration.git
cd apify-camunda-integration
mvn clean package
```

> **Note:** To build against a specific Camunda version, see [Test against specific Camunda versions](DEVELOPMENT.md#test-against-specific-camunda-versions).

### 3) Run connector runtime

```bash
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test
```

Done.

Open Web Modeler at <http://localhost:8070/> (`demo` / `demo`) and add the Apify connector template.

---

## What You Need

| Requirement | Version / Notes |
|---|---|
| Java | 21+ |
| Maven | 3.8+ |
| Docker + Docker Compose | Latest stable |
| Camunda stack | 8.8 or 8.9 (`docker-compose-full.yaml`) |
| Apify account + token | Free tier works |

Use `apify/hello-world` as your smoke-test Actor. It is fast and needs no extra config.

---

## Choose Your Path

- **Outbound connector testing:** [Development guide - Outbound](DEVELOPMENT.md#outbound-modeler-smoke-test)
- **Inbound connector testing (webhooks + ngrok):** [Development guide - Inbound](DEVELOPMENT.md#inbound-testing-webhooks)
- **Test as if deployed (Self-Managed / Hybrid):** [Development guide - Docker bundle check](DEVELOPMENT.md#test-shaded-jar-in-connectors-bundle)
- **Version-specific checks (8.8 / 8.9):** [Development guide - Camunda versions](DEVELOPMENT.md#test-against-specific-camunda-versions)

---

## Running Tests

```bash
mvn clean verify
```

All tests are self-contained and use mocked Apify API calls.

---

## Contributing Workflow

1. Fork and branch from `main`.
2. Keep changes focused and small where possible.
3. Run `mvn clean verify` before pushing.
4. Add or update tests for behavior changes.
5. Open a PR to `main` and explain what changed, why, and how you tested it.

### PR checklist

- [ ] Build passes locally (`mvn clean verify`)
- [ ] New behavior has tests (or a short reason why not)
- [ ] Docs updated if user-facing behavior changed
- [ ] PR description includes test steps/results

---

## Commit Style

We use [Conventional Commits](https://www.conventionalcommits.org/). Keep subjects short and meaningful.

| Prefix | Use for | Version impact |
|---|---|---|
| `feat:` | New feature | Minor |
| `fix:` | Bug fix | Patch |
| `feat!:` / `fix!:` / `BREAKING CHANGE:` | Breaking change | Major |
| `docs:`, `chore:`, `ci:`, `refactor:`, `test:`, `style:` | Non-feature/bug work | None |

Examples:

- `feat: add actor run timeout option`
- `fix: handle missing webhook correlation key`
- `docs: clarify ngrok setup for inbound testing`

---

## More Detailed Docs

- Local development and Modeler testing: [DEVELOPMENT.md](DEVELOPMENT.md)
- Release process and policies: [RELEASING.md](RELEASING.md)
- Troubleshooting tables and fixes: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- Compatibility matrix and secret naming: [COMPATIBILITY.md](COMPATIBILITY.md)
- Connector behavior and all field docs: [README.md](README.md)
