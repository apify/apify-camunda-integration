# Development Guide

This guide covers local runtime setup, testing paths, and Modeler smoke tests.

## Prerequisites

- Camunda 8.8 or 8.9 stack from [camunda-distributions](https://github.com/camunda/camunda-distributions)
- Java 21+
- Maven 3.8+
- Docker + Docker Compose
- Apify account and API token

## Start local runtime

```bash
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test
```

`LocalConnectorRuntime` listens on port `9898` by default (`src/test/resources/application.properties`).

## Test against specific Camunda versions

Use `-Dversion.connectors` and, if needed, override the REST address:

> The Orchestration REST API port differs between Camunda versions (e.g. `:8088` on 8.8, `:8080` on 8.9). Override with `-Dcamunda.client.rest-address` when it doesn't match the default.

```bash
# Camunda 8.8
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test \
  -Dversion.connectors=8.8.8 \
  -Dcamunda.client.rest-address=http://localhost:8088

# Camunda 8.9
mvn test-compile exec:java \
  -Dexec.mainClass="io.camunda.connector.apify.LocalConnectorRuntime" \
  -Dexec.classpathScope=test \
  -Dversion.connectors=8.9.0
```

Tip: verify actual mapped ports in your compose setup with `docker ps`.

## Test shaded JAR in connectors-bundle

Use this before releases and after dependency/shading changes.

> **What's a shaded JAR?** It's a single "fat" JAR that bundles the connector code together with its dependencies, repackaged so nothing clashes with the host runtime. This is the artifact end users actually install into their Camunda setup.

### 1) Build shaded JAR

```bash
# Camunda 8.8
mvn clean package -DskipTests -Dversion.connectors=8.8.8
cp target/apify-camunda-connector-*.jar /tmp/apify-connector.jar

# Camunda 8.9
mvn clean package -DskipTests -Dversion.connectors=8.9.0
cp target/apify-camunda-connector-*.jar /tmp/apify-connector.jar
```

### 2) Stop stack connectors container

```bash
docker update --restart=no connectors 2>/dev/null; docker stop connectors
```

### 3) Run connectors-bundle with mounted JAR

Replace `<compose-project>` with your Docker Compose project name. For the default local setup this is typically `camunda-88` or `camunda-89`. 
> You can verify with `docker inspect orchestration --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}'`.

```bash
# Camunda 8.8
docker run --rm --name apify-connectors \
  --network <compose-project>_camunda-platform \
  --add-host host.docker.internal:host-gateway \
  -p 8086:8080 \
  -v /tmp/apify-connector.jar:/opt/custom/connector.jar \
  -e CAMUNDA_CLIENT_MODE=self-managed \
  -e CAMUNDA_CLIENT_GRPCADDRESS=http://orchestration:26500 \
  -e CAMUNDA_CLIENT_RESTADDRESS=http://orchestration:8080 \
  -e CAMUNDA_CLIENT_AUTH_METHOD=oidc \
  -e CAMUNDA_CLIENT_AUTH_TOKENURL=http://host.docker.internal:18080/auth/realms/camunda-platform/protocol/openid-connect/token \
  -e CAMUNDA_CLIENT_AUTH_CLIENTID=connectors \
  -e CAMUNDA_CLIENT_AUTH_CLIENTSECRET=demo-connectors-secret \
  -e CAMUNDA_CLIENT_AUTH_AUDIENCE=orchestration-api \
  -e CAMUNDA_CLIENT_AUTH_SCOPE='openid profile email' \
  -e LOGGING_LEVEL_IO_CAMUNDA_CONNECTOR=DEBUG \
  camunda/connectors-bundle:8.8.12

# Camunda 8.9
docker run --rm --name apify-connectors \
  --network <compose-project>_camunda-platform \
  --add-host host.docker.internal:host-gateway \
  -p 8086:8080 \
  -v /tmp/apify-connector.jar:/opt/custom/connector.jar \
  -e CAMUNDA_CLIENT_MODE=self-managed \
  -e CAMUNDA_CLIENT_GRPCADDRESS=http://orchestration:26500 \
  -e CAMUNDA_CLIENT_RESTADDRESS=http://orchestration:8080 \
  -e CAMUNDA_CLIENT_AUTH_METHOD=oidc \
  -e CAMUNDA_CLIENT_AUTH_TOKENURL=http://host.docker.internal:18080/auth/realms/camunda-platform/protocol/openid-connect/token \
  -e CAMUNDA_CLIENT_AUTH_CLIENTID=connectors \
  -e CAMUNDA_CLIENT_AUTH_CLIENTSECRET=demo-connectors-secret \
  -e CAMUNDA_CLIENT_AUTH_AUDIENCE=orchestration-api \
  -e CAMUNDA_CLIENT_AUTH_SCOPE='openid profile email' \
  -e LOGGING_LEVEL_IO_CAMUNDA_CONNECTOR=DEBUG \
  camunda/connectors-bundle:8.9.4
```

Use `/opt/custom/connector.jar` (not `/opt/app/`) so the bundle loader picks it up.

### 4) Verify load

```bash
docker logs apify-connectors 2>&1 | grep -iE "Started Connector|apify-outbound|apify-inbound|job worker"
curl -fsS http://localhost:8086/actuator/health/readiness && echo " READY"
```

## Outbound Modeler smoke test

1. Open Web Modeler (`http://localhost:8070/`).
2. Upload and publish `element-templates/apify-outbound-connector.json`.
3. Create BPMN with a service task using Apify template.
4. Set:
   - API Token: your Apify token
   - Operation: `Run Actor`
   - Actor: `apify/hello-world`
   - Wait for Finish: `true`
5. Deploy and run.
6. Confirm completion in Operate.

For full operation docs and response schema, see [README.md](README.md#outbound-connector).

## Inbound testing (webhooks)

Inbound needs a public callback URL so Apify can deliver webhook events to your machine. Use ngrok:

```bash
# Running connector directly (LocalConnectorRuntime on port 9898,
# chosen to avoid clashing with the Camunda stack's own ports)
ngrok http 9898

# - or -

# Running connector inside the connectors-bundle Docker container
# (port 8086, exposed as part of the Camunda stack)
ngrok http 8086
```

Paste the ngrok URL into the inbound template field **Camunda webhook URL**.

### Supported inbound event patterns

- Start Event
- Message Start Event
- Intermediate Catch Event
- Boundary Event

For detailed setup and field-level guidance, see:

- [README.md - Inbound Connectors](README.md#inbound-connectors)
- [README.md - Webhook Payload Structure](README.md#webhook-payload-structure)
- [README.md - Activation Condition](README.md#activation-condition)

## Deploy vs Play mode

- **Deploy:** use for inbound start-event flows (persistent webhook behavior).
- **Play:** use for quick outbound checks and intermediate/boundary flows.

If a process waits on correlation, inspect process variables in Operate and compare with runtime logs (`connectorData.runId`).
