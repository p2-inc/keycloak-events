# keycloak-events

<!-- phasetwo:banner:start - managed by p2-inc/shared-github-actions, edit there -->
[![build](https://img.shields.io/github/actions/workflow/status/p2-inc/keycloak-events/ci.yml?branch=main&label=build)](https://github.com/p2-inc/keycloak-events/actions/workflows/ci.yml) [![Maven Central](https://img.shields.io/maven-central/v/io.phasetwo.keycloak/keycloak-events?label=maven%20central)](https://central.sonatype.com/artifact/io.phasetwo.keycloak/keycloak-events) [![license](https://img.shields.io/badge/license-ELv2-blue)](COPYING) [![docs](https://img.shields.io/badge/docs-phasetwo.io-blue)](https://phasetwo.io/docs/audit-logs/)

📚 [Documentation](https://phasetwo.io/docs/audit-logs/) • 🚀 [Hosted Keycloak](https://dash.phasetwo.io/?utm_source=github&utm_medium=readme&utm_campaign=keycloak-events) • 🤖 [Keycloak Skills](https://github.com/p2-inc/keycloak-skills) • 💬 [Support](mailto:support@phasetwo.io)

> [!TIP]
> **Run this in our cloud, no setup needed.** Phase Two hosts Keycloak with this extension and the rest of ours built in. [Try it free with your own cluster →](https://dash.phasetwo.io/?utm_source=github&utm_medium=readme&utm_campaign=keycloak-events)
>
> **Using a coding agent?** Add the Phase Two Keycloak skills ([launch post](https://phasetwo.io/blog/keycloak-skills-mcp-launch)) to Claude Code, Cursor or Codex so your agent can set up passwordless login, SSO, organizations and clusters for you:
>
> ```sh
> # Install the skills (connects to our hosted MCP server)
> claude plugin marketplace add p2-inc/keycloak-skills
> claude plugin install phasetwo@keycloak-skills
> # Or connect to the MCP server directly
> claude mcp add --transport http keycloak https://mcp.phasetwo.io/mcp
> ```
<!-- phasetwo:banner:end -->

Event listeners and utilities for Keycloak: send events to webhooks and SIEM platforms, run scripts on events, publish your own events, and write events to structured logs.

## What's included

| Feature | What it does | Guide |
| --- | --- | --- |
| Webhooks | Sends user and admin events to HTTP endpoints you subscribe through a REST API, filtered by event type, with signing, retries and optional delivery history | [Webhooks](docs/webhooks.md) |
| Payload formats | Sends webhook events to Microsoft Sentinel, Datadog and Google SecOps in the shape each platform's ingestion API expects. You can add your own formats. | [Payload formats](docs/webhook-payload-formats.md) |
| Publishing events | A REST endpoint for your application's own events, which are delivered to the same webhooks | [Publishing your own events](docs/publishing-events.md) |
| HTTP sender | Posts every event in a realm to one URL, with optional HMAC signing and retries | [HTTP sender](docs/http-sender.md) |
| Script listener | Runs JavaScript you provide for each event | [Script event listener](docs/script-listener.md) |
| MDC logger event store | Writes events to Keycloak's log as structured fields, in place of the database event store | [MDC logger event store](docs/mdc-logger-event-store.md) |
| User change listener | A base class for running code when a user is added or removed | [User change listener](docs/user-change-listener.md) |
| Listener configuration | Per-realm configuration from realm attributes, and several instances of one listener, for these listeners and your own | [Configuring event listeners](docs/configuration.md) |

SIEM guides: [Microsoft Sentinel](docs/payload-formats/azure-sentinel.md), [Datadog](docs/payload-formats/datadog.md), [Google SecOps](docs/payload-formats/google-secops.md).

## Quick start

The easiest way to get started is the [Phase Two Keycloak image](https://quay.io/repository/phasetwo/phasetwo-keycloak?tab=tags), which includes the most recent version of this extension. Documentation and examples are in the [phasetwo-containers](https://github.com/p2-inc/phasetwo-containers) repo.

Then, to receive a realm's events at an HTTP endpoint:

1. In the admin console, go to **Realm settings** > **Events** > **Event listeners**, add `ext-event-webhook`, and save.
2. Create a webhook:

   ```
   POST /auth/realms/my-realm/webhooks

   {
     "enabled": true,
     "url": "https://receiver.example.com/keycloak-events",
     "secret": "A3jt6D8lz",
     "eventTypes": ["*"]
   }
   ```

See [Webhooks](docs/webhooks.md) for the rest.

## Installation

To add the extension to your own Keycloak:

1. Download the jar from [Maven Central](https://central.sonatype.com/artifact/io.phasetwo.keycloak/keycloak-events), or build it with `mvn clean install`. The build produces a single jar that includes its dependencies.
2. Put the jar in Keycloak's `providers/` directory.
3. Run `kc.sh build` and restart Keycloak.

Each listener must then be enabled in the realms that use it. See the guide for each feature.

## Compatibility

Keycloak changes its internal APIs often, so each release of this extension supports only the Keycloak version in its `pom.xml`, which is the most recent one at the time of release.

## Contributing

The build enforces Google Java formatting with [Spotless](https://github.com/diffplug/spotless). Run `mvn spotless:apply` before committing, or install the pre-push hook, which checks formatting on every push and fixes it for you to review when the check fails:

```
mvn spotless:install-git-pre-push-hook
```

---

All documentation, source code and other files in this repository are Copyright 2025 Phase Two, Inc.
