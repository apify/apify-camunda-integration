# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in the Apify Camunda Connector, please **do not open a public GitHub issue**. Report it privately so we can investigate and ship a fix before details are made public.

- **Email:** `<security@apify.com>` *(placeholder, to be confirmed by Apify Security team)*
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

## Public disclosure

Once a fix has been released, we will publish a GitHub Security Advisory on this repository describing the vulnerability, affected versions, the fix, and any required user action. We are happy to credit reporters who would like attribution.
