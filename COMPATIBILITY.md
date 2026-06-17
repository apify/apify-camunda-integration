# Camunda Version Compatibility

This document describes which Apify Connector JAR to use with which Camunda platform version,
and highlights configuration differences between supported versions.

## Supported Versions

| JAR suffix | Camunda Platform | Connector SDK | Spring Boot | Status |
|---|---|---|---|---|
| `-c8.8` | 8.8.x | 8.8.8 | 3.x | Stable, supported |
| `-c8.9` | 8.9.x | 8.9.0 | 4.0.x | Stable, supported |

> **Why separate JARs?** The Camunda Connector SDK minor must match the Connector Runtime minor.
> Between 8.8 and 8.9, Spring Boot jumped from 3.x to 4.0.x and the Java client introduced
> type-safe pagination - both require recompilation. A single JAR cannot serve both runtimes.

Each JAR is built against the SDK version in the table above and runs on **any runtime patch of the same minor** - patches are interchangeable, minors are not.

## Choosing the Right JAR

1. Check your Camunda cluster/runtime version (Camunda Console → cluster details, or the `camunda/connectors-bundle` Docker image tag you run).
2. Download the JAR with the matching minor suffix from the [GitHub Releases](https://github.com/apify/apify-camunda-integration/releases) page.

| Your runtime | Download |
|---|---|
| `connectors-bundle:8.8.x` | `apify-camunda-connector-<version>-c8.8.jar` |
| `connectors-bundle:8.9.x` | `apify-camunda-connector-<version>-c8.9.jar` |

## Version-Specific Configuration

### Connector Secrets

For local/self-managed runtime, when a connector template field references `{{secrets.APIFY_TOKEN}}`, the connector runtime resolves it from an environment variable on the machine (or container) running the connectors bundle. The naming of that environment variable changed in 8.9:

| Version | Environment variable for `secrets.APIFY_TOKEN` | Notes |
|---|---|---|
| 8.8.x | `APIFY_TOKEN=<value>` | No prefix required |
| 8.9.x | `SECRET_APIFY_TOKEN=<value>` | `SECRET_` prefix required by default |

In 8.9, the environment-based secret provider uses `SECRET_` as the default prefix. The `{{secrets.APIFY_TOKEN}}` reference in the element template stays the same — only the environment variable naming changes.

> **Note:** This only applies when the template uses a secret reference. If the token is entered directly in the connector template field (e.g. in Web Modeler), no environment variable is needed.

> **SaaS note:** In Camunda SaaS, secrets are typically managed in Camunda Console instead of runtime environment variables.

**Customizing the prefix (8.9+):**
- Set a custom prefix: `CAMUNDA_CONNECTOR_SECRETPROVIDER_ENVIRONMENT_PREFIX=MY_PREFIX_`
- Restore 8.8 behavior (empty prefix): `CAMUNDA_CONNECTOR_SECRETPROVIDER_ENVIRONMENT_PREFIX=`
  (not recommended for production)

### REST API Port

Inside the container the orchestration REST API always listens on `8080`. Only the **host** port mapping differs between versions in the official [camunda-distributions](https://github.com/camunda/camunda-distributions) compose files:

| Version | Host port | docker-compose mapping |
|---|---|---|
| 8.8.x | 8088 | `8088:8080` |
| 8.9.x | 8080 | `8080:8080` |

When the connector runs **inside** the bundle on the compose network, it reaches the cluster at `http://orchestration:8080` regardless of version. The host port only matters for a runtime running outside Docker (e.g. `LocalConnectorRuntime`) or for external clients; set `CAMUNDA_CLIENT_RESTADDRESS` to match.

## Upstream References

- [Camunda 8.8 - What's New](https://docs.camunda.io/docs/reference/announcements-release-notes/880/whats-new-in-88/)
- [Camunda 8.8 - Announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/880/880-announcements/)
- [Camunda 8.9 - What's New](https://docs.camunda.io/docs/reference/announcements-release-notes/890/whats-new-in-89/)
- [Camunda 8.9 - Announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/890/890-announcements/)
- [Connector SDK Compatibility](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/#runtime-environments)
