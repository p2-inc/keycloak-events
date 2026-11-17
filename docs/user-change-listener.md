# User change listener

`UserEventListenerProviderFactory` is a base class for the common need to "do something when a user is added or removed". It does nothing on its own: you subclass it and provide the code to run.

## How it detects changes

| Change | Detected from |
| --- | --- |
| User added | The admin event for creating a user, and the user event for self-registration |
| User removed | Keycloak's internal `UserModel.UserRemovedEvent` |

## Example

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

Package the class in a jar with a `META-INF/services/org.keycloak.events.EventListenerProviderFactory` entry, put it in Keycloak's `providers/` directory, and enable the listener (`ext-event-myuseraddremove` here) under **Realm settings** > **Events** > **Event listeners**.

## What it doesn't do

It has not been tested with users that are created by an identity provider login or by user federation (LDAP, custom user storage). Those users may be added without the events this listener watches for, so `onUserAdded` may not be called for them.
