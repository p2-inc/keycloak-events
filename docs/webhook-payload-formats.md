# Webhook payload formats

By default a webhook posts the keycloak-events JSON representation of each event (see [Webhooks](../README.md#webhooks)). A **payload format** changes what is posted, so that events can go straight into a log or security platform's ingestion API, in the shape that platform's parsers and detections expect.

| Format id | Sends to | Guide |
| --- | --- | --- |
| `default` | Any receiver of the keycloak-events JSON payload | This page |
| `azure-sentinel` | Microsoft Sentinel, through the Azure Monitor Logs Ingestion API | [Microsoft Sentinel](payload-formats/azure-sentinel.md) |
| `datadog` | Datadog Logs and Cloud SIEM, through the Logs HTTP intake | [Datadog](payload-formats/datadog.md) |

Formats are pluggable: other extensions can add their own (see [Writing your own format](#writing-your-own-format)). `GET /auth/realms/:realm/webhooks/formats` lists the formats installed on a server.

## Choosing a format for a webhook

A webhook has two fields for this:

| Field | Description |
| --- | --- |
| `payloadFormat` | The format id. Omit it on update to leave the format unchanged; set it to `""` to go back to the server default. |
| `config` | A JSON object of string values: the format's options, plus the settings of the `headers` and `oauth2-client-credentials` auth types. |

```json
{
  "enabled": true,
  "url": "https://receiver.example.com/ingest",
  "eventTypes": ["access.LOGIN", "access.LOGIN_ERROR", "admin.*"],
  "payloadFormat": "azure-sentinel",
  "authType": "oauth2-client-credentials",
  "config": {
    "oauth2.tokenUrl": "https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token",
    "oauth2.clientId": "<application-id>",
    "oauth2.clientSecret": "<client-secret>",
    "oauth2.scope": "https://monitor.azure.com//.default"
  }
}
```

Creating or updating a webhook returns `400` if the format isn't installed, if a format option is invalid, or if the auth type is missing a setting it needs.

A webhook without a `payloadFormat` uses the server default. That is `default` unless a server operator selects another with the SPI option `--spi-webhook-payload-formatter--provider=<id>` (env `KC_SPI_WEBHOOK_PAYLOAD_FORMATTER__PROVIDER`).

### Listing the installed formats

`GET /auth/realms/:realm/webhooks/formats` (requires `view-events`) returns each format with its options:

```json
[
  {
    "id": "azure-sentinel",
    "helpText": "Microsoft Sentinel via the Azure Monitor Logs Ingestion API ...",
    "properties": []
  },
  {
    "id": "default",
    "helpText": "The keycloak-events JSON payload (default).",
    "properties": []
  }
]
```

`properties` uses Keycloak's `ConfigPropertyRepresentation` (`name`, `label`, `helpText`, `type`, `defaultValue`, `secret`).

## Authenticating to an ingestion API

Ingestion APIs usually authenticate with an API key header or an OAuth2 token rather than an HMAC signature, so two auth types read their settings from `config`. They work with any format.

| `authType` | `config` entries | What is sent |
| --- | --- | --- |
| `headers` | One or more `header.<Name>` entries | Each entry as request header `<Name>`, e.g. `header.DD-API-KEY` is sent as `DD-API-KEY` |
| `oauth2-client-credentials` | `oauth2.tokenUrl`, `oauth2.clientId`, `oauth2.clientSecret`, and optionally `oauth2.scope` | `Authorization: Bearer <token>`, with the token obtained by the client credentials grant |

OAuth2 tokens are cached per set of credentials and refreshed a minute before they expire. If the receiver answers `401`, the cached token is dropped and the retry fetches a new one. A token request times out after 10 seconds, so an unreachable token endpoint only delays the webhooks that use it.

The `hmac`, `bearer` and `none` auth types work with every format too. The HMAC signature and the bearer token's `request_body_sha256` claim are computed over the formatted body, i.e. the exact bytes that are posted.

### Secrets in `config`

`header.*` values, `oauth2.clientSecret`, and any option a format marks secret are returned as `**********` when a webhook is read. Sending `**********` back on update keeps the stored value, so a webhook can be read, edited and saved without re-entering its secrets. Any other value replaces it.

## Retries

Webhooks are retried with exponential backoff when the receiver doesn't answer `2xx` (see [Webhooks](../README.md#webhooks)). The `default` format retries every failure. Vendor formats don't retry responses that can't succeed on a retry, such as a malformed request or a forbidden API key; each vendor guide lists them.

## Using a format without a managed webhook

**The catch-all webhook.** A server operator can give the catch-all webhook (`WEBHOOK_URI`) a format with these environment variables:

| Env var | Description |
| --- | --- |
| `WEBHOOK_FORMAT` | The format id |
| `WEBHOOK_CONFIG` | The webhook `config`, as a JSON object of string values |
| `WEBHOOK_AUTH_TYPE` | Any auth type, including `headers` and `oauth2-client-credentials` |

```
WEBHOOK_URI=https://<dce-or-dcr-endpoint>/dataCollectionRules/<dcr-immutable-id>/streams/Custom-KeycloakEvents?api-version=2023-01-01
WEBHOOK_FORMAT=azure-sentinel
WEBHOOK_AUTH_TYPE=oauth2-client-credentials
WEBHOOK_CONFIG={"oauth2.tokenUrl":"https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token","oauth2.clientId":"<application-id>","oauth2.clientSecret":"<client-secret>","oauth2.scope":"https://monitor.azure.com//.default"}
```

**The HTTP sender.** The `ext-event-http` listener takes a format in its realm configuration (see [HTTP Sender](../README.md#http-sender)):

| Name | Description |
| --- | --- |
| `format` | The format id. Without it, the listener posts Keycloak's own event representation, as before. |
| `formatConfig` | The format's options, as a JSON object of string values |
| `headers` | Request headers to add, as a JSON object of string values (e.g. an API key) |

## Writing your own format

A format is a Keycloak provider for the `webhook-payload-formatter` SPI. These interfaces are marked `@PublicAPI(unstable = true)`: they may still change between releases.

1. Implement `io.phasetwo.keycloak.events.format.WebhookPayloadFormatter`:
   - `format(List<ExtendedAdminEvent> events, Map<String, String> config)` returns a `FormattedPayload`: the body bytes, the content type and any extra headers. `EventView` gives a flat view of an event that hides the differences between user, admin and custom events.
   - Optionally override `isRetryable(int httpStatus)`.

   A formatter may be called from several threads at once and may be used after the session that created it has closed, so it must not keep state or hold on to the session.
2. Implement `io.phasetwo.keycloak.events.format.WebhookPayloadFormatterFactory` and register it with `@AutoService(WebhookPayloadFormatterFactory.class)` (or a `META-INF/services` entry). Its `getId()` is the value of `payloadFormat`. Describe your options with `getConfigProperties()`; properties marked secret are masked by the API. Reject bad options in `validateConfig(Map<String, String>)` by throwing `IllegalArgumentException` with a message for the user.
3. Put your jar in Keycloak's `providers/` directory next to keycloak-events.

The built-in formats in `src/main/java/io/phasetwo/keycloak/events/format/` are working examples.
