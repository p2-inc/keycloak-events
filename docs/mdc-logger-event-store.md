# MDC logger event store (`ext-event-mdc-logger-store`)

This is a Keycloak `EventStoreProvider` that, instead of saving events to the database, writes each user and admin event as a structured log message. The event's fields are put in the logging [MDC](https://docs.jboss.org/jbosslogging/latest/org/jboss/logging/MDC.html), so a JSON log encoder shipping to Loki, Elasticsearch, CloudWatch or similar can index them as fields of their own.

## What it does and doesn't do

- **It replaces Keycloak's event store.** Keycloak uses exactly one event store, so events are no longer saved to the database while this one is selected.
- **It is write-only.** Anything that reads stored events gets an empty result: the **Events** pages of the admin console, the Admin API's event queries, and [stored webhook sends](webhooks.md#storing-webhook-events-and-sends). Clearing and expiring events do nothing.
- **It doesn't ship logs anywhere itself.** It writes to Keycloak's log; your log pipeline does the rest.

If you also need to query events through Keycloak, don't use this store. Use a listener such as [webhooks](webhooks.md) to send events out and keep the default store.

## Enable it

Three steps, the first two on the server and the third in each realm.

### 1. Make the provider available

The provider only registers when it is switched on with the `EXT_EVENT_MDC_LOGGER_ENABLED` environment variable or the `ext.event.mdc-logger.enabled` system property:

```
EXT_EVENT_MDC_LOGGER_ENABLED=true
```

This is read at Keycloak's **build** time, so it must be set when `kc.sh build` runs (or when a non-optimized `kc.sh start` builds automatically). Without it, step 2 fails the build with `Failed to find provider ext-event-mdc-logger-store for eventsStore`.

### 2. Select it as the event store

With a CLI option to `kc.sh start` or `kc.sh start-dev`:

```
--spi-events-store-provider=ext-event-mdc-logger-store
```

Or with an environment variable:

```
KC_SPI_EVENTS_STORE_PROVIDER=ext-event-mdc-logger-store
```

See Keycloak's guide to [configuring providers](https://www.keycloak.org/server/configuration-provider). The store has no options of its own.

### 3. Turn on event saving in each realm

Keycloak only passes events to the store for realms that save them. In each realm, go to **Realm settings** > **Events** and turn on **Save events** under both **User events settings** and **Admin events settings**.

## The log messages

Two loggers are used, so user and admin events can be routed separately:

| Logger | Events |
| --- | --- |
| `io.phasetwo.keycloak.EVENT_LOGGER` | User events |
| `io.phasetwo.keycloak.ADMIN_EVENT_LOGGER` | Admin events |

Each event is one `INFO` message with the text `Event Logger`. The data is in the MDC, under keys that start with `event.`. A key is left out when the event has no value for it.

To set the loggers' levels, configure them like any other log category:

```
--log-level=info,io.phasetwo.keycloak.EVENT_LOGGER:info,io.phasetwo.keycloak.ADMIN_EVENT_LOGGER:info
```

The MDC entries are set only for the log call and then restored, so they don't appear on other log lines from the same thread.

### User events

| Key | Value |
| --- | --- |
| `event.class` | `USER` |
| `event.id` | The event id (a generated UUID if Keycloak didn't set one) |
| `event.type` | The event type, e.g. `LOGIN`, `LOGIN_ERROR` |
| `event.realmId` | The realm's id |
| `event.realmName` | The realm's name |
| `event.clientId` | The client id |
| `event.userId` | The user's id |
| `event.sessionId` | The session id |
| `event.ipAddress` | The IP address |
| `event.error` | The error code, when there is one |
| `event.time` | When the event happened, in epoch milliseconds |
| `event.detailsJson` | The event details, as JSON |

### Admin events

| Key | Value |
| --- | --- |
| `event.class` | `ADMIN` |
| `event.id` | The event id (a generated UUID if Keycloak didn't set one) |
| `event.time` | When the event happened, in epoch milliseconds |
| `event.realmId` | The realm's id |
| `event.realmName` | The realm's name |
| `event.operationType` | The operation, e.g. `CREATE`, `UPDATE` |
| `event.resourceType` | The resource type, e.g. `USER`, `CLIENT` |
| `event.resourcePath` | The resource path |
| `event.representation` | The JSON representation, when the realm includes representations in admin events |
| `event.error` | The error code, when there is one |
| `event.authRealmId` | The id of the realm the admin signed in to |
| `event.authRealmName` | The name of the realm the admin signed in to |
| `event.authClientId` | The client the admin used |
| `event.authUserId` | The admin's user id |
| `event.authIpAddress` | The admin's IP address |
| `event.detailsJson` | The admin event details, as JSON |
