# Apify Camunda Connector

Integrate [Apify](https://apify.com/) web scraping and automation capabilities into your **Camunda 8** workflows. This connector enables you to run Actors, execute tasks, and retrieve data from Apify directly within your BPMN processes.

> **Compatibility:** Requires Camunda 8.3 or later.

## Features

**Outbound Connector** (call Apify from your process):
- **Run Actor** - Start an Apify Actor with custom input
- **Run task** - Execute a saved Actor task with optional input override
- **Get dataset items** - Retrieve data from Apify datasets
- **Get key-value store record** - Fetch stored data by key
- **Scrape single URL** - Quick web scraping for a single page

**Inbound Connectors** (trigger processes from Apify):
- **Start Event** - Start a new process when an Apify event occurs
- **Message Start Event** - Start a new process via message correlation (supports subprocesses)
- **Intermediate Catch Event** - Pause and wait for an Apify event before continuing
- **Boundary Event** - React to Apify events while an activity is running

> **Documentation:** For in-depth tutorials and detailed documentation, visit the [Apify Camunda Integration Guide](https://docs.apify.com/platform/integrations/camunda).

---

## Table of Contents

- [Authentication](#authentication)
- [Outbound Connector](#outbound-connector)
  - [Run Actor](#run-actor)
  - [Run Task](#run-task)
  - [Scrape Single URL](#scrape-single-url)
  - [Get Dataset Items](#get-dataset-items)
  - [Get Key-Value Store Record](#get-key-value-store-record)
- [Inbound Connectors](#inbound-connectors)
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
- [Contributing](#contributing)

---

## Authentication

All Apify Connector operations require an **Apify API Token**.

1. Log in to [Apify Console](https://console.apify.com/).
2. Navigate to [**Settings → Integrations**](https://console.apify.com/settings/integrations).
3. Copy your **API Token**.

> **Security Best Practice:** In Camunda, avoid hardcoding your token directly in the process design. Instead, use [**Camunda Secrets**](https://docs.camunda.io/docs/components/console/manage-clusters/manage-secrets/) (e.g., [`secrets.APIFY_TOKEN`](https://docs.camunda.io/docs/components/connectors/use-connectors/#using-secrets)) to store your API token securely.

---

## Outbound Connector

The **Apify Outbound Connector** allows your BPMN process to call out to Apify to invoke operations.

**Output Mapping:** Each outbound operation returns a JSON response. Use the **Result Variable** field (e.g., `runResult`) to store the full response, or use a **Result Expression** (FEEL) to extract specific fields into process variables (e.g., `={ runId: response.id, datasetId: response.defaultDatasetId }`).

> **Note:** Throughout this document, a leading `=` in a value denotes a FEEL expression. For example, `=runResult.id` means "evaluate the FEEL expression `runResult.id`".

### Run Actor

Start a new execution of an Actor.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Run Actor` |
| **Actor** | The Actor name or ID (e.g., `apify/hello-world` or `E2jjCZBezvAZnX8Rb`) |
| **Input Body** | The input configuration for the run (e.g., `= { "message": "Hello from Camunda!" }`) |
| **Wait for Finish** | `true` (Synchronous) or `false` (Asynchronous) |
| **Timeout (seconds)** | Maximum duration for the run (optional) |
| **Memory (MB)** | Memory allocation (optional) |
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
| **Task** | The task name or ID (e.g., `author/task-name` or `E2jjCZBezvAZnX8Rb`) |
| **Input Override** | (Optional) JSON to override the task's saved input |
| **Wait for Finish** | `true` (Synchronous) or `false` (Asynchronous) |
| **Timeout (seconds)** | Maximum duration (optional) |
| **Memory (MB)** | Memory allocation (optional) |
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
| **Dataset** | The dataset ID. Use a variable from a previous run: `=runResult.defaultDatasetId` |
| **Limit / Offset** | (Optional) Control pagination |

### Get Key-Value Store Record

Fetch a specific record from a key-value store.

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Operation** | Select `Get key-value store record` |
| **Key-Value Store** | The store ID (e.g., `=runResult.defaultKeyValueStoreId`) |
| **Key** | The record key to retrieve (e.g., `OUTPUT`) |

---

## Inbound Connectors

Inbound connectors allow Apify to start or resume your Camunda processes via webhooks.

### Start Event

Use the **Apify Start Event Connector** to begin a *new* process instance when a specific event occurs in Apify (e.g., "Run Succeeded"). This is the simplest inbound connector — each incoming webhook event creates a new top-level process instance.

**When to use:**
- Trigger a workflow based on an external event (e.g., "Every time this daily scrape finishes, start a review process")

**Configuration:**

| Setting | Description |
|---------|-------------|
| **Apify API Token** | Your Apify API token (see [Authentication](#authentication)) |
| **Resource Type** | `Actor` or `Task` |
| **Resource Identifier** | The ID to watch (e.g., `abcdefg1234`) |
| **Activation Condition** | FEEL expression to filter events (e.g., `=connectorData.status = "SUCCEEDED"`). |
| **Result Variable** | Name of the variable to store the webhook payload |
| **Result Expression** | FEEL expression to transform the data (e.g., `={ result: connectorData }`) |

### Message Start Event

Use the **Apify Message Start Event Connector** to start a process instance through message correlation. Unlike the plain Start Event, this variant uses Camunda's message correlation mechanism, which prevents duplicate instances for the same correlation key and supports starting embedded subprocesses.

**When to use:**
- You need to prevent duplicate process instances for the same run (using correlation keys)
- You want to start an embedded subprocess from an Apify event

**Configuration:**

Same base configuration as the [Start Event](#start-event), plus:

| Setting | Value Expression (FEEL) | Description |
|---------|-------------------------|-------------|
| **Correlation Key (Payload)** | `=connectorData.runId` | Extract the correlation key from the incoming webhook |
| **Message ID Expression** | (Optional) | Expression to extract a unique message ID for deduplication |

### Intermediate Catch Event

Use the **Apify Intermediate Catch Event Connector** to pause a running process and wait for a callback from Apify.

**When to use:**
- Long-running Actor (async execution) where you want to wait without blocking process engine resources
- Running tasks in parallel while waiting for a scrape to complete

**Correlation:**
To ensure the webhook resumes the *correct* process instance, configure **Correlation Keys**:

| Setting | Value Expression (FEEL) | Description |
|---------|-------------------------|-------------|
| **Correlation Key (Process)** | `=runResult.id` | The correlation key from process variables |
| **Correlation Key (Payload)** | `=connectorData.runId` | Extract the correlation key from the incoming webhook |

### Boundary Event

Use the **Apify Boundary Event Connector** to react to an Apify event while an activity is still running. A boundary event is attached to an activity (e.g., a user task or subprocess) and triggers when the specified webhook event arrives.

**When to use:**
- Cancel or redirect a running activity when an Apify run completes, fails, or times out
- Implement timeout/fallback logic — e.g., if a scrape fails, take an alternative path

**Configuration:**

Same base configuration as the [Intermediate Catch Event](#intermediate-catch-event) (including Correlation Keys). The boundary event can be **interrupting** (terminates the activity) or **non-interrupting** (allows the activity to continue).

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
   - Set **Correlation Key (Process)** to `=runResult.id`
   - Set **Correlation Key (Payload)** to `=connectorData.runId`

4. **Parallel Gateway (Join)**: Merge the branches. The process continues only when the webhook is received.

5. **Get Dataset Items**:
   - Set **Dataset** to `=runResult.defaultDatasetId`

### Boundary Event for Runtime Reactions

A [Boundary Event](https://docs.camunda.io/docs/components/modeler/bpmn/events/) attaches directly to an activity (task or subprocess) and fires when an Apify webhook arrives **while that activity is still running**. Unlike the Async Execution pattern above, the boundary event does **not** wait for the attached activity to finish — it interrupts or runs alongside it. This means any variables that the attached activity would have produced are **not available** after the boundary event fires.

This makes it suited for a different use case than async execution:

- **Interrupting boundary event** — cancel a running activity when an external signal arrives (e.g., abort a manual review task when the Apify scrape fails or times out).
- **Non-interrupting boundary event** — spawn a parallel path without stopping the activity (e.g., send a progress notification while a long-running task continues).

**Example flow (interrupting):**

```
                                ┌──(Apify Boundary Event)──→ [Handle Failure] → [End]
[Start] → [Run Actor Async] → [User Review Task]
                                └──(normal completion)─────→ [Process Results] → [End]
```

If the Apify run fails while the user review is still in progress, the boundary event interrupts the review task and redirects the flow to a failure-handling path.

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
| `=runResult.id` | Accessing the run ID from a response |
| `=runResult.defaultDatasetId` | Accessing the default dataset ID |
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

## Contributing

For development setup, local testing, and contribution guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).
