# Google Security Operations (`google-secops`)

The `google-secops` payload format sends Keycloak events to a Google Security Operations (formerly Chronicle) **webhook feed** with the **Keycloak** log type, so that Google's [default Keycloak parser](https://cloud.google.com/chronicle/docs/ingestion/default-parsers/keycloak) turns them into UDM events.

That parser was written for Keycloak's event tables replicated by Google Datastream. This format wraps each event in the same change-record envelope, with the event as a row of Keycloak's `EVENT_ENTITY` or `ADMIN_EVENT_ENTITY` table, and with the plain Keycloak event type (`LOGIN`, not `access.LOGIN`) that the parser classifies events by.

See [Webhook payload formats](../webhook-payload-formats.md) for how formats and the `headers` auth type work in general.

## Set up Google SecOps

1. **Create the feed.** In Google SecOps, go to SIEM Settings > Feeds > Add New Feed. Choose source type **Webhook** and log type **Keycloak**, and create the feed.
2. **Generate a secret key** for the feed from its details page, and copy it. This is the feed secret.
3. **Copy the feed's endpoint URL** from the feed details. It ends in `:importPushLogs`, for example:

   ```
   https://<region>-chronicle.googleapis.com/v1alpha/projects/<project>/locations/<region>/instances/<instance-id>/feeds/<feed-id>:importPushLogs
   ```

4. **Create an API key** in the Google Cloud project linked to your SecOps instance: APIs & Services > Credentials > Create credentials > API key. Restrict it to the Chronicle API.

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

Google's setup guide shows the API key and secret as `key` and `secret` query parameters on the URL. That works too, but sending them as headers keeps them out of the webhook URL: `config` header values are masked by the API, while the URL is returned as-is and appears in [send logs](../../README.md#logging-webhook-send-attempts).

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

The parser is Google's, and it changes over time; Google publishes its field mapping on the [Keycloak parser page](https://cloud.google.com/chronicle/docs/ingestion/default-parsers/keycloak). The records above follow that page and the parser's changelog, but have **not been checked against a live SecOps tenant**. After setting up the feed, check a few events in your tenant before relying on detections:

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

- Each request carries one event. Webhook feeds accept up to 4 MB per request.

## Troubleshooting

Enable [logging of webhook send attempts](../../README.md#logging-webhook-send-attempts) to see each response status.

| Symptom | Check |
| --- | --- |
| `401` or `403` | `header.x-goog-chronicle-auth` is an API key from the project linked to SecOps, allowed to call the Chronicle API, and `header.x-chronicle-auth` is the feed's current secret. Generating a new secret invalidates the old one. |
| `404` | The URL is the feed's endpoint, copied from the feed details. |
| `200` but no events | Check the feed's status in SIEM Settings > Feeds, and search the raw logs for `KEYCLOAK`. |
