# Google Security Operations (`google-secops`)

The `google-secops` payload format sends Keycloak events to a Google Security Operations (formerly Chronicle) **webhook feed** with the **Keycloak** log type, so that Google's [default Keycloak parser](https://cloud.google.com/chronicle/docs/ingestion/default-parsers/keycloak) turns them into UDM events.

The field mapping Google publishes for that parser reads a change-record envelope: the event's fields under `payload` (`payload.type`, `payload.ip_address`, `payload.session_id`, ...), with `source_timestamp`, `source_metadata` and `uuid` beside it. This format wraps each event in that envelope, as a row of Keycloak's `EVENT_ENTITY` or `ADMIN_EVENT_ENTITY` table, with the plain Keycloak event type (`LOGIN`, not `access.LOGIN`).

Google's guide describes sending keycloak-events' default payload to the feed, with the credentials in the URL. It was written before this format and the `headers` auth type existed. The default payload has none of the fields in Google's mapping table, which is why this format exists. See [How events map to UDM](#how-events-map-to-udm) for what has and hasn't been verified.

See [Webhook payload formats](../webhook-payload-formats.md) for how formats and the `headers` auth type work in general.

## Set up Google SecOps

These steps follow Google's [webhook feed setup](https://cloud.google.com/chronicle/docs/administration/feed-management#set-up-webhook-feed). Your SecOps instance must be linked to a Google Cloud project with the Chronicle API enabled.

1. **Create the feed.** In Google SecOps, go to **Settings** > **Feeds** (**SIEM Settings** > **Feeds** in some consoles) and click **Add New Feed**. Give it a name, choose source type **Webhook** and log type **Keycloak**, and submit it.
2. **Generate the secret key.** Click **Generate Secret Key** and copy it. It is shown only once, and generating a new one invalidates the old one.
3. **Copy the endpoint URL** from the **Endpoint Information** field on the feed's **Details** tab. Use the whole URL exactly as shown.
4. **Create an API key** in the Google Cloud project linked to your SecOps instance: **APIs & Services** > **Credentials** > **Create credentials** > **API key**. Restrict it to the Chronicle API.

## Create the webhook

```
POST /auth/realms/:realm/webhooks
```

```json
{
  "enabled": true,
  "url": "<feed endpoint URL>",
  "eventTypes": ["access.*", "admin.*"],
  "payloadFormat": "google-secops",
  "authType": "headers",
  "config": {
    "header.x-goog-chronicle-auth": "<api-key>",
    "header.x-chronicle-auth": "<feed-secret>"
  }
}
```

**Which header names to use.** Google documents two pairs of header names for webhook feeds, and which one a feed accepts has not been confirmed against a live tenant. The example uses the pair from Google's Keycloak guide. If the feed answers `401` or `403`, use the other pair.

| | API key | Feed secret key |
| --- | --- | --- |
| Google's [Keycloak guide](https://cloud.google.com/chronicle/docs/ingestion/default-parsers/keycloak) | `x-goog-chronicle-auth` | `x-chronicle-auth` |
| Google's [feed management guide](https://cloud.google.com/chronicle/docs/administration/feed-management#set-up-webhook-feed) | `X-goog-api-key` | `X-Webhook-Access-Key` |

To use the second pair, name the `config` entries `header.X-goog-api-key` and `header.X-Webhook-Access-Key`.

