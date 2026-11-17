# HTTP sender (`ext-event-http`)

The HTTP sender posts every event in a realm to one URL as JSON. It can sign the request with an HMAC, add request headers, and retry failed sends.

It is the simpler of the two ways this extension sends events over HTTP:

| | HTTP sender | [Webhooks](webhooks.md) |
| --- | --- | --- |
| Configured with | A realm attribute | A REST API |
| Events sent | All of them | Filtered by event type, per webhook |
| Payload | Keycloak's own event representation, unless a [payload format](webhook-payload-formats.md) is set | The keycloak-events unified event format, unless a payload format is set |
| Authentication | HMAC signature, static headers | HMAC signature, bearer JWT, static headers, OAuth2 client credentials |
| Delivery history | No | Optional |

## Set it up

1. **Enable the listener.** In the admin console, go to **Realm settings** > **Events** > **Event listeners**, add `ext-event-http`, and click **Save**.
2. **Add the configuration** as the realm attribute `_providerConfig.ext-event-http` (or `_providerConfig.ext-event-http.N` to send to several URLs). See [Setting realm attributes](configuration.md#setting-realm-attributes).

A minimal configuration:

```json
{
  "targetUri": "https://receiver.example.com/keycloak-events",
  "sharedSecret": "A3jt6D8lz"
}
```

## Configuration

| Name | Required | Default | Description |
| --- | --- | --- | --- |
| `targetUri` | Yes | | The URL to post events to |
| `sharedSecret` | No | | The shared secret for HMAC signing. When set, the [RFC 2104](https://datatracker.ietf.org/doc/html/rfc2104) HMAC of the request body is sent in the `X-Keycloak-Signature` header. |
| `hmacAlgorithm` | No | `HmacSHA256` | The HMAC algorithm. `HmacSHA1` is available for backwards compatibility. |
| `retry` | No | `true` | Retry with exponential backoff when the response isn't `2xx` |
| `backoffInitialInterval` | No | `500` | The first retry interval, in milliseconds |
| `backoffMaxElapsedTime` | No | `900000` | How long to keep retrying, in milliseconds (15 minutes) |
| `backoffMaxInterval` | No | `180000` | The longest retry interval, in milliseconds (3 minutes) |
| `backoffMultiplier` | No | `5` | How much the interval grows on each retry. `1.5` is a 50% increase. |
| `backoffRandomizationFactor` | No | `0.5` | How much each interval is randomized. `0.5` gives an interval between 50% below and 50% above the calculated one. |
| `format` | No | | A [payload format](webhook-payload-formats.md) id, such as `datadog`. Without it, Keycloak's own event representation is sent. |
| `formatConfig` | No | | The payload format's options, as a JSON object of string values |
| `headers` | No | | Request headers to add, as a JSON object of string values, for example an API key |

To send events in a payload format with an API key header:

```json
{
  "targetUri": "https://http-intake.logs.datadoghq.com/api/v2/logs",
  "format": "datadog",
  "formatConfig": { "tags": "env:prod" },
  "headers": { "DD-API-KEY": "<api-key>" }
}
```

Header values are stored as plain text in the realm attribute, and anyone who can view the realm can read them. A [webhook](webhooks.md) masks its secrets instead.

## What it doesn't do

- **It doesn't filter events.** Every user and admin event in the realm is sent.
- **It doesn't retry forever.** A send that keeps failing is dropped after `backoffMaxElapsedTime`. Pending retries are held in memory, so they are lost when Keycloak restarts.
- **It doesn't support the bearer JWT or OAuth2 authentication of webhooks.** Use a [webhook](webhooks.md) for those.
