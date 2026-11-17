# Configuring event listeners

The event listeners in this extension are configured per realm, and more than one instance of the same listener can run in a realm, each with its own configuration. This page explains how that works, and how to use the same mechanism in your own listeners.

This applies to the [script listener](script-listener.md) and the [HTTP sender](http-sender.md). [Webhooks](webhooks.md) are managed through their own REST API instead.

## Enabling a listener

A listener does nothing until it is enabled for a realm:

1. Open the Keycloak admin console and select the realm.
2. Go to **Realm settings** > **Events** > **Event listeners**.
3. Add the listener's provider id (for example `ext-event-script` or `ext-event-webhook`) and click **Save**.

## Where configuration is stored

Configuration is stored in realm attributes, so it can be changed at runtime without restarting Keycloak.

| | |
| --- | --- |
| Attribute key | `_providerConfig.<provider-id>` for one instance, or `_providerConfig.<provider-id>.<N>` for several, where `N` is an integer |
| Attribute value | A JSON object. Values are strings, numbers or booleans, except where a listener's own page says an option takes an object. |

For example, two scripts are configured with the attributes `_providerConfig.ext-event-script.0` and `_providerConfig.ext-event-script.1`. Each gets its own listener instance.

## Setting realm attributes

Keycloak's built-in admin console doesn't show realm attributes. Set them one of these ways:

- **The realm attributes API** that this extension adds (below).
- **Keycloak's [realm update](https://www.keycloak.org/docs-api/latest/rest-api/index.html#_put_adminrealmsrealm) API**, by sending the realm's `attributes`.
- **The Phase Two admin console theme.** The [Phase Two Keycloak image](https://quay.io/repository/phasetwo/phasetwo-keycloak?tab=tags) with the admin theme set to `phasetwo.v2` has a page for editing realm attributes.

### Realm attributes API

| Path | Method | Payload | Returns | Requires |
| --- | --- | --- | --- | --- |
| `/auth/realms/:realm/attributes` | `GET` | | All realm attributes | `view-realm` |
| `/auth/realms/:realm/attributes` | `POST` | Attribute object | `201` | `manage-realm` |
| `/auth/realms/:realm/attributes/:key` | `GET` | | Attribute object | `view-realm` |
| `/auth/realms/:realm/attributes/:key` | `PUT` | Attribute object | `204` | `manage-realm` |
| `/auth/realms/:realm/attributes/:key` | `DELETE` | | `204` | `manage-realm` |

The attribute object has a `name`, a `value` and the `realm`. To configure a script:

```
POST /auth/realms/my-realm/attributes

{
  "name": "_providerConfig.ext-event-script.0",
  "value": "{\"scriptName\":\"Log logins\",\"scriptDescription\":\"Logs each event\",\"scriptCode\":\"function onEvent(event) { LOG.info(event.type); }\"}",
  "realm": "my-realm"
}
```

The `value` is a string, so the JSON configuration inside it is escaped.

## Using this in your own listeners

Two pieces are available to listeners you write yourself.

### Load configuration from realm attributes

1. Implement `ConfigurationAware` in your `EventListenerProviderFactory`. It has no methods to implement. It gives you `getConfiguration` and `getConfigurations`, which load the configuration stored for your factory's provider id.
2. Implement `Configurable` in your `EventListenerProvider`. It has one method, `void setConfig(Map<String, Object> config)`.
3. In your factory's `create(...)` method, call `setConfig(...)` on the provider.

### Run several instances with different configurations

Extend `MultiEventListenerProviderFactory` and implement:

```java
EventListenerProvider configure(KeycloakSession session, Map<String, Object> config)
```

It is the same as `create(...)`, except that it also receives one instance's configuration. It is called once for each `_providerConfig.<provider-id>.<N>` attribute, so it **must not return a singleton**.

`ScriptEventListenerProviderFactory` and `HttpSenderEventListenerProviderFactory` are working examples.
