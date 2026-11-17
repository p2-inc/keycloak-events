# Datadog (`datadog`)

The `datadog` payload format sends Keycloak events to the [Datadog Logs HTTP intake](https://docs.datadoghq.com/api/latest/logs/#send-logs). The log entries look like the ones Datadog's own Keycloak integration collects:

- `ddsource:keycloak`
- the `org.keycloak.events` logger name
- a `message` that is exactly the line Keycloak's event logger writes

So Datadog's [Keycloak log pipeline](https://github.com/DataDog/integrations-core/tree/master/keycloak/assets/logs) parses them, and the Cloud SIEM Keycloak content pack's detection rules apply, the same as for logs collected from Keycloak's log files.

See [Webhook payload formats](../webhook-payload-formats.md) for how formats and the `headers` auth type work in general.

## Set up Datadog

1. Create an **API key** under Organization Settings > API Keys. An application key is not needed.
2. Find your **logs intake host** from your Datadog site:

   | Site | Intake URL |
   | --- | --- |
   | US1 (`datadoghq.com`) | `https://http-intake.logs.datadoghq.com/api/v2/logs` |
   | US3 (`us3.datadoghq.com`) | `https://http-intake.logs.us3.datadoghq.com/api/v2/logs` |
   | US5 (`us5.datadoghq.com`) | `https://http-intake.logs.us5.datadoghq.com/api/v2/logs` |
   | EU1 (`datadoghq.eu`) | `https://http-intake.logs.datadoghq.eu/api/v2/logs` |
   | AP1 (`ap1.datadoghq.com`) | `https://http-intake.logs.ap1.datadoghq.com/api/v2/logs` |
   | AP2 (`ap2.datadoghq.com`) | `https://http-intake.logs.ap2.datadoghq.com/api/v2/logs` |
   | UK1 (`uk1.datadoghq.com`) | `https://http-intake.logs.uk1.datadoghq.com/api/v2/logs` |
   | US1-FED (`ddog-gov.com`) | `https://http-intake.logs.ddog-gov.com/api/v2/logs` |
   | US2-FED (`us2.ddog-gov.com`) | `https://http-intake.logs.us2.ddog-gov.com/api/v2/logs` |

   Datadog lists the current hosts under [Send logs](https://docs.datadoghq.com/api/latest/logs/#send-logs).

3. Optionally, for Cloud SIEM detections, enable the **Keycloak** [content pack](https://docs.datadoghq.com/security/cloud_siem/ingest_and_enrich/content_packs/) under **Security** > **Cloud SIEM** > **Content Packs**. Datadog applies its [Keycloak](https://docs.datadoghq.com/integrations/keycloak/) log pipeline to `source:keycloak` logs automatically.

You don't need the Datadog Agent or Keycloak's syslog output, which Datadog's Keycloak integration page describes. This format posts log entries straight to the intake API, in a form the same pipeline parses.

## Create the webhook

```
POST /auth/realms/:realm/webhooks
```

```json
{
  "enabled": true,
  "url": "https://http-intake.logs.datadoghq.com/api/v2/logs",
  "eventTypes": ["access.*", "admin.*"],
  "payloadFormat": "datadog",
  "authType": "headers",
  "config": {
    "header.DD-API-KEY": "<api-key>",
    "service": "keycloak",
    "tags": "env:prod,team:iam"
  }
}
```

The API key is sent as the `DD-API-KEY` header, and the API returns it masked.

To send every realm's events from one server-wide webhook instead, use the catch-all webhook:

```
WEBHOOK_URI=https://http-intake.logs.datadoghq.com/api/v2/logs
WEBHOOK_FORMAT=datadog
WEBHOOK_AUTH_TYPE=headers
WEBHOOK_CONFIG={"header.DD-API-KEY":"<api-key>","tags":"env:prod"}
```

Events show up in the Log Explorer under `source:keycloak`.

### Options

| `config` key | Default | Description |
| --- | --- | --- |
| `service` | `keycloak` | The `service` attribute |
| `source` | `keycloak` | The `ddsource` attribute. Keep `keycloak` for Datadog's Keycloak pipeline and the content pack to apply. |
| `hostname` | This Keycloak node's hostname | The `hostname` attribute |
| `tags` | | Comma-separated `ddtags`, e.g. `env:prod,team:iam` |

## The log entries

Each request posts a JSON array of log entries:

| Attribute | Contents |
| --- | --- |
| `ddsource`, `service`, `hostname`, `ddtags` | From the options above |
| `message` | Keycloak's event log line (see below) |
| `timestamp` | When the event happened, in epoch milliseconds |
| `status` | `info`, or `warn` when the event has an error |
| `loggerName`, `logger.name` | `org.keycloak.events`, which the Keycloak pipeline uses to recognise event logs |
| `evt.name` | The type as Keycloak names it: `LOGIN`, `LOGIN_ERROR`, ... for user events; `CREATE`, `UPDATE`, ... for admin events |
| `evt.outcome` | `success` or `failure` |
| `evt.category` | `authentication` for login, logout, token, impersonation and identity provider login events; `admin` for admin events |
| `usr.id`, `usr.name` | The user the event is about. For admin events, the admin who made the change. |
| `usr.email` | User events: the `email` detail, when present |
| `network.client.ip` | The client IP address |
| `error.message` | The error, e.g. `invalid_user_credentials` |
| `keycloak.*` | The whole event: `id`, `uid`, `type` (e.g. `access.LOGIN`), `category`, `realmId`, `realmName`, `clientId`, `sessionId`, `error`, `details`. Admin events also carry `operationType`, `resourceType`, `resourcePath` and `authRealm`. |

The `evt.*`, `usr.*`, `network.*` and `error.*` attributes follow Datadog's [standard attributes](https://docs.datadoghq.com/standard-attributes/), so detections work even where the message isn't parsed.

### The message

`message` is the line Keycloak's `org.keycloak.events` logger writes for the event, quoted and sanitized the same way. For a user event:

```
type="LOGIN", realmId="ce09554b-...", realmName="master", clientId="admin-cli", userId="fe94cd42-...", sessionId="77crxycrk_Y-_m4usiaErjZh", ipAddress="192.168.65.1", auth_method="openid-connect", grant_type="password", username="admin"
```

For an admin event:

```
operationType="CREATE", realmId="ce09554b-...", realmName="master", clientId="fd4272a8-...", userId="fe94cd42-...", ipAddress="192.168.65.1", resourceType="USER", resourcePath="users/3771790e-...", resourceUserId="3771790e-...", resourceUsername="datadog-user"
```

keycloak-events adds the changed user's `userId` and `username` to admin events on users. In the message they are renamed `resourceUserId` and `resourceUsername`, so they don't overwrite the admin's `userId` when the pipeline extracts attributes. Any other detail whose name collides with a field of the line gets a `detail_` prefix.

A complete entry for a login:

```json
[
  {
    "ddsource": "keycloak",
    "service": "keycloak",
    "hostname": "fa6957957f32",
    "ddtags": "env:test,team:iam",
    "message": "type=\"LOGIN\", realmId=\"ce09554b-70dd-4726-8244-a20cd8f2d869\", realmName=\"master\", clientId=\"admin-cli\", userId=\"fe94cd42-324d-4e69-adff-480bae053351\", sessionId=\"77crxycrk_Y-_m4usiaErjZh\", ipAddress=\"192.168.65.1\", auth_method=\"openid-connect\", grant_type=\"password\", username=\"admin\"",
    "timestamp": 1794836612105,
    "status": "info",
    "loggerName": "org.keycloak.events",
    "logger": { "name": "org.keycloak.events" },
    "evt": { "name": "LOGIN", "outcome": "success", "category": "authentication" },
    "usr": { "id": "fe94cd42-324d-4e69-adff-480bae053351", "name": "admin" },
    "network": { "client": { "ip": "192.168.65.1" } },
    "keycloak": {
      "id": "9abdf8a9-baac-4945-b7a8-004309d313d3",
      "uid": "3f4cfa7c-8a50-4ceb-98b0-378bd97dbf2f",
      "type": "access.LOGIN",
      "category": "user",
      "realmId": "ce09554b-70dd-4726-8244-a20cd8f2d869",
      "realmName": "master",
      "clientId": "admin-cli",
      "sessionId": "77crxycrk_Y-_m4usiaErjZh",
      "details": { "auth_method": "openid-connect", "grant_type": "password", "username": "admin" }
    }
  }
]
```

## Example searches

| What | Log Explorer query |
| --- | --- |
| All Keycloak events | `source:keycloak` |
| Failed logins | `source:keycloak @evt.name:LOGIN_ERROR` |
| Failed logins for a user | `source:keycloak @evt.outcome:failure @usr.name:jdoe` |
| Admin changes to users | `source:keycloak @evt.category:admin @keycloak.resourceType:USER` |
| One realm | `source:keycloak @keycloak.realmName:my-realm` |

## Retries

| Response | What happens |
| --- | --- |
| `202` | Sent |
| `400`, `401`, `403`, `413` | Not retried: an invalid payload, a bad or revoked API key, or a request that is too large |
| `408`, `429`, `5xx`, network errors | Retried with backoff |

## Limits

- Each request carries one event. The intake accepts up to 1,000 entries and 5 MB per request, and each entry is far smaller than that.
- Datadog truncates log entries over 1 MB.

## Troubleshooting

Enable [logging of webhook send attempts](../webhooks.md#logging-webhook-send-attempts) to see each response status.

| Symptom | Check |
| --- | --- |
| `403` | The webhook URL is for your Datadog site, and `header.DD-API-KEY` holds an API key, not an application key. |
| Logs arrive but aren't parsed | `source` is `keycloak`, and the Keycloak pipeline is enabled under Logs > Pipelines. |
| No Cloud SIEM signals | The Keycloak content pack is enabled, and logs are indexed in an index Cloud SIEM analyzes. |