**Why headers and not the URL.** Google also accepts the API key and secret as `key` and `secret` query parameters on the URL. Headers are safer: `config` header values are masked by the API, while the URL is returned as-is and appears in [send logs](../webhooks.md#logging-webhook-send-attempts).

To send every realm's events from one server-wide webhook instead, use the catch-all webhook:

```
WEBHOOK_URI=<feed endpoint URL>
WEBHOOK_FORMAT=google-secops
WEBHOOK_AUTH_TYPE=headers
WEBHOOK_CONFIG={"header.x-goog-chronicle-auth":"<api-key>","header.x-chronicle-auth":"<feed-secret>"}
```

The format has no options of its own.

## The records

Each request posts one event as a change record:

| Field | Contents |
| --- | --- |
| `uuid` | The id of this webhook send |
| `read_timestamp` | When the record was sent |
| `source_timestamp` | When the event happened |
| `object` | `keycloak_event_entity` for user events, `keycloak_admin_event_entity` for admin events |
| `read_method` | `keycloak-events-webhook` |
| `source_metadata` | `schema` `keycloak`, `table` `event_entity` or `admin_event_entity`, `change_type` `INSERT` |
| `payload` | The event as a table row, with snake_case column names |

A user event's `payload` has the columns of `EVENT_ENTITY`: `id`, `type`, `realm_id`, `client_id`, `user_id`, `session_id`, `ip_address`, `error`, `event_time` (epoch milliseconds) and `details_json`. `details_json` is the event details as a JSON string. It always includes `username` when the user is known, because the parser reads the user name from there.

A login:

```json
{
  "uuid": "be3aa526-32b3-4097-ac6c-c0bc7a2159cc",
  "read_timestamp": "2026-11-16T13:44:30.815Z",
  "source_timestamp": "2026-11-16T13:44:30.813Z",
  "object": "keycloak_event_entity",
  "read_method": "keycloak-events-webhook",
  "source_metadata": {
    "schema": "keycloak",
    "table": "event_entity",
    "is_deleted": false,
    "change_type": "INSERT"
  },
  "payload": {
    "id": "e44b8d5b-f5e2-48d3-bb4f-c362de327e3f",
    "type": "LOGIN",
    "realm_id": "cb1b71f7-74b5-400f-8928-6ae3ecd4ecd6",
    "client_id": "admin-cli",
    "user_id": "a048b280-0bda-46cf-85bc-b1ddf0c12c7e",
    "session_id": "EFCFgRjoNaquQLsEI4c-ZOaP",
    "ip_address": "192.168.65.1",
    "event_time": 1794836670813,
    "details_json": "{\"auth_method\":\"openid-connect\",\"grant_type\":\"password\",\"username\":\"admin\"}"
  }
}
```

An admin event's `payload` has the columns of `ADMIN_EVENT_ENTITY`: `id`, `admin_event_time`, `realm_id`, `operation_type`, `resource_type`, `resource_path`, `auth_realm_id`, `auth_client_id`, `auth_user_id`, `ip_address`, `error`, `representation` and `details_json`. It also has a `type` of `<RESOURCE>-<OPERATION>`, e.g. `USER-CREATE`.

```json
{
  "uuid": "cbeecef1-a77e-4067-9065-fe6b2e76ec61",
  "read_timestamp": "2026-11-16T13:44:30.838Z",
  "source_timestamp": "2026-11-16T13:44:30.835Z",
  "object": "keycloak_admin_event_entity",
  "read_method": "keycloak-events-webhook",
  "source_metadata": {
    "schema": "keycloak",
    "table": "admin_event_entity",
    "is_deleted": false,
    "change_type": "INSERT"
  },
  "payload": {
    "id": "ce9b301e-303d-40f4-9ce1-682cc3816a2d",
    "type": "USER-CREATE",
    "admin_event_time": 1794836670835,
    "realm_id": "cb1b71f7-74b5-400f-8928-6ae3ecd4ecd6",
    "operation_type": "CREATE",
    "resource_type": "USER",
    "resource_path": "users/a80eb6f7-b527-4b85-bd84-b30ec138200a",
    "auth_realm_id": "master",
    "auth_client_id": "cd6a1bae-09a5-4d67-9693-b903975fed66",
    "auth_user_id": "a048b280-0bda-46cf-85bc-b1ddf0c12c7e",
    "ip_address": "192.168.65.1",
    "representation": "{\"username\":\"secops-user\",\"enabled\":true}",
    "details_json": "{\"userId\":\"a80eb6f7-b527-4b85-bd84-b30ec138200a\",\"username\":\"secops-user\"}"
  }
}
```

## How events map to UDM

The parser is Google's, and it changes over time. Google publishes its field mapping on the [Keycloak parser page](https://cloud.google.com/chronicle/docs/ingestion/default-parsers/keycloak). The records above follow that mapping, but have **not been checked against a live SecOps tenant**. After setting up the feed, check a few events in your tenant before relying on detections:

- A `LOGIN` should become a `USER_LOGIN` event with the principal user and IP address filled in.
- Admin events may be parsed as generic events: the parser's documentation describes user events only.

Search for the parsed events with UDM search:

```
metadata.log_type = "KEYCLOAK"
```

```
metadata.log_type = "KEYCLOAK" AND metadata.event_type = "USER_LOGIN"
```

If events are ingested but parse poorly, a [parser extension](https://cloud.google.com/chronicle/docs/event-processing/parser-extensions) can map more fields from the raw record.

## Retries

| Response | What happens |
| --- | --- |
| `200` | Sent |
| `400`, `401`, `403`, `404`, `413` | Not retried: a malformed request, a wrong API key or feed secret, an unknown feed, or a request that is too large |
| `429`, `5xx`, network errors | Retried with backoff |

## Limits

- Each request carries one event. Webhook feeds accept up to 4 MB per request and 15,000 requests per second per SecOps instance.

## Troubleshooting

Enable [logging of webhook send attempts](../webhooks.md#logging-webhook-send-attempts) to see each response status.

| Symptom | Check |
| --- | --- |
| `401` or `403` | `header.x-goog-chronicle-auth` is an API key from the project linked to SecOps, allowed to call the Chronicle API, and `header.x-chronicle-auth` is the feed's current secret. Generating a new secret invalidates the old one. |
| `404` | The URL is the feed's whole endpoint URL, copied from the **Endpoint Information** field of the feed's details. |
| `200` but no events | Check the feed's status in SIEM Settings > Feeds, and search the raw logs for `KEYCLOAK`. |
