# Apify Camunda Connector

Integrate [Apify](https://apify.com/) web scraping and automation capabilities into your **Camunda** workflows. This connector enables you to run Actors, execute tasks, and retrieve data from Apify directly within your BPMN processes.

## Features

**Outbound Connector** (call Apify from your process):
- **Run Actor**: Start an Apify Actor with custom input
- **Run task**: Execute a saved Actor task with optional input override
- **Get dataset items**: Retrieve data from Apify datasets
- **Get key-value store record**: Fetch stored data by key
- **Scrape single URL**: Quick web scraping for a single page

**Inbound Connectors** (trigger processes from Apify):
- **Start Event**: Start a new process when an Apify event occurs
- **Message Start Event**: Start a new process via message correlation (supports subprocesses)
- **Intermediate Catch Event**: Pause and wait for an Apify event before continuing
- **Boundary Event**: React to Apify events while an activity is running

> **Full setup walkthrough on Apify docs:** This README is the technical reference. For a step-by-step setup tutorial with screenshots, end-to-end examples, and platform-specific guidance, see the **[Apify Camunda Integration Guide](https://docs.apify.com/platform/integrations/camunda)** at `docs.apify.com`.

---

## Table of Contents

- [Compatibility](#compatibility)
- [Installation](#installation)
  - [Deployment scenarios](#deployment-scenarios)
  - [Setting up the connectors runtime](#setting-up-the-connectors-runtime)
  - [Configuring the Camunda webhook URL](#configuring-the-camunda-webhook-url)
- [Authentication](#authentication)
- [Try it out](#try-it-out)
- [Outbound Connector](#outbound-connector)
  - [Run Actor](#run-actor)
  - [Run task](#run-task)
  - [Scrape single URL](#scrape-single-url)
  - [Get dataset items](#get-dataset-items)
  - [Get key-value store record](#get-key-value-store-record)
  - [Error Handling and Retries](#error-handling-and-retries)
- [Inbound Connectors](#inbound-connectors)
  - [Activation Condition](#activation-condition)
  - [Start Event](#start-event)
  - [Message Start Event](#message-start-event)
  - [Intermediate Catch Event](#intermediate-catch-event)
  - [Boundary Event](#boundary-event)
- [Usage Patterns](#usage-patterns)
  - [Async Execution with Parallel Gateway](#async-execution-with-parallel-gateway)
  - [Boundary Event for Runtime Reactions](#boundary-event-for-runtime-reactions)
- [Reference](#reference)
  - [Finding Resource IDs](#finding-resource-ids)
  - [Common expressions](#common-expressions)
  - [Webhook Payload Structure](#webhook-payload-structure)
  - [Event Types and Statuses](#event-types-and-statuses)
- [Troubleshooting](#troubleshooting)
- [Support](#support)
- [Marketplace listing assets](#marketplace-listing-assets)
- [Contributing](#contributing)
- [License](#license)

---

## Compatibility

| Component | Supported versions |
|---|---|
| Camunda 8 platform | 8.8.x, 8.9.x |
| Camunda Connectors runtime | 8.8.x, 8.9.x |
| Java (runtime) | 21+ |
| Apify API | Public REST API (v2), API token authentication |

> **Important:** The connector ships separate JARs per Camunda minor version (`-c8.8` and `-c8.9`).
> You must use the JAR matching your runtime's minor. See [COMPATIBILITY.md](COMPATIBILITY.md)
> for the full version matrix, configuration differences between versions, and selection guidance.

These versions apply to Self-Managed and Hybrid deployments. Pure Camunda SaaS is not currently supported - see [Deployment scenarios](#deployment-scenarios) for details.

> **Forward compatibility:** Smoke-tested against Camunda 8.10 pre-release. The compatibility matrix will be updated once 8.10 reaches GA.

---

## Installation

> **Looking for a step-by-step setup tutorial?** The walkthrough at **[docs.apify.com/platform/integrations/camunda](https://docs.apify.com/platform/integrations/camunda)** goes deeper than this section, with screenshots and end-to-end examples. This README is the technical reference.

> **Arrived from the Camunda Marketplace?** If you clicked **"For SaaS"** on the listing, the element templates are already imported into Camunda Web Modeler. You still need to install the connector JAR on a connectors runtime - continue below.

Each release publishes two artifacts on the [GitHub Releases page](https://github.com/apify/apify-camunda-integration/releases):

| Artifact | What it is | Where it goes |
|---|---|---|
| `apify-camunda-connector-<version>.jar` | The shaded connector runtime JAR (includes all dependencies) | Drop it on the Camunda Connectors runtime classpath. See [Setting up the connectors runtime](#setting-up-the-connectors-runtime) below. |
| `apify-camunda-connector-element-templates-<version>.zip` | All five element template JSONs (one outbound + four inbound) | Upload to your Camunda **Web Modeler** project, or place into the `resources/element-templates/` directory of **Desktop Modeler**. After publishing, the connectors appear in the BPMN palette. |

Pick the latest release whose version matches the [Compatibility](#compatibility) row for your Camunda 8 minor.

### Deployment scenarios

The connector ships custom Java code that runs on the Camunda Connectors runtime. Where that runtime lives determines which deployment path applies:

| Scenario | Supported? | Notes |
|---|---|---|
| **Self-Managed** | Yes | Full functionality - you control the connectors runtime on your own infrastructure. |
| **Hybrid** | Yes | Full functionality - Zeebe runs on Camunda SaaS, but you host the connectors runtime yourself. |
| **Pure SaaS** | No | Custom connectors require hosting the JAR on your own runtime. Consider [Hybrid mode](https://docs.camunda.io/docs/components/connectors/use-connectors-in-hybrid-mode/) as an alternative. |

### Setting up the connectors runtime

The simplest path is Camunda's official `camunda/connectors-bundle` Docker image with our JAR mounted into it. From [Host custom connectors](https://docs.camunda.io/docs/guides/host-custom-connectors/):

```bash
docker run --rm \
  -v $PWD/apify-camunda-connector-<version>.jar:/opt/app/connector.jar \
  camunda/connectors-bundle:8.9.0
```

> Substitute `<version>` with the release tag of the JAR you downloaded (e.g., `1.0.0`). The `camunda/connectors-bundle` tag should match your Camunda 8 minor — `8.9.0` for an 8.9 cluster, `8.8.0` for 8.8, and so on.

**Hybrid mode** (your connectors runtime + Camunda SaaS Zeebe): pass your cluster credentials as environment variables alongside the JAR mount. Find the cluster ID, region, and client credentials in Camunda Console under your cluster → **API** tab.

```bash
docker run --rm \
  -v $PWD/apify-camunda-connector-<version>.jar:/opt/app/connector.jar \
  -e CAMUNDA_CLIENT_MODE=saas \
  -e CAMUNDA_CLIENT_CLOUD_CLUSTERID='<YOUR_CLUSTER_ID>' \
  -e CAMUNDA_CLIENT_CLOUD_REGION='<YOUR_REGION>' \
  -e CAMUNDA_CLIENT_AUTH_CLIENTID='<YOUR_CLIENT_ID>' \
  -e CAMUNDA_CLIENT_AUTH_CLIENTSECRET='<YOUR_CLIENT_SECRET>' \
  camunda/connectors-bundle:8.9.0
```

The client credentials need the **Orchestration Cluster REST API** scope at minimum. For the full reference (including scopes, alternate auth modes, and the `CONNECTOR_HTTP_REST_TYPE` override for local debugging), see [Use connectors in hybrid mode](https://docs.camunda.io/docs/components/connectors/use-connectors-in-hybrid-mode/).

For production, bake the JAR into a custom image instead of mounting it:

```dockerfile
FROM camunda/connectors-bundle:8.9.0
COPY apify-camunda-connector-<version>.jar /opt/app/connector.jar
```

> **Reachability requirement:** The connectors-runtime container must be reachable on the public internet so Apify can deliver webhook events to it. For production, expose it via your ingress / reverse proxy / load balancer with a stable HTTPS URL.
>
> **Testing locally?** Use a tunneling tool like [ngrok](https://ngrok.com/) to expose your local connectors-runtime container to the internet:
>
> ```bash
> ngrok http <runtime-port>
> ```
>
> Paste the ngrok URL into the **Camunda webhook URL** field on each inbound element template. The connector takes care of registering and tearing down the Apify-side webhook automatically on BPMN deploy/undeploy.

### Configuring the Camunda webhook URL

The connector handles the Apify-side webhook lifecycle for you: when the BPMN is deployed, it calls Apify's API to create the webhook; when the inbound is deactivated, the webhook is removed. The only thing **you** must tell the connector is its own public address.

Each inbound element template has a required **Camunda webhook URL** field. The connector uses it to register the callback on the Apify side at deploy time. Apify will POST Actor events to this URL.

| Environment | What to put in the field |
|---|---|
| **Self-Managed** | The public URL of your connector runtime, e.g. `https://camunda-connectors.example.com`. |
| **Hybrid** | The public URL of your self-hosted connector runtime. Find your cluster details in Camunda Console → your cluster → API tab. See [Use connectors in hybrid mode](https://docs.camunda.io/docs/components/connectors/use-connectors-in-hybrid-mode/). |

You paste just the base URL without a trailing slash. The connector appends `/inbound/{webhookId}` automatically when it registers the webhook with Apify.

> **Tip:** After deploying your BPMN diagram in **Web Modeler**, click on the inbound event element and open the **Webhooks** tab in the properties panel. It displays the complete, ready-to-use URL for your cluster. See the [HTTP Webhook connector docs](https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#activate-the-http-webhook-connector-by-deploying-your-diagram) for details.

For a deeper explanation of how inbound webhook URLs are structured in Camunda, see [Use an inbound connector](https://docs.camunda.io/docs/components/connectors/use-connectors/inbound/) and [HTTP Webhook connector](https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/) in the Camunda docs.

> **Convenience tip:** The URL is the same for every BPMN process on a given cluster, so you can store it as a [Camunda Secret](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) (e.g., `CAMUNDA_WEBHOOK_URL`) and reference it from each inbound template as `{{secrets.CAMUNDA_WEBHOOK_URL}}`. That way you only update one place when your cluster moves or your dev URL rotates. This is *convenience*, not security. The URL is not sensitive.

---

## Authentication

All Apify Connector operations require an **Apify Token**.

1. Log in to [Apify Console](https://console.apify.com/).
2. Navigate to [**Settings → Integrations**](https://console.apify.com/settings/integrations).
3. Copy your **Apify Token**.

> **Security Best Practice:** In Camunda, avoid hardcoding your token directly in the process design. Instead, use [**Camunda Secrets**](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) (e.g., [`{{secrets.APIFY_TOKEN}}`](https://docs.camunda.io/docs/components/connectors/use-connectors/#using-secrets)) to store your API token securely.
>
> **Camunda 8.9+ secret prefix:** The environment-based secret provider now requires a `SECRET_` prefix by default. Set your token as `SECRET_APIFY_TOKEN=<value>` (not `APIFY_TOKEN`). The `{{secrets.APIFY_TOKEN}}` reference in element templates stays the same. To customize or disable the prefix, see [COMPATIBILITY.md](COMPATIBILITY.md#connector-secrets).

### Security model

The Apify API token is user-supplied through the **Apify API token** field on each element template; the connector does not collect, request, or generate any credentials of its own. Operationally:

- **Storage:** The token lives in the Camunda process where the designer placed it. Using a [Camunda Secret](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) keeps it out of the BPMN XML.
- **Logging:** The connector never logs the token, neither in plain text nor hashed, at any log level.
- **Persistence:** The connector does not write the token to disk, to local state, or to any store outside the Camunda process.
- **Transport:** The token is sent only to `api.apify.com` over HTTPS with TLS 1.2 or higher. It is never sent to any third-party endpoint.
- **Webhook URL field:** The **Camunda webhook URL** value is treated as configuration, not as a credential, since it is the public address of your own Camunda runtime.

For vulnerability disclosure, see [SECURITY.md](SECURITY.md).

---

## Try it out

A 30-second smoke test that confirms the connector is wired up end-to-end. Uses the public [`apify/hello-world`](https://apify.com/apify/hello-world) Actor, which completes in under a minute and needs no special configuration.

1. In your Camunda Modeler project, upload the outbound element template (`apify-outbound-connector.json`) and **publish** it to the project.
2. Create a new BPMN diagram with a single **Service Task** between Start and End events.
3. Apply the **Apify Outbound Connector** template to the service task and set:

   | Field | Value |
   |---|---|
   | **Apify API Token** | Your token (or `{{secrets.APIFY_TOKEN}}` if you have a Camunda secret) |
   | **Operation** | `Run Actor` |
   | **Actor** | `apify/hello-world` |
   | **Wait for Finish** | `true` |
   | **Result Variable** | `runResult` |

4. **Deploy and run** the process.
5. Open Camunda Operate and confirm the process reached the End event. The full Actor run response is available under the `runResult` process variable.

If anything goes wrong, see [Troubleshooting](#troubleshooting) - the most common first-time issue is that the connector template is not visible in the Modeler palette. For a longer walkthrough with screenshots, see the [Apify Camunda Integration Guide](https://docs.apify.com/platform/integrations/camunda).

---

## Outbound Connector

The **Apify Outbound Connector** allows your BPMN process to call out to Apify to invoke operations.

**Output Mapping:** Each outbound operation returns a JSON response. Use the **Result Variable** field (e.g., `runResult`) to store the full response, or use a **Result Expression** (FEEL) to extract specific fields into process variables (e.g., `={ runId: response.data.id, datasetId: response.data.defaultDatasetId }`).

> **Note:** Throughout this document, a leading `=` in a value denotes a FEEL expression. For example, `=runResult.data.id` means "evaluate the FEEL expression `runResult.data.id`".

### Run Actor

Start a new execution of an Actor.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Run Actor` |
| **Actor** | The Actor name or ID (e.g., `apify/web-scraper` or `E2jjCZBezvAZnX8Rb`) |
| **Input Body** | *(Optional)* JSON input configuration for the run (e.g., `={ "message": "Hello from Camunda!" }`) |
| **Wait for Finish** | `true` (Synchronous) or `false` (Asynchronous) |
| **Timeout (seconds)** | Maximum duration for the run (optional) |
| **Memory (MB)** | Memory allocation (optional). Dropdown: 128, 256, 512, 1024, 2048, 4096, 8192, 16384, or 32768 MB |
| **Build** | Build tag to use (optional, defaults to `latest`) |

**Wait for Finish options:**
- `true` (Synchronous): The process waits until the Actor run completes. Use for short-running tasks.
- `false` (Asynchronous): The process starts the run and immediately moves to the next step. Use for long-running scrapes or with [Intermediate Catch Events](#intermediate-catch-event).

### Run task

Execute a saved Actor task.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Run task` |
| **Task** | The task name or ID (e.g., `username/my-task` or `abc123DEF456`) |
| **Input Override** | *(Optional)* JSON to override the task's saved input |
| **Wait for Finish** | `true` (Synchronous) or `false` (Asynchronous) |
| **Timeout (seconds)** | Maximum duration (optional) |
| **Memory (MB)** | Memory allocation (optional). Dropdown: 128, 256, 512, 1024, 2048, 4096, 8192, 16384, or 32768 MB |
| **Build** | Build tag to use (optional, defaults to `latest`) |

### Scrape single URL

Quickly scrape a webpage using one of Apify's standard crawlers.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Scrape single URL` |
| **URL** | The full URL to scrape (e.g., `https://example.com`) |
| **Crawler Type** | `Cheerio (Raw HTTP)`, `Adaptive`, or `Firefox (Headless Browser)` |

### Get dataset items

Retrieve the results of an Actor run. Typically used after a `Run Actor` task has completed.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Get dataset items` |
| **Dataset** | The dataset ID. Use a variable from a previous run: `=runResult.data.defaultDatasetId` |
| **Offset** | *(Optional)* Number of items to skip from the beginning. Default: `0` |
| **Limit** | *(Optional)* Maximum number of items to return. Default: no limit |

### Get key-value store record

Fetch a specific record from a key-value store.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Get key-value store record` |
| **Key-Value Store** | The store ID (e.g., `=runResult.data.defaultKeyValueStoreId`) |
| **Key** | The record key to retrieve (e.g., `OUTPUT`) |

### Error Handling and Retries

All outbound operations support error handling and automatic retries. These fields appear in the Modeler under the **Error handling** and **Retries** groups.

| Setting | Description |
|---------|-------------|
| **Error Expression** | *(Optional)* A FEEL expression to handle errors (e.g., `if error.code = "ACTOR_NOT_FOUND" then null else error`) |
| **Retries** | Number of retry attempts. Default: `3` |
| **Retry Backoff** | ISO-8601 duration to wait between retries. Default: `PT0S` (no delay). Example: `PT5S` for 5 seconds |

---

## Inbound Connectors

Inbound connectors allow Apify to start or resume your Camunda processes via webhooks.

All inbound connectors share these common fields:

| Setting | Description |
|---------|-------------|
| **Apify API Token** | Your Apify API token (see [Authentication](#authentication)) |
| **Resource Type** | `Actor` or `Task` |
| **Actor** / **Task** | The Actor or task name or ID to monitor (e.g., `apify/web-scraper` or `E2jjCZBezvAZnX8Rb`). The field label changes based on the selected Resource Type. |
| **Activation Condition** | *(Optional)* FEEL expression to filter events (e.g., `=connectorData.status = "SUCCEEDED"`). Leave empty to process all events. |
| **Result Variable** | *(Optional)* Variable name to store the webhook payload |
| **Result Expression** | *(Optional)* FEEL expression to transform the data (e.g., `={ result: connectorData }`) |

### Activation Condition

The **Activation Condition** is an optional FEEL expression that acts as a gate for incoming webhook events. When set, the connector evaluates the expression against each incoming event and only triggers the process if the expression evaluates to `true`. Events that do not match are silently ignored: no process instance is created and no correlation occurs.

This is useful when you subscribe to all event types from an Actor or Task but only want to react to specific outcomes. For example, you might want to start a process only when a run succeeds and ignore failures, timeouts, and aborts.

**Examples:**

| Expression | Effect |
|------------|--------|
| *(empty)* | All events trigger the connector (default) |
| `=connectorData.status = "SUCCEEDED"` | Only successful runs trigger the connector |
| `=connectorData.status != "ABORTED"` | All events except aborted runs trigger the connector |
| `=connectorData.eventType = "ACTOR.RUN.FAILED" or connectorData.eventType = "ACTOR.RUN.TIMED_OUT"` | Only failures and timeouts trigger the connector |

> **Tip:** The expression has access to the full `connectorData` object described in the [Webhook Payload Structure](#webhook-payload-structure) section. You can filter on any field, including `status`, `eventType`, `actorId`, or `runId`. For more details on webhook dispatch events and available fields, see the Apify client docs: [JavaScript](https://docs.apify.com/api/client/js/reference/interface/WebhookDispatch) | [Python](https://docs.apify.com/api/client/python/reference/class/WebhookDispatch).

### Start Event

Use the **Apify Start Event Connector** to begin a *new* process instance when a specific event occurs in Apify (e.g., "Run Succeeded"). This is the simplest inbound connector: each incoming webhook event creates a new top-level process instance.

**When to use:**
- Trigger a workflow based on an external event (e.g., "Every time this daily scrape finishes, start a review process")

**Configuration:**

Uses the [common inbound fields](#inbound-connectors) listed above. No additional fields are required.

### Message Start Event

Use the **Apify Message Start Event Connector** to start a process instance through message correlation. Unlike the plain Start Event, this variant uses Camunda's message correlation mechanism, which prevents duplicate instances for the same correlation key and supports starting embedded subprocesses.

**When to use:**
- You need to prevent duplicate process instances for the same run (using correlation keys)
- You want to start an embedded subprocess from an Apify event

**Configuration:**

Uses the [common inbound fields](#inbound-connectors), plus:

| Setting | Description |
|---------|-------------|
| **Subprocess Correlation Required** | Select `Correlation not required` (default) or `Correlation required`. When set to required, the Correlation Key fields below become visible. This is needed for event-based subprocess message start events. |
| **Correlation Key (Process)** | *(Shown when correlation is required)* FEEL expression for the correlation key from process variables (e.g., `=previousEventResponse.data.id`) |
| **Correlation Key (Payload)** | *(Shown when correlation is required)* FEEL expression to extract the correlation key from the incoming webhook (e.g., `=connectorData.runId`) |
| **Message ID Expression** | *(Optional)* Expression to extract a unique ID from the webhook payload for deduplication (e.g., `=connectorData.eventData.actorRunId`) |
| **Message TTL** | *(Optional)* Time-to-live for the message in the broker as an ISO-8601 duration (e.g., `PT1H` for 1 hour) |

### Intermediate Catch Event

Use the **Apify Intermediate Catch Event Connector** to pause a running process and wait for a callback from Apify.

**When to use:**
- Long-running Actor (async execution) where you want to wait without blocking process engine resources
- Running tasks in parallel while waiting for a scrape to complete

**Configuration:**

Uses the [common inbound fields](#inbound-connectors), plus:

| Setting | Description |
|---------|-------------|
| **Correlation Key (Process)** | FEEL expression for the correlation key from process variables (e.g., `=runResult.data.id`) |
| **Correlation Key (Payload)** | FEEL expression to extract the correlation key from the incoming webhook (e.g., `=connectorData.runId`) |
| **Message ID Expression** | *(Optional)* Expression to extract a unique ID from the webhook payload for deduplication |
| **Message TTL** | *(Optional)* Time-to-live for the message in the broker as an ISO-8601 duration (e.g., `PT1H`) |

To ensure the webhook resumes the *correct* process instance, the **Correlation Key (Process)** value must exactly match the **Correlation Key (Payload)** value extracted from the incoming webhook.

### Boundary Event

Use the **Apify Boundary Event Connector** to react to an Apify event while an activity is still running. A boundary event is attached to an activity (e.g., a user task or subprocess) and triggers when the specified webhook event arrives.

**When to use:**
- Cancel or redirect a running activity when an Apify run completes, fails, or times out
- Implement timeout/fallback logic, e.g., if a scrape fails, take an alternative path

**Configuration:**

Same configuration as the [Intermediate Catch Event](#intermediate-catch-event) (common inbound fields plus Correlation Keys, Message ID Expression, and Message TTL). The boundary event can be **interrupting** (terminates the activity) or **non-interrupting** (allows the activity to continue).

---

## Usage Patterns

### Async Execution with Parallel Gateway

This is the recommended pattern for handling long-running scrapes reliably. It prevents timeout issues and allows other tasks while waiting.

**Flow:**

```
                              ┌───→ [Other Tasks (Optional)] ────┐
[Start] → [Run Actor Async] → [Fork]                           [Join] → [Get Dataset] → [End]
                              └───→ [Wait for Webhook] ──────────┘
```

**Steps:**

1. **Run Actor (Async)**:
   - Select `Run Actor` or `Run task`
   - Set **Wait for Finish** to `false`
   - Save the response to a result variable (e.g., `runResult`)

2. **Parallel Gateway (Fork)**: Split the flow immediately after the run starts.

3. **Apify Intermediate Catch Event**:
   - Set **Correlation Key (Process)** to `=runResult.data.id`
   - Set **Correlation Key (Payload)** to `=connectorData.runId`

4. **Parallel Gateway (Join)**: Merge the branches. The process continues only when the webhook is received.

5. **Get dataset items**:
   - Set **Dataset** to `=runResult.data.defaultDatasetId`

### Boundary Event for Runtime Reactions

A [Boundary Event](https://docs.camunda.io/docs/components/modeler/bpmn/events/) attaches directly to an activity (task or subprocess) and fires when an Apify webhook arrives **while that activity is still running**. Unlike the Async Execution pattern above, the boundary event does **not** wait for the attached activity to finish, it interrupts or runs alongside it. This means any variables that the attached activity would have produced are **not available** after the boundary event fires.

This makes it suited for a different use case than async execution:

- **Interrupting boundary event**: cancel a running activity when an external signal arrives (e.g., abort a long-running scrape when a validation check fails or times out).
- **Non-interrupting boundary event**: spawn a parallel path without stopping the activity (e.g., send a progress notification while a long-running scrape continues).

**Example flow (interrupting):**

```
                                    ┌──(Apify Boundary Event)──→ [Handle Failure] → [End]
[Start] → [Run Actor Async] → [Run Large Scrape]
                                    └──(normal completion)─────→ [Process Results] → [End]
```

If the async Actor run fails while the large scrape is still running, the boundary event interrupts the scrape and redirects the flow to a failure-handling path.

> **Tip:** If you need the run results (dataset, key-value store) after the Apify event, use the [Async Execution with Parallel Gateway](#async-execution-with-parallel-gateway) pattern instead. The boundary event pattern is best when you want to **react** to an event (failure, timeout, status change) rather than **collect** its output.

---

## Reference

### Finding Resource IDs

You can find IDs in the [Apify Console](https://console.apify.com/):

- **Actor ID**: `https://console.apify.com/actors/<THIS_IS_THE_ID>` or see the API tab
- **Task ID**: `https://console.apify.com/actors/tasks/<THIS_IS_THE_ID>` or see the API tab
- **Dataset ID**: Found in the Storage section or run details

### Common expressions

The connector templates accept two distinct syntaxes that look similar but are evaluated at different stages by different components: **FEEL expressions** and **secret placeholders**.

**FEEL expressions** (prefix: `=`) are evaluated by the Zeebe engine *before* the connector runs. The leading `=` tells the engine to parse the field as FEEL (Friendly Enough Expression Language) and resolve it to a concrete value. Use FEEL to reference process variables, filter events, or build output mappings.

| FEEL expression | Use case |
|---|---|
| `=runResult.data.id` | Access the run ID from a previous response |
| `=runResult.data.defaultDatasetId` | Access the default dataset ID |
| `=connectorData.status` | Read the status from an inbound webhook payload |
| `=connectorData.runId` | Read the run ID from an inbound webhook payload |

**Secret placeholders** (syntax: `{{secrets.NAME}}`) are *not* FEEL. The engine passes them through as literal strings and the **connector runtime** substitutes them at execution time, just before the outbound HTTP call. This keeps the secret value out of process variables, audit logs, and incident messages. Paste a placeholder directly into the corresponding template field - no `=` prefix, no surrounding quotes. The connector reads `{{secrets.APIFY_TOKEN}}` from the **Apify API token** field and resolves it at runtime.

| Placeholder | Use case |
|---|---|
| `{{secrets.APIFY_TOKEN}}` | Reference a stored Apify API token |
| `{{secrets.CAMUNDA_WEBHOOK_URL}}` | Reference a stored Camunda webhook URL |

See [Manage Secrets](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) for how to create and store secrets in your Camunda cluster.

### Webhook Payload Structure

When an Apify inbound connector is triggered, it receives a payload with event and run information.

**Top-Level Structure:**

- `connectorData`: Simplified object with the most important fields
- `request`: The raw webhook request including headers and body
- `connectorData.resource`: The full Actor Run object

**Payload Example:**

```json
{
  "connectorData": {
    "eventType": "ACTOR.RUN.SUCCEEDED",
    "userId": "user1234",
    "createdAt": "2026-01-03T12:00:00.000Z",
    "runId": "efgh5678",
    "status": "SUCCEEDED",
    "actorId": "abcd1234",
    "defaultDatasetId": "d9E0f1G2h3I4j5K6",
    "eventData": {
      "actorId": "abcd1234",
      "actorRunId": "efgh5678"
    },
    "resource": {
      "id": "efgh5678",
      "status": "SUCCEEDED",
      "stats": { "..." },
      "options": { "..." },
      "usage": { "..." }
    }
  },
  "request": {
    "body": {
      "eventType": "ACTOR.RUN.SUCCEEDED",
      "resource": { "..." }
    },
    "headers": { "..." }
  }
}
```

### Event Types and Statuses

**Event Types (`eventType`):**

- `ACTOR.RUN.CREATED`: A new Actor run has been created
- `ACTOR.RUN.SUCCEEDED`: Run finished with status `SUCCEEDED`
- `ACTOR.RUN.FAILED`: Run finished with status `FAILED`
- `ACTOR.RUN.ABORTED`: Run finished with status `ABORTED`
- `ACTOR.RUN.TIMED_OUT`: Run finished with status `TIMED-OUT`
- `ACTOR.RUN.RESURRECTED`: Run has been resurrected (moved back to `RUNNING`)

**Run Statuses (`status`):**

- `SUCCEEDED`
- `FAILED`
- `ABORTED`
- `TIMED-OUT`

> **Note:** The event type uses an underscore (`TIMED_OUT`) while the run status uses a hyphen (`TIMED-OUT`). This is how the Apify API returns these values.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Apify connector not visible in the Modeler palette | Confirm the element template was both **uploaded and published** to your Modeler project (publishing is a separate step from upload). Make sure you are looking at the correct project. If the templates are present but the service task fails on deploy, the JAR is not loaded on your connectors runtime - see [Setting up the connectors runtime](#setting-up-the-connectors-runtime). |
| Webhook not triggering | Ensure you have deployed the process. For Start Events, deploying automatically creates the webhook in Apify. Check the **Integrations** tab of your Actor in Apify Console to verify the webhook exists. |
| Process stuck at Intermediate Event | Check your **Correlation Keys**. The value in the process variable must *exactly* match the value in the webhook payload. Use Camunda Operate to inspect variable values. |
| `401 Unauthorized` | Check your API Token. Regenerate it in Apify Console (Settings → Integrations) if necessary. |
| `Connection refused` on `localhost:8080` | The connector runtime expects the Camunda orchestration API on port `8080`. If you run Camunda via Docker Compose, ensure the orchestration service maps to host port `8080`. Only run **one Camunda version at a time** — run `docker compose down` before switching between versions. |

---

## Support

This connector is maintained by **Apify**. Camunda disclaims any support obligation for it; please contact Apify directly using the channels below.

| Channel | Use for |
|---|---|
| [GitHub Issues](https://github.com/apify/apify-camunda-integration/issues) | Bug reports, feature requests, configuration questions |
| [Apify integration docs](https://docs.apify.com/platform/integrations/camunda) | Tutorials, walkthroughs, payload reference |
| [Apify Discord](https://discord.com/invite/jyEM2PRvMU) | Community discussion |
| [integrations@apify.com](mailto:integrations@apify.com) | Direct support inquiries |
| [SECURITY.md](SECURITY.md) | Private vulnerability disclosure |

For security-related issues, please follow the disclosure process in [SECURITY.md](SECURITY.md) instead of opening a public issue.

---

## Marketplace listing assets

Logos and screenshots for the Camunda Marketplace submission live in `docs/assets/`:

| File | Purpose |
|---|---|
| `logo/apify-camunda-listing.png` | Marketplace "App Listing" logo |
| `logo/apify-camunda-profile.png` | Marketplace "App Profile" logo |
| `logo/apify-symbol-source.svg` | Source SVG ([apify.com/resources/brand](https://apify.com/resources/brand)) |
| `screenshots/01-bpmn-actor-finished-dataset-items-loop-ai-extract-crm-upsert.png` | Actor finishes → loop dataset items → AI extract → CRM upsert |
| `screenshots/02-bpmn-parallel-actors-lead-enrichment-ai-summarize-crm-update.png` | Parallel Actors scrape → AI summarize lead → CRM update |

---

## Contributing

For development setup, local testing, and contribution guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

Released under the Apache License, Version 2.0. See [LICENSE.md](LICENSE.md).
