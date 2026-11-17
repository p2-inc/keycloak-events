# Script event listener (`ext-event-script`)

The script listener runs JavaScript that you provide each time an event happens in a realm. It works the same way as Keycloak's script-based authenticators: the script runs inside the Keycloak server and has access to its objects.

Use it for small reactions to events, such as logging or updating a user attribute. To send events to another system, [webhooks](webhooks.md) or the [HTTP sender](http-sender.md) are usually a better fit: they retry, and they don't run your code inside Keycloak.

## What the script can use

Define either or both of these functions:

```js
function onEvent(event) { }                      // user events
function onAdminEvent(event, representation) { } // admin events
```

These variables are available to them:

| Variable | Contents |
| --- | --- |
| `event` | The `Event` or `AdminEvent` |
| `realm` | The `RealmModel` |
| `user` | The `UserModel` of the event's user (`onEvent` only) |
| `authUser` | The `UserModel` of the admin who made the change (`onAdminEvent` only) |
| `session` | The `KeycloakSession` |
| `LOG` | A JBoss `Logger` |

## Set it up

1. **Enable the listener.** In the admin console, go to **Realm settings** > **Events** > **Event listeners**, add `ext-event-script`, and click **Save**.
2. **Add the script** as the realm attribute `_providerConfig.ext-event-script.N`, where `N` is an integer that is different for each script (`0`, `1`, `2`, ...). Scripts run in the order of `N`. See [Setting realm attributes](configuration.md#setting-realm-attributes) for how to set the attribute.

The attribute's value is a JSON object with these fields, all required:

| Name | Description |
| --- | --- |
| `scriptName` | A name for the script |
| `scriptDescription` | What the script does |
| `scriptCode` | The JavaScript code |

## Example

Set the realm attribute `_providerConfig.ext-event-script.0` to:

```json
{
  "scriptName": "Example Script",
  "scriptDescription": "Logs events to the console",
  "scriptCode": "function onEvent(event) {\n  LOG.info(event.type + \" in realm \" + realm.name + \" for user \" + user.username);\n}\n\nfunction onAdminEvent(event, representation) {\n  LOG.info(event.operationType + \" on \" + event.resourceType + \" in realm \" + realm.name + \" by user \" + authUser.username);\n}"
}
```

## What it doesn't do

- **It doesn't sandbox the script.** The script runs in the Keycloak process with access to the session, so anyone who can set realm attributes can run code in the server.
- **It doesn't retry.** If the script throws, the event is not processed again.

## Disabling scripts on a server

On shared servers, where realm administrators shouldn't be able to run code in the Keycloak process, set this environment variable:

```
SCRIPTS_DISABLED=true
```

Configured scripts then don't run. They fail silently: nothing tells the realm administrator that the script was skipped.
