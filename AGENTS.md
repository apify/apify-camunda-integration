# AGENTS.md

Guidance for coding agents working in this repository. `CLAUDE.md` is only an `@AGENTS.md` pointer,
so put instructions here and leave the pointer alone. `.github/workflows/agents-md-maintenance.yml`
updates this file inside each PR that changes the code.

## Project

Apify connector for Camunda 8, written in Java 21 against the Camunda Connector SDK. It ships as
two artifacts that users need together:

- **A shaded JAR** that runs inside a self-hosted `camunda/connectors-bundle` runtime. It contains
  two connectors, both registered through `src/main/resources/META-INF/services/`:
  - **Outbound** `ApifyFunction` (task type `io.camunda:apify-outbound:1`): a service task with
    five operations (Run Actor, Run Task, Scrape Single URL, Get Dataset Items, Get Key-Value
    Store Record).
  - **Inbound** `ApifyInboundExecutable` (type `io.camunda:apify-inbound:1`): a webhook connector
    that creates an Apify webhook for an Actor or task when the process is deployed, receives
    `ACTOR.RUN.*` events, and deletes the webhook on deactivation.
- **Five element templates** in `element-templates/` (one outbound, four inbound: start, message
  start, intermediate catch, boundary) that add the connector to the Modeler palette.

The user-facing docs are `README.md`, `COMPATIBILITY.md` and `TROUBLESHOOTING.md`. Local setup is
in `CONTRIBUTING.md` and `DEVELOPMENT.md`, and the release process is in `RELEASING.md`.

## Commands

```bash
mvn clean verify                                        # build + 102 unit tests (what CI runs)
mvn clean package -DskipTests -Dversion.connectors=8.9.0  # JAR for Camunda 8.9 (default is 8.8.8)
```

There is no Maven wrapper and no formatter or linter. `.tool-versions` pins Temurin 21 and Maven
3.8.4. CI (`ci.yml`) runs `mvn verify` on every push to `main` and on every PR. Dependencies
resolve from Camunda's Artifactory, which is configured in `pom.xml`.

- `mvn verify` needs network access. `MyFunctionTest.shouldReturnReceivedMessageWhenExecute` makes
  a real call to `api.apify.com` with a fake token and asserts on the resulting "Apify API error"
  (a 401). No other test calls the real API.
- Tests only run against the 8.8 SDK. With `-Dversion.connectors=8.9.0` the main code compiles,
  but test discovery fails with a JUnit platform clash (`NoSuchMethodError` in
  `CollectionUtils`). The release workflow builds the 8.9 JAR with `-DskipTests`.
- Running the connector end to end needs a Camunda 8.8 or 8.9 stack in Docker. You can run it from
  the IDE (`LocalConnectorRuntime`, port 9898) or mount the JAR into `connectors-bundle`. Inbound
  also needs a public URL such as ngrok. See `DEVELOPMENT.md`.

## Architecture

### Outbound: `ApifyFunction`

`execute` binds variables to `ApifyRequest` (`authentication`, `operation`, `apifyRequestInput`)
and switches on `operation.type`. Each handler opens a fresh `ApifyClient` in try-with-resources.
To add an operation, you need to:

1. add an input record in `outbound/dto/` and a field for it on `ApifyRequestInput`
2. add `apifyRequestInput.<name>` to `inputVariables` on `@OutboundConnector`
3. add a `case` and handler in `ApifyFunction`, plus a response record that implements `ApifyResult`
4. hand-edit `element-templates/apify-outbound-connector.json`: add a dropdown choice and fields
   bound to `apifyRequestInput.<name>.<field>`, conditioned on `operation.type`

Behaviour that isn't obvious from the field names:

- **Run Actor** fetches the Actor and its build (`buildTag` is resolved through
  `taggedBuilds`). It reads each `prefill` from the build's input schema and merges the user's
  `inputJson` over those values. **Run Task** sends `inputJson` as-is, or no body at all, so the
  task's saved input applies. In both, `inputJson` can be a JSON object or a JSON string.
- `waitForFinish` (and Scrape Single URL, which always waits) polls
  `GET /v2/actor-runs/{id}?waitForFinish=1` in a `while (true)` loop with **no deadline**. The
  job worker thread stays busy until the run reaches a terminal state.
- Scrape Single URL runs `apify/website-content-crawler` (hard-coded ID `aYG0l9s7dbB7j3gbS`)
  for a single page. It returns the first dataset item with `text` removed, to keep the output
  small for AI agents.
- Get Key-Value Store Record decides on the response `Content-Type`. JSON goes to `jsonValue`,
  `text/*` goes to `textValue`, and anything else is base64 in `base64Value`.
- In Actor and task IDs, `username/name` becomes `username~name` before any API call.

### Errors and retries (two layers)

1. `ApifyClient` makes up to 3 attempts on 429, 5xx and transport errors, sleeping 1 s and then
   2 s between them (after a third 429/5xx it also sleeps 4 s before giving up). It does this for
   **every** method, including `POST .../runs`, so a 5xx on a run start can launch a duplicate run.
   Underneath, `HttpClients.createDefault()` keeps Apache HttpClient's default retry strategy,
   which re-sends a 429 or 503 response once (POSTs included), so one attempt can be two requests. Any other non-2xx response throws `ApifyApiException` straight
   away. That exception parses Apify's `{"error":{"type","message"}}` envelope.
