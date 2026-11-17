# Webhooks (`ext-event-webhook`)

Webhooks send a realm's events to HTTP endpoints as they happen. Each webhook is a subscription: a URL, the event types it wants, and how its requests are authenticated. Webhooks are created and changed through a REST API, without restarting Keycloak.

User events and admin events are sent in one unified format, told apart by a prefix on the event type: `access.` for user events such as `access.LOGIN`, and `admin.` for admin events such as `admin.USER-CREATE`. The format and the event types are described in the Phase Two [Audit Logs](https://phasetwo.io/docs/audit-logs/) documentation.

## What webhooks do and don't do

- **They send each event once per matching webhook,** after the transaction that produced the event has committed. An event that is rolled back is not sent.
- **They retry failed sends,** with exponential backoff, for up to 15 minutes. After that the send is dropped.
- **They don't guarantee delivery.** Sends and their retries are queued in memory. If Keycloak stops while a send is pending, it is lost.
- **They don't guarantee order.** Sends run on a thread pool, and a retried event can arrive after a later one.
- **They don't batch.** Each request carries one event.
- **They don't require events to be saved in the realm.** Events are sent whether or not **Save events** is on. Only [delivery history](#storing-webhook-events-and-sends) needs it.

## Enable webhooks

1. Open the Keycloak admin console and select the realm.
2. Go to **Realm settings** > **Events** > **Event listeners**.
3. Add `ext-event-webhook` and click **Save**.

Do this in every realm whose events you want. A webhook belongs to one realm and receives only that realm's events.

## Create a webhook

To send every event in the `my-realm` realm to an endpoint, signed with a shared secret:

```
POST /auth/realms/my-realm/webhooks
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "enabled": true,
  "url": "https://receiver.example.com/keycloak-events",
  "secret": "A3jt6D8lz",
  "eventTypes": ["*"]
}
```

The response is `201`, with the new webhook's URL in the `Location` header.

A request inspection service such as [Pipedream](https://pipedream.com/) is an easy way to look at the requests before you build your own receiver.

## The webhooks API

Reading requires the `view-events` role and changing requires `manage-events`. Both are roles of the realm's `realm-management` client.

| Path | Method | Payload | Returns | Description |
| --- | --- | --- | --- | --- |
| `/auth/realms/:realm/webhooks` | `GET` | `first`, `max` query parameters | List of webhook objects | List webhooks |
| `/auth/realms/:realm/webhooks` | `POST` | Webhook object | `201` | Create a webhook |
| `/auth/realms/:realm/webhooks/count` | `GET` | | A number | Count webhooks |
| `/auth/realms/:realm/webhooks/formats` | `GET` | | List of payload formats | List the installed [payload formats](webhook-payload-formats.md) |
| `/auth/realms/:realm/webhooks/:id` | `GET` | | Webhook object | Get a webhook |
| `/auth/realms/:realm/webhooks/:id` | `PUT` | Webhook object | `204` | Update a webhook |
| `/auth/realms/:realm/webhooks/:id` | `DELETE` | | `204` | Delete a webhook |
| `/auth/realms/:realm/webhooks/:id/secret` | `GET` | | Secret object | Get a webhook's `secret` (requires `manage-events`) |

### The webhook object

```json
{
  "id": "475cd2fd-3ca8-4c22-b5c8-c8b8927dcc10",
  "enabled": true,
  "url": "https://example.com/some/webhook",
  "secret": "ofj09saP4",
  "authType": "hmac",
  "algorithm": "HmacSHA256",
  "audience": null,
  "eventTypes": ["*"],
  "payloadFormat": null,
  "config": {},
  "createdBy": "ff730b72-a421-4f6e-9e4e-7fc7f53bac88",
  "createdAt": "2021-04-21T18:25:43-05:00"
}
```

| Field | Description |
| --- | --- |
| `id`, `createdBy`, `createdAt` | Set by the server. Ignored when creating or updating. |
| `enabled` | Whether events are sent to this webhook |
| `url` | Where events are posted |
| `eventTypes` | Which events are sent (see [Event types](#event-types)) |
| `authType` | How requests are authenticated (see [Authentication](#authentication)) |
| `secret` | The shared secret for `hmac`. It is never returned when webhooks are read; use the `/secret` endpoint. |
| `algorithm` | The signing algorithm for `hmac` or `bearer` |
| `audience` | The token audience for `bearer` |
| `payloadFormat` | A [payload format](webhook-payload-formats.md) id. Leave it out to send the keycloak-events JSON payload. |
| `config` | Settings for the payload format and for the `headers` and `oauth2-client-credentials` auth types, as a JSON object of string values. Secret values are returned as `**********`. |

### Event types

`eventTypes` is a list of expressions. An event is sent to a webhook when its type matches any of them.

| Expression | Matches |
| --- | --- |
| `*` | Every event |
| `access.*` | Every user event |
| `admin.*` | Every admin event |
| `system.*` | Every system event |
| `access.LOGIN` | Exactly that type |
| `admin.USER-.*` | A regular expression, here every admin event on users |

An expression that isn't one of the first four is tried as a [Java regular expression](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html) against the whole type, and then as an exact match. In a regular expression `.` matches any character, so `access.LOGIN` works as you would expect.

## Authentication

Each webhook authenticates its requests in one of these ways, selected by `authType`:

| `authType` | Header sent | How the receiver verifies it |
| --- | --- | --- |
| `none` | | The request is not authenticated. |
| `hmac` | `X-Keycloak-Signature` | Compute the HMAC of the raw request body with the shared `secret` and compare. |
| `bearer` | `Authorization: Bearer <jwt>` | Verify a short-lived JWT signed by the realm against the realm's published keys. No shared secret. |
| `headers` | Any, from `config` | Static credentials such as an API key header. See [Authenticating to an ingestion API](webhook-payload-formats.md#authenticating-to-an-ingestion-api). |
| `oauth2-client-credentials` | `Authorization: Bearer <token>` | A token from an OAuth2 client credentials grant. See [Authenticating to an ingestion API](webhook-payload-formats.md#authenticating-to-an-ingestion-api). |

When `authType` is left out, a webhook with a `secret` uses `hmac` and one without uses `none`.

### HMAC

Set `authType` to `hmac` and provide a `secret`. `algorithm` is `HmacSHA256` by default; `HmacSHA1` is available for backwards compatibility.

The [RFC 2104](https://datatracker.ietf.org/doc/html/rfc2104) HMAC of the request body, in lowercase hex, is sent in the `X-Keycloak-Signature` header. The receiver must compute the HMAC over the raw body bytes, before parsing the JSON.

### Bearer JWT

Set `authType` to `bearer` and provide an `audience`. For each send, Keycloak creates a JWT signed with the realm's active signing key (`algorithm` is `RS256` by default) and sends it in the `Authorization` header. The receiver validates it against the realm's published keys, the same ones used for access tokens, so no secret is exchanged or stored:

```json
{
  "jwks_uri": "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/certs",
  "issuer": "https://keycloak.example.com/realms/my-realm",
  "audience": "https://receiver.example.com"
}
```

The token has these claims:

| Claim | Value |
| --- | --- |
| `iss` | The realm's issuer, e.g. `https://keycloak.example.com/realms/my-realm` |
| `aud` | The webhook's `audience` |
| `iat`, `exp` | When the token was issued and when it expires. It lasts 300 seconds by default; set the `WEBHOOK_JWT_LIFESPAN_SECONDS` environment variable to change that. A new token is created for every attempt, including retries. |
| `jti` | A unique token id |
| `request_body_sha256` | The SHA-256 of the exact request body, in hex. It ties the token to the body, so the token can't be replayed with a different one. |

**Set the realm's frontend URL or `KC_HOSTNAME` in production.** The issuer is taken from the realm's frontend URL, then the `KC_HOSTNAME` environment variable, then the URL of the request that produced the event. Webhooks are sent from a background thread where there is no request, so without one of the first two the `iss` claim may not match the realm's real issuer.

## Payload formats

A payload format changes what a webhook posts, so that events can go straight to a log or SIEM platform's ingestion API in the shape that platform expects.

- [Webhook payload formats](webhook-payload-formats.md): choosing a format, authenticating to ingestion APIs, and writing your own
- [Microsoft Sentinel](payload-formats/azure-sentinel.md) (`azure-sentinel`)
- [Datadog](payload-formats/datadog.md) (`datadog`)
- [Google SecOps](payload-formats/google-secops.md) (`google-secops`)

## Delivery and retries

A receiver should answer `2xx` straight away and do its work afterwards. Any other response, or no response, is a failure and is retried:

| | |
| --- | --- |
| First retry | After about 500 milliseconds |
| Each following retry | 5 times the previous interval, up to 3 minutes, randomized by 50% either way |
| Gives up | 15 minutes after the first attempt |

Payload formats can mark some responses as not worth retrying, such as a rejected API key. The page for each format lists them.

## Recording what was sent

Two server options record each send attempt. They are independent of each other, and both are off by default.

| Option | CLI option | Environment variable | What it does |
| --- | --- | --- | --- |
| `storeWebhookEvents` | `--spi-events-listener-ext-event-webhook-store-webhook-events=true` | `KC_SPI_EVENTS_LISTENER_EXT_EVENT_WEBHOOK_STORE_WEBHOOK_EVENTS=true` | Saves events and send attempts to the database, and turns on the delivery history endpoints |
| `logWebhookEvents` | `--spi-events-listener-ext-event-webhook-log-webhook-events=true` | `KC_SPI_EVENTS_LISTENER_EXT_EVENT_WEBHOOK_LOG_WEBHOOK_EVENTS=true` | Writes a log message for each send attempt |

Neither option records:

- **Sends from the [catch-all webhook](#the-catch-all-webhook).** Only webhooks created through the API are recorded.
- **Events that aren't Keycloak user or admin events,** such as [events you publish](publishing-events.md).
- **Attempts that got no HTTP response,** such as a refused connection, a timeout or a DNS failure. Those are still retried.

### Storing webhook events and sends

With `storeWebhookEvents=true`, each event's payload and the status of each send are saved, and these endpoints are available:

| Path | Method | Returns | Description |
| --- | --- | --- | --- |
| `/auth/realms/:realm/webhooks/:id/sends` | `GET` | Webhook send objects, without payloads | List a webhook's sends. Takes `first` and `max` query parameters. |
| `/auth/realms/:realm/webhooks/:id/sends/:sid` | `GET` | Webhook send object, with its payload | Get one send |
| `/auth/realms/:realm/webhooks/:id/sends/:sid/resend` | `POST` | `202` | Send the same payload again (requires `manage-events`) |
| `/auth/realms/:realm/webhooks/sends/:type/:eventId` | `GET` | Webhook send objects, without payloads | List the sends for a Keycloak event. `:type` is `user` or `admin`. |
| `/auth/realms/:realm/webhooks/payload/:type/:eventId` | `GET` | Event object | Get the stored payload for a Keycloak event. `:type` is `user` or `admin`. |

Events are stored only for realms that save them. In **Realm settings** > **Events**, turn on **Save events** for user events, admin events or both. A user event type is stored only if it is among the realm's saved event types.

Storage uses Keycloak's configured event store, so it doesn't work with the write-only [MDC logger event store](mdc-logger-event-store.md).

### Logging webhook send attempts

With `logWebhookEvents=true`, each send attempt that gets an HTTP response writes one `INFO` message with the text `Webhook Send` to the logger `io.phasetwo.keycloak.WEBHOOK_SEND_LOGGER`. Nothing is written to the database, so this suits servers that ship delivery records to a log pipeline.

The details are in the logging [MDC](https://docs.jboss.org/jbosslogging/latest/org/jboss/logging/MDC.html), so a JSON log encoder can index them as fields. A key is left out when it has no value.

| Key | Value |
| --- | --- |
| `webhook.eventType` | `USER` or `ADMIN` |
| `webhook.eventId` | The id of the Keycloak event |
| `webhook.webhookId` | The id of the webhook |
| `webhook.sendId` | The id of this send. It matches the `uid` in the payload. |
| `webhook.status` | The HTTP status the receiver returned |
| `webhook.retryNum` | The attempt number: `1` for the first try, one more for each retry |
| `webhook.sentAt` | When the response was received, in epoch milliseconds |
| `webhook.rawPayload` | The body that was posted |

To set the logger's level, configure it like any other log category:

```
--log-level=info,io.phasetwo.keycloak.WEBHOOK_SEND_LOGGER:info
```

## The catch-all webhook

A server operator can send every event from every realm to one endpoint, without creating webhooks through the API. Realm administrators can't see or change it. It is meant for cases such as copying all events to a store outside Keycloak.

It is configured with environment variables:

| Environment variable | Description |
| --- | --- |
| `WEBHOOK_URI` | The URL to post events to. Setting it turns the catch-all webhook on. |
| `WEBHOOK_SECRET` | The shared secret for HMAC signing |
| `WEBHOOK_AUTH_TYPE` | Any [auth type](#authentication). Without it, `hmac` is used when `WEBHOOK_SECRET` is set. |
| `WEBHOOK_ALGORITHM` | The signing algorithm for `hmac` or `bearer` |
| `WEBHOOK_AUDIENCE` | The token audience for `bearer` |
| `WEBHOOK_FORMAT` | A [payload format](webhook-payload-formats.md) id |
| `WEBHOOK_CONFIG` | The webhook `config`, as a JSON object of string values |

For example, signed with a shared secret:

```
WEBHOOK_URI=https://receiver.example.com/all-events
WEBHOOK_SECRET=A3jt6D8lz
```

Or authenticated with a bearer JWT:

```
WEBHOOK_URI=https://receiver.example.com/all-events
WEBHOOK_AUTH_TYPE=bearer
WEBHOOK_AUDIENCE=https://receiver.example.com
```

The `ext-event-webhook` listener must still be enabled in each realm whose events should be sent.
