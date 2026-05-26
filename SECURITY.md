# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in the Apify Camunda Connector, please **do not open a public GitHub issue**. Report it privately so we can investigate and ship a fix before details are made public.

- **Email:** [security@apify.com](mailto:security@apify.com). This is the canonical Apify security contact published in [Apify's security.txt](https://apify.com/.well-known/security.txt) and used across all Apify products.
- Alternatively, use [GitHub's private vulnerability reporting](https://github.com/apify/apify-camunda-integration/security/advisories/new) on this repository.

When reporting, please include:

- A description of the issue and the impact you believe it has.
- Steps to reproduce, or a proof-of-concept if available.
- The connector version (or commit SHA) affected.
- The Camunda 8 version and deployment mode (SaaS / Self-Managed).
- Any suggested mitigations you are aware of.

We will acknowledge your report and keep you informed as we triage and remediate.

## Response targets

This connector is listed on the Camunda Marketplace and follows the [Camunda Marketplace Security](https://camunda.com/trust-center/marketplace-security/) requirements for vulnerability handling. Targets are CVSS v3 score-based:

| Severity (CVSS v3) | Acknowledge | Mitigate | Fix |
|---|---|---|---|
| Critical (9.0–10.0) | 24 hours | 48 hours | 4 weeks |
| High (7.0–8.9) | 48 hours | 72 hours | 6 weeks |
| Medium (4.0–6.9) | 5 days | 10 days | 8 weeks |
| Low (0.1–3.9) | accepted | n/a | n/a |

Per the [Camunda Marketplace Security Guidelines](https://camunda.com/trust-center/marketplace-security/), "failure to meet this timeframe will result in temporary or permanent removal of the Connector from the Marketplace."

## Scope

In scope:

- The connector source code in this repository.
- The element template JSON files distributed via GitHub Releases.
- Vulnerabilities arising from how the connector handles Apify API tokens, webhook payloads, or process variables.

Out of scope:

- Issues in Camunda 8 itself; please report those to Camunda directly.
- Issues in the Apify platform; please report those via the [Apify security policy](https://apify.com/.well-known/security.txt) (or Apify's standard channels).
- Vulnerabilities in third-party dependencies that have already been disclosed and are tracked in our dependency-update process. We still appreciate the heads-up but treat them as standard updates rather than embargoed disclosures.

## Security operating principles

The connector is built and maintained against the following baseline obligations from the Camunda Marketplace Security program:

- All outbound traffic to the Apify API uses **TLS 1.2 or higher**.
- The connector does **not** collect or store end-user credentials. The only secret it handles is the Apify API token, which is supplied by the process designer (typically via Camunda Secrets).
- All third-party runtime dependencies are open-source. See [pom.xml](pom.xml) for the full list.
- No source code obfuscation is used; the published JAR is built from the public source tree in this repository.

## Known limitations

### Inbound webhook source authentication

The inbound connector does not currently verify the source of incoming webhook POSTs. The endpoint at `{Camunda webhook URL}/inbound/{webhookId}` accepts any POST whose body parses as a valid Apify event payload. Apify's webhook delivery does not include an HMAC signature; the platform's recommended authentication is a secret embedded in the destination URL or in a custom header configured via Apify's `headersTemplate` field.

**Threat model:** an attacker who learns the full inbound URL (for example via Camunda runtime HTTP logs, reverse-proxy logs, or the Apify Console webhook list) can post forged events to it. Forged events can trigger spurious process instances or prematurely resume waiting processes with attacker-controlled `connectorData`.

**Planned remediation:** a future release will generate a per-webhook secret on activation, register it with Apify via the `headersTemplate` field as an `Authorization` header, and verify it on each inbound request before processing the payload. Tracked in [#69](https://github.com/apify/apify-camunda-integration/issues/69).

**Mitigations operators can apply today:**

1. Run the connector runtime behind a reverse proxy that does not log full request paths, or that strips path-segment UUIDs from access logs.
2. Apply downstream input validation to any process variable derived from `connectorData`. For example, do not blindly trust `connectorData.defaultDatasetId` to point at a dataset your account actually owns.
3. Use the **Activation Condition** FEEL expression on each inbound element template to filter for expected values such as `eventType` and `status`, reducing the attack surface to events that match those filters.

## Public disclosure

Once a fix has been released, we will publish a GitHub Security Advisory on this repository describing the vulnerability, affected versions, the fix, and any required user action. We are happy to credit reporters who would like attribution.
