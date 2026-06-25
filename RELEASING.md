# Releasing

Maintainer-focused release workflow and policies.

## Overview

Releases are created by GitHub Actions workflow [`Create a release`](.github/workflows/release.yml).

## Run a release

1. Open **Actions -> Create a release -> Run workflow**.
2. Choose release type:
   - `auto` (default): derive bump from Conventional Commits.
   - `patch` / `minor` / `major`: force bump.
   - `custom`: pin explicit version.
3. Run and wait for completion.

## What the workflow does

1. Compute version and notes with [git-cliff](https://git-cliff.org/).
2. Build and test (`mvn clean verify`).
3. Bump `pom.xml` and update `CHANGELOG.md`.
4. Commit and push release bump to `main`.
5. Build shaded JARs for supported Camunda minors.
6. Create GitHub Release and upload artifacts.

## Produced artifacts

- `apify-camunda-connector-<version>-c8.8.jar`
- `apify-camunda-connector-<version>-c8.9.jar`
- `apify-camunda-connector-element-templates-<version>.zip`

## Prerequisites

- Workflow must run on `main` in an `apify/*` repository.
- `APIFY_SERVICE_ACCOUNT_GITHUB_TOKEN` secret must be configured.

## If release fails mid-flight

If bump commit lands on `main` but release/tag creation fails:

1. Revert the bump commit and re-run after fixing root cause, or
2. Finish manually by creating tag and attaching artifacts.

Do not blindly rerun; that can stack another bump.

## Conventional Commits and versioning

Versioning uses commit semantics:

- `feat:` -> minor
- `fix:` -> patch
- `feat!:` / `fix!:` / `BREAKING CHANGE:` -> major

## Element template version policy

- Bump template `version` integer for template JSON changes.
- Do not bump template version for JAR-only code changes.
- Change template `id` suffix (`:v1` -> `:v2`) for breaking template redesigns.

## Distribution channels

Two channels are supported:

- **GitHub Releases:** pinned JAR and template ZIP per tag.
- **Camunda Marketplace (For SaaS):** template URLs from `main`.

Marketplace URLs track latest template state from `main`; release artifacts remain version-pinned.

## New Camunda minor checklist

When Camunda ships a new minor GA:

1. Validate connector behavior against the new stack.
2. Verify compile/test with new connector SDK version.
3. Add SDK version to release workflow matrix.
4. Update compatibility docs (`COMPATIBILITY.md`, `README.md`, `CONTRIBUTING.md`).
5. Adjust template versions if template JSON changed.
6. Cut a release.