2. `ApifyFunction.raiseApiFailure` maps 4xx (except 429) to `ConnectorInputException`, which
   becomes an incident with no retry. 5xx, 429 and transport failures (status 0) become a plain
   `RuntimeException`, which Zeebe retries (the template defaults to 3 retries, `PT0S`). On an
   Actor or task `invalid-input` error, `formatApiFailureMessage` adds a hint about "Custom Input
   JSON". Keep this split when you touch error handling. `FormatApiFailureMessageTest` covers the hint
   and `ApifyApiExceptionTest` the envelope parsing; the 4xx/5xx mapping itself has no test.

Any other exception inside a handler is wrapped as `RuntimeException("Error: Failed to … - …")`,
so it is retried too.

### Inbound: `ApifyInboundExecutable`

- `activate` binds `ApifyInboundProperties` and builds the callback URL from
  `connectorWebhookUrl` + `/inbound/` + `inbound.context`. The SDK does not expose the runtime's
  public address, so the user must fill in the template's "Camunda webhook URL" field.
- If a slug (`user~name`) is given, `activate` resolves it to a real ID via `GET` on the Actor or
  task, because webhook conditions need IDs. It then creates one webhook for all four terminal
  events (`SUCCEEDED`, `FAILED`, `TIMED_OUT`, `ABORTED`), conditioned on `actorId` or
  `actorTaskId` (`ResourceType.getConditionKey`).
- The webhook's `idempotencyKey` is a SHA-256 of `callbackUrl:resourceId`. Apify returns the
  existing webhook when a key repeats, so activating again for the same URL and resource (e.g.
  after a runtime restart that skipped `deactivate`) doesn't create a duplicate. Don't add
  anything that changes per activation to that key.
- `deactivate` deletes the webhook. `ApifyClient.deleteWebhook` treats a 404 as success.
- `triggerWebhook` parses the body (the shape comes from `ApifyPayloadTemplate`) into
  `ApifyInboundEvent` and returns `ApifyWebhookResponse` as the process variables (`eventType`,
  `runId`, `status`, `defaultDatasetId`, the full `resource`, …). An empty or unparsable body
  returns HTTP 400, and other failures return 500. Incoming requests are not authenticated or
  signature-checked.

## Conventions and gotchas

- **Element templates are hand-written. Don't regenerate them.** Each template has
  `"_note": "Hand-curated. Edit this file directly, don't regenerate."`. The `generate-templates`
  profile (`-Dgenerate.templates=true`) still exists in `pom.xml`, but running it overwrites all
  five files with stripped-down versions. `ApifyInboundProperties` deliberately has no
  `@TemplateProperty` annotations. When you change a DTO field, update the matching `binding.name`
  in the JSON by hand. A mismatch only shows up at runtime.
- **Template changes go live without a release.** Camunda Marketplace (SaaS) serves the templates
  straight from `main`, while JARs and the template ZIP are pinned to GitHub releases. Template
  `version` policy (`RELEASING.md`): bump the integer when the template JSON changes, don't bump it
  for JAR-only changes, and change the `:v1` id suffix only for a breaking redesign.
- **Keep the `provided` scopes.** `connector-core`, `element-template-generator-core` and the
  `httpclient5`/`httpcore5` artifacts come from `connectors-bundle`. If `httpclient5` gets shaded
  into the JAR, it shadows the runtime's copy and fails with `NoClassDefFoundError` in
  `DefaultHostnameVerifier`.
- **Keep `version.connectors` at or below the lowest supported runtime patch.** Provided deps
  resolve at runtime, so compiling against a newer SDK can break on an older runtime. Only the
  minor has to match.
- **8.8 and 8.9 need separate JARs** (Spring Boot 3 vs 4). `release.yml` builds both and adds the
  `-c8.8`/`-c8.9` suffix. Adding a Camunda minor means updating the `SDK_VERSIONS` matrix in
  `release.yml`, `COMPATIBILITY.md`, `README.md` and `CONTRIBUTING.md`, following the checklist in
  `RELEASING.md`.
- **Releases are workflow-only** ("Create a release", run from `main`). git-cliff picks the bump
  from Conventional Commits, and a service-account token pushes the `pom.xml` version and
  `CHANGELOG.md` to `main`. Don't bump the version or tag by hand. Commit messages must follow
  Conventional Commits.
- Outbound tests call private `ApifyFunction` methods by name via reflection, so renaming a
  private helper breaks `MyFunctionTest` only at run time. `ApifyClient` is constructed inline,
  so inbound tests use Mockito `mockConstruction(ApifyClient.class)`. Shared payloads live in
  `InboundTestFixtures`.
- Every request sends `Authorization: Bearer <token>` and `x-apify-integration-platform: camunda`.
  Console links in templates and README carry `utm_source=camunda&utm_medium=integrations`, so
  keep them.
- Indentation is 2 spaces in some files and 4 in others (e.g. `ApifyFunction` uses 2,
  `ApifyClient` and the inbound package use 4). Match the file you're editing.
- `ApifyInboundProperties.toString()` masks the token. Keep tokens out of logs.
