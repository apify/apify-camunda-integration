# Troubleshooting

Quick fixes for common setup and runtime issues.

## Environment issues

| Issue | Fix |
|---|---|
| Docker Compose fails / containers crash | Allocate at least 8 GB RAM to Docker Desktop. Check failing containers with `docker compose ps`. |
| `Unsupported class file major version 65` | Use Java 21. Verify `java -version` and `JAVA_HOME`. |
| Port already in use (`8070`, `8080`, `9200`, etc.) | Stop conflicting service or change compose port mapping. |
| Maven dependency resolution fails | Check connectivity and run `mvn clean package -U`. |

## Connector runtime issues

| Issue | Fix |
|---|---|
| Webhook not received | Make sure ngrok is running and current URL is set in **Camunda webhook URL**. Re-deploy process after changes. |
| Process not visible in Operate | Check filters (including finished instances). |
| Process stuck at Intermediate Catch Event | Correlation mismatch: compare process variable with `connectorData.runId` from runtime logs. |
| Activation fails with "Camunda webhook URL is not configured" | Fill **Camunda webhook URL** in inbound template and redeploy. |
| `ProcessDefinitionImporter` errors | Confirm `audience=orchestration-api` in connector auth config. |
| `Failed to apply credentials` (400) | Re-check OAuth client credentials and realm config. |
| gRPC connection failed | Ensure gRPC address uses `grpc://` and correct host/port. |

## connectors-bundle issues

| Issue | Fix |
|---|---|
| `NoClassDefFoundError` for `org/slf4j/LoggerFactory` from shaded deps | Keep conflicting HTTP client deps as `provided` in `pom.xml`; confirm classes are not packaged in shaded JAR. |
| `NoClassDefFoundError: io/camunda/connector/api/outbound/OutboundConnectorFunction` | Mount JAR at `/opt/custom/connector.jar`, not `/opt/app/`. |
| Port `8086` already allocated | Stop stock `connectors` container or map different host port. |
| Container starts but Apify jobs are not consumed | Verify container network and presence of Apify worker line in logs. |
| Secret `{{secrets.APIFY_TOKEN}}` resolves empty | Use correct env var by version (`SECRET_APIFY_TOKEN` for 8.9.x, `APIFY_TOKEN` for 8.8.x). |

## Stale webhooks / reset local stack

```bash
cd camunda-distributions/docker-compose/versions/camunda-8.9
docker compose -f docker-compose-full.yaml down -v
docker compose -f docker-compose-full.yaml up -d
```

Warning: this wipes local stack data (deployments, runtime state).

## Helpful references

- [DEVELOPMENT.md](DEVELOPMENT.md)
- [RELEASING.md](RELEASING.md)
- [COMPATIBILITY.md](COMPATIBILITY.md)
- [README.md](README.md)
