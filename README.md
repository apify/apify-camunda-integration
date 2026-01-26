# Apify Camunda Connector

Integrate [Apify](https://apify.com/) web scraping and automation capabilities into your **Camunda 8** workflows. This connector enables you to run Actors, execute tasks, and retrieve data from Apify directly within your BPMN processes.

## Features

**Outbound Operations** (call Apify from your process):
- **Run Actor** - Start an Apify Actor and get results
- **Run Task** - Execute a saved Actor task
- **Get Dataset Items** - Retrieve data from Apify datasets
- **Get Key-Value Store Record** - Fetch stored data by key
- **Scrape Single URL** - Quick web scraping for a single page

**Inbound Operations** (trigger processes from Apify):
- **Start Event** - Start a new process when an Apify webhook fires
- **Intermediate Event** - Pause and wait for an Apify webhook before continuing

---

## Table of Contents

- [Quick Start](#quick-start)
- [Running the Connector](#running-the-connector)
- [Using the Outbound Connector](#using-the-outbound-connector)
- [Using the Inbound Connectors](#using-the-inbound-connectors)
  - [Start Event](#start-event)
  - [Intermediate Event](#intermediate-event)
- [Reference](#reference)
  - [Camunda Architecture](#camunda-architecture)
  - [Service URLs](#service-urls)
  - [Keycloak Configuration](#keycloak-configuration)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

### Prerequisites

- **Java 21** or later
- **Maven 3.8+**
- **Docker** and **Docker Compose**

### 1. Start Camunda Stack

Follow the [Camunda Docker Compose quickstart](https://docs.camunda.io/docs/self-managed/quickstart/developer-quickstart/docker-compose) to spin up the full stack locally.

> **Note:** Install the FULLY configured stack which includes Web Modeler. This connector was tested with [Camunda 8.8](https://github.com/camunda/camunda-distributions/releases/tag/docker-compose-8.8).

### 2. Clone and Build

```bash
git clone https://github.com/apify/apify-camunda-integration.git
cd apify-camunda-integration
mvn clean package
```

### 3. Run the Connector

```bash
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test
```

### 4. Open Web Modeler

Go to http://localhost:8070/ (credentials: `demo` / `demo`) and start creating processes with the Apify connector!

---

## Running the Connector

This section covers how to run the connector locally. The same command is used for both outbound and inbound connectors.

### Environment Variables

For **inbound connectors** (webhooks), you must set `CONNECTOR_BASE_URL` so Apify knows where to send webhook events.

**Option A: Placeholder URL (for initial setup)**

```bash
export CONNECTOR_BASE_URL=http://example.com
```

You can update the webhook URL in Apify later after deploying your process.

**Option B: Using ngrok (recommended for testing)**

This approach allows real-time webhook testing without manually updating URLs.

Install ngrok from [https://ngrok.com/download/](https://ngrok.com/download/).

```bash
ngrok http 9898
```

Copy the generated URL (e.g., `https://abc123.ngrok-free.app`) and set it:

```bash
export CONNECTOR_BASE_URL=https://abc123.ngrok-free.app
```

### Start Command

```bash
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test
```

Keep this terminal running while working with Camunda Modeler.

### Regenerating Element Templates

The templates in `element-templates/` were generated and then customized for Apify. If you want to regenerate the original (base) templates—including all possible versions—use the command below. We use two inbound and one outbound template; several additional inbound templates exist, but you typically shouldn't regenerate unless you're sure, as Apify-specific changes may be lost.


```bash
# Use only if necessary
mvn clean package -Dgenerate.templates=true
```

---

## Using the Outbound Connector

The Outbound connector allows you to call the Apify API from your BPMN process.

**Before you begin:** Ensure both the Camunda stack and the connector are running (see [Running the Connector](#running-the-connector)).

### Setup Steps

1. Go to **Web Modeler** (http://localhost:8070/) and create a new project.

![Creating a new project](docs/modeler-create-project.png)

2. Upload the outbound connector template:
   - Template file: `element-templates/apify-outbound-connector.json`

![Uploading the connector template JSON](docs/modeler-upload-template.png)

3. **Publish** the connector template to the project.

![Publishing the connector template](docs/modeler-publish-template.png)

4. Create a new **BPMN diagram**.

![Creating a new BPMN diagram](docs/modeler-create-bpmp-diagram.png)

5. Design a process using the **Apify BPMN connector** as a service task.

![Designing a process using the Apify BPMN connector](docs/modeler-create-apify-bpmn-task.png)

6. Set the connector input variables and run the process.

![Setting the connector input variables](docs/modeler-set-inputs-and-run.png)

7. Verify the run status and result in **Camunda Operate** (http://localhost:8088/).

![Verifying the result in Camunda Operate](docs/operate-check-run-result.png)

---

## Using the Inbound Connectors

Inbound connectors listen for webhook events from Apify to trigger or continue processes.

**Before you begin:** Ensure both the Camunda stack and the connector are running with `CONNECTOR_BASE_URL` set (see [Running the Connector](#running-the-connector)).

### Start Event

Use the Start Event to begin a new process instance when an Apify webhook fires (e.g., when an Actor run finishes).

#### Setup Steps

1. Go to **Web Modeler** (http://localhost:8070/) and create a new project (or use an existing one).

![Creating a new project](docs/modeler-create-project.png)

2. Upload the inbound connector templates:
   - **Start Event**: `element-templates/apify-inbound-connector.json`
   - **Intermediate Event**: `element-templates/apify-inbound-intermediate-connector.json`

3. **Publish** both templates to the project.

![Publishing the connector template](docs/modeler-publish-inbound-template.png)

4. Create a new **BPMN diagram**.

![Creating a new BPMN diagram](docs/modeler-create-bpmn-diagram-inbound.png)

5. Design a process with an **Apify Inbound Connector** as the start event.

![Selecting the inbound connector](docs/modeler-select-inbound.png)

6. Configure the **Start Event** with:
   - **Token**: Your Apify API token
   - **Resource ID**: The Actor/Task **ID** (e.g., `abcdef123456`), not the name with tilde
   - **Output Variable**: Variable name for the webhook result (e.g., `webhookResult`)

7. **Deploy** the process. This automatically creates a webhook in Apify.

![Deploying the process](docs/modeler-set-inputs-and-deploy.png)

8. Verify the webhook was created in Apify (Actor page → **Integrations** tab).

9. If you used `http://example.com` as the connector base URL, update the webhook URL in Apify to your ngrok URL.

10. **Trigger the event** by running the Actor on Apify.

11. Verify the process in **Camunda Operate** (http://localhost:8088/):
    - Select the **Finished** filter to see completed processes

![Process in Operate](docs/operate-select.png)

![Finished process](docs/operate-select-finished.png)

---

### Intermediate Event

Use the Intermediate Event to pause a running process and wait for an Apify webhook before continuing. This is ideal for long-running Actors where you want to continue only when a specific run finishes.

#### How Correlation Works

Unlike Start Events, Intermediate Events need to know **which** process instance to wake up. This is done via **Correlation Keys**.

Think of it as matching tickets:
1. **Correlation key (process)**: A value stored in your process (e.g., `userId` or `runId` from a previous step)
2. **Correlation key (payload)**: The same value extracted from the incoming webhook

When they match, the waiting process continues.

#### Setup Steps

1. **Design your BPMN process**:
   - Start with any start event (can be an Apify Inbound Start Event)
   - Add an **Apify Inbound Intermediate Event** as a catch event

![Process with intermediate event](docs/modeler-intermediate-design.png)

2. **Configure the Start Event** (if using Apify):
   - Set **Result Variable** to `start_res`
   - This stores the webhook payload including any IDs for correlation

3. **Configure the Intermediate Event**:
   - **Token**: Your Apify API token
   - **Resource ID**: Actor/Task ID to wait for
   - **Correlation key (process)**: `=start_res.request.body.userId` (value from process)
   - **Correlation key (payload)**: `=request.body.userId` (value from incoming webhook)
   - **Result Variable**: `inter_res`

![Correlation configuration](docs/modeler-intermediate-correlation.png)

4. **Deploy and Test**:
   - Deploy the process
   - Trigger the Start Event (run the first Actor)
   - The process will wait at the Intermediate Event
   - Trigger the Intermediate Event (run the second Actor)
   - If correlation keys match, the process completes

![Successful correlation](docs/operate-intermediate-success.png)

---

## Reference

This section contains detailed technical information about the Camunda platform.

### Camunda Architecture

The Camunda platform consists of several services:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Connector Runtime                                    │
│                      (LocalConnectorRuntime)                                │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              v                               v
┌─────────────────────────┐       ┌───────────────────────────────────────────┐
│      Keycloak           │       │         Orchestration Service             │
│      :18080             │       │    (Zeebe + Operate + Tasklist)           │
│                         │       │                                           │
│  Realm: camunda-platform│       │   gRPC API: localhost:26500               │
│  Admin: admin/admin     │       │   REST API: localhost:8088                │
│                         │       │                                           │
│  OAuth Clients:         │       │   Audience: orchestration-api             │
│  - connectors           │       │                                           │
│  - orchestration        │       └───────────────────────────────────────────┘
│  - console              │                         │
└─────────────────────────┘                         │
                                                    v
                                        ┌───────────────────────┐
                                        │    Elasticsearch      │
                                        │       :9200           │
                                        └───────────────────────┘
```

**Components:**

- **Orchestration Service** - Core workflow engine containing:
  - **Zeebe** - Executes BPMN processes, handles job workers, manages process state
  - **Operate** - Web UI for monitoring processes and investigating incidents
  - **Tasklist** - Web UI for managing human tasks

- **Keycloak** - Identity provider for OAuth 2.0 / OIDC authentication

- **Elasticsearch** - Stores process execution data for Operate and Tasklist

- **Web Modeler** - Browser-based BPMN diagram editor

- **Connector Runtime** - Executes connector logic (outbound API calls, inbound webhooks)

### Service URLs

| Service | URL | Credentials | Purpose |
|---------|-----|-------------|---------|
| **Web Modeler** | http://localhost:8070/ | `demo` / `demo` | BPMN diagram editor |
| **Operate/Tasklist** | http://localhost:8088/ | `demo` / `demo` | Process monitoring |
| **Console** | http://localhost:8087/ | `demo` / `demo` | Cluster management |
| **Optimize** | http://localhost:8083/ | `demo` / `demo` | Process analytics |
| **Identity** | http://localhost:8084/ | - | User/role management |
| **Keycloak Admin** | http://localhost:18080/auth/admin | `admin` / `admin` | OAuth configuration |
| **Elasticsearch** | http://localhost:9200/ | - | Data storage |
| **Mailpit** | http://localhost:8075/ | - | Email testing |

**API Endpoints:**

| API | URL | Protocol |
|-----|-----|----------|
| **Zeebe gRPC** | `grpc://localhost:26500` | gRPC |
| **Orchestration REST** | `http://localhost:8088` | HTTP/REST |

### Keycloak Configuration

| Property | Value |
|----------|-------|
| **URL** | http://localhost:18080/auth |
| **Admin Console** | http://localhost:18080/auth/admin |
| **Admin Credentials** | `admin` / `admin` |
| **Realm** | `camunda-platform` |
| **Token Endpoint** | `http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token` |

**Pre-configured OAuth Clients:**

| Client ID | Secret | Audience | Purpose |
|-----------|--------|----------|---------|
| `connectors` | `demo-connectors-secret` | `orchestration-api` | Connector runtime |
| `orchestration` | `demo-orchestration-secret` | `orchestration-api` | Orchestration service |
| `console` | `demo-console-secret` | `console` | Console webapp |
| `optimize` | `demo-optimize-secret` | `optimize-api` | Optimize |

---

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Webhook not received | Ensure ngrok is running and `CONNECTOR_BASE_URL` is set to the ngrok URL |
| "Resource ID not found" | Use the Actor/Task **ID** (e.g., `abcdef123456`), not the name with tilde (e.g., `username~actor-name`) |
| Process not visible in Operate | Check the **Finished** filter - completed processes may not show in default view |
| Connector crashes on startup | Ensure `CONNECTOR_BASE_URL` environment variable is set |
| `ProcessDefinitionImporter` errors | Ensure `audience=orchestration-api` in config (not `zeebe-api`) |
| `Failed to apply credentials` (400) | Check OAuth client credentials match Keycloak config |
| gRPC connection failed | Ensure `grpc-address` uses `grpc://` protocol (not `http://`) |

### Cleaning Up Stale Webhooks

During testing, you may accumulate webhooks. To start fresh:

```bash
cd docker-compose-8.8
docker compose -f docker-compose-full.yaml down -v
docker compose -f docker-compose-full.yaml up -d
```

> **Warning:** This deletes all data including deployed processes and webhooks. Webhooks created in Apify must be deleted manually in the Apify console.
