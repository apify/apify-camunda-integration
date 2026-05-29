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

> **Documentation:** For in-depth tutorials and detailed documentation, visit the [Apify Camunda Integration Guide](https://docs.apify.com/platform/integrations/camunda).

---

## Table of Contents

- [Compatibility](#compatibility)
- [Authentication](#authentication)
- [Outbound Connector](#outbound-connector)
  - [Run Actor](#run-actor)
  - [Run Task](#run-task)
  - [Scrape Single URL](#scrape-single-url)
  - [Get Dataset Items](#get-dataset-items)
  - [Get Key-Value Store Record](#get-key-value-store-record)
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
  - [Common FEEL Expressions](#common-feel-expressions)
  - [Webhook Payload Structure](#webhook-payload-structure)
  - [Event Types and Statuses](#event-types-and-statuses)
- [Troubleshooting](#troubleshooting)
- [Support](#support)
- [Contributing](#contributing)
- [License](#license)

---

## Compatibility

| Component | Supported versions |
|---|---|
| Camunda 8 | 8.8.x, 8.9.x, 8.10.x (SaaS and Self-Managed) |
| Camunda Connectors runtime | 8.8.x, 8.9.x, 8.10.x |
| Java (runtime) | 21+ |
| Apify API | Public REST API (v2), API token authentication |

### Deployment matrix

| Capability | Camunda SaaS | Self-Managed | Hybrid |
|---|---|---|---|
| Outbound (runActor, runTask, getDatasetItems, scrapeSingleUrl, getKeyValueStoreRecord) | Yes | Yes | Yes |
| Inbound (auto-registers the webhook in Apify on deploy) | Yes | Yes | Yes |

The connector handles the Apify-side webhook lifecycle for you: when the BPMN is deployed, it calls Apify's API to create the webhook; when the inbound is deactivated, the webhook is removed. The only thing **you** must tell the connector is its own public address. See the next section.

### Configuring the **Camunda webhook URL**

Each inbound element template has a required **Camunda webhook URL** field. The connector uses it to register the callback on the Apify side at deploy time. Apify will POST Actor events to this URL. The same flow applies on Camunda SaaS, Self-Managed, and Hybrid.

| Environment | What to put in the field |
|---|---|
| **Camunda SaaS** | `https://{region}.connectors.camunda.io/{clusterId}`. Find your region and cluster ID in Camunda Console → your cluster → API tab. Example: `https://bru-2.connectors.camunda.io/abc-123-cluster-id`. |
| **Self-Managed** | The public URL of your connector runtime, e.g. `https://camunda-connectors.example.com`. |
| **Hybrid** | The public URL of your self-hosted hybrid runtime (same as Self-Managed). |

You paste just the base. The connector appends `/inbound/{webhookId}` automatically when it registers the webhook with Apify.

> **Convenience tip:** The URL is the same for every BPMN process on a given cluster, so you can store it as a [Camunda Secret](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) (e.g., `CAMUNDA_WEBHOOK_URL`) and reference it from each inbound template as `{{secrets.CAMUNDA_WEBHOOK_URL}}`. That way you only update one place when your cluster moves or your dev URL rotates. This is *convenience*, not security. The URL is not sensitive.

The connector is built against the Camunda Connectors SDK at the version pinned in [pom.xml](pom.xml). The compatibility matrix above lists the Camunda 8 minor versions the connector has been verified against; support for newer minors is added once verified and reflected here.

---

## Authentication

All Apify Connector operations require an **Apify Token**.

1. Log in to [Apify Console](https://console.apify.com/).
2. Navigate to [**Settings → Integrations**](https://console.apify.com/settings/integrations).
3. Copy your **Apify Token**.

> **Security Best Practice:** In Camunda, avoid hardcoding your token directly in the process design. Instead, use [**Camunda Secrets**](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) (e.g., [`secrets.APIFY_TOKEN`](https://docs.camunda.io/docs/components/connectors/use-connectors/#using-secrets)) to store your API token securely.

### Security model

The Apify API token is user-supplied through the **Apify API token** field on each element template; the connector does not collect, request, or generate any credentials of its own. Operationally:

- **Storage:** The token lives in the Camunda process where the designer placed it. Using a [Camunda Secret](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) keeps it out of the BPMN XML.
- **Logging:** The connector never logs the token, neither in plain text nor hashed, at any log level.
- **Persistence:** The connector does not write the token to disk, to local state, or to any store outside the Camunda process.
- **Transport:** The token is sent only to `api.apify.com` over HTTPS with TLS 1.2 or higher. It is never sent to any third-party endpoint.
- **Webhook URL field:** The **Camunda webhook URL** value is treated as configuration, not as a credential, since it is the public address of your own Camunda runtime.

For vulnerability disclosure, see [SECURITY.md](SECURITY.md).

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

### Run Task

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

### Scrape Single URL

Quickly scrape a webpage using one of Apify's standard crawlers.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Scrape single URL` |
| **URL** | The full URL to scrape (e.g., `https://example.com`) |
| **Crawler Type** | `Cheerio` (lightweight), `JSDOM`, `Playwright Adaptive`, or `Playwright Firefox` |

### Get Dataset Items

Retrieve the results of an Actor run. Typically used after a `Run Actor` task has completed.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Get dataset items` |
| **Dataset** | The dataset ID. Use a variable from a previous run: `=runResult.data.defaultDatasetId` |
| **Offset** | *(Optional)* Number of items to skip from the beginning. Default: `0` |
| **Limit** | *(Optional)* Maximum number of items to return. Default: no limit |

### Get Key-Value Store Record

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

The **Activation Condition** is an optional FEEL expression that acts as a gate for incoming webhook events. When set, the connector evaluates the expression against each incoming event and only triggers the process if the expression evaluates to `true`. Events that do not match are silently ignored, no process instance is created and no correlation occurs.

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

Use the **Apify Start Event Connector** to begin a *new* process instance when a specific event occurs in Apify (e.g., "Run Succeeded"). This is the simplest inbound connector, each incoming webhook event creates a new top-level process instance.

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

5. **Get Dataset Items**:
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

### Common FEEL Expressions

Camunda uses FEEL (Friendly Enough Expression Language) for dynamic values. The leading `=` in each expression tells Camunda to evaluate what follows as a FEEL expression rather than a literal string.

| Expression | Use Case |
|------------|----------|
| `=secrets.APIFY_TOKEN` | Accessing a secure credential |
| `=runResult.data.id` | Accessing the run ID from a response |
| `=runResult.data.defaultDatasetId` | Accessing the default dataset ID |
| `=connectorData.status` | Reading the status from inbound webhook payload |
| `=connectorData.runId` | Reading the Run ID from inbound webhook payload |

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
| Webhook not triggering | Ensure you have deployed the process. For Start Events, deploying automatically creates the webhook in Apify. Check the **Integrations** tab of your Actor in Apify Console to verify the webhook exists. |
| Process stuck at Intermediate Event | Check your **Correlation Keys**. The value in the process variable must *exactly* match the value in the webhook payload. Use Camunda Operate to inspect variable values. |
| `401 Unauthorized` | Check your API Token. Regenerate it in Apify Console (Settings → Integrations) if necessary. |

---

## Support

This connector is maintained by **Apify**. Camunda disclaims any support obligation for it; please contact Apify directly using the channels below.

| Channel | Use for |
|---|---|
| [GitHub Issues](https://github.com/apify/apify-camunda-integration/issues) | Bug reports, feature requests, configuration questions |
| [Apify integration docs](https://docs.apify.com/platform/integrations/camunda) | Tutorials, walkthroughs, payload reference |
| [Apify Discord](https://discord.com/invite/jyEM2PRvMU) | Community discussion |
| [SECURITY.md](SECURITY.md) | Private vulnerability disclosure |

For security-related issues, please follow the disclosure process in [SECURITY.md](SECURITY.md) instead of opening a public issue.

**Support targets** (best-effort, per the Camunda Marketplace certification program):
- Acknowledge support queries escalated by Camunda within **7 business days**.
- Resolve customer technical issues within **10 business days**.

---

## Contributing

For development setup, local testing, and contribution guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

Released under the Apache License, Version 2.0. See [LICENSE.md](LICENSE.md).
