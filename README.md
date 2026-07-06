> :rocket: **Try it for free** in the new Phase Two [keycloak managed service](https://phasetwo.io/?utm_source=github&utm_medium=readme&utm_campaign=keycloak-events). See the [announcement and demo video](https://phasetwo.io/blog/self-service/) for more information.

# keycloak-events

Useful Keycloak `EventListenerProvider` implementations and utilities.

- [Generic script event listener](#script)
- [Event emitter to send events to an HTTP endpoint](#http-sender)
- [A mechanism for retrieving event listener configurations from realm attributes](#adding-configuration-to-your-eventlistenerprovider)
- [A mechanism for running multiple event listeners of the same type with different configurations](#enabling-running-multiple-eventlistenerprovider-instances-of-the-same-type)
- [Base classes for a User added/removed listener](#user-change-listener)
- [A unified event model with facility for subscribing to webhooks](#webhooks)
- [An EventStoreProvider that writes events to a JBoss logger via MDC](#mdc-logger-event-store)

## Quick start

The easiest way to get started is our [Docker image](https://quay.io/repository/phasetwo/phasetwo-keycloak?tab=tags). Documentation and examples for using it are in the [phasetwo-containers](https://github.com/p2-inc/phasetwo-containers) repo. The most recent version of this extension is included.

## Compatibility

The rate of breaking changes upstream in Keycloak make it impossible for us to support anything but the most recent version indicated in the `pom.xml`.

## Installation

The maven build can be triggered by running `mvn clean install`. It uses the shade plugin to package a fat-jar with all dependencies. Put the jar in your `providers` directory (for Quarkus) or `standalone/deployments` directory (for legacy) and rebuild/restart keycloak. The build enforces Google Java formatting standards via [Spotless](https://github.com/diffplug/spotless); run `mvn spotless:apply` to auto-format your code before committing.

### Code formatting

Spotless replaces the previously used Spotify `fmt-maven-plugin`. Use `mvn spotless:check` to verify formatting and `mvn spotless:apply` to fix it.

To enforce formatting automatically before every push, install the provided git pre-push hook:

```
mvn spotless:install-git-pre-push-hook
```

When you push, the hook runs `spotless:check`. If violations are found, it automatically runs `spotless:apply`, aborts the push, and lets you review and commit the formatted files before retrying.

### Releases

You can also download a release jar directly from [Maven Central](https://central.sonatype.com/artifact/io.phasetwo.keycloak/keycloak-events).

## Use

The `EventListenerProvider` implementations in this library rely on two utilities packaged within.

1. The first is that configuration is loaded from Realm attributes. This means that you can update the configuration for these implementations at runtime by either writing directly to the `realm_attributes` table or by calling the [Realm Update](https://www.keycloak.org/docs-api/latest/rest-api/index.html#_put_adminrealmsrealm) method. Also, In order to make using these easier, there is included a `RealmAttributesResource` that allows you to CRUD the attributes separately from updating the whole realm. It's available at the `/auth/realms/<realm>/attributes` endpoint. Attribute keys are `_providerConfig.<provider_id>.<optional:N>`, and the configurations are stored in the value field as JSON. Currently only single depth JSON objects are supported.
2. The optional `N` value in the key is relevant to the second important utility. The `EventListenerProviderFactory` implementations are all subclasses of `MultiEventListenerProviderFactory`, which enables multiple `EventListenerProvider` instances of the same type to run with different configurations. This is a facility that is not currently available in Keycloak, although some tickets and features in the future Admin UI indicate that it is coming soon.

Most events require being enabled through the Admin UI. Go to (Configure) Realm Settings > Events (tab) > Event listeners. In the Event listeners dropdown, select the specific provider ID (e.g., `ext-event-script`, `ext-event-webhook`) for the event listener you want to enable, and click "Save".

### Script

The script event listener allows you to run JS event listeners in the same fashion as the `ScriptBasedAuthenticator`. The following script bindings are present for the `onEvent` and `onAdminEvent` methods.

- `event`: the Event or AdminEvent
- `realm`: the RealmModel
- `user`: the UserModel (for `onEvent` only)
- `authUser`: the UserModel (for `onAdminEvent` only)
- `session`: the KeycloakSession
- `LOG`: a JBoss Logger

#### Steps to Configure the Script Event Listener

1. **Enable the Script Event Listener in the Admin UI**:
   - Go to the Keycloak Admin UI.
   - Navigate to (Configure) Realm Settings > Events (tab) > Event listeners.
   - In the Event listeners dropdown, select `ext-event-script` and click "Save".

2. **Configure the Script via Realm Attribute**:
   - The script is configured by setting a Realm attribute.
   - **Note**: Keycloak's built-in Admin UI theme does not expose realm attributes for editing. To set this attribute, you have two options:
     - Use the Phase Two Keycloak image with the admin theme set to `phasetwo.v2`, which provides a UI for managing realm attributes.
     - Set the attribute via an API call to the [Realm Update](https://www.keycloak.org/docs-api/latest/rest-api/index.html#_put_adminrealmsrealm) endpoint or directly to the `realm_attributes` table.
   - The attribute key is `_providerConfig.ext-event-script.N`, where `N` is a unique integer index (e.g., 0, 1, 2) for each script you want to run. Scripts are executed in order of their index.
   - The attribute value must be a JSON object containing the following mandatory fields:
     | Name | Required | Default | Description |
     | -----| -------- | ------- | ----------- |
     | `scriptName` | Y | | The name of the script |
     | `scriptDescription` | Y | | A description of what the script does |
     | `scriptCode` | Y | | The JS code |

3. **Example Configuration**:
   - Set the realm attribute `_providerConfig.ext-event-script.0` to the following JSON:
     ```json
     {
       "scriptName": "Example Script",
       "scriptDescription": "Logs events to the console",
       "scriptCode": "function onEvent(event) {\n  LOG.info(event.type + \" in realm \" + realm.name + \" for user \" + user.username);\n}\n\nfunction onAdminEvent(event, representation) {\n  LOG.info(event.operationType + \" on \" + event.resourceType + \" in realm \" + realm.name + \" by user \" + authUser.username);\n}"
     }
     ```

### HTTP Sender

Send the events to a specified URI. May sign the request using keyed-HMAC. Optionally retryable using exponetial backoff.

Configuration values:
| Name | Required | Default | Description |
| -----| -------- | ------- | ----------- |
| `targetUri` | Y | | The URI to send the event payload |
| `sharedSecret` | N | | The shared secret value to use for HMAC signing. If present, the signature according to RFC2104 will be passed as `X-Keycloak-Signature` header |
| `hmacAlgorithm` | N | HmacSHA256 | The HMAC algortihm used for signing. Defaults to HmacSHA256. Can be set to HmacSHA1 for backwards compatibility |
| `retry` | N | true | Should it use exponential backoff to retry on non 2xx response |
| `backoffInitialInterval` | N | 500 | Initial interval value in milliseconds |
| `backoffMaxElapsedTime` | N | 900000 | Maximum elapsed time in milliseconds |
| `backoffMaxInterval` | N | 180000 | Maximum back off time in milliseconds |
| `backoffMultiplier` | N | 5 | Multiplier value (E.g. 1.5 is 50% increase per back off) |
| `backoffRandomizationFactor` | N | 0.5 | Randomization factor (E.g. 0.5 results in a random period ranging between 50% below and 50% above the retry interval) |

### Adding Configuration to your EventListenerProvider

1. Implement the interface `ConfigurationAware` in your `EventListenerProviderFactory`. This doesn't require implementing any methods, but gives you access to the `getConfiguration` and `getConfigurations` methods, which load the configuration from the `realm_attribute` table for that `EventListenerProviderFactory` provider ID.
2. Implement the interface `Configurable` in your `EventListenerProvider`. This requires you to implement one method that takes the configuration map `void setConfig(Map<String, Object> config)`.
3. In your `EventListenerProviderFactory.create(...)` method, call the `setConfig(...)` method on your `EventListenerProvider`.

### Enabling running multiple EventListenerProvider instances of the same type

1. Extend the abstract class `MultiEventListenerProviderFactory`. Implement the abstract method `EventListenerProvider configure(KeycloakSession session, Map<String, Object> config)`, which is otherwise the same as the `create(...)` method, but also takes a config map. This **should not return a singleton**. Take a look at the `ScriptEventListenerProviderFactory` and `HttpSenderEventListenerProviderFactory` as examples.

### User change listener

This provides a base class for the ever-requested "do something when a user is added or removed". It listens for an admin user creation event, a user registration event to detect user adds, and uses the internal `ProviderEvent` `UserModel.UserRemovedEvent` to detect user removals. This does nothing on it's own, but must be subclassed with your `onUserAdded`/`onUserRemoved` implementation.

Please note that this has not been tested with users added via Identity/Federated Providers, and it may not catch the appropriate events for those. There is future work to verify that this does/not work in those cases, and potentially implement a wrapper to the `UserStorageProvider` which would correctly intercept those events.

For example:

```java
public class MyUserAddRemove extends UserEventListenerProviderFactory {

  @Override
  public String getId() {
    return "ext-event-myuseraddremove";
  }

  @Override
  UserChangedHandler getUserChangedHandler() {
    return new UserChangedHandler() {
      @Override
      void onUserAdded(KeycloakSession session, RealmModel realm, UserModel user) {
        log.infof("User %s added to Realm %s", user.getUsername(), realm.getName());
      }

      @Override
      void onUserRemoved(KeycloakSession session, RealmModel realm, UserModel user) {
        log.infof("User %s removed from Realm %s", user.getUsername(), realm.getName());
	  }
    };
  }

}
```

### MDC Logger Event Store

An `EventStoreProvider` implementation that emits each user and admin event as a structured JBoss log message. The flattened event fields are put into the [MDC](https://docs.jboss.org/jbosslogging/latest/org/jboss/logging/MDC.html) under the `event.` prefix for the duration of the log call, so downstream log appenders (e.g. a JSON encoder shipping to Loki, Elasticsearch, or CloudWatch) can index them as first-class fields.

Two named JBoss loggers are used so user and admin events can be routed independently:

- `io.phasetwo.keycloak.EVENT_LOGGER` — user events (`onEvent(Event)`)
- `io.phasetwo.keycloak.ADMIN_EVENT_LOGGER` — admin events (`onEvent(AdminEvent, boolean)`)

Both emit an `INFO`-level message with the body `Event Logger`. The interesting data is in the MDC.

#### MDC fields

For user events (`FlatEvent`), the following MDC keys are set (omitted when null):

| Key | Value |
| --- | --- |
| `event.class` | `USER` |
| `event.id` | Event id (auto-generated UUID if Keycloak did not set one) |
| `event.type` | `EventType` name, e.g. `LOGIN`, `LOGIN_ERROR` |
| `event.realmId` | Realm UUID |
| `event.realmName` | Realm name |
| `event.clientId` | Client id |
| `event.userId` | User UUID |
| `event.sessionId` | Session id |
| `event.ipAddress` | IP address |
| `event.error` | Error code, when present |
| `event.time` | Event timestamp (epoch millis) |
| `event.detailsJson` | JSON-serialized event details map, when present |

For admin events (`FlatAdminEvent`), the following MDC keys are set (omitted when null):

| Key | Value |
| --- | --- |
| `event.class` | `ADMIN` |
| `event.id` | Event id (auto-generated UUID if Keycloak did not set one) |
| `event.time` | Event timestamp (epoch millis) |
| `event.realmId` | Realm UUID |
| `event.realmName` | Realm name |
| `event.operationType` | `OperationType` name, e.g. `CREATE`, `UPDATE` |
| `event.resourceType` | `ResourceType` name, e.g. `USER`, `CLIENT` |
| `event.resourcePath` | Resource path |
| `event.representation` | JSON representation, when `include-representation` is enabled |
| `event.error` | Error code, when present |
| `event.authRealmId` | Authenticating realm UUID |
| `event.authRealmName` | Authenticating realm name |
| `event.authClientId` | Authenticating client id |
| `event.authUserId` | Authenticating user UUID |
| `event.authIpAddress` | Authenticating user IP |
| `event.detailsJson` | JSON-serialized admin event details map, when present |

MDC entries are restored to their previous values immediately after the log call, so the fields do not leak across executor threads.

#### Caveats

This is a write-only sink. The query methods (`createQuery`, `createAdminQuery`) return empty result streams and the clear methods (`clear*`, `clearExpiredEvents`) are no-ops, so any Keycloak feature or REST endpoint that reads stored events (such as the Admin UI events tab) will return an empty result set. If you also need queryable storage, run a separate listener that writes to a queryable destination.

#### Enabling

Keycloak chooses exactly one `EventStoreProvider`, selected via the `events-store` SPI. Activating this provider takes two steps.

**1. Make the provider available.** The factory implements `EnvironmentDependentProviderFactory`, so it only registers when explicitly switched on via either the `EXT_EVENT_MDC_LOGGER_ENABLED` environment variable or the `ext.event.mdc-logger.enabled` system property:

```
EXT_EVENT_MDC_LOGGER_ENABLED=true
```

This flag is read at Keycloak's **build (augmentation) time**, so it must be present when `kc.sh build` runs (and for the auto-build performed by a non-optimized `kc.sh start`). If it is not set, the provider is filtered out and selecting it in step 2 fails the build with `Failed to find provider ext-event-mdc-logger-store for eventsStore`.

**2. Select it as the active `events-store` provider** (see [Keycloak configuration providers](https://www.keycloak.org/server/configuration-provider)).

Via CLI flag passed to `kc.sh start` / `kc.sh start-dev`:

```
--spi-events-store-provider=ext-event-mdc-logger-store
```

Via environment variable:

```
KC_SPI_EVENTS_STORE_PROVIDER=ext-event-mdc-logger-store
```

No further provider-specific configuration is required. To route the two loggers, configure JBoss log levels and handlers the same way as any other category, e.g.:

```
--log-level=info,io.phasetwo.keycloak.EVENT_LOGGER:info,io.phasetwo.keycloak.ADMIN_EVENT_LOGGER:info
```

Also make sure user and admin events are enabled on each realm whose events you want to capture (Realm settings > Events > Event settings).

### Webhooks

This provides the entities and REST endpoints required to allow webhook subscriptions to events. The events have been slightly modified so that there are no longer 2 types of events, but are now distinguished by a type prefix. Definition on the event format and types is available in the [Phase Two](https://phasetwo.io/) documentation under [Audit Logs](https://phasetwo.io/docs/audit-logs/).

Webhooks are sent using the same mechanics as the `HttpSenderEventListenerProvider`, and there is an automatic exponential backoff if there is not a 2xx response. The sending tasks are scheduled in a thread pool and executed after the Keycloak transaction has been committed.

#### Steps to Enable Webhook Events

1. **Enable Webhook Events in the Admin UI**:
   - Go to the Keycloak Admin UI.
   - Navigate to (Configure) Realm Settings > Events (tab) > Event Listeners.
   - In the Event listeners dropdown, select `ext-event-webhook` and save.

#### Managing webhook subscriptions

Webhooks are managed with a custom REST resource with the following methods. Use of these methods requires the authenticated user to have the `view-events` and `manage-events` permissions.

| Path                               | Method   | Payload        | Returns                 | Description    |
| ---------------------------------- | -------- | -------------- | ----------------------- | -------------- |
| `/auth/realms/:realm/webhooks`     | `GET`    |                | List of webhook objects | Get webhooks   |
| `/auth/realms/:realm/webhooks`     | `POST`   | Webhook object | `201`                   | Create webhook |
| `/auth/realms/:realm/webhooks/:id` | `GET`    |                | Webhook object          | Get webhook    |
| `/auth/realms/:realm/webhooks/:id` | `PUT`    | Webhook object | `204`                   | Update webhook |
| `/auth/realms/:realm/webhooks/:id` | `DELETE` | Webhook object | `204`                   | Delete webhook |

The webhook object has this format:

```json
{
  "id": "475cd2fd-3ca8-4c22-b5c8-c8b8927dcc10",
  "enabled": "true",
  "url": "https://example.com/some/webhook",
  "secret": "ofj09saP4",
  "authType": "hmac",
  "algorithm": "HmacSHA256",
  "audience": null,
  "eventTypes": ["*"],
  "createdBy": "ff730b72-a421-4f6e-9e4e-7fc7f53bac88",
  "createdAt": "2021-04-21T18:25:43-05:00"
}
```

For creating and updating of webhooks, `id`, `createdBy` and `createdAt` are ignored. `secret` is not sent when fetching webhooks.

#### Authenticating the webhook payload

Each webhook may authenticate its payload one of two ways, selected by the `authType` field:

| `authType` | Header | How the receiver verifies |
| --- | --- | --- |
| `hmac` (default) | `X-Keycloak-Signature` | Recompute the keyed-HMAC of the raw request body using the shared `secret` and compare. |
| `bearer` | `Authorization: Bearer <jwt>` | Verify a short-lived JWT signed by the realm's active signing key against the realm's JWKS. No shared secret is required. |

**HMAC (shared secret).** Set `secret` (and optionally `algorithm`, defaulting to `HmacSHA256`, or `HmacSHA1` for backwards compatibility). The RFC2104 signature of the request body is sent in the `X-Keycloak-Signature` header. This is the default and is unchanged from previous versions — webhooks created without an `authType` continue to behave exactly as before.

**Bearer JWT (asymmetric, no shared secret).** Set `authType` to `bearer` and provide an `audience`. On each send a fresh JWT is minted, signed with the realm's active signing key (`algorithm` defaults to `RS256`), and sent in the `Authorization: Bearer` header. The receiver validates it against the realm's published keys — the same keys used for access tokens — so no secret needs to be exchanged or stored:

```json
{
  "jwks_uri": "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/certs",
  "issuer": "https://keycloak.example.com/realms/my-realm",
  "audience": "https://receiver.example.com"
}
```

The token carries these claims:

| Claim | Value |
| --- | --- |
| `iss` | The realm issuer, e.g. `https://keycloak.example.com/realms/my-realm` |
| `aud` | The configured `audience` |
| `iat` / `exp` | Issued-at and expiry. Lifespan defaults to 300s; override with the `WEBHOOK_JWT_LIFESPAN_SECONDS` env var. A fresh token is minted for every send attempt, including retries. |
| `jti` | A unique token id |
| `request_body_sha256` | Hex-encoded SHA-256 of the exact request body, binding the token to the payload so it cannot be replayed against a different body. |

The token issuer is resolved, in order, from: the realm's configured frontend URL, the `KC_HOSTNAME` environment variable, and finally the base URI of the request that produced the event. For the issuer to match the realm's real token issuer in production (where webhooks are dispatched on a background thread with no request context), configure the realm frontend URL or `KC_HOSTNAME`.

#### SPI configuration

The `ext-event-webhook` listener exposes two boolean SPI config variables that control what happens after each webhook send attempt. They are independent — either, both, or neither may be enabled.

| SPI config | CLI flag | Env var | Default | Description |
| --- | --- | --- | --- | --- |
| `storeWebhookEvents` | `--spi-events-listener-ext-event-webhook-store-webhook-events=true` | `KC_SPI_EVENTS_LISTENER_EXT_EVENT_WEBHOOK_STORE_WEBHOOK_EVENTS=true` | `false` | Persist webhook events and send attempts via the configured `EventStoreProvider`. Enables the `/sends` REST endpoints. |
| `logWebhookEvents` | `--spi-events-listener-ext-event-webhook-log-webhook-events=true` | `KC_SPI_EVENTS_LISTENER_EXT_EVENT_WEBHOOK_LOG_WEBHOOK_EVENTS=true` | `false` | Emit one JBoss log line per webhook send attempt with the send details in the MDC. See [Logging webhook send attempts](#logging-webhook-send-attempts). |

Both pathways share the following early-return rules, applied before either storage or logging happens:

- Skipped when the send was for the system catch-all webhook (i.e. `WEBHOOK_URI` rather than a managed webhook entity).
- Skipped for non-native event types (`SYSTEM`) — only `USER` and `ADMIN` events produce a store/log record.
- Triggered only when an HTTP response was received from the target. Transport-level failures (connection refused, timeout, DNS) do not produce a store/log record; they are retried per the backoff policy.

#### Storing webhook events and sends

When `storeWebhookEvents=true` and your realm settings have events and admin events enabled, payloads and send statuses are persisted using the configured `EventStoreProvider`. This also enables a few additional custom REST endpoints for querying information about the payload and status of webhook sends.

| Path                               | Method   | Payload        | Returns                 | Description    |
| ---------------------------------- | -------- | -------------- | ----------------------- | -------------- |
| `/auth/realms/:realm/webhooks/:id/sends`             | `GET`    | `first`, `max` query params for pagination | Webhook send objects (brief)       | Get webhook sends        |
| `/auth/realms/:realm/webhooks/:id/sends/:sid`        | `GET`    |                                            | Webhook send object (with payload) | Get a webhook send       |
| `/auth/realms/:realm/webhooks/:id/sends/:sid/resend` | `POST`   |                                            | `202`                              | Resend a webhook payload |

#### Logging webhook send attempts

When `logWebhookEvents=true`, every webhook send attempt that receives an HTTP response emits one `INFO`-level message with the body `Webhook Send` to the named logger:

- `io.phasetwo.keycloak.WEBHOOK_SEND_LOGGER`

The send details are placed in the [MDC](https://docs.jboss.org/jbosslogging/latest/org/jboss/logging/MDC.html) under the `webhook.` prefix for the duration of the log call only, so downstream log appenders (e.g. a JSON encoder shipping to Loki, Elasticsearch, or CloudWatch) can index them as first-class fields. Entries are restored on close, so they do not leak across executor threads.

MDC keys (omitted when null):

| Key | Value |
| --- | --- |
| `webhook.eventType` | `KeycloakEventType` name — `USER` or `ADMIN` |
| `webhook.eventId` | Source Keycloak event id (typically a UUID) |
| `webhook.webhookId` | Id of the `WebhookModel` that defines the endpoint |
| `webhook.sendId` | Id generated for this unique send attempt (matches the `uid` on the payload) |
| `webhook.status` | HTTP status code returned by the target |
| `webhook.retryNum` | Attempt number, starting at `1` on the first try and incrementing on each retry |
| `webhook.sentAt` | Epoch millis at the time the response was received |
| `webhook.rawPayload` | JSON payload that was sent (the same body posted to the webhook URL) |

This pathway does **not** depend on `storeWebhookEvents` and does not write to the database; it is suitable for environments that want webhook delivery telemetry shipped to a log pipeline rather than queried back through Keycloak.

To route the logger, configure JBoss log levels and handlers the same way as any other category, e.g.:

```
--log-level=info,io.phasetwo.keycloak.WEBHOOK_SEND_LOGGER:info
```


##### Example

To create a webhook for all events on the `master` realm:

```
POST /auth/realms/master/webhooks

{
  "enabled": "true",
  "url": "https://en6fowyrouz6q4o.m.pipedream.net",
  "secret": "A3jt6D8lz",
  "eventTypes": [
    "*"
  ]
}
```

[Pipedream](https://pipedream.com/) is a great way to test your webhooks, and use the data to integrate with your other applications.

#### Sending app events

There is also a custom REST resource that allows publishing of arbitrary events. These are subsequently sent to the registered webhooks. In order to publish events, there is a new role `publish-events` which callers must have.

| Path                         | Method | Payload      | Returns                                                                                                                | Description   |
| ---------------------------- | ------ | ------------ | ---------------------------------------------------------------------------------------------------------------------- | ------------- |
| `/auth/realms/:realm/events` | `POST` | Event object | `202 = Event received`<br/>`400 = Malformed event`<br/>`403 = API rate limit exceeded`<br/>`409 = Reserved event type` | Publish event |

#### For system owners
=======

#### Scripts
It is possible to disable the scripts run by the `ScriptEventListenerProvider` by setting `SCRIPTS_DISABLED=true`. This may be desirable in shared environments where it is not ideal to allow user code to run in the Keycloak process. Note that this will just cause the scripts to fail silently.

#### Webhooks
There is a special catch-all webhook that can be used by system owners to always send events to an endpoint, even though it is not defined as a manageable webhook entity. Set the `WEBHOOK_URI` AND `WEBHOOK_SECRET` environment variables, and all events will be sent to this endpoint. This is used, for example, in cases where system owners want to send events to a more scalable store.

The catch-all webhook can also authenticate with a bearer JWT instead of an HMAC shared secret (see [Authenticating the webhook payload](#authenticating-the-webhook-payload)). Set `WEBHOOK_AUTH_TYPE=bearer` and `WEBHOOK_AUDIENCE=<audience>`; `WEBHOOK_ALGORITHM` selects the JWS algorithm (defaults to `RS256`).

---

All documentation, source code and other files in this repository are Copyright 2025 Phase Two, Inc.
