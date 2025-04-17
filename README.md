> :rocket: **Try it for free** in the new Phase Two [keycloak managed service](https://phasetwo.io/?utm_source=github&utm_medium=readme&utm_campaign=keycloak-events). See the [announcement and demo video](https://phasetwo.io/blog/self-service/) for more information.

# keycloak-events

Useful Keycloak `EventListenerProvider` implementations and utilities.

- [Generic script event listener](#script)
- [Event emitter to send events to an HTTP endpoint](#http-sender)
- [A mechanism for retrieving event listener configurations from realm attributes](#adding-configuration-to-your-eventlistenerprovider)
- [A mechanism for running multiple event listeners of the same type with different configurations](#enabling-running-multiple-eventlistenerprovider-instances-of-the-same-type)
- [Base classes for a User added/removed listener](#user-change-listener)
- [A unified event model with facility for subscribing to webhooks](#webhooks)

## Quick start

The easiest way to get started is our [Docker image](https://quay.io/repository/phasetwo/phasetwo-keycloak?tab=tags). Documentation and examples for using it are in the [phasetwo-containers](https://github.com/p2-inc/phasetwo-containers) repo. The most recent version of this extension is included.

## Compatibility

The rate of breaking changes upstream in Keycloak make it impossible for us to support anything but the most recent version indicated in the `pom.xml`.

## Installation

The maven build can be triggered by running `mvn clean install`. It uses the shade plugin to package a fat-jar with all dependencies. Put the jar in your `providers` directory (for Quarkus) or `standalone/deployments` directory (for legacy) and rebuild/restart keycloak.

### Releases

You can also download a release jar directly from [Maven Central](https://central.sonatype.com/artifact/io.phasetwo.keycloak/keycloak-events).

## Use

The `EventListenerProvider` implementations in this library rely on two utilities packaged within.

1. The first is that configuration is loaded from Realm attributes. This means that you can update the configuration for these implementations at runtime by either writing directly to the `realm_attributes` table or by calling the [Realm Update](https://www.keycloak.org/docs-api/latest/rest-api/index.html#_put_adminrealmsrealm) method. Also, In order to make using these easier, there is included a `RealmAttributesResource` that allows you to CRUD the attributes separately from updating the whole realm. It's available at the `/auth/realms/<realm>/attributes` endpoint. Attribute keys are `_providerConfig.<provider_id>.<optional:N>`, and the configurations are stored in the value field as JSON. Currently only single depth JSON objects are supported.
2. The optional `N` value in the key is relevant to the second important utility. The `EventListenerProviderFactory` implementations are all subclasses of `MultiEventListenerProviderFactory`, which enables multiple `EventListenerProvider` instances of the same type to run with different configurations. This is a facility that is not currently available in Keycloak, although some tickets and features in the future Admin UI indicate that it is coming soon.

Most events require being enabled through the Admin UI. Go to (Configure) Realm Settings > Events (tab) > Event listeners. In the Event listeners dropdown select `ext-event-********` for the appropriate event being enabled.

### Script

The script event listener allows you to run JS event listeners in the same fashion as the `ScriptBasedAuthenticator`. The following script bindings are present for the `onEvent` and `onAdminEvent` methods.

- `event`: the Event or AdminEvent
- `realm`: the RealmModel
- `user`: the UserModel (for `onEvent` only)
- `authUser`: the UserModel (for `onAdminEvent` only)
- `session`: the KeycloakSession
- `LOG`: a JBoss Logger

From the Keycloak admin UI "Events"->"Config" section, add `ext-event-script` to the "Event Listeners" form field and click "Save".

The script event listener is configured by setting a Realm attribute in the Realm you have enabled the listener with a `_providerConfig.ext-event-script.N` key. The `N` value should correspond to a unique integer index for each script you want to run, and scripts will be run in that order.

The configuration requires 3 mandatory values:
| Name | Required | Default | Description |
| -----| -------- | ------- | ----------- |
| `scriptName` | Y | | The name of the script |
| `scriptDescription` | Y | | A description of what the script does |
| `scriptCode` | Y | | The JS code |

A trivial example:

```js
function onEvent(event) {
  LOG.info(
    event.type + " in realm " + realm.name + " for user " + user.username
  );
}

function onAdminEvent(event, representation) {
  LOG.info(
    event.operationType +
      " on " +
      event.resourceType +
      " in realm " +
      realm.name +
      " by user " +
      authUser.username
  );
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

### Webhooks

This provides the entities and REST endpoints required to allow webhook subscriptions to events. The events have been slightly modified so that there are no longer 2 types of events, but are now distinguished by a type prefix. Definition on the event format and types is available in the [Phase Two](https://phasetwo.io/) documentation under [Audit Logs](https://phasetwo.io/docs/audit-logs/).

Webhooks are sent using the same mechanics as the `HttpSenderEventListenerProvider`, and there is an automatic exponential backoff if there is not a 2xx response. The sending tasks are scheduled in a thread pool and executed after the Keycloak transaction has been committed.

Enable webhook events in the Admin UI by going to (Configure) Realm Settings > Events (tab) > Event Listeners, in the Event listeners dropdown select `ext-event-webhook` and save.

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
  "eventTypes": ["*"],
  "createdBy": "ff730b72-a421-4f6e-9e4e-7fc7f53bac88",
  "createdAt": "2021-04-21T18:25:43-05:00"
}
```

For creating and updating of webhooks, `id`, `createdBy` and `createdAt` are ignored. `secret` is not sent when fetching webhooks.

#### Storing webhook events and sends

This extension contains the functionality to store and retrieve the payload that was sent to a webhook, as well as the sending status. In order to enable this functionality, you must set the SPI config variable `--spi-events-listener-ext-event-webhook-store-webhook-events=true` and ensure that your realm settings have events and admin events enabled, which causes them to be stored using the configured `EventStoreProvider`.

This also enables a few additional custom REST endpoints for querying information about the payload and status of webhook sends.

| Path                               | Method   | Payload        | Returns                 | Description    |
| ---------------------------------- | -------- | -------------- | ----------------------- | -------------- |
| `/auth/realms/:realm/webhooks/:id/sends`             | `GET`    | `first`, `max` query params for pagination | Webhook send objects (brief)       | Get webhook sends        |
| `/auth/realms/:realm/webhooks/:id/sends/:sid`        | `GET`    |                                            | Webhook send object (with payload) | Get a webhook send       |
| `/auth/realms/:realm/webhooks/:id/sends/:sid/resend` | `POST`   |                                            | `202`                              | Resend a webhook payload |


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
There is a special catch-all webhook that can be used by system owners to always send events to an endpoint, even though it is not defined as a manageable webhook entity. Set the `WEBHOOK_URI` AND `WEBHOOK_SECRET` environtment variables, and all events will be sent to this endpoint. This is used, for example, in cases where system owners want to send events to a more scalable store.

---

All documentation, source code and other files in this repository are Copyright 2025 Phase Two, Inc.
