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

## Choosing the Right JAR

1. Check your Camunda cluster/runtime version (Camunda Console → cluster details, or the `camunda/connectors-bundle` Docker image tag you run).
2. Download the JAR with the matching minor suffix from the [GitHub Releases](https://github.com/apify/apify-camunda-integration/releases) page.

| Your runtime | Download |
|---|---|
| `connectors-bundle:8.8.x` | `apify-camunda-connector-<version>-c8.8.jar` |
| `connectors-bundle:8.9.x` | `apify-camunda-connector-<version>-c8.9.jar` |

## Version-Specific Configuration

### Connector Secrets

The way connector secrets are resolved from environment variables changed in 8.9:

| Version | Environment variable for `secrets.APIFY_TOKEN` | Notes |
|---|---|---|
| 8.8.x | `APIFY_TOKEN=<value>` | No prefix required |
| 8.9.x | `SECRET_APIFY_TOKEN=<value>` | `SECRET_` prefix required by default |

In 8.9, the environment-based connector secret provider uses `SECRET_` as the default prefix.
Unprefixed environment variables are no longer resolved as connector secrets.

The element template references remain the same (`secrets.APIFY_TOKEN`) — only the environment
variable naming changes.

**Customizing the prefix (8.9+):**
- Set a custom prefix: `CAMUNDA_CONNECTOR_SECRET_PROVIDER_ENVIRONMENT_PREFIX=MY_PREFIX_`
- Restore 8.8 behavior (empty prefix): `CAMUNDA_CONNECTOR_SECRET_PROVIDER_ENVIRONMENT_PREFIX=`
  (not recommended for production)

### REST API Port

The default orchestration REST API port changed between versions:

| Version | Default REST port | Docker mapping |
|---|---|---|
| 8.8.x | 8090 | Typically exposed as `8088:8080` or `8090:8080` |
| 8.9.x | 8080 | Exposed as `8080:8080` |

Configure via `CAMUNDA_CLIENT_REST_ADDRESS` environment variable when running the connector runtime.

## Upstream References

- [Camunda 8.8 - What's New](https://docs.camunda.io/docs/reference/announcements-release-notes/880/whats-new-in-88/)
- [Camunda 8.8 - Announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/880/880-announcements/)
- [Camunda 8.9 - What's New](https://docs.camunda.io/docs/reference/announcements-release-notes/890/whats-new-in-89/)
- [Camunda 8.9 - Announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/890/890-announcements/)
- [Connector SDK Compatibility](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/#runtime-environments)
