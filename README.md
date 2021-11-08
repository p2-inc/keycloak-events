# keycloak-events

Useful Keycloak `EventListenerProvider` implementations and utilities.

- [Generic script event listener](#script)
- [Event emitter to send events to an HTTP endpoint](#http-sender)
- [A mechanism for retrieving event listener configurations from realm attributes](#adding-configuration-to-your-eventlistenerprovider)
- [A mechanism for running multiple event listeners of the same type with different configurations](#enabling-running-multiple-eventlistenerprovider-instances-of-the-same-type)
- [Base classes for a User added/removed listener](#user-change-listener)

## Compatibility

This is currently known to work with Keycloak > 15.0.2. Other versions may work also. Please file an issue if you have successfully installed it with prior versions.

## Installation

The maven build uses the shade plugin to package a fat-jar with all dependencies. Put the jar in your `standalone/deployments` directory and restart keycloak.

## Use

The `EventListenerProvider` implementations in this library rely on two utilities packaged within.
1. The first is that configuration is loaded from Realm attributes. This means that you can update the configuration for these implementations at runtime by either writing directly to the `realm_attributes` table or by calling the [Realm Update](https://www.keycloak.org/docs-api/15.0/rest-api/index.html#_updaterealm) method. Attribute keys are `_providerConfig.<provider_id>.<optional:N>`, and the configurations are stored in the value field as JSON. Currently only single depth JSON objects are supported.
2. The optional `N` value in the key is relevant to the second important utility. The `EventListenerProviderFactory` implementations are all subclasses of `MultiEventListenerProviderFactory`, which enables multiple `EventListenerProvider` instances of the same type to run with different configurations. This is a facility that is not currently available in Keycloak, although some tickets and features in the future Admin UI indicate that it is coming soon.

### Script

The script event listener allows you to run JS event listeners in the same fashion as the `ScriptBasedAuthenticator`. The following script bindings are present for the `onEvent` and `onAdminEvent` methods.
- `event`: the Event or AdminEvent
- `realm`: the RealmModel
- `user`: the UserModel (for `onEvent` only)
- `session`: the KeycloakSession
- `LOG`: a JBoss Logger

From the Keycloak admin UI "Events"->"Config" section, add `ext-event-script` to the "Event Listeners" form field and click "Save".

The script event listener is configured by setting a Realm attribute in the Realm you have enabled the listener with a `_providerConfig.ext-event-script.N` key. The `N` value should correspond to a unique integer index for each script you want to run, and scripts will be run in that order.

The configuration requires 3 mandatory values:
| Name | Required | Default | Description |
| -----| -------- | ------- | ----------- |
| `scriptName` | Y |  | The name of the script |
| `scriptDescription` | Y |  | A description of what the script does |
| `scriptCode` | Y |  | The JS code |

A trivial example:
```js
function onEvent(event) {
  LOG.info(event.type + " in realm " + realm.name + " for user " + user.username);
}

function onAdminEvent(event, representation) {
  LOG.info(event.operationType + " on " + event.resourceType + " in realm " + realm.name");
}
```

### HTTP Sender

Send the events to a specified URI. May sign the request using keyed-HMAC. Optionally retryable using exponetial backoff.

Configuration values:
| Name | Required | Default | Description |
| -----| -------- | ------- | ----------- |
| `targetUri` | Y |  | The URI to send the event payload |
| `sharedSecret` | N |  | The shared secret value to use for HMAC signing |
| `retry` | N | false | Should it use exponential backoff to retry on non 2xx response |
| `backoffInitialInterval` | N | 500 | Initial interval value in milliseconds |
| `backoffMaxElapsedTime` | N | 900000 | Maximum elapsed time in milliseconds |
| `backoffMaxInterval` | N | 60000 | Maximum back off time in milliseconds |
| `backoffMultiplier` | N | 1.5 | Multiplier value (E.g. 1.5 is 50% increase per back off) |
| `backoffRandomizationFactor` | N | 0.5 | Randomization factor (E.g. 0.5 results in a random period ranging between 50% below and 50% above the retry interval) |

### Adding Configuration to your EventListenerProvider

1. Implement the interface `ConfigurationAware` in your `EventListenerProviderFactory`. This doesn't require implementing any methods, but gives you access to the `getConfiguration` and `getConfigurations` methods, which load the configuration from the `realm_attribute` table for that `EventListenerProviderFactory` provider ID.
2. Implement the interface `Configurable` in your `EventListenerProvider`. This requires you to implement one method that takes the configuration map `void setConfig(Map<String, Object> config)`.
3. In your `EventListenerProviderFactory.create(...)` method, call the `setConfig(...)` method on your `EventListenerProvider`.

### Enabling running multiple EventListenerProvider instances of the same type

1. Extend the abstract class `MultiEventListenerProviderFactory`. Implement the abstract method `EventListenerProvider configure(KeycloakSession session, Map<String, Object> config)`, which is otherwise the same as the `create(...)` method, but also takes a config map. This **should not return a singleton**.

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

